package jdiskmark;

// constants
import com.fasterxml.jackson.annotation.JsonAlias;
import static jdiskmark.App.MEGABYTE;
import static jdiskmark.Benchmark.BlockSequence.RANDOM;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A unit of IO measurement
 */
public class Sample {
    
    static final DecimalFormat DF = new DecimalFormat("###.###");
    public enum Type { READ, WRITE; }
    
    @JsonIgnore
    Type type;
    int sampleNum = 0;     // x-axis
    double bwMbSec = 0;    // y-axis
    double cumAvg = 0;
    double cumMax = 0;
    double cumMin = 0;
    double accessTimeMs;
    double cumAccTimeMs;
        
    // needed for jackson
    public Sample() {}
    
    Sample(Type type, int sampleNumber) {
        this.type = type;
        sampleNum = sampleNumber;
    }
    
    @Override
    public String toString() {
        return "Sample(" + type + "): " + sampleNum + " bwMBs=" + getBwMbSecDisplay() 
                + " avg=" + getAvgDisplay() + " accessTimeMs=" + accessTimeMs;
    }
    
    // getters and setters
    
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    
    @JsonProperty("sn") // sample number
    public int getSampleNum() { return sampleNum; }
    public void setSampleNum(int number) { sampleNum = number; }
    
    // bandwidth statistics
    
    @JsonProperty("bw") // bandwidth
    @JsonSerialize(using = RoundingSerializer.class)
    public double getBwMbSec() { return bwMbSec; }
    public void setBwMbSec(double bwMb) { bwMbSec = bwMb; }
    
    @JsonProperty("bt") // bandwidth trend
    @JsonAlias({"bwt", "bt"})
    @JsonSerialize(using = RoundingSerializer.class)
    public double getAvg() { return cumAvg; }    
    public void setAvg(double avg) { cumAvg = avg; }

    @JsonProperty("mx") // bandwidth maximum
    @JsonSerialize(using = RoundingSerializer.class)
    public double getMax() { return cumMax; }
    public void setMax(double max) { cumMax = max; }
    
    @JsonProperty("mn") // bandwidth minimum
    @JsonSerialize(using = RoundingSerializer.class)
    public double getMin() { return cumMin; }
    public void setMin(double min) { cumMin = min; }

    // access time statistics
    
    @JsonProperty("la") // latency
    @JsonSerialize(using = RoundingSerializer.class)
    public double getAccessTimeMs() { return accessTimeMs; }
    public void setAccessTimeMs(double accessTime) { accessTimeMs = accessTime; }
    
    @JsonProperty("lt") // latency trend
    @JsonAlias({"lat", "lt"})
    @JsonSerialize(using = RoundingSerializer.class)
    public double getCumAccTimeMs() { return cumAccTimeMs; }
    public void setCumAccTimeMs(double cumAccTime) { cumAccTimeMs = cumAccTime; }

    // display methods
    @JsonIgnore
    public String getBwMbSecDisplay() {
        return DF.format(bwMbSec);
    }
    @JsonIgnore
    public String getAvgDisplay() {
        return DF.format(cumAvg);
    }
    @JsonIgnore
    public String getMaxDisplay() {
        return DF.format(cumMax);
    }
    @JsonIgnore
    public String getMinDisplay() {
        return DF.format(cumMin);
    }
    
    @JsonIgnore
    public File getTestFile(BenchmarkRunner bRunner) {
        if (App.multiFile) {
            return new File(bRunner.config.testDir + File.separator + "testdata" + sampleNum + ".jdm");
        }
        return new File(bRunner.config.testDir + File.separator + "testdata.jdm");
    }
    
