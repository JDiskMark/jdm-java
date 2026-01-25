package jdiskmark;

import java.util.logging.Level;
import java.util.logging.Logger;
import static jdiskmark.App.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import jdiskmark.Benchmark.IOMode;

public class BenchmarkLogic {
    
    // Listener interface to decouple from Swing/CLI
    public interface BenchmarkListener {
        void onSampleComplete(Sample sample);
        void onProgressUpdate(long completed, long total);
        boolean isCancelled();
        void requestCacheDrop();
    }
    
    @FunctionalInterface
    private interface IOAction {
        void perform(Sample sample) throws Exception;
    }

    // Minimum milliseconds between progress updates to avoid excessive UI refreshes
    private static final long UPDATE_INTERVAL = 25;
    
    private static final Logger logger = Logger.getLogger(BenchmarkLogic.class.getName());
    
    final BenchmarkListener listener;
    final AtomicLong lastUpdateMs = new AtomicLong(0);
    final LongAdder writeUnitsComplete = new LongAdder();
    final LongAdder readUnitsComplete = new LongAdder();
    int unitsTotal;
    int blockSize;
    byte[] blockArr; // for legacy jdk io

    public static int[][] divideIntoRanges(int startIndex, int endIndex, int numThreads) {
        if (numThreads <= 0 || endIndex < startIndex) {
            return new int[0][0]; // Handle invalid input
        }

        int numElements = endIndex - startIndex; // Calculate the total number of elements
        int[][] ranges = new int[numThreads][2];
        int rangeSize = numElements / numThreads;
        int remainder = numElements % numThreads;
        int start = startIndex;

        for (int i = 0; i < numThreads; i++) {
            int end = start + rangeSize;
            if (remainder > 0) {
                end++; // Distribute the remainder
                remainder--;
            }
            ranges[i][0] = start;
            ranges[i][1] = end;
            start = end;
        }
        return ranges;
    }
    
    public BenchmarkLogic(BenchmarkListener listener) {
        this.listener = listener;
    }

    public Benchmark execute() throws Exception {
        // 1. Setup metadata
        int wUnitsTotal = isWriteEnabled() ? numOfBlocks * numOfSamples : 0;
        int rUnitsTotal = isReadEnabled() ? numOfBlocks * numOfSamples : 0;
        unitsTotal = wUnitsTotal + rUnitsTotal;
        blockSize = blockSizeKb * KILOBYTE;
        
        if (ioEngine == IoEngine.LEGACY) {
            blockArr = new byte[blockSize];
            for (int b = 0; b < blockArr.length; b++) {
                if (b % 2 == 0) blockArr[b] = (byte) 0xFF;
            }
        }

        String driveModel = Util.getDriveModel(locationDir);
        String partitionId = Util.getPartitionId(locationDir.toPath());
        DiskUsageInfo usageInfo = Util.getDiskUsage(locationDir.toString());

        // 2. Initialize Benchmark Object
        Benchmark benchmark = new Benchmark(benchmarkType);
        mapSystemInfo(benchmark, driveModel, partitionId, usageInfo);

        int[][] tRanges = divideIntoRanges(nextSampleNumber, nextSampleNumber + numOfSamples, numOfThreads);

        // 3. Execution Loops
        if (isWriteEnabled()) {
            runOperation(benchmark, Benchmark.IOMode.WRITE, tRanges, blockSize, blockArr, unitsTotal);
        }
        
        if (isReadEnabled() && isWriteEnabled() && !listener.isCancelled()) {
            throttledProgressUpdate(true);
            listener.requestCacheDrop();
        }

        if (isReadEnabled()) {
            runOperation(benchmark, IOMode.READ, tRanges, blockSize, blockArr, unitsTotal);
        }

        benchmark.endTime = LocalDateTime.now();
        return benchmark;
    }

