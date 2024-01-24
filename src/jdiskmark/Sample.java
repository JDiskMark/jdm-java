
package jdiskmark;

import java.text.DecimalFormat;

/**
 * A unit of IO measurement
 */
public class Sample {
    
    static final DecimalFormat DF = new DecimalFormat("###.###");
    
    public enum Type { READ, WRITE; }
    
    Sample(Type type, int sampleNumber) {
        this.type = type;
        this.sampleNum = sampleNumber;
    }
    
    Type type;
    int sampleNum = 0;     // x-axis
    
    double bwMbSec = 0;    // y-axis
    double cumMin = 0;
    double cumMax = 0;
    double cumAvg = 0;
    double accessTimeMs;
    double cumAccTimeMs;
    
    @Override
    public String toString() {
        return "Sample(" + type + "): " + sampleNum + " bwMBs: " + getBwMbSec() 
                + " avg: " + getAvg();
    }
    
    public String getBwMbSec() {
        return DF.format(bwMbSec);
    }
    
    public String getMin() {
        return DF.format(cumMin);
    }
    
    public String getMax() {
        return DF.format(cumMax);
    }
    
    public String getAvg() {
        return DF.format(cumAvg);
    }
}