    // pre jdk 25 io api
    public void measureWriteLegacy(long blockSize, int numOfBlocks, byte[] blockArr, BenchmarkRunner bRunner) {
        File testFile = getTestFile(bRunner);
        long startTime = System.nanoTime();
        long totalBytesWrittenInSample = 0;
        String mode = (App.writeSyncEnable) ? "rwd" : "rw";
        try {
            try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, mode)) {
                for (int b = 0; b < numOfBlocks; b++) {
                    if (App.blockSequence == Benchmark.BlockSequence.RANDOM) {
                        int rLoc = Util.randInt(0, numOfBlocks - 1);
                        rAccFile.seek(rLoc * blockSize);
                    } else {
                        rAccFile.seek(b * blockSize);
                    }
                    rAccFile.write(blockArr, 0, (int)blockSize);
                    totalBytesWrittenInSample += blockSize;
                    bRunner.updateWriteProgress();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, null, ex);
        }
        long endTime = System.nanoTime();
        long elapsedTimeNs = endTime - startTime;
        accessTimeMs = (elapsedTimeNs / 1_000_000f) / numOfBlocks;
        double sec = (double) elapsedTimeNs / 1_000_000_000d;
        double mbWritten = (double) totalBytesWrittenInSample / (double) MEGABYTE;
        bwMbSec = mbWritten / sec;
    }
    
    // pre jdk 25 io api
    public void measureReadLegacy(long blockSize, int numOfBlocks, byte[] blockArr, BenchmarkRunner bRunner) {
        File testFile = getTestFile(bRunner);
        long startTime = System.nanoTime();
        long totalBytesReadInMark = 0;
        try {
            try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, "r")) {
                for (int b = 0; b < numOfBlocks; b++) {
                    if (App.blockSequence == Benchmark.BlockSequence.RANDOM) {
                        int rLoc = Util.randInt(0, numOfBlocks - 1);
                        rAccFile.seek(rLoc * blockSize);
                    } else {
                        rAccFile.seek(b * blockSize);
                    }
                    rAccFile.readFully(blockArr, 0, (int)blockSize);
                    totalBytesReadInMark += blockSize;
                    bRunner.updateReadProgress();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, null, ex);
        }
        long endTime = System.nanoTime();
        long elapsedTimeNs = endTime - startTime;
        accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numOfBlocks;
        double sec = (double) elapsedTimeNs / 1_000_000_000d;
        double mbRead = (double) totalBytesReadInMark / (double) MEGABYTE;
        bwMbSec = mbRead / sec;
    }
    
    public void measureWrite(long blockSize, int numOfBlocks, BenchmarkRunner bRunner) {
        long totalBytesWritten = 0;
        long byteAlignment = bRunner.config.sectorAlignment.bytes;
        if (byteAlignment <= 0) {
            // if not selected use default layout alignment
            MemoryLayout layout = MemoryLayout.sequenceLayout(blockSize, ValueLayout.JAVA_BYTE);
            byteAlignment = layout.byteAlignment();
        }
        File testFile = getTestFile(bRunner);
        long startTime = System.nanoTime();
        
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.WRITE);
        options.add(StandardOpenOption.CREATE);
        if (App.writeSyncEnable) {
            options.add(StandardOpenOption.DSYNC);
        }
        if (App.directEnable) {
            options.add(ExtendedOpenOption.DIRECT); // non-standard api
        }
        FileChannel initialFc = null;
        try {
            initialFc = FileChannel.open(testFile.toPath(), options);
        } catch (UnsupportedOperationException e) {
            // Fallback: Remove ExtendedOpenOption.DIRECT and try again
            App.err("Direct I/O is not supported on this system. Falling back to buffered I/O; benchmark results may differ from native Direct I/O performance.");
            options.remove(ExtendedOpenOption.DIRECT);
            try {
                initialFc = FileChannel.open(testFile.toPath(), options);
            } catch (IOException ex) {
                Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, "Failed to open FileChannel", ex);
                App.err("Failed to open FileChannel, aborting measurement");
                return;
            }
        } catch (IOException e) {
            if (App.directEnable) {
                App.err("Direct I/O open failed: " + e.getMessage() + ". Falling back to buffered I/O.");
                options.remove(ExtendedOpenOption.DIRECT);
                try {
                    initialFc = FileChannel.open(testFile.toPath(), options);
                } catch (IOException ex) {
                    Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, "Failed to open FileChannel", ex);
                    App.err("Failed to open FileChannel, aborting measurement");
                    return;
                }
            } else {
                Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, null, e);
                return;
            }
        }
        
        try (FileChannel fc = initialFc; Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(blockSize, byteAlignment);
            for (int b = 0; b < numOfBlocks; b++) {
                if (bRunner.listener.isCancelled()) break;
                long blockIndex = (bRunner.config.blockOrder == RANDOM) ?
                        Util.randInt(0, numOfBlocks - 1) : b;
                long byteOffset = blockIndex * blockSize;

                int written = fc.write(segment.asByteBuffer(), byteOffset);
                totalBytesWritten += written;
                bRunner.updateWriteProgress();
            }
        } catch (IOException e) {
            Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, null, e);
        }
        long elapsedTimeNs = System.nanoTime() - startTime;
        accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numOfBlocks;
        double sec = (double) elapsedTimeNs / 1_000_000_000d;
        bwMbSec = (double) totalBytesWritten / (double) MEGABYTE / sec;
    }
    
