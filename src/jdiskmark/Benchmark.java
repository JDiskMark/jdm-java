
package jdiskmark;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A benchmark
 */
@Entity
@Table(name="Benchmark")
@NamedQueries({
@NamedQuery(name="Benchmark.findAll",
    query="SELECT b FROM Benchmark b")    
})
public class Benchmark implements Serializable {
    
    static final DecimalFormat DF = new DecimalFormat("###.##");
    static final DecimalFormat DFT = new DecimalFormat("###");
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
        public enum BenchmarkType {
        READ {
            @Override
            public String toString() { return "Read"; }
        },
        WRITE {
            @Override
            public String toString() { return "Write"; }
        },
        READ_WRITE {    
            @Override
            public String toString() { return "Read & Write"; }
        }
    }
    
    public enum IOMode {
        READ {
            @Override
            public String toString() { return "Read"; }
        },
        WRITE {
            @Override
            public String toString() { return "Write"; }
        }
    }

    public enum BlockSequence {
        SEQUENTIAL {
            @Override
            public String toString() { return "Sequential"; }
        },
        RANDOM {
            @Override
            public String toString() { return "Random"; }
        }
    }
    
    /**
     * Jackson custom serializer to convert the Java UUID into a plain string.
     *
     * IMPORTANT NOTE on UUID vs ObjectId:
     * - UUID is a 16-byte value (36-character string representation).
     * - MongoDB ObjectId is a 12-byte value (24-character hex string).
     * Since they are different lengths and structures, a direct conversion
     * is non-standard. The industry best practice for external clients sending
     * their own primary key is to send the UUID as a simple string, and the MERN
     * backend will store it as the document's _id (usually as a string, not a native ObjectId object).
     * This serializer performs that essential conversion.
     */
    public static class UuidToMongoIdSerializer extends JsonSerializer<UUID> {
        @Override
        public void serialize(UUID value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                // Serializes the UUID into its standard string representation.
                // Example: "a1b2c3d4-e5f6-7890-1234-567890abcdef"
                gen.writeString(value.toString());
            }
        }
    }
    
    // surrogate key
    /**
     * The unique identifier for this benchmark run.
     * Mapped as the primary key for JPA/Derby.
     * The GenerationType.UUID tells JPA to use the database's UUID generation
     * mechanism (or an equivalent strategy provided by the JPA vendor).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    // --- JSON Serialization for MERN App ---

    /**
     * @JsonProperty("_id"): Ensures that when this object is serialized to JSON,
     * the key name for this field is "_id", matching the standard MongoDB primary key convention.
     *
     * @JsonSerialize(using = UuidToMongoIdSerializer.class): Tells Jackson to use
     * our custom inner class serializer to handle the conversion logic of UUID -> JSON string.
     */
    @JsonProperty("_id")
    @JsonSerialize(using = UuidToMongoIdSerializer.class)
    private UUID id;
    
    // user account
    @Column
    String username = "anonymous"; // "user" is reserved in Derby
    public String getUsername() { return username; }
    
    // system info
    @Embedded
    BenchmarkSystemInfo systemInfo = new BenchmarkSystemInfo();
    public BenchmarkSystemInfo getSystemInfo() { return systemInfo; }
    
    // drive info
    @Embedded
    BenchmarkDriveInfo driveInfo = new BenchmarkDriveInfo();
    public BenchmarkDriveInfo getDriveInfo() { return driveInfo; }

    // benchmark parameters
    @Embedded
    BenchmarkConfig config = new BenchmarkConfig();
    public BenchmarkConfig getConfig() { return config; }
    
    // timestamps
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "startTime", columnDefinition = "TIMESTAMP")
    LocalDateTime startTime;
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column
    LocalDateTime endTime = null;
    
    @OneToMany(mappedBy = "benchmark", cascade = CascadeType.ALL, orphanRemoval = true)
    List<BenchmarkOperation> operations = new ArrayList<>();
    public List<BenchmarkOperation> getOperations() { return operations; }
    
    // get the first operation of that type
    public BenchmarkOperation getOperation(IOMode mode) {
        for (BenchmarkOperation operation : operations) {
            if (operation.ioMode == mode) {
                return operation;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "Benchmark(" + config.benchmarkType + ") start=" + startTime + "numOps=" + operations.size();
    }
    
    /**
     * Use for command line output
     * @return the result string
     */
    public String toResultString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("-------------------------------------------\n");
        sb.append("JDiskMark Benchmark Results (v").append(App.VERSION).append(")\n");
        sb.append("-------------------------------------------\n");
        sb.append("Benchmark: ").append(config.benchmarkType).append("\n");
        sb.append("Drive: ").append(App.getDriveModel()).append("\n");
        sb.append("Capacity: ").append(App.getDriveCapacity()).append("\n");
        sb.append("Timestamp: ").append(startTime).append("\n");
        sb.append("CPU: ").append(systemInfo.processorName).append("\n");
        sb.append("System: ").append(systemInfo.os).append(" / ").append(systemInfo.arch).append("\n");
        sb.append("Java: ").append(systemInfo.jdk).append("\n");
        sb.append("Path: ").append(systemInfo.locationDir).append("\n");
        for (BenchmarkOperation o : operations) {
            sb.append("-------------------------------------------\n");
            sb.append("Order: ").append(o.blockOrder).append("\n");
            sb.append("IOMode: ").append(o.ioMode).append("\n");
            sb.append("Thread(s): ").append(o.numThreads).append("\n");
            sb.append("Blocks(size): ").append(o.numBlocks).append("(").append(o.blockSize).append(")").append("\n");
            sb.append("Samples: ").append(o.numSamples).append("\n");
            sb.append("TxSize(KB): ").append(o.txSize).append("\n");
            sb.append("Speed(MB/s): ").append(DF.format(o.bwAvg)).append("\n");
            sb.append("SpeedMin(MB/s): ").append(DF.format(o.bwMin)).append("\n");
            sb.append("SpeedMax(MB/s): ").append(DF.format(o.bwMax)).append("\n");
            sb.append("Latency(ms): ").append(DF.format(o.accAvg)).append("\n");
            sb.append("IOPS: ").append(o.iops).append("\n");
        }
        sb.append("-------------------------------------------\n");
        return sb.toString();
    }
    
    public Benchmark() {
        startTime = LocalDateTime.now();
        config.profileName = App.activeProfile.getName();
        config.numSamples = App.numOfSamples;
        config.numBlocks = App.numOfBlocks;
        config.blockSize = App.blockSizeKb;
    }
    
    public Benchmark(BenchmarkType type) {
        startTime = LocalDateTime.now();
        config.profileName = App.activeProfile.getName();
        config.numSamples = App.numOfSamples;
        config.numBlocks = App.numOfBlocks;
        config.blockSize = App.blockSizeKb;
        if (config != null) {
            config.benchmarkType = type;
            config.profileName = App.activeProfile.getName();
        }
    }
    
    // basic getters and setters
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    @JsonIgnore
    public String getDriveInfoDisplay() {
        return driveInfo.driveModel + " - " + driveInfo.partitionId + ": " + getUsageTitleDisplay();
    }
    
    @JsonIgnore
    public String getUsageTitleDisplay() {
        return  driveInfo.percentUsed + "% (" + DFT.format(driveInfo.usedGb) + "/" + DFT.format(driveInfo.totalGb) + " GB)";
    }
    
    @JsonIgnore
    public String getUsageColumnDisplay() {
        return driveInfo.percentUsed + "%";
    }
       
    public String getStartTimeString() {
        return startTime.format(DATE_FORMAT);
    }
    
    public String getDuration() {
        if (endTime == null) {
            return "unknown";
        }
        long diffMs = Duration.between(startTime, endTime).toMillis();
        return String.valueOf(diffMs);
    }
    
    // utility methods for collection
    
    @JsonIgnore
    static List<Benchmark> findAll() {
        EntityManager em = EM.getEntityManager();
        return em.createNamedQuery("Benchmark.findAll", Benchmark.class).getResultList();
    }
    
    @JsonIgnore
    static int deleteAll() {
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        int deletedOperationsCount = em.createQuery("DELETE FROM BenchmarkOperation").executeUpdate();
        int deletedBenchmarksCount = em.createQuery("DELETE FROM Benchmark").executeUpdate();
        if (App.verbose) {
            App.msg("deletedOperations=" + deletedOperationsCount);
            App.msg("deletedBenchmarks=" + deletedBenchmarksCount);
        }
        em.getTransaction().commit();
        return deletedBenchmarksCount;
    }
    
    @JsonIgnore
    static int delete(List<UUID> benchmarkIds) {
        if (benchmarkIds.isEmpty()) {
            return 0;
        }
        
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        
        // delete the child BenchmarkOperation records.
        String deleteOperationsJpql = "DELETE FROM BenchmarkOperation bo WHERE bo.benchmark.id IN :benchmarkIds";
        int deletedOperationsCount = em.createQuery(deleteOperationsJpql)
                .setParameter("benchmarkIds", benchmarkIds)
                .executeUpdate();
        
        // delete the parent BenchmarkOperation records
        String deleteBenchmarksJpql = "DELETE FROM Benchmark b WHERE b.id IN :benchmarkIds";
        int deletedBenchmarksCount = em.createQuery(deleteBenchmarksJpql)
                .setParameter("benchmarkIds", benchmarkIds)
                .executeUpdate();
        
        if (App.verbose) {
            App.msg("deletedOperations=" + deletedOperationsCount);
            App.msg("deletedBenchmarks=" + deletedBenchmarksCount);
        }
        
        em.getTransaction().commit();
        return deletedBenchmarksCount;
    }
}