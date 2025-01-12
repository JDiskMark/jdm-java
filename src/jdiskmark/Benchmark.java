
package jdiskmark;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/**
 * A read or write benchmark
 */
@Entity
@Table(name="Benchmark")
@NamedQueries({
@NamedQuery(name="Benchmark.findAll",
    query="SELECT d FROM Benchmark d")
})
public class Benchmark implements Serializable {
    
    static final DecimalFormat DF = new DecimalFormat("###.##");
    static final DecimalFormat DFT = new DecimalFormat("###");
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    static public enum IOMode { READ, WRITE, READ_WRITE; }
    static public enum BlockSequence { SEQUENTIAL, RANDOM; }
    
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
    
    // configuration
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
        return "Benchmark(" + ioMode + "," + blockOrder + "): " + numSamples + " bw avg: " + bwAvg;
    }
    
    public Benchmark() {
        startTime = LocalDateTime.now();
    }
    
    Benchmark(IOMode type, BlockSequence order) {
        startTime = LocalDateTime.now();
        ioMode = type;
        blockOrder = order;
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
    
    public void add(Sample s) {
        samples.add(s);
    }
    
    // display friendly methods
    
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
        System.err.println("startTime=" + startTime);
        System.err.println("endTime=" + endTime);
        long diffMs = Duration.between(startTime, endTime).toMillis();
        if (diffMs != 0) {
            double iopsDouble = (double) (totalOps * 1_000_000) / (double) diffMs;
            iops = Math.round(iopsDouble);
        }
    }
    
    // utility methods for collection
    
    static List<Benchmark> findAll() {
        EntityManager em = EM.getEntityManager();
        return em.createNamedQuery("Benchmark.findAll", Benchmark.class).getResultList();
    }
    
    static int deleteAll() {
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        int deletedCount = em.createQuery("DELETE FROM Benchmark").executeUpdate();
        em.getTransaction().commit();
        return deletedCount;
    }
    
    static int delete(List<Long> benchmarkIds) {
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        String jpql = "DELETE FROM Benchmark b WHERE b.id IN :benchmarkIds";
        int deletedCount = em.createQuery(jpql)
                .setParameter("benchmarkIds", benchmarkIds)
                .executeUpdate();
        em.getTransaction().commit();
        return deletedCount;
    }
}
