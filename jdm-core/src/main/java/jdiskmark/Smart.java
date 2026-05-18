package jdiskmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses the JSON output of {@code smartctl --json -a /dev/&lt;device&gt;}.
 *
 * <p>Top-level sections covered:
 * <ul>
 *   <li>{@code smartctl}         – tool version / exit status</li>
 *   <li>{@code device}           – device name, type, protocol</li>
 *   <li>{@code model_family}     – drive family string (SATA only)</li>
 *   <li>{@code model_name}       – drive model</li>
 *   <li>{@code serial_number}    – serial number</li>
 *   <li>{@code firmware_version} – firmware version string</li>
 *   <li>{@code user_capacity}    – drive capacity</li>
 *   <li>{@code smart_status}     – overall SMART passed/failed</li>
 *   <li>{@code temperature}      – current / drive-trip temps</li>
 *   <li>{@code power_on_time}    – hours powered on</li>
 *   <li>{@code power_cycle_count}– number of power cycles</li>
 *   <li>{@code ata_smart_attributes} – classical ATA SMART attributes table</li>
 *   <li>{@code nvme_smart_health_information_log} – NVMe health log</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 *   String json = ...; // output from smartctl --json -a /dev/nvme0n1
 *   Smart data = Smart.fromJson(json);
 *   System.out.println(data.getModelName());
 *   System.out.println(data.getSmartStatus().isPassed());
 * }</pre>
 *
 * @author jasmine
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Smart {

    // -------------------------------------------------------------------------
    // Feature toggle
    // -------------------------------------------------------------------------

    /** Whether SMART data collection is enabled. Persisted in app.properties. */
    public static boolean smartEnable = false;
    
    /** The long-lived privileged bash shell process, started once via pkexec. */
    public static Process process;
    /** Writer attached to the bash shell's stdin for sending commands. */
    public static BufferedWriter shellWriter;
    /** Reader attached to the bash shell's stdout for reading command output. */
    public static BufferedReader shellReader;
    public static Thread hbThread;
    public static final Object pLock = new Object();

    private static final Logger LOGGER = Logger.getLogger(Smart.class.getName());

    /**
     * Launches a single {@code pkexec bash} process and wires up the
     * shared {@link #shellWriter} / {@link #shellReader}.  The user is
     * prompted for their password exactly once; subsequent SMART queries
     * reuse this shell without re-escalating privileges.
     *
     * <p>Safe to call multiple times — a no-op if the shell is already alive.
     *
     * @throws IOException if the process cannot be started
     */
    public static void startPrivilegedShell() throws IOException {
        synchronized (pLock) {
            if (process != null && process.isAlive()) {
                return; // already running
            }
            LOGGER.info("Starting privileged bash shell via pkexec...");
            ProcessBuilder pb = new ProcessBuilder("pkexec", "bash");
            pb.redirectErrorStream(false); // keep stderr separate from stdout
            process = pb.start();
            shellWriter = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            shellReader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            LOGGER.info("Privileged shell started (pid reuse enabled).");
        }
    }

    /**
     * Starts a background keepalive thread that pings the privileged bash
     * shell every 5 minutes with a no-op echo so the process stays alive.
     * Only one thread is started; subsequent calls are ignored.
     */
    public static void startHeartbeat() {
        if (hbThread != null && hbThread.isAlive()) return;
        hbThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TimeUnit.MINUTES.sleep(5);
                    synchronized (pLock) {
                        if (process != null && process.isAlive() && shellWriter != null) {
                            shellWriter.write("echo heartbeat\n");
                            shellWriter.flush();
                            // Drain the echo reply so it doesn't pollute the next getSmart() read
                            shellReader.readLine();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Heartbeat write failed; shell may have died", ex);
                }
            }
        }, "smart-heartbeat");
        hbThread.setDaemon(true);
        hbThread.start();
    }
    
    /**
     * Queries SMART data for the given device by writing a {@code smartctl}
     * command to the persistent privileged shell and reading back its output
     * up to a unique sentinel line.  Logs the key fields at INFO level.
     *
     * <p>{@link #startPrivilegedShell()} must have been called before this.
     *
     * @param deviceName bare device name, e.g. {@code nvme0n1}
     * @return a populated {@link Smart} instance, or {@code null} on error
     */
    public static Smart getSmart(String deviceName) {
        final String sentinel = "---SMART_DONE---";
        try {
            synchronized (pLock) {
                // Write the smartctl command followed by an echo of the sentinel
                // so we know exactly where the JSON output ends.
                shellWriter.write("/usr/sbin/smartctl --json -a /dev/" + deviceName + "\n");
                shellWriter.write("echo '" + sentinel + "'\n");
                shellWriter.flush();

                // Accumulate lines until the sentinel appears
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = shellReader.readLine()) != null) {
                    if (sentinel.equals(line)) break;
                    sb.append(line).append('\n');
                }

                String result = sb.toString().trim();
                if (result.isEmpty()) {
                    LOGGER.severe("getSmart: empty response from shell for device " + deviceName);
                    return null;
                }

                Smart smart = fromJson(result);
                logSmart(smart);
                return smart;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "getSmart failed for device: " + deviceName, ex);
        }
        return null;
    }

    /** Logs the key SMART fields at INFO level. */
    public static void logSmart(Smart smart) {
        if (smart == null) return;
        LOGGER.log(Level.INFO, "SMART model      : {0}", smart.getModelName());
        LOGGER.log(Level.INFO, "SMART serial     : {0}", smart.getSerialNumber());
        LOGGER.log(Level.INFO, "SMART firmware   : {0}", smart.getFirmwareVersion());
        if (smart.getSmartStatus() != null) {
            LOGGER.log(Level.INFO, "SMART status     : {0}",
                smart.getSmartStatus().isPassed() ? "PASSED" : "FAILED");
        }
        if (smart.getTemperature() != null) {
            LOGGER.log(Level.INFO, "SMART temp       : {0} C", smart.getTemperature().getCurrent());
        }
        if (smart.getPowerOnTime() != null) {
            LOGGER.log(Level.INFO, "SMART power-on   : {0} hours", smart.getPowerOnTime().getHours());
        }
        if (smart.getNvmeHealthLog() != null) {
            NvmeHealthLog nvme = smart.getNvmeHealthLog();
            LOGGER.log(Level.INFO, "NVMe avail spare : {0}%", nvme.getAvailableSpare());
            LOGGER.log(Level.INFO, "NVMe used %      : {0}%", nvme.getPercentageUsed());
            LOGGER.log(Level.INFO, "NVMe written     : {0} GB", nvme.getDataWrittenGb());
            LOGGER.log(Level.INFO, "NVMe read        : {0} GB", nvme.getDataReadGb());
            LOGGER.log(Level.INFO, "NVMe media errs  : {0}", nvme.getMediaErrors());
            if (nvme.hasCriticalWarning()) {
                LOGGER.log(Level.WARNING, "NVMe critical warning flag: {0}", nvme.getCriticalWarning());
            }
        }
    }
            
    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @JsonProperty("json_format_version")
    private List<Integer> jsonFormatVersion;

    @JsonProperty("smartctl")
    private SmartctlInfo smartctlInfo;

    @JsonProperty("device")
    private DeviceInfo device;

    @JsonProperty("model_family")
    private String modelFamily;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("firmware_version")
    private String firmwareVersion;

    @JsonProperty("user_capacity")
    private UserCapacity userCapacity;

    @JsonProperty("smart_status")
    private SmartStatus smartStatus;

    @JsonProperty("temperature")
    private Temperature temperature;

    @JsonProperty("power_on_time")
    private PowerOnTime powerOnTime;

    @JsonProperty("power_cycle_count")
    private Integer powerCycleCount;

    @JsonProperty("ata_smart_attributes")
    private AtaSmartAttributes ataSmartAttributes;

    @JsonProperty("nvme_smart_health_information_log")
    private NvmeHealthLog nvmeHealthLog;

    // -------------------------------------------------------------------------
    // Factory / parsing
    // -------------------------------------------------------------------------

    /** No-arg constructor required by Jackson. */
    public Smart() {}

    /**
     * Parses a {@code smartctl --json -a} JSON string into a {@link Smart} object.
     *
     * @param json the raw JSON string from smartctl
     * @return a populated {@link Smart} instance
     * @throws IOException if the JSON cannot be parsed
     */
    public static Smart fromJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, Smart.class);
    }

    // -------------------------------------------------------------------------
    // Top-level getters & setters
    // -------------------------------------------------------------------------

    public List<Integer> getJsonFormatVersion() { return jsonFormatVersion; }
    public void setJsonFormatVersion(List<Integer> jsonFormatVersion) { this.jsonFormatVersion = jsonFormatVersion; }

    public SmartctlInfo getSmartctlInfo() { return smartctlInfo; }
    public void setSmartctlInfo(SmartctlInfo smartctlInfo) { this.smartctlInfo = smartctlInfo; }

    public DeviceInfo getDevice() { return device; }
    public void setDevice(DeviceInfo device) { this.device = device; }

    public String getModelFamily() { return modelFamily; }
    public void setModelFamily(String modelFamily) { this.modelFamily = modelFamily; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }

    public UserCapacity getUserCapacity() { return userCapacity; }
    public void setUserCapacity(UserCapacity userCapacity) { this.userCapacity = userCapacity; }

    public SmartStatus getSmartStatus() { return smartStatus; }
    public void setSmartStatus(SmartStatus smartStatus) { this.smartStatus = smartStatus; }

    public Temperature getTemperature() { return temperature; }
    public void setTemperature(Temperature temperature) { this.temperature = temperature; }

    public PowerOnTime getPowerOnTime() { return powerOnTime; }
    public void setPowerOnTime(PowerOnTime powerOnTime) { this.powerOnTime = powerOnTime; }

    public Integer getPowerCycleCount() { return powerCycleCount; }
    public void setPowerCycleCount(Integer powerCycleCount) { this.powerCycleCount = powerCycleCount; }

    public AtaSmartAttributes getAtaSmartAttributes() { return ataSmartAttributes; }
    public void setAtaSmartAttributes(AtaSmartAttributes ataSmartAttributes) { this.ataSmartAttributes = ataSmartAttributes; }

    public NvmeHealthLog getNvmeHealthLog() { return nvmeHealthLog; }
    public void setNvmeHealthLog(NvmeHealthLog nvmeHealthLog) { this.nvmeHealthLog = nvmeHealthLog; }

    // =========================================================================
    // Nested classes
    // =========================================================================

    // -------------------------------------------------------------------------
    // smartctl tool info
    // -------------------------------------------------------------------------

    /** Represents the {@code smartctl} block containing tool version information. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SmartctlInfo {

        @JsonProperty("version")
        private List<Integer> version;

        @JsonProperty("svn_revision")
        private String svnRevision;

        @JsonProperty("platform_info")
        private String platformInfo;

        @JsonProperty("build_info")
        private String buildInfo;

        @JsonProperty("exit_status")
        private Integer exitStatus;

        @JsonProperty("messages")
        private List<SmartMessage> messages;

        public SmartctlInfo() {}

        public List<Integer> getVersion() { return version; }
        public void setVersion(List<Integer> version) { this.version = version; }

        public String getSvnRevision() { return svnRevision; }
        public void setSvnRevision(String svnRevision) { this.svnRevision = svnRevision; }

        public String getPlatformInfo() { return platformInfo; }
        public void setPlatformInfo(String platformInfo) { this.platformInfo = platformInfo; }

        public String getBuildInfo() { return buildInfo; }
        public void setBuildInfo(String buildInfo) { this.buildInfo = buildInfo; }

        public Integer getExitStatus() { return exitStatus; }
        public void setExitStatus(Integer exitStatus) { this.exitStatus = exitStatus; }

        public List<SmartMessage> getMessages() { return messages; }
        public void setMessages(List<SmartMessage> messages) { this.messages = messages; }

        /** Returns a version string such as {@code "7.2"}. */
        public String getVersionString() {
            if (version == null || version.isEmpty()) return "unknown";
            if (version.size() >= 2) return version.get(0) + "." + version.get(1);
            return String.valueOf(version.get(0));
        }
    }

    // -------------------------------------------------------------------------
    // smartctl messages
    // -------------------------------------------------------------------------

    /** A single message entry inside the {@code smartctl.messages} array. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SmartMessage {

        @JsonProperty("string")
        private String string;

        @JsonProperty("severity")
        private String severity;

        public SmartMessage() {}

        public String getString() { return string; }
        public void setString(String string) { this.string = string; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }

    // -------------------------------------------------------------------------
    // device
    // -------------------------------------------------------------------------

    /** Represents the {@code device} block (name, info_name, type, protocol). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceInfo {

        @JsonProperty("name")
        private String name;

        @JsonProperty("info_name")
        private String infoName;

        @JsonProperty("type")
        private String type;

        @JsonProperty("protocol")
        private String protocol;

        public DeviceInfo() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getInfoName() { return infoName; }
        public void setInfoName(String infoName) { this.infoName = infoName; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
    }

    // -------------------------------------------------------------------------
    // user_capacity
    // -------------------------------------------------------------------------

    /** Represents the {@code user_capacity} block (bytes and blocks). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserCapacity {

        @JsonProperty("blocks")
        private Long blocks;

        @JsonProperty("bytes")
        private Long bytes;

        public UserCapacity() {}

        public Long getBlocks() { return blocks; }
        public void setBlocks(Long blocks) { this.blocks = blocks; }

        public Long getBytes() { return bytes; }
        public void setBytes(Long bytes) { this.bytes = bytes; }

        /** Returns capacity in GB (1 GB = 10^9 bytes), rounded to 2 decimal places. */
        public double getCapacityGb() {
            if (bytes == null) return 0;
            return Math.round((bytes / 1_000_000_000.0) * 100.0) / 100.0;
        }
    }

    // -------------------------------------------------------------------------
    // smart_status
    // -------------------------------------------------------------------------

    /** Represents the {@code smart_status} block. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SmartStatus {

        @JsonProperty("passed")
        private Boolean passed;

        public SmartStatus() {}

        public Boolean isPassed() { return passed; }
        public void setPassed(Boolean passed) { this.passed = passed; }
    }

    // -------------------------------------------------------------------------
    // temperature
    // -------------------------------------------------------------------------

    /** Represents the {@code temperature} block. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Temperature {

        @JsonProperty("current")
        private Integer current;

        @JsonProperty("drive_trip")
        private Integer driveTrip;

        public Temperature() {}

        public Integer getCurrent() { return current; }
        public void setCurrent(Integer current) { this.current = current; }

        public Integer getDriveTrip() { return driveTrip; }
        public void setDriveTrip(Integer driveTrip) { this.driveTrip = driveTrip; }
    }

    // -------------------------------------------------------------------------
    // power_on_time
    // -------------------------------------------------------------------------

    /** Represents the {@code power_on_time} block. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PowerOnTime {

        @JsonProperty("hours")
        private Long hours;

        @JsonProperty("minutes")
        private Integer minutes;

        public PowerOnTime() {}

        public Long getHours() { return hours; }
        public void setHours(Long hours) { this.hours = hours; }

        public Integer getMinutes() { return minutes; }
        public void setMinutes(Integer minutes) { this.minutes = minutes; }
    }

    // =========================================================================
    // ATA SMART attributes  (SATA / SAS drives)
    // =========================================================================

    /** Container for the {@code ata_smart_attributes} block. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtaSmartAttributes {

        @JsonProperty("revision")
        private Integer revision;

        @JsonProperty("table")
        private List<AtaAttribute> table;

        public AtaSmartAttributes() {}

        public Integer getRevision() { return revision; }
        public void setRevision(Integer revision) { this.revision = revision; }

        public List<AtaAttribute> getTable() { return table; }
        public void setTable(List<AtaAttribute> table) { this.table = table; }
    }

    // -------------------------------------------------------------------------
    // Individual ATA attribute
    // -------------------------------------------------------------------------

    /** One row in the ATA SMART attributes table. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtaAttribute {

        @JsonProperty("id")
        private Integer id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("value")
        private Integer value;

        @JsonProperty("worst")
        private Integer worst;

        @JsonProperty("thresh")
        private Integer thresh;

        @JsonProperty("when_failed")
        private String whenFailed;

        @JsonProperty("flags")
        private AtaAttributeFlags flags;

        @JsonProperty("raw")
        private AtaAttributeRaw raw;

        public AtaAttribute() {}

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }

        public Integer getWorst() { return worst; }
        public void setWorst(Integer worst) { this.worst = worst; }

        public Integer getThresh() { return thresh; }
        public void setThresh(Integer thresh) { this.thresh = thresh; }

        public String getWhenFailed() { return whenFailed; }
        public void setWhenFailed(String whenFailed) { this.whenFailed = whenFailed; }

        public AtaAttributeFlags getFlags() { return flags; }
        public void setFlags(AtaAttributeFlags flags) { this.flags = flags; }

        public AtaAttributeRaw getRaw() { return raw; }
        public void setRaw(AtaAttributeRaw raw) { this.raw = raw; }

        /** Returns {@code true} if the attribute's value is below its threshold. */
        public boolean isFailing() {
            return value != null && thresh != null && value < thresh;
        }
    }

    // -------------------------------------------------------------------------
    // ATA attribute flags
    // -------------------------------------------------------------------------

    /** The {@code flags} subobject of an ATA attribute. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtaAttributeFlags {

        @JsonProperty("value")
        private Integer value;

        @JsonProperty("string")
        private String string;

        @JsonProperty("prefailure")
        private Boolean prefailure;

        @JsonProperty("updated_online")
        private Boolean updatedOnline;

        @JsonProperty("performance")
        private Boolean performance;

        @JsonProperty("error_rate")
        private Boolean errorRate;

        @JsonProperty("event_count")
        private Boolean eventCount;

        @JsonProperty("auto_keep")
        private Boolean autoKeep;

        public AtaAttributeFlags() {}

        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }

        public String getString() { return string; }
        public void setString(String string) { this.string = string; }

        public Boolean isPrefailure() { return prefailure; }
        public void setPrefailure(Boolean prefailure) { this.prefailure = prefailure; }

        public Boolean isUpdatedOnline() { return updatedOnline; }
        public void setUpdatedOnline(Boolean updatedOnline) { this.updatedOnline = updatedOnline; }

        public Boolean isPerformance() { return performance; }
        public void setPerformance(Boolean performance) { this.performance = performance; }

        public Boolean isErrorRate() { return errorRate; }
        public void setErrorRate(Boolean errorRate) { this.errorRate = errorRate; }

        public Boolean isEventCount() { return eventCount; }
        public void setEventCount(Boolean eventCount) { this.eventCount = eventCount; }

        public Boolean isAutoKeep() { return autoKeep; }
        public void setAutoKeep(Boolean autoKeep) { this.autoKeep = autoKeep; }
    }

    // -------------------------------------------------------------------------
    // ATA attribute raw value
    // -------------------------------------------------------------------------

    /** The {@code raw} subobject of an ATA attribute. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtaAttributeRaw {

        @JsonProperty("value")
        private Long value;

        @JsonProperty("string")
        private String string;

        public AtaAttributeRaw() {}

        public Long getValue() { return value; }
        public void setValue(Long value) { this.value = value; }

        public String getString() { return string; }
        public void setString(String string) { this.string = string; }
    }

    // =========================================================================
    // NVMe health log  (NVMe drives)
    // =========================================================================

    /**
     * Represents the {@code nvme_smart_health_information_log} block returned
     * for NVMe devices.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NvmeHealthLog {

        @JsonProperty("critical_warning")
        private Integer criticalWarning;

        @JsonProperty("temperature")
        private Integer temperature;

        @JsonProperty("available_spare")
        private Integer availableSpare;

        @JsonProperty("available_spare_threshold")
        private Integer availableSpareThreshold;

        @JsonProperty("percentage_used")
        private Integer percentageUsed;

        @JsonProperty("data_units_read")
        private Long dataUnitsRead;

        @JsonProperty("data_units_written")
        private Long dataUnitsWritten;

        @JsonProperty("host_reads")
        private Long hostReads;

        @JsonProperty("host_writes")
        private Long hostWrites;

        @JsonProperty("controller_busy_time")
        private Long controllerBusyTime;

        @JsonProperty("power_cycles")
        private Long powerCycles;

        @JsonProperty("power_on_hours")
        private Long powerOnHours;

        @JsonProperty("unsafe_shutdowns")
        private Long unsafeShutdowns;

        @JsonProperty("media_errors")
        private Long mediaErrors;

        @JsonProperty("num_err_log_entries")
        private Long numErrLogEntries;

        @JsonProperty("warning_temp_time")
        private Long warningTempTime;

        @JsonProperty("critical_comp_time")
        private Long criticalCompTime;

        public NvmeHealthLog() {}

        public Integer getCriticalWarning() { return criticalWarning; }
        public void setCriticalWarning(Integer criticalWarning) { this.criticalWarning = criticalWarning; }

        public Integer getTemperature() { return temperature; }
        public void setTemperature(Integer temperature) { this.temperature = temperature; }

        public Integer getAvailableSpare() { return availableSpare; }
        public void setAvailableSpare(Integer availableSpare) { this.availableSpare = availableSpare; }

        public Integer getAvailableSpareThreshold() { return availableSpareThreshold; }
        public void setAvailableSpareThreshold(Integer availableSpareThreshold) { this.availableSpareThreshold = availableSpareThreshold; }

        public Integer getPercentageUsed() { return percentageUsed; }
        public void setPercentageUsed(Integer percentageUsed) { this.percentageUsed = percentageUsed; }

        public Long getDataUnitsRead() { return dataUnitsRead; }
        public void setDataUnitsRead(Long dataUnitsRead) { this.dataUnitsRead = dataUnitsRead; }

        public Long getDataUnitsWritten() { return dataUnitsWritten; }
        public void setDataUnitsWritten(Long dataUnitsWritten) { this.dataUnitsWritten = dataUnitsWritten; }

        public Long getHostReads() { return hostReads; }
        public void setHostReads(Long hostReads) { this.hostReads = hostReads; }

        public Long getHostWrites() { return hostWrites; }
        public void setHostWrites(Long hostWrites) { this.hostWrites = hostWrites; }

        public Long getControllerBusyTime() { return controllerBusyTime; }
        public void setControllerBusyTime(Long controllerBusyTime) { this.controllerBusyTime = controllerBusyTime; }

        public Long getPowerCycles() { return powerCycles; }
        public void setPowerCycles(Long powerCycles) { this.powerCycles = powerCycles; }

        public Long getPowerOnHours() { return powerOnHours; }
        public void setPowerOnHours(Long powerOnHours) { this.powerOnHours = powerOnHours; }

        public Long getUnsafeShutdowns() { return unsafeShutdowns; }
        public void setUnsafeShutdowns(Long unsafeShutdowns) { this.unsafeShutdowns = unsafeShutdowns; }

        public Long getMediaErrors() { return mediaErrors; }
        public void setMediaErrors(Long mediaErrors) { this.mediaErrors = mediaErrors; }

        public Long getNumErrLogEntries() { return numErrLogEntries; }
        public void setNumErrLogEntries(Long numErrLogEntries) { this.numErrLogEntries = numErrLogEntries; }

        public Long getWarningTempTime() { return warningTempTime; }
        public void setWarningTempTime(Long warningTempTime) { this.warningTempTime = warningTempTime; }

        public Long getCriticalCompTime() { return criticalCompTime; }
        public void setCriticalCompTime(Long criticalCompTime) { this.criticalCompTime = criticalCompTime; }

        /**
         * Returns {@code true} if {@code critical_warning} is non-zero,
         * indicating a health issue that needs attention.
         */
        public boolean hasCriticalWarning() {
            return criticalWarning != null && criticalWarning != 0;
        }

        /**
         * Converts NVMe data units written (1 unit = 512,000 bytes) to GB.
         * Returns 0 if the field is null.
         */
        public double getDataWrittenGb() {
            if (dataUnitsWritten == null) return 0;
            return Math.round((dataUnitsWritten * 512_000.0 / 1_000_000_000.0) * 100.0) / 100.0;
        }

        /**
         * Converts NVMe data units read (1 unit = 512,000 bytes) to GB.
         * Returns 0 if the field is null.
         */
        public double getDataReadGb() {
            if (dataUnitsRead == null) return 0;
            return Math.round((dataUnitsRead * 512_000.0 / 1_000_000_000.0) * 100.0) / 100.0;
        }
    }
}
