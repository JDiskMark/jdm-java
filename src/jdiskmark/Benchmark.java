
package jdiskmark;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A read or write benchmark
 */
@Entity
@Table(name="Benchmark")
@NamedQueries({
@NamedQuery(name="Benchmark.findAll",
    query="SELECT b FROM Benchmark b JOIN FETCH b.operations")
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
    
    // surrogate key
    @Column
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;
    
    // system data
    @Column
    String os;
    @Column
    String arch;
    @Column
    String processorName;
    @Column
    String jdk;
    @Column
    String locationDir;
    
    // drive info
    @Column
    String driveModel = null;
    @Column
    String partitionId;      // on windows the drive letter
    @Column
    long percentUsed;
    @Column
    double usedGb;
    @Column
    double totalGb;
    
    // benchmark parameters
    @Column
    BenchmarkType benchmarkType;

    // timestamps
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "startTime", columnDefinition = "TIMESTAMP")
    LocalDateTime startTime;
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column
    LocalDateTime endTime = null;

    
    @OneToMany(mappedBy = "benchmark", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BenchmarkOperation> operations = new ArrayList<>();
    
    public List<BenchmarkOperation> getOperations() {
        return operations;
    }
    
    // get the first operation of that type
    public BenchmarkOperation getOperation(BenchmarkOperation.IOMode mode) {
        for (BenchmarkOperation operation : operations) {
            if (operation.ioMode == mode) {
                return operation;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "Benchmark(" + benchmarkType + ") start=" + startTime + "numOps=" + operations.size();
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
        sb.append("Benchmark: ").append(benchmarkType).append("\n");
        sb.append("Drive: ").append(App.getDriveModel()).append("\n");
        sb.append("Capacity: ").append(App.getDriveCapacity()).append("\n");
        sb.append("Timestamp: ").append(startTime).append("\n");
        sb.append("CPU: ").append(processorName).append("\n");
        sb.append("System: ").append(os).append(" / ").append((arch)).append("\n");
        sb.append("Java: ").append(jdk).append("\n");
        sb.append("Path: ").append(locationDir).append("\n");
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
    }
    
    Benchmark(BenchmarkType type) {
        startTime = LocalDateTime.now();
        benchmarkType = type;
    }
    
    // basic getters and setters
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getDriveInfo() {
        return driveModel + " - " + partitionId + ": " + getUsageTitleDisplay();
    }
    public String getUsageTitleDisplay() {
        return  percentUsed + "% (" + DFT.format(usedGb) + "/" + DFT.format(totalGb) + " GB)";
    }
    public String getUsageColumnDisplay() {
        return percentUsed + "%";
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
    
    static List<Benchmark> findAll() {
        EntityManager em = EM.getEntityManager();
        return em.createNamedQuery("Benchmark.findAll", Benchmark.class).getResultList();
    }
    
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
    
    static int delete(List<Long> benchmarkIds) {
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