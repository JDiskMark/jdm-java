
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
import static java.time.temporal.ChronoUnit.SECONDS;
import java.util.List;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This is also referred to as a Benchmark
 */
@Entity
@Table(name="Benchmark")
@NamedQueries({
@NamedQuery(name="Benchmark.findAll",
    query="SELECT d FROM Benchmark d")
})
public class Benchmark implements Serializable {
    
    static final DecimalFormat DF = new DecimalFormat("###.##");
    //previous date format "EEE, MMM d HH:mm:ss" >>>  Thu, Jan 20 21:45:01
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    static public enum IOMode { READ, WRITE, READ_WRITE; }
    static public enum BlockSequence { SEQUENTIAL, RANDOM; }

    @Column
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id;
    
    // configuration
    @Column
    String diskInfo = null;
    @Column
    IOMode ioMode;
    @Column
    BlockSequence blockOrder;
    @Column
    int numSamples = 0;
    @Column
    int numBlocks = 0;
    @Column
    int blockSize = 0;
    @Column
    long txSize = 0;
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column
    LocalDateTime startTime;
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    @Column
    LocalDateTime  endTime = null;
    @Column
    int totalMarks = 0;
    @Column
    double accAvg = 0;
    @Column
    double runMin = 0;
    @Column
    double runMax = 0;
    @Column
    double runAvg = 0;
    
    @Override
    public String toString() {
        return "Run(" + ioMode + "," + blockOrder + "): " + totalMarks + " run avg: " + runAvg;
    }
    
    public Benchmark() {
        startTime = LocalDateTime.now();
    }
    
    Benchmark(IOMode type, BlockSequence order) {
        startTime = LocalDateTime.now();
        ioMode = type;
        blockOrder = order;
    }
    
    // display friendly methods
    
    public String getStartTimeString() {
        return startTime.format(DATE_FORMAT);
    }
    
    public String getAccTime() {
        return accAvg == -1? "- -" : DF.format(accAvg);
    }
    
    public String getMin() {
        return runMin == -1 ? "- -" : DF.format(runMin);
    }
    
    public void setMin(double min) {
        runMin = min;
    }
    
    public String getMax() {
        return runMax == -1 ? "- -" : DF.format(runMax);
    }
    
    public void setMax(double max) {
        runMax = max;
    }
    
    public String getAvg() {
        return runAvg == -1 ? "- -" : DF.format(runAvg);
    }
    
    public void setAvg(double avg) {
        runAvg = avg;
    }
    
    public String getDuration() {
        if (endTime == null) {
            return "unknown";
        }
        long diffMs = Duration.between(startTime, endTime).toMillis();
        return String.valueOf(diffMs);
    }
    
    // basic getters and setters
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getDriveInfo() {
        return diskInfo;
    }
    public void setDriveInfo(String info) {
        diskInfo = info;
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
}
