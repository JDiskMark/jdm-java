
package jdiskmark;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import jdiskmark.Benchmark.BlockSequence;
import jdiskmark.Benchmark.IOMode;

/**
 * A read or write benchmark
 */
@Entity
@Table(name="BenchmarkOperation")
@NamedQueries({
@NamedQuery(name="BenchmarkOperation.findAll",
    query="SELECT o FROM BenchmarkOperation o")
})
public class BenchmarkOperation implements Serializable {
    
    static final DecimalFormat DF = new DecimalFormat("###.##");
    static final DecimalFormat DFT = new DecimalFormat("###");
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // surrogate key
    @Column
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;
    
    // This is the foreign key relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id") // Specifies the foreign key column
    @JsonIgnore
    private Benchmark benchmark;
    
    // benchmark parameters
    @Column
    IOMode ioMode;
    @Column
    BlockSequence blockOrder;
    @Column
    int numBlocks = 0;
    @Column
    int blockSize = 0;
    @Column
    int numSamples = 0;
    @Column
    long txSize = 0;
    @Column
    int numThreads = 1;
    // NEW: whether write-sync was enabled for this run (only meaningful for WRITE; may be null for READ)
    @Column
    Boolean writeSyncEnabled;
    
    // timestamps
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column(name = "startTime", columnDefinition = "TIMESTAMP")
    LocalDateTime startTime;
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column
    LocalDateTime endTime = null;
    
    // sample data
    @Column
    @Convert(converter = SampleAttributeConverter.class)
    ArrayList<Sample> samples = new ArrayList<>();
    
    // results
    @Column
    double bwAvg = 0;
    @Column
    double bwMax = 0;
    @Column
    double bwMin = 0;
    @Column
    double accAvg = 0;
    @Column
    long iops = 0;
    
    @Override
    public String toString() {
        return "BenchmarkOp(" + ioMode + "," + blockOrder + "): " + numSamples + " bw avg: " + bwAvg;
    }
    
    public BenchmarkOperation() {
        startTime = LocalDateTime.now();
    }
    
    // basic getters and setters
    public Benchmark getBenchmark() {
        return benchmark;
    }
    public void setBenchmark(Benchmark benchmark) {
        this.benchmark = benchmark;
    }    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getModeDisplay() {
        // Show "Write*" when write-sync was enabled for a WRITE run
        if (ioMode == IOMode.WRITE && Boolean.TRUE.equals(getWriteSyncEnabled())) {
            return "Write*";
        }
        return (ioMode == null) ? "" : ioMode.toString(); // "Read", "Write", "Read & Write"
    }
    
    // GH-20 TODO: review should this be synchronized or redone to not be blocking?
    public synchronized void add(Sample s) {
        samples.add(s);
    }
    
    public synchronized ArrayList<Sample> getSamples() {
        return samples;
    }
    
    // display friendly methods
    
    public String getBlocksDisplay() {
        return numBlocks + " (" + blockSize + ")";
    }
    
    public String getStartTimeString() {
        return startTime.format(DATE_FORMAT);
    }
    
    public String getAccTimeDisplay() {
        return accAvg == -1? "- -" : DF.format(accAvg);
    }
    
    public String getBwMinDisplay() {
        return bwMin == -1 ? "- -" : DF.format(bwMin);
    }
    
    public String getBwMaxDisplay() {
        return bwMax == -1 ? "- -" : DF.format(bwMax);
    }
    
    public String getBwMinMaxDisplay() {
        return bwMax == -1 ? "- -" : DFT.format(bwMin) + "/" + DFT.format(bwMax);
    }
    
    public String getBwAvgDisplay() {
        return bwAvg == -1 ? "- -" : DF.format(bwAvg);
    }
    
    public String getDuration() {
        if (endTime == null) {
            return "unknown";
        }
        long diffMs = Duration.between(startTime, endTime).toMillis();
        return String.valueOf(diffMs);
    }
    
    public void setTotalOps(long totalOps) {
        // iops = operations / sec = ops / (elapsed ms / 1,000ms)
        // Multiply by 1_000_000 to convert milliseconds to seconds
        if (App.verbose) {
            System.err.println("startTime=" + startTime);
            System.err.println("endTime=" + endTime);
        }
        long diffMs = Duration.between(startTime, endTime).toMillis();
        if (diffMs != 0) {
            double iopsDouble = (double) (totalOps * 1_000_000) / (double) diffMs;
            iops = Math.round(iopsDouble);
        }
    }
    
    // NEW: getters/setters for writeSyncEnabled and iops (expose iops too if needed)

    public Boolean getWriteSyncEnabled() {
        return writeSyncEnabled;
    }

    public void setWriteSyncEnabled(Boolean writeSyncEnabled) {
        this.writeSyncEnabled = writeSyncEnabled;
    }

    public long getIops() {
        return iops;
    }

    public void setIops(long iops) {
        this.iops = iops;
    }
    
    // utility methods for collection
    
    public static List<BenchmarkOperation> findAll() {
        EntityManager em = EM.getEntityManager();
        return em.createNamedQuery("BenchmarkOperation.findAll", BenchmarkOperation.class).getResultList();
    }
    
    public static int deleteAll() {
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        int deletedCount = em.createQuery("DELETE FROM BenchmarkOperation").executeUpdate();
        em.getTransaction().commit();
        return deletedCount;
    }
    
    public static int delete(List<Long> benchmarkIds) {
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        String jpql = "DELETE FROM BenchmarkOperation b WHERE b.id IN :benchmarkOperationIds";
        int deletedCount = em.createQuery(jpql)
                .setParameter("benchmarkOperationIds", benchmarkIds)
                .executeUpdate();
        em.getTransaction().commit();
        return deletedCount;
    }
}