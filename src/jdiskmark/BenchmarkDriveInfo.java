package jdiskmark;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BenchmarkDriveInfo {
    @Column
    String driveModel = null;
    public String getDriveModel() { return driveModel; }
    @Column
    String partitionId;      // on windows the drive letter
    public String getPartitionId() { return partitionId; }
    @Column
    long percentUsed;
    public long getPercentUsed() { return percentUsed; }
    @Column
    double usedGb;
    public double getUsedGb() { return usedGb; }
    @Column
    double totalGb;
    public double getTotalGb() { return totalGb; }
    
    public BenchmarkDriveInfo() {}
}
