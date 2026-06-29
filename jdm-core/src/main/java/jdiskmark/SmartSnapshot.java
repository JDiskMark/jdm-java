package jdiskmark;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A point-in-time snapshot of S.M.A.R.T. health data for one drive.
 *
 * <p>Persisted independently of benchmark data in the same Derby database.
 * Rows are created explicitly by the user via the "Save Snapshot" button in
 * the SMART tab — there is no automatic capture.
 *
 * <p>Use {@link #save(Smart, String)} to persist and {@link #findAll()} to
 * retrieve all stored snapshots ordered by capture time descending.
 *
 * @author jasmine
 */
@Entity
@Table(name = "SmartSnapshot")
@NamedQueries({
    @NamedQuery(
        name  = "SmartSnapshot.findAll",
        query = "SELECT s FROM SmartSnapshot s ORDER BY s.capturedAt DESC"
    ),
    @NamedQuery(
        name  = "SmartSnapshot.deleteAll",
        query = "DELETE FROM SmartSnapshot s"
    )
})
public class SmartSnapshot implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(SmartSnapshot.class.getName());

    // -------------------------------------------------------------------------
    // Primary key
    // -------------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -------------------------------------------------------------------------
    // Capture metadata
    // -------------------------------------------------------------------------

    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "capturedAt", columnDefinition = "TIMESTAMP")
    private LocalDateTime capturedAt;

    @Column
    private String deviceName;

    // -------------------------------------------------------------------------
    // Drive identity
    // -------------------------------------------------------------------------

    @Column
    private String modelName;

    @Column
    private String serialNumber;

    @Column
    private String firmwareVersion;

    @Column
    private Double capacityGb;

    @Column
    private String protocol;

    // -------------------------------------------------------------------------
    // Health fields
    // -------------------------------------------------------------------------

    @Column
    private Boolean smartPassed;

    @Column
    private Integer tempC;

    @Column
    private Long powerOnHours;

    @Column
    private Long powerCycles;

    // -------------------------------------------------------------------------
    // NVMe health log fields (null for ATA drives)
    // -------------------------------------------------------------------------

    @Column
    private Integer percentageUsed;

    @Column
    private Integer availableSpare;

    @Column
    private Long mediaErrors;

    @Column
    private Double dataWrittenGb;

    @Column
    private Double dataReadGb;

    // -------------------------------------------------------------------------
    // Full JSON (for lossless replay)
    // -------------------------------------------------------------------------

    /**
     * The raw {@code smartctl --json -a} output this snapshot was captured from.
     * {@code null} for snapshots saved before this field was added.
     * When present, the SMART tab can perform a full replay via
     * {@link Smart#fromJson(String)} instead of the scalar-only fallback.
     */
    @Lob
    @Column(name = "rawJson", columnDefinition = "CLOB")
    private String rawJson;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public SmartSnapshot() {}

    // -------------------------------------------------------------------------
    // Factory / save
    // -------------------------------------------------------------------------

    /**
     * Extracts the key health fields from a live {@link Smart} object and
     * persists a new {@link SmartSnapshot} row in the Derby database.
     *
     * @param smart      the parsed SMART data (must not be null)
     * @param deviceName the bare device name, e.g. {@code nvme0n1}
     * @return the persisted snapshot, or {@code null} on error
     */
    public static SmartSnapshot save(Smart smart, String deviceName) {
        if (smart == null) {
            LOGGER.warning("SmartSnapshot.save: smart is null (device=" + deviceName + ")");
            return null;
        }
        SmartSnapshot snap = new SmartSnapshot();
        snap.capturedAt  = LocalDateTime.now();
        snap.deviceName  = deviceName;
        snap.modelName   = smart.getModelName();
        snap.serialNumber = smart.getSerialNumber();
        snap.firmwareVersion = smart.getFirmwareVersion();

        if (smart.getUserCapacity() != null) {
            snap.capacityGb = smart.getUserCapacity().getCapacityGb();
        }
        if (smart.getDevice() != null) {
            snap.protocol = smart.getDevice().getProtocol();
        }
        if (smart.getSmartStatus() != null) {
            snap.smartPassed = smart.getSmartStatus().isPassed();
        }

        // Temperature — prefer top-level block, fall back to NVMe health log
        if (smart.getTemperature() != null && smart.getTemperature().getCurrent() != null) {
            snap.tempC = smart.getTemperature().getCurrent();
        } else if (smart.getNvmeHealthLog() != null
                && smart.getNvmeHealthLog().getTemperature() != null) {
            snap.tempC = smart.getNvmeHealthLog().getTemperature();
        }

        // Power-on hours
        if (smart.getPowerOnTime() != null && smart.getPowerOnTime().getHours() != null) {
            snap.powerOnHours = smart.getPowerOnTime().getHours();
        } else if (smart.getNvmeHealthLog() != null
                && smart.getNvmeHealthLog().getPowerOnHours() != null) {
            snap.powerOnHours = smart.getNvmeHealthLog().getPowerOnHours();
        }

        // Power cycles
        if (smart.getPowerCycleCount() != null) {
            snap.powerCycles = smart.getPowerCycleCount().longValue();
        } else if (smart.getNvmeHealthLog() != null
                && smart.getNvmeHealthLog().getPowerCycles() != null) {
            snap.powerCycles = smart.getNvmeHealthLog().getPowerCycles();
        }

        // NVMe health log
        Smart.NvmeHealthLog nvme = smart.getNvmeHealthLog();
        if (nvme != null) {
            snap.percentageUsed = nvme.getPercentageUsed();
            snap.availableSpare = nvme.getAvailableSpare();
            snap.mediaErrors    = nvme.getMediaErrors();
            snap.dataWrittenGb  = nvme.getDataWrittenGb();
            snap.dataReadGb     = nvme.getDataReadGb();
        }

        // Full JSON for lossless replay
        snap.rawJson = smart.getRawJson();

        try {
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(snap);
            em.getTransaction().commit();
            LOGGER.info("SmartSnapshot saved: device=" + deviceName
                    + " model=" + snap.modelName + " passed=" + snap.smartPassed);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to persist SmartSnapshot", ex);
            return null;
        }
        return snap;
    }

    /**
     * Returns all stored snapshots ordered by capture time descending
     * (newest first).
     */
    public static List<SmartSnapshot> findAll() {
        return EM.getEntityManager()
                 .createNamedQuery("SmartSnapshot.findAll", SmartSnapshot.class)
                 .getResultList();
    }

    /**
     * Deletes the snapshot with the given primary key.
     *
     * @param id the {@link #id} of the record to remove
     * @return {@code true} if the record was found and deleted
     */
    public static boolean delete(Long id) {
        try {
            EntityManager em = EM.getEntityManager();
            SmartSnapshot snap = em.find(SmartSnapshot.class, id);
            if (snap == null) return false;
            em.getTransaction().begin();
            em.remove(snap);
            em.getTransaction().commit();
            LOGGER.info("SmartSnapshot deleted: id=" + id);
            return true;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete SmartSnapshot id=" + id, ex);
            return false;
        }
    }

    /**
     * Deletes all stored snapshots in a single bulk operation.
     *
     * @return the number of rows deleted
     */
    public static int deleteAll() {
        try {
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            int count = em.createNamedQuery("SmartSnapshot.deleteAll").executeUpdate();
            em.getTransaction().commit();
            LOGGER.info("SmartSnapshot.deleteAll: removed " + count + " row(s).");
            return count;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete all SmartSnapshots", ex);
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId()              { return id; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public String getDeviceName()    { return deviceName; }
    public String getModelName()     { return modelName; }
    public String getSerialNumber()  { return serialNumber; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public Double getCapacityGb()    { return capacityGb; }
    public String getProtocol()      { return protocol; }
    public Boolean getSmartPassed()  { return smartPassed; }
    public Integer getTempC()        { return tempC; }
    public Long getPowerOnHours()    { return powerOnHours; }
    public Long getPowerCycles()     { return powerCycles; }
    public Integer getPercentageUsed() { return percentageUsed; }
    public Integer getAvailableSpare() { return availableSpare; }
    public Long getMediaErrors()     { return mediaErrors; }
    public Double getDataWrittenGb() { return dataWrittenGb; }
    public Double getDataReadGb()    { return dataReadGb; }
    public String getRawJson()       { return rawJson; }
}
