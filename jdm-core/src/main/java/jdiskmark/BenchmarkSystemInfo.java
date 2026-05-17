package jdiskmark;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class BenchmarkSystemInfo implements Serializable {
    // system data
    @Column
    String os;
    public String getOs() { return os; }
    @Column
    String arch;
    public String getArch() { return arch; }
    @Column
    String processorName;
    public String getProcessorName() { return processorName; }
    @Column
    String jdk;
    public String getJdk() { return jdk; }
    @Column
    String locationDir;
    public String getLocationDir() { return locationDir; }
    
    public BenchmarkSystemInfo() {}
}
