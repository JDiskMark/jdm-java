package jdiskmark;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
    @JsonSerialize(using = RoundingSerializer.class)
    public double getUsedGb() { return usedGb; }
    @Column
    double totalGb;
    @JsonSerialize(using = RoundingSerializer.class)
    public double getTotalGb() { return totalGb; }
    @Column
    String driveInterface = null;
    public String getDriveInterface() { return driveInterface; }
    @Column
    String driveInterfaceVersion = null;     // e.g. USB link version "3.0"
    public String getDriveInterfaceVersion() { return driveInterfaceVersion; }
    @Column
    String usbVendorId = null;               // 4-char lowercase hex, e.g. "0781"
    public String getUsbVendorId() { return usbVendorId; }
    @Column
    String usbProductId = null;              // 4-char lowercase hex, e.g. "5583"
    public String getUsbProductId() { return usbProductId; }
    
    public BenchmarkDriveInfo() {}
}