public void prepareRead(long blockSize, int numOfBlocks, BenchmarkRunner bRunner) {
    long byteAlignment = bRunner.config.sectorAlignment.bytes;
    if (byteAlignment <= 0) {
        MemoryLayout layout = MemoryLayout.sequenceLayout(blockSize, ValueLayout.JAVA_BYTE);
        byteAlignment = layout.byteAlignment();
    }
    
    File testFile = getTestFile(bRunner);
    Set<OpenOption> options = new HashSet<>();
    options.add(StandardOpenOption.WRITE);
    options.add(StandardOpenOption.CREATE);
    options.add(StandardOpenOption.TRUNCATE_EXISTING);

    // Use a single try-with-resources and let the Exception bubble up
    // to the BenchmarkRunner's try-catch block.
    try (FileChannel fc = FileChannel.open(testFile.toPath(), options); 
         Arena arena = Arena.ofConfined()) {
        
        MemorySegment segment = arena.allocate(blockSize, byteAlignment);
        long totalBytesWritten = 0;
        
        for (int b = 0; b < numOfBlocks; b++) {
            if (bRunner.listener.isCancelled()) break;
            
            long byteOffset = (long) b * blockSize;
            int written = fc.write(segment.asByteBuffer(), byteOffset);
            totalBytesWritten += written;
            // For read-only benchmarks, we reuse the "write" progress counters to
            // track preparation of data to be read. In execute(), wUnitsTotal is
            // set from rUnitsTotal so this correctly reflects read preparation.
            bRunner.updateWriteProgress();
        }

        if (App.verbose) {
            App.msg("bytesGenerated=" + totalBytesWritten + " for " + testFile.getName());
        }
        
    } catch (IOException e) {
        // Log it, but CRITICALLY: throw a RuntimeException so the 
        // ExecutorService's Future.get() catches the failure.
        Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, "Prep failed", e);
        throw new RuntimeException("Read preparation failed for: " + testFile.getName(), e);
    }
}
    
    public void measureRead(long blockSize, int numOfBlocks, BenchmarkRunner bRunner) {
        long totalBytesRead = 0;
        File testFile = getTestFile(bRunner);
        long startTime = System.nanoTime();
        long byteAlignment = bRunner.config.sectorAlignment.bytes;
        if (byteAlignment <= 0) {
            // if not selected use default layout alignment
            MemoryLayout layout = MemoryLayout.sequenceLayout(blockSize, ValueLayout.JAVA_BYTE);
            byteAlignment = layout.byteAlignment();
        }
        
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        if (App.directEnable) {
            options.add(ExtendedOpenOption.DIRECT); // non-standard api
        }
        
        FileChannel initialFc = null;
        try {
            initialFc = FileChannel.open(testFile.toPath(), options);
        } catch (UnsupportedOperationException e) {
            // Fallback: Remove ExtendedOpenOption.DIRECT and try again
            App.err("Direct I/O is not supported on this system. Falling back to buffered I/O; benchmark results may differ from native Direct I/O performance.");
            options.remove(ExtendedOpenOption.DIRECT);
            try {
                initialFc = FileChannel.open(testFile.toPath(), options);
            } catch (IOException ex) {
                Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, "Failed to open FileChannel", ex);
                App.err("Failed to open FileChannel, aborting measurement");
                return;
            }
        } catch (IOException ex) {
            if (App.directEnable) {
                App.err("Direct I/O open failed: " + ex.getMessage() + ". Falling back to buffered I/O.");
                options.remove(ExtendedOpenOption.DIRECT);
                try {
                    initialFc = FileChannel.open(testFile.toPath(), options);
                } catch (IOException e) {
                    Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, "Failed to open FileChannel", e);
                    App.err("Failed to open FileChannel, aborting measurement");
                    return;
                }
            } else {
                Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        }
        
        try (FileChannel fc = initialFc; Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(blockSize, byteAlignment);
            for (int b = 0; b < numOfBlocks; b++) {
                if (bRunner.listener.isCancelled()) break;
                long blockIndex = (bRunner.config.blockOrder == RANDOM) ? Util.randInt(0, (int)(numOfBlocks - 1)) : b;
                long byteOffset = blockIndex * blockSize;
                int read = fc.read(segment.asByteBuffer(), byteOffset);
                totalBytesRead += read;
                bRunner.updateReadProgress();
            }
        } catch (IOException ex) {
            Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, null, ex);
        }
        long elapsedTimeNs = System.nanoTime() - startTime;
        accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numOfBlocks;
        double sec = (double) elapsedTimeNs / 1_000_000_000d;
        bwMbSec = ((double) totalBytesRead / (double) MEGABYTE) / sec;
    }
}