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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
     * Resolves the path to the {@code smartctl} binary, preferring a bundled
     * copy shipped with the JDiskMark fat installer over any system installation.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code $APPDIR/../smartctl/smartctl} — Linux jpackage sets {@code APPDIR}
     *       to the {@code app/} subdirectory of the install root. The bundled binary
     *       lands one level up at {@code /opt/jdiskmark/smartctl/smartctl}.</li>
     *   <li>macOS {@code .app} bundle — {@code APPDIR} is not set by macOS jpackage.
     *       Instead, the jar's own {@code CodeSource} location is used to find
     *       {@code Contents/app/<jar>} → walk up to {@code Contents/smartctl/smartctl},
     *       where {@code package-pkg.sh} injects the bundled binary.</li>
     *   <li>{@code /opt/jdiskmark/smartctl/smartctl} — well-known absolute path
     *       for the fat Linux DEB install when {@code APPDIR} is not in the env.</li>
     *   <li>Homebrew — {@code /usr/local/bin/smartctl} (Intel) or
     *       {@code /opt/homebrew/bin/smartctl} (Apple Silicon) for dev machines.</li>
     *   <li>{@code /usr/sbin/smartctl} — system fallback for slim-DEB / other Unix.</li>
     * </ol>
     */
    static String resolveSmartctlPath() {
        if (App.isWindows()) {
            // 1. Bundled copy — derive app dir from the running jar's location.
            //    jpackage on Windows does NOT set APPDIR (that's Linux-only).
            //    jar lives at <install-dir>\app\<jar>.jar
            //    → <install-dir>\app\smartctl\smartctl.exe
            try {
                java.net.URL jarUrl = Smart.class.getProtectionDomain().getCodeSource().getLocation();
                if (jarUrl != null) {
                    Path jarPath = Path.of(jarUrl.toURI());
                    Path appDir  = jarPath.getParent();   // …\app\
                    Path bundled = appDir.resolve("smartctl/smartctl.exe");
                    if (Files.isExecutable(bundled)) {
                        LOGGER.info("Using bundled smartctl (jar-relative): " + bundled);
                        return bundled.toString();
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("resolveSmartctlPath: jar URL lookup failed: " + e.getMessage());
            }
            // 2. Fallback via java.home: runtime\ is sibling of app\
            //    <install-dir>\runtime  →  <install-dir>\app\smartctl\smartctl.exe
            try {
                Path runtimeDir = Path.of(System.getProperty("java.home"));
                Path bundled = runtimeDir.getParent().resolve("app/smartctl/smartctl.exe");
                if (Files.isExecutable(bundled)) {
                    LOGGER.info("Using bundled smartctl (java.home-relative): " + bundled);
                    return bundled.toString();
                }
            } catch (Exception e) {
                LOGGER.warning("resolveSmartctlPath: java.home lookup failed: " + e.getMessage());
            }
            // 3. Legacy: APPDIR env var (set by some custom launchers, not standard jpackage on Windows)
            String appDirEnv = System.getenv("APPDIR");
            if (appDirEnv != null) {
                Path bundled = Path.of(appDirEnv).resolve("smartctl/smartctl.exe");
                if (Files.isExecutable(bundled)) {
                    LOGGER.info("Using bundled smartctl (APPDIR): " + bundled);
                    return bundled.toString();
                }
            }
            // 4. Well-known system installation paths
            Path installed = Path.of("C:\\Program Files\\smartmontools\\bin\\smartctl.exe");
            if (Files.isExecutable(installed)) {
                LOGGER.info("Using installed smartctl: " + installed);
                return installed.toString();
            }
            installed = Path.of("C:\\Program Files (x86)\\smartmontools\\bin\\smartctl.exe");
            if (Files.isExecutable(installed)) {
                LOGGER.info("Using installed smartctl: " + installed);
                return installed.toString();
            }
            // 5. System PATH fallback
            LOGGER.info("Using system smartctl: smartctl.exe");
            return "smartctl.exe";
        }

        // 1. Bundled copy via APPDIR (Linux jpackage sets this; macOS does not).
        String appDir = System.getenv("APPDIR");
        if (appDir != null) {
            Path bundled = Path.of(appDir).getParent().resolve("smartctl/smartctl");
            if (Files.isExecutable(bundled)) {
                LOGGER.info("Using bundled smartctl (APPDIR): " + bundled);
                return bundled.toString();
            }
        }

        // 2. macOS .app bundle: APPDIR is not set, but the fat jar lives at
        //    Contents/app/<jar>. Walk up to Contents/ and look for the bundled
        //    smartctl injected by package-pkg.sh at Contents/smartctl/smartctl.
        try {
            java.net.URL jarUrl = Smart.class.getProtectionDomain().getCodeSource().getLocation();
            if (jarUrl != null) {
                // jarUrl → file:/Applications/JDiskMark.app/Contents/app/<jar>
                Path jarPath   = Path.of(jarUrl.toURI());          // …/Contents/app/<jar>
                Path contentsDir = jarPath.getParent().getParent(); // …/Contents/
                Path macBundled  = contentsDir.resolve("smartctl/smartctl");
                if (Files.isExecutable(macBundled)) {
                    LOGGER.info("Using bundled smartctl (macOS app bundle): " + macBundled);
                    return macBundled.toString();
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "macOS bundle smartctl lookup failed", ex);
        }

        // 2b. Dev / IDE mode: when running from NetBeans (or any IDE) the
        //     CodeSource points to target/classes/ (or the fat jar in target/).
        //     One level up lands in target/, where the Maven smartctl-macos
        //     profile stages the binary at target/smartctl/smartctl.
        //     The packaged .app bundle check above goes up two levels
        //     (Contents/app/<jar> → Contents/), which overshoots in dev mode.
        try {
            java.net.URL jarUrl = Smart.class.getProtectionDomain().getCodeSource().getLocation();
            if (jarUrl != null) {
                Path jarPath  = Path.of(jarUrl.toURI());
                Path buildDir = jarPath.getParent();              // …/target/
                Path devBundled = buildDir.resolve("smartctl/smartctl");
                if (Files.isExecutable(devBundled)) {
                    LOGGER.info("Using dev/IDE smartctl (CodeSource-relative): " + devBundled);
                    return devBundled.toString();
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Dev/IDE smartctl lookup failed", ex);
        }

        // 3. Well-known absolute path (fat DEB install without APPDIR in env)
        Path installed = Path.of("/opt/jdiskmark/smartctl/smartctl");
        if (Files.isExecutable(installed)) {
            LOGGER.info("Using installed smartctl: " + installed);
            return installed.toString();
        }

        // 4. Homebrew locations — common on dev machines and macOS without a system smartctl
        for (String brew : new String[]{
                "/usr/local/bin/smartctl",   // Homebrew on Intel Mac
                "/opt/homebrew/bin/smartctl" // Homebrew on Apple Silicon
        }) {
            Path brewPath = Path.of(brew);
            if (Files.isExecutable(brewPath)) {
                LOGGER.info("Using Homebrew smartctl: " + brewPath);
                return brewPath.toString();
            }
        }

        // 5. System fallback — slim DEB with apt smartmontools, or any other Unix path
        LOGGER.info("Using system smartctl: /usr/sbin/smartctl");
        return "/usr/sbin/smartctl";
    }

    /**
     * Launches a single privileged {@code bash} process and wires up the
     * shared {@link #shellWriter} / {@link #shellReader}.  The user is
     * prompted for their password exactly once; subsequent SMART queries
     * reuse this shell without re-escalating privileges.
     *
     * <p>On Linux, privilege escalation uses {@code pkexec bash}.
     * On macOS, a native password dialog ({@code osascript display dialog})
     * collects the password, which is fed to {@code sudo -S bash}.
     *
     * <p>Safe to call multiple times — a no-op if the shell is already alive.
     *
     * @throws IOException if the process cannot be started
     */
    public static void startPrivilegedShell() throws IOException {
        if (App.isWindows()) {
            return;
        }
        synchronized (pLock) {
            if (process != null && process.isAlive()) {
                return; // already running
            }

            if (App.isMacOs()) {
                LOGGER.info("Starting privileged bash shell via osascript/sudo...");

                // macOS: prompt for password using native macOS authorization dialog
                ProcessBuilder dialogPb = new ProcessBuilder("osascript", "-e",
                        "return text returned of (display dialog "
                        + "\"JDiskMark needs administrator privileges to read SMART data.\" "
                        + "default answer \"\" with hidden answer "
                        + "with title \"JDiskMark\" "
                        + "buttons {\"Cancel\", \"OK\"} default button \"OK\")");
                dialogPb.redirectErrorStream(true);
                Process dialogProcess = dialogPb.start();
                String password;
                try {
                    password = new String(
                            dialogProcess.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8).trim();
                    int exitCode = dialogProcess.waitFor();
                    if (exitCode != 0 || password.isEmpty()) {
                        throw new IOException("User cancelled macOS authorization dialog");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for macOS authorization", ex);
                }

                ProcessBuilder pb = new ProcessBuilder("sudo", "-S", "bash");
                pb.redirectErrorStream(false);
                process = pb.start();
                shellWriter = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                shellReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

                // Feed password to sudo via stdin
                shellWriter.write(password);
                shellWriter.newLine();
                shellWriter.flush();
            } else {
                // Linux: use pkexec for privilege escalation (polkit)
                LOGGER.info("Starting privileged bash shell via pkexec...");
                ProcessBuilder pb = new ProcessBuilder("pkexec", "bash");
                pb.redirectErrorStream(false);
                process = pb.start();
                shellWriter = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                shellReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            }

            // Drain stderr so the process can't deadlock if it emits output there.
            final BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
            Thread errThread = new Thread(() -> {
                try {
                    String l;
                    while ((l = errReader.readLine()) != null) {
                        LOGGER.warning("[smart-shell] " + l);
                        App.err("Smart - [smart-shell] " + l);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "smart-shell stderr reader stopped", e);
                }
            }, "smart-shell-stderr");
            errThread.setDaemon(true);
            errThread.start();

            LOGGER.info("Privileged shell started (pid reuse enabled).");
        }
    }

    /**
     * Starts a background keepalive thread that pings the privileged bash
     * shell every 5 minutes with a no-op echo so the process stays alive.
     * Only one thread is started; subsequent calls are ignored.
     */
    public static void startHeartbeat() {
        if (App.isWindows()) {
            // TODO: implement persistent process to avoid repeated UAC auth prompt
            return;
        }
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
     * Runs {@code smartctl} directly in-process (Windows elevated / fast path).
     * Tries {@code /dev/<device>} first, then the bare device name as a fallback,
     * since some Windows controller drivers require one form or the other.
     *
     * @param deviceName bare device name, e.g. {@code pd0}
     * @param smartctlPath absolute path to {@code smartctl.exe}
     * @return a populated {@link Smart} instance, or {@code null} on error
     */
    private static Smart getSmartDirect(String deviceName, String smartctlPath) {
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(List.of("--json", "-a", "/dev/" + deviceName));
        candidates.add(List.of("--json", "-a", deviceName));
        if (deviceName.startsWith("pd")) {
            String win32 = "\\\\.\\PhysicalDrive" + deviceName.substring(2);
            candidates.add(List.of("--json", "-a", win32));
            candidates.add(List.of("--json", "-a", win32, "-d", "nvme"));
            candidates.add(List.of("--json", "-a", win32, "-d", "sat"));
        }
        Smart fallback = null;
        try {
            for (List<String> args : candidates) {
                List<String> cmd = new ArrayList<>();
                cmd.add(smartctlPath);
                cmd.addAll(args);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                }
                if (!p.waitFor(15, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    LOGGER.warning("getSmartDirect: smartctl timed out for: " + args);
                    continue;
                }
                String result = sb.toString().trim();
                if (result.isEmpty() || !result.startsWith("{")) continue;
                if ((p.exitValue() & 2) != 0) {
                    LOGGER.info("getSmartDirect: device open failed (exit " + p.exitValue() + ") for: " + args);
                    if (fallback == null) fallback = fromJson(result);
                    continue;
                }
                Smart smart = fromJson(result);
                logSmart(smart);
                return smart;
            }
            if (fallback != null) {
                LOGGER.warning("getSmartDirect: all candidates failed; using error response for: " + deviceName);
                logSmart(fallback);
                return fallback;
            }
            LOGGER.severe("getSmartDirect: all attempts failed for: " + deviceName);
            App.err("Smart - all attempts failed for: " + deviceName);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "getSmartDirect interrupted for: " + deviceName, ex);
            App.err("Smart - interrupted for: " + deviceName);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "getSmartDirect failed for: " + deviceName, ex);
            App.err("Smart - failed for: " + deviceName + " \u2014 " + ex.getMessage());
        }
        return null;
    }

    /**
     * Queries SMART data for the given device.
     *
     * <p>On <b>Windows</b>:
     * <ul>
     *   <li>If the process is already elevated ({@link App#isAdmin}), runs
     *       {@code smartctl} directly via {@link #getSmartDirect}.</li>
     *   <li>Otherwise, delegates to {@link SmartEscalation#runElevated} which
     *       triggers a UAC prompt and runs an elevated helper, returning the
     *       JSON via a temp file in {@code %LOCALAPPDATA%\JDiskMark\}.</li>
     * </ul>
     *
     * <p>On <b>Linux / macOS</b>, writes the command to the persistent privileged
     * shell started by {@link #startPrivilegedShell()} and reads back the output.
     *
     * @param deviceName bare device name, e.g. {@code nvme0n1} or {@code pd0}
     * @return a populated {@link Smart} instance, or {@code null} on error
     */
    public static Smart getSmart(String deviceName) {
        if (deviceName == null || !deviceName.matches("[A-Za-z0-9._-]+")) {
            LOGGER.severe("getSmart: invalid device name: " + deviceName);
            return null;
        }
        if (App.isWindows()) {
            String smartctlPath = resolveSmartctlPath();
            LOGGER.info("getSmart: using smartctl at: " + smartctlPath + " for device: " + deviceName);

            if (App.isAdmin) {
                // ── Fast path: already elevated, run smartctl directly ──────────────
                return getSmartDirect(deviceName, smartctlPath);
            } else {
                // ── Escalation path: request UAC elevation for the helper ────────────
                try {
                    LOGGER.info("getSmart: not admin — requesting UAC elevation for device: " + deviceName);
                    String json = SmartEscalation.runElevated(deviceName, smartctlPath);
                    if (json == null) {
                        LOGGER.warning("getSmart: escalation returned null (UAC cancelled?) for: " + deviceName);
                        return null;
                    }
                    if (!json.startsWith("{")) {
                        LOGGER.warning("getSmart: escalation returned non-JSON for " + deviceName + ": " + json);
                        return null;
                    }
                    Smart smart = fromJson(json);
                    logSmart(smart);
                    return smart;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.SEVERE, "getSmart escalation interrupted for: " + deviceName, ex);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "getSmart escalation failed for: " + deviceName, ex);
                }
                return null;
            }
        }
        final String sentinel = "---SMART_DONE---";
        try {
            synchronized (pLock) {
                if (shellWriter == null || shellReader == null) {
                    LOGGER.severe("getSmart: privileged shell not initialised");
                    App.err("Smart - privileged shell not initialised");
                    return null;
                }
                // Write the smartctl command followed by an echo of the sentinel
                // so we know exactly where the JSON output ends.
                shellWriter.write(resolveSmartctlPath() + " --json -a /dev/" + deviceName + "\n");
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
                    App.err("Smart - empty response from shell for device " + deviceName);
                    return null;
                }

                Smart smart = fromJson(result);
                logSmart(smart);
                return smart;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "getSmart failed for device: " + deviceName, ex);
            App.err("Smart - failed for device: " + deviceName + " — " + ex.getMessage());
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

    /** The raw JSON string this object was parsed from. Set by {@link #fromJson}; not a JSON property. */
    private String rawJson;

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

    // ── NVMe-specific info-section fields ──────────────────────────────────

    @JsonProperty("nvme_pci_vendor")
    private NvmePciVendor nvmePciVendor;

    @JsonProperty("nvme_ieee_oui_identifier")
    private Long nvmeIeeeOuiIdentifier;

    @JsonProperty("nvme_total_capacity")
    private Long nvmeTotalCapacity;

    @JsonProperty("nvme_unallocated_capacity")
    private Long nvmeUnallocatedCapacity;

    @JsonProperty("nvme_controller_id")
    private Integer nvmeControllerId;

    @JsonProperty("nvme_version")
    private NvmeVersionInfo nvmeVersion;

    @JsonProperty("nvme_number_of_namespaces")
    private Integer nvmeNumberOfNamespaces;

    @JsonProperty("local_time")
    private LocalTimeInfo localTime;

    @JsonProperty("rotation_rate")
    private Integer rotationRate;

    @JsonProperty("in_smartctl_database")
    private Boolean inSmartctlDatabase;

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
        Smart smart = mapper.readValue(json, Smart.class);
        smart.rawJson = json;   // preserve original for snapshot replay
        return smart;
    }

    /** Returns the raw JSON string this object was parsed from, or {@code null} if not set. */
    public String getRawJson() { return rawJson; }

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

    public NvmePciVendor getNvmePciVendor() { return nvmePciVendor; }
    public void setNvmePciVendor(NvmePciVendor nvmePciVendor) { this.nvmePciVendor = nvmePciVendor; }

    public Long getNvmeIeeeOuiIdentifier() { return nvmeIeeeOuiIdentifier; }
    public void setNvmeIeeeOuiIdentifier(Long nvmeIeeeOuiIdentifier) { this.nvmeIeeeOuiIdentifier = nvmeIeeeOuiIdentifier; }

    public Long getNvmeTotalCapacity() { return nvmeTotalCapacity; }
    public void setNvmeTotalCapacity(Long nvmeTotalCapacity) { this.nvmeTotalCapacity = nvmeTotalCapacity; }

    public Long getNvmeUnallocatedCapacity() { return nvmeUnallocatedCapacity; }
    public void setNvmeUnallocatedCapacity(Long nvmeUnallocatedCapacity) { this.nvmeUnallocatedCapacity = nvmeUnallocatedCapacity; }

    public Integer getNvmeControllerId() { return nvmeControllerId; }
    public void setNvmeControllerId(Integer nvmeControllerId) { this.nvmeControllerId = nvmeControllerId; }

    public NvmeVersionInfo getNvmeVersion() { return nvmeVersion; }
    public void setNvmeVersion(NvmeVersionInfo nvmeVersion) { this.nvmeVersion = nvmeVersion; }

    public Integer getNvmeNumberOfNamespaces() { return nvmeNumberOfNamespaces; }
    public void setNvmeNumberOfNamespaces(Integer nvmeNumberOfNamespaces) { this.nvmeNumberOfNamespaces = nvmeNumberOfNamespaces; }

    public LocalTimeInfo getLocalTime() { return localTime; }
    public void setLocalTime(LocalTimeInfo localTime) { this.localTime = localTime; }

    public Integer getRotationRate() { return rotationRate; }
    public void setRotationRate(Integer rotationRate) { this.rotationRate = rotationRate; }

    public Boolean getInSmartctlDatabase() { return inSmartctlDatabase; }
    public void setInSmartctlDatabase(Boolean inSmartctlDatabase) { this.inSmartctlDatabase = inSmartctlDatabase; }

    // =========================================================================
    // Nested classes
    // =========================================================================

    // -------------------------------------------------------------------------
    // NVMe PCI Vendor
    // -------------------------------------------------------------------------

    /** Represents the {@code nvme_pci_vendor} block (id, subsystem_id). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NvmePciVendor {

        @JsonProperty("id")
        private Integer id;

        @JsonProperty("subsystem_id")
        private Integer subsystemId;

        public NvmePciVendor() {}

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public Integer getSubsystemId() { return subsystemId; }
        public void setSubsystemId(Integer subsystemId) { this.subsystemId = subsystemId; }

        /** Returns a human-readable {@code "0x1234 / 0x5678"} string, or {@code null}. */
        public String getDisplayString() {
            if (id == null) return null;
            String sub = subsystemId != null ? " / 0x" + Integer.toHexString(subsystemId).toUpperCase() : "";
            return "0x" + Integer.toHexString(id).toUpperCase() + sub;
        }
    }

    // -------------------------------------------------------------------------
    // NVMe version
    // -------------------------------------------------------------------------

    /** Represents the {@code nvme_version} block. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NvmeVersionInfo {

        @JsonProperty("string")
        private String string;

        @JsonProperty("value")
        private Integer value;

        public NvmeVersionInfo() {}

        public String getString() { return string; }
        public void setString(String string) { this.string = string; }

        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }
    }

    // -------------------------------------------------------------------------
    // Local time
    // -------------------------------------------------------------------------

    /** Represents the {@code local_time} block. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocalTimeInfo {

        @JsonProperty("time_t")
        private Long timeT;

        @JsonProperty("asctime")
        private String asctime;

        public LocalTimeInfo() {}

        public Long getTimeT() { return timeT; }
        public void setTimeT(Long timeT) { this.timeT = timeT; }

        public String getAsctime() { return asctime; }
        public void setAsctime(String asctime) { this.asctime = asctime; }
    }

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

        /** Finds the first attribute with the given {@code id}, or {@code null} if absent. */
        public AtaAttribute findById(int id) {
            if (table == null) return null;
            for (AtaAttribute a : table) {
                if (Integer.valueOf(id).equals(a.getId())) return a;
            }
            return null;
        }

        /**
         * Returns the first attribute matching any of the given IDs (checked in
         * priority order), or {@code null} if none are present.
         */
        public AtaAttribute findByIdAny(int... ids) {
            for (int id : ids) {
                AtaAttribute a = findById(id);
                if (a != null) return a;
            }
            return null;
        }
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

        @JsonProperty("temperature_sensors")
        private List<Integer> temperatureSensors;

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

        public List<Integer> getTemperatureSensors() { return temperatureSensors; }
        public void setTemperatureSensors(List<Integer> temperatureSensors) { this.temperatureSensors = temperatureSensors; }

        /** Returns the first temperature sensor value, or {@code null} if absent. */
        public Integer getTemperatureSensor1() {
            return (temperatureSensors != null && temperatureSensors.size() >= 1)
                    ? temperatureSensors.get(0) : null;
        }

        /** Returns the second temperature sensor value, or {@code null} if absent. */
        public Integer getTemperatureSensor2() {
            return (temperatureSensors != null && temperatureSensors.size() >= 2)
                    ? temperatureSensors.get(1) : null;
        }

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
