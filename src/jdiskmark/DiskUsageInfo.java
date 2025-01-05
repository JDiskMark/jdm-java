/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package jdiskmark;

import java.text.DecimalFormat;

public class DiskUsageInfo {
    static final DecimalFormat DFT = new DecimalFormat("#");
    
    public long percentUsed;
    public double freeGb;
    public double usedGb;
    public double totalGb;

    public DiskUsageInfo() {}
    
    public DiskUsageInfo(double percentUsed, double usedGB, double totalGB) {
        this.percentUsed = Math.round(percentUsed);
        this.usedGb = usedGB;
        this.totalGb = totalGB;
    }
    
    public DiskUsageInfo(double percentUsed, double freeGB, double usedGB, double totalGB) {
        this.percentUsed = Math.round(percentUsed);
        this.freeGb = freeGB;
        this.usedGb = usedGB;
        this.totalGb = totalGB;
    }
    
    public long calcPercentageUsed() {
        if (totalGb != 0) {
            percentUsed = Math.round(100 * usedGb / totalGb);
        }
        return percentUsed;
    }

    /**
     * Format as:
     * option a: [23%] (52/228 GB)
     * option b: 23% 52/228GB
     * @return 
     */
    public String toDisplayString() {
        return percentUsed + "% " + DFT.format(usedGb) + "/" + DFT.format(totalGb) + " GB";
    }
    
    public String getUsageTitleDisplay() {
        return percentUsed + "% (" + DFT.format(usedGb) + "/" + DFT.format(totalGb) + " GB)";
    }
}