    private void runOperation(Benchmark b, IOMode mode, int[][] ranges, int blockSize, byte[] blockArr, int total) throws Exception {
        BenchmarkOperation op = createOp(b, mode);
        ExecutorService executor = Executors.newFixedThreadPool(numOfThreads);
        List<Future<?>> futures = new ArrayList<>();

        final IOAction action;
        if (ioEngine == IoEngine.LEGACY) {
            if (mode == IOMode.WRITE) {
                action = (s) -> s.measureWriteLegacy(blockSize, numOfBlocks, blockArr, this);
            } else {
                action = (s) -> s.measureReadLegacy(blockSize, numOfBlocks, blockArr, this);
            }
        } else {
            if (mode == IOMode.WRITE) {
                action = (s) -> s.measureWrite(blockSize, numOfBlocks, this);
            } else {
                action = (s) -> s.measureRead(blockSize, numOfBlocks, this);
            }
        }
        
        for (int[] range : ranges) {
            futures.add(executor.submit(() -> {
                for (int s = range[0]; s < range[1] && !listener.isCancelled(); s++) {
                    Sample.Type type = mode == IOMode.WRITE ? Sample.Type.WRITE : Sample.Type.READ;
                    Sample sample = new Sample(type, s);
                    try {
                        action.perform(sample);
                    } catch (Exception ex) {
                        Logger.getLogger(BenchmarkLogic.class.getName()).log(Level.SEVERE, null, ex);
                        throw new RuntimeException(ex);
                    }
                    
                    updateMetrics(sample);
                    // Update op-level cumulative stats
                    op.bwMax = sample.cumMax;
                    op.bwMin = sample.cumMin;
                    op.bwAvg = sample.cumAvg;
                    op.accAvg = sample.cumAccTimeMs;
                    op.add(sample);
                    
                    if (mode == IOMode.WRITE) writeUnitsComplete.increment();
                    else readUnitsComplete.increment();
                    
                    listener.onSampleComplete(sample);
                    throttledProgressUpdate(false);
                }
            }));
        }
        executor.shutdown();
        try {
            for (Future<?> f : futures) f.get(); // Wait and propagate exceptions
        } catch (ExecutionException e) {
            throw new Exception("Threaded IO operation failed", e.getCause());
        } finally {
            op.endTime = LocalDateTime.now();
            op.setTotalOps(mode == IOMode.WRITE ? writeUnitsComplete.sum() : readUnitsComplete.sum());
            if (op.ioMode == IOMode.WRITE) App.wIops = op.iops;
            else App.rIops = op.iops;
        }
    }
    
    public void throttledProgressUpdate(boolean forceUpdate) {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastUpdateMs.get();
        long elapsedTime = currentTime - lastTime;
        
        // Aggregate from LongAdders (Thread-safe, no sync needed)
        long totalCompleted = writeUnitsComplete.sum() + readUnitsComplete.sum();
        float percentComplete = (float)totalCompleted / (float) unitsTotal * 100f;
        int newProgress = (int)percentComplete;
        if (elapsedTime >= UPDATE_INTERVAL || forceUpdate) {
            if (lastUpdateMs.compareAndSet(lastTime, currentTime) || forceUpdate) {
                // Clamp value to Swing limits
                int clampedProgress = Math.min(100, Math.max(0, newProgress));
                if (listener != null) {
                    listener.onProgressUpdate(clampedProgress, 100);
                }
            }
        }
    }
    
    public void updateWriteProgress() {
        writeUnitsComplete.increment();
        throttledProgressUpdate(false);
    }
    
    public void updateReadProgress() {
        readUnitsComplete.increment();
        throttledProgressUpdate(false);
    }
    
    // Helper methods for mapping metadata omitted for brevity...
    private BenchmarkOperation createOp(Benchmark b, IOMode mode) {
        BenchmarkOperation op = new BenchmarkOperation();
        op.setBenchmark(b);
        op.ioMode = mode;
        op.blockOrder = blockSequence;
        op.numSamples = numOfSamples;
        op.numBlocks = numOfBlocks;
        op.blockSize = blockSizeKb;
        op.txSize = targetTxSizeKb();
        op.numThreads = numOfThreads;
        if (mode == IOMode.WRITE) {
            op.setWriteSyncEnabled(writeSyncEnable);
        }
        b.getOperations().add(op);
        return op;
    }
    
    private void mapSystemInfo(Benchmark b, String model, String partId, DiskUsageInfo u) {
        b.systemInfo.processorName = processorName;
        b.systemInfo.os = os;
        b.systemInfo.arch = arch;
        b.systemInfo.jdk = jdk;
        b.systemInfo.locationDir = locationDir.toString();
        
        b.driveInfo.driveModel = model;
        b.driveInfo.partitionId = partId;
        b.driveInfo.percentUsed = u.percentUsed;
        b.driveInfo.usedGb = u.usedGb;
        b.driveInfo.totalGb = u.totalGb;
    }
}