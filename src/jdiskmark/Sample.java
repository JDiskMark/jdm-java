
package jdiskmark;

import java.text.DecimalFormat;

/**
 * A unit of IO measurement
 */
public class Sample {
    
    static DecimalFormat df = new DecimalFormat("###.###");
    
    public enum SampleType { READ, WRITE; }
    
    Sample(SampleType type) {
        this.type=type;
    }
    
    SampleType type;
    int sampleNum = 0;       // x-axis
    double bwMbSec = 0;    // y-axis
    double cumMin = 0;
    double cumMax = 0;
    double cumAvg = 0;
    double elapsedTimeMs;
    
    @Override
    public String toString() {
        return "Sample(" + type + "): " + sampleNum + " bwMbSec: " + getBwMbSec() 
                + " avg: " + getAvg();
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
