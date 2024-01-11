
package jdiskmark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 *
 */
@Entity
@Table(name="DiskRun")
@NamedQueries({
@NamedQuery(name="DiskRun.findAll",
    query="SELECT d FROM DiskRun d")
})
public class DiskRun implements Serializable {
    
    static final DecimalFormat DF = new DecimalFormat("###.##");
    //previous date format "EEE, MMM d HH:mm:ss" >>>  Thu, Jan 20 21:45:01
    static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
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
    int numMarks = 0;
    @Column
    int numBlocks = 0;
    @Column
    int blockSize = 0;
    @Column
    long txSize = 0;
    @Temporal(TemporalType.TIMESTAMP)
    @Column
    Date startTime;
    @Temporal(TemporalType.TIMESTAMP)
    @Column
    Date endTime = null;
    @Column
    int totalMarks = 0;
    @Column
    double runMin = 0;
    @Column
    double runMax = 0;
    @Column
    double runAvg = 0;
    
    @Override
    public String toString() {
        return "Run("+ioMode+","+blockOrder+"): "+totalMarks+" run avg: "+runAvg;
    }
    
    public DiskRun() {
        this.startTime = new Date();
    }
    
    DiskRun(IOMode type, BlockSequence order) {
        this.startTime = new Date();
        ioMode = type;
        blockOrder = order;
    }
    
    // display friendly methods
    
    public String getStartTimeString() {
        return DATE_FORMAT.format(startTime);
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
    
    public void SetAvg(double avg) {
        runAvg = avg;
    }
    
    public String getDuration() {
        if (endTime == null) {
            return "unknown";
        }
        long duration = endTime.getTime() - startTime.getTime();
        long diffSeconds = duration / 1000 % 60;
        return String.valueOf(diffSeconds) + "s";
    }
    
    // basic getters and setters
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getDiskInfo() {
        return diskInfo;
    }
    public void setDiskInfo(String info) {
        diskInfo = info;
    }
    
    // utility methods for collection
    
    static List<DiskRun> findAll() {
        EntityManager em = EM.getEntityManager();
        return em.createNamedQuery("DiskRun.findAll", DiskRun.class).getResultList();
    }
    
    static int deleteAll() {
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        int deletedCount = em.createQuery("DELETE FROM DiskRun").executeUpdate();
        em.getTransaction().commit();
        return deletedCount;
    }
}
