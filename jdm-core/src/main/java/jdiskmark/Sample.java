package jdiskmark;

// constants
import com.fasterxml.jackson.annotation.JsonAlias;
import static jdiskmark.App.MEGABYTE;
import static jdiskmark.Benchmark.BlockSequence.RANDOM;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
// See https://github.com/JDiskMark/jdm-java/issues/190
// on approaches to formally resolve
import com.sun.nio.file.ExtendedOpenOption;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
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
    double cumStdDev = 0;
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

    @JsonProperty("sd") // bandwidth standard deviation
    @JsonSerialize(using = RoundingSerializer.class)
    public double getStdDev() { return cumStdDev; }
    public void setStdDev(double stdDev) { cumStdDev = stdDev; }

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
        int part = bRunner.fileIndex(sampleNum);
        String name = (part == 0) ? "testdata.jdm" : "testdata.part" + part + ".jdm";
        return new File(bRunner.config.testDir + File.separator + name);
    }
    
    /** Start of this sample's region; in single-file mode each sample has its own region. */
    long regionOffset(long blockSize, int numOfBlocks, BenchmarkRunner bRunner) {
        if (App.multiFile) return 0;
        return (long) bRunner.regionInFile(sampleNum) * numOfBlocks * blockSize;
    }
    
    /** Reports a failed sample; the operation aborts instead of recording 0 MB/s (#253). */
    private UncheckedIOException sampleFailed(String action, File testFile, IOException e) {
        String msg = action + " failed for sample " + sampleNum + " on " + testFile + ": "
                + Util.describeIoError(e);
        Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, msg, e);
        App.err(msg);
        return new UncheckedIOException(msg, e);
    }
    
    // pre jdk 25 io api
    public void measureWriteLegacy(long blockSize, int numOfBlocks, byte[] blockArr, BenchmarkRunner bRunner) {
        File testFile = getTestFile(bRunner);
        long base = regionOffset(blockSize, numOfBlocks, bRunner);
        long startTime = System.nanoTime();
        long totalBytesWrittenInSample = 0;
        String mode = (App.writeSyncEnable) ? "rwd" : "rw";
        int[] writeOrder = (App.blockSequence == Benchmark.BlockSequence.RANDOM)
                ? Util.shuffledIndices(numOfBlocks) : null;
        try {
            try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, mode)) {
                for (int b = 0; b < numOfBlocks; b++) {
                    if (writeOrder != null) {
                        rAccFile.seek(base + writeOrder[b] * blockSize);
                    } else {
                        rAccFile.seek(base + b * blockSize);
                    }
                    rAccFile.write(blockArr, 0, (int)blockSize);
                    totalBytesWrittenInSample += blockSize;
                    bRunner.updateWriteProgress();
                }
                if (bRunner.flushWriteSamples()) rAccFile.getFD().sync();
            }
        } catch (IOException ex) {
            throw sampleFailed("Write", testFile, ex);
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
        long base = regionOffset(blockSize, numOfBlocks, bRunner);
        long startTime = System.nanoTime();
        long totalBytesReadInMark = 0;
        try {
            try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, "r")) {
                for (int b = 0; b < numOfBlocks; b++) {
                    if (App.blockSequence == Benchmark.BlockSequence.RANDOM) {
                        int rLoc = Util.randInt(0, numOfBlocks - 1);
                        rAccFile.seek(base + rLoc * blockSize);
                    } else {
                        rAccFile.seek(base + b * blockSize);
                    }
                    rAccFile.readFully(blockArr, 0, (int)blockSize);
                    totalBytesReadInMark += blockSize;
                    bRunner.updateReadProgress();
                }
            }
        } catch (IOException ex) {
            throw sampleFailed("Read", testFile, ex);
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
        long byteAlignment = bRunner.effectiveAlignment;
        File testFile = getTestFile(bRunner);
        long base = regionOffset(blockSize, numOfBlocks, bRunner);
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
        } catch (UnsupportedOperationException | IOException e) {
            initialFc = openFallbackOrAbort(testFile, options, e, bRunner);
        }
        
        // Direct I/O is only blamed when the buffered retry succeeds (#253)
        IOException directError = null;
        retry:
        while (true) {
            FileChannel retryFc = (directError != null)
                    ? openWithoutDirect(testFile, options)
                    : initialFc;
            if (retryFc == null) break;
            try (FileChannel fc = retryFc; Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(blockSize, byteAlignment);
                MemorySegment.copy(MemorySegment.ofArray(bRunner.blockArr), 0, segment, 0, blockSize);
                totalBytesWritten = 0;
                // each block exactly once, so the whole region exists for later random reads
                int[] writeOrder = (bRunner.config.blockOrder == RANDOM)
                        ? Util.shuffledIndices(numOfBlocks) : null;
                for (int b = 0; b < numOfBlocks; b++) {
                    if (bRunner.isStopping()) break;
                    long blockIndex = (writeOrder != null) ? writeOrder[b] : b;
                    long byteOffset = base + blockIndex * blockSize;

                    int written = fc.write(segment.asByteBuffer(), byteOffset);
                    totalBytesWritten += written;
                    bRunner.updateWriteProgress();
                }
                if (directError != null) {
                    bRunner.disableDirectIo("Direct I/O write failed: " + Util.describeIoError(directError));
                }
                if (bRunner.flushWriteSamples()) fc.force(false);
                break retry;
            } catch (IOException e) {
                if (directError == null && options.contains(ExtendedOpenOption.DIRECT)) {
                    directError = e;
                    continue retry;
                }
                throw sampleFailed("Write", testFile, e);
            }
        }
        long elapsedTimeNs = System.nanoTime() - startTime;
        accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numOfBlocks;
        double sec = (double) elapsedTimeNs / 1_000_000_000d;
        bwMbSec = (double) totalBytesWritten / (double) MEGABYTE / sec;
    }
    
