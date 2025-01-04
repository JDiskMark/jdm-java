/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package jdiskmark;

public class DiskUsageInfo {
    public int percentUsed;
    public long freeGb;
    public long usedGb;
    public long totalGb;

    public DiskUsageInfo() {}
    
    public DiskUsageInfo(double percentUsed, double usedGB, double totalGB) {
        this.percentUsed = (int) Math.round(percentUsed);
        this.usedGb = Math.round(usedGB);
        this.totalGb = Math.round(totalGB);
    }
    
    public DiskUsageInfo(double percentUsed, double freeGB, double usedGB, double totalGB) {
        this.percentUsed = (int) Math.round(percentUsed);
        this.freeGb = Math.round(freeGB);
        this.usedGb = Math.round(usedGB);
        this.totalGb = Math.round(totalGB);
    }
    
    public int calcPercentageUsed() {
        if (totalGb != 0) {
            percentUsed = Math.round(100f * (float) usedGb / (float) totalGb);
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
        return + percentUsed + "% " + usedGb + "/" + totalGb + " GB";
    }
    
    public String getUsageTitleDisplay() {
        return  percentUsed + "% (" + usedGb + "/" + totalGb + " GB)";
    }
}