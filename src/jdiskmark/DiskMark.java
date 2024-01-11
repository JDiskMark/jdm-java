
package jdiskmark;

import java.text.DecimalFormat;

/**
 *
 */
public class DiskMark {
    
    static DecimalFormat df = new DecimalFormat("###.###");
    
    public enum MarkType { READ,WRITE; }
    
    DiskMark(MarkType type) {
        this.type=type;
    }
    
    MarkType type;
    int markNum = 0;       // x-axis
    double bwMbSec = 0;    // y-axis
    double cumMin = 0;
    double cumMax = 0;
    double cumAvg = 0;
    
    @Override
    public String toString() {
        return "Mark("+type+"): "+markNum+" bwMbSec: "+getBwMbSec()+" avg: "+getAvg();
    }
    
    public String getBwMbSec() {
        return df.format(bwMbSec);
    }
    
    public String getMin() {
        return df.format(cumMin);
    }
    
    public String getMax() {
        return df.format(cumMax);
    }
    
    public String getAvg() {
        return df.format(cumAvg);
    }
}