public void prepareRead(long blockSize, int numOfBlocks, BenchmarkRunner bRunner) {
    long byteAlignment = bRunner.effectiveAlignment;
    
    File testFile = getTestFile(bRunner);
    long base = regionOffset(blockSize, numOfBlocks, bRunner);
    Set<OpenOption> options = new HashSet<>();
    options.add(StandardOpenOption.WRITE);
    options.add(StandardOpenOption.CREATE);
    if (App.multiFile) {
        // not the single file: its regions are written by several samples and threads
        options.add(StandardOpenOption.TRUNCATE_EXISTING);
    }

    // Use a single try-with-resources and let the Exception bubble up
    // to the BenchmarkRunner's try-catch block.
    try (FileChannel fc = FileChannel.open(testFile.toPath(), options); 
         Arena arena = Arena.ofConfined()) {
        
        MemorySegment segment = arena.allocate(blockSize, byteAlignment);
        MemorySegment.copy(MemorySegment.ofArray(bRunner.blockArr), 0, segment, 0, blockSize);
        long totalBytesWritten = 0;
        
        for (int b = 0; b < numOfBlocks; b++) {
            if (bRunner.isStopping()) break;
            
            long byteOffset = base + (long) b * blockSize;
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
        long base = regionOffset(blockSize, numOfBlocks, bRunner);
        long startTime = System.nanoTime();
        long byteAlignment = bRunner.effectiveAlignment;
        
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.READ);
        if (App.directEnable) {
            options.add(ExtendedOpenOption.DIRECT); // non-standard api
        }
        
        FileChannel initialFc = null;
        try {
            initialFc = FileChannel.open(testFile.toPath(), options);
        } catch (UnsupportedOperationException | IOException e) {
            initialFc = openFallbackOrAbort(testFile, options, e, bRunner);
        }
        
        // Direct I/O is only blamed when the buffered retry succeeds (#253)
        IOException directError = null;
        retry:
        while (true) {
            FileChannel retryFc = (directError != null)
                    ? openWithoutDirect(testFile, options)
                    : initialFc;
            if (retryFc == null) break;
            try (FileChannel fc = retryFc; Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(blockSize, byteAlignment);
                totalBytesRead = 0;
                for (int b = 0; b < numOfBlocks; b++) {
                    if (bRunner.isStopping()) break;
                    long blockIndex = (bRunner.config.blockOrder == RANDOM) ? Util.randInt(0, (int)(numOfBlocks - 1)) : b;
                    long byteOffset = base + blockIndex * blockSize;
                    int read = fc.read(segment.asByteBuffer(), byteOffset);
                    if (read < 0) {
                        throw new EOFException("test data ends before offset " + byteOffset);
                    }
                    totalBytesRead += read;
                    bRunner.updateReadProgress();
                }
                if (directError != null) {
                    bRunner.disableDirectIo("Direct I/O read failed: " + Util.describeIoError(directError));
                }
                break retry;
            } catch (IOException e) {
                // a short file is a test data problem, not a Direct I/O failure
                if (directError == null && !(e instanceof EOFException)
                        && options.contains(ExtendedOpenOption.DIRECT)) {
                    directError = e;
                    continue retry;
                }
                throw sampleFailed("Read", testFile, e);
            }
        }
        long elapsedTimeNs = System.nanoTime() - startTime;
        accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numOfBlocks;
        double sec = (double) elapsedTimeNs / 1_000_000_000d;
        bwMbSec = ((double) totalBytesRead / (double) MEGABYTE) / sec;
    }

    /**
     * Retries a failed open without Direct I/O. Direct I/O is only blamed when
     * the buffered retry succeeds; otherwise the real open error is reported
     * and the operation is aborted.
     */
    private static FileChannel openFallbackOrAbort(File testFile, Set<OpenOption> options,
            Exception cause, BenchmarkRunner bRunner) {
        if (options.remove(ExtendedOpenOption.DIRECT)) {
            try {
                FileChannel fc = FileChannel.open(testFile.toPath(), options);
                bRunner.disableDirectIo("Direct I/O open failed or unsupported: "
                        + Util.describeIoError(cause));
                return fc;
            } catch (IOException ex) {
                cause = ex;
            }
        }
        String msg = "Cannot open test file " + testFile + ": " + Util.describeIoError(cause);
        Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, msg, cause);
        App.err(msg);
        throw new UncheckedIOException(msg,
                cause instanceof IOException io ? io : new IOException(cause));
    }

    /** Buffered retry after a Direct I/O write/read error; aborts the run if it can't open. */
    private static FileChannel openWithoutDirect(File testFile,
            Set<OpenOption> originalOptions) {
        Set<OpenOption> fallback = new HashSet<>(originalOptions);
        fallback.remove(ExtendedOpenOption.DIRECT);
        try {
            return FileChannel.open(testFile.toPath(), fallback);
        } catch (IOException e) {
            String msg = "Cannot open test file " + testFile + " for buffered I/O fallback: "
                    + Util.describeIoError(e);
            Logger.getLogger(Sample.class.getName()).log(Level.SEVERE, msg, e);
            App.err(msg);
            throw new UncheckedIOException(msg, e);
        }
    }
}