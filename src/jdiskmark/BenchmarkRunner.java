package jdiskmark;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import jdiskmark.App.IoEngine;
import jdiskmark.Benchmark.IOMode;

public class BenchmarkRunner {
    
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
    
    private static final Logger logger = Logger.getLogger(BenchmarkRunner.class.getName());
    
    final BenchmarkListener listener;
    final BenchmarkConfig config;
    final AtomicLong lastUpdateMs = new AtomicLong(0);
    final LongAdder writeUnitsComplete = new LongAdder();
    final LongAdder readUnitsComplete = new LongAdder();
    long unitsTotal;
    long blockSize;
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
    
    public BenchmarkRunner(BenchmarkListener listener, BenchmarkConfig config) {
        this.listener = listener;
        this.config = config;
    }

    public Benchmark execute() throws Exception {
        long wUnitsTotal = config.hasWriteOperation() ? (long) config.numBlocks * config.numSamples : 0L;
        long rUnitsTotal = config.hasReadOperation() ? (long) config.numBlocks * config.numSamples : 0L;
        unitsTotal = wUnitsTotal + rUnitsTotal;
        blockSize = config.blockSize;
        
        if (config.ioEngine == IoEngine.LEGACY) {
            blockArr = new byte[(int)blockSize];
            for (int b = 0; b < blockArr.length; b++) {
                if (b % 2 == 0) blockArr[b] = (byte) 0xFF;
            }
        }

        //TODO: use config if possible
        String driveModel = Util.getDriveModel(App.locationDir);
        String partitionId = Util.getPartitionId(App.locationDir.toPath());
        DiskUsageInfo usageInfo = Util.getDiskUsage(App.locationDir.toString());

        // Initialize Benchmark
        
        Benchmark benchmark = new Benchmark(config);
        mapSystemInfo(benchmark, driveModel, partitionId, usageInfo);

        int startingSample = App.nextSampleNumber;
        int endingSample = App.nextSampleNumber + config.numSamples;
        int[][] tRanges = divideIntoRanges(startingSample, endingSample, config.numThreads);

        benchmark.recordStartTime();
        
        // Execution Loops
        if (config.hasWriteOperation()) {
            runOperation(benchmark, IOMode.WRITE, tRanges);
        }
        
        if (config.hasReadOperation() && config.hasWriteOperation() && !listener.isCancelled()) {
            throttledProgressUpdate(true);
            listener.requestCacheDrop();
        }

        if (config.hasReadOperation()) {
            runOperation(benchmark, IOMode.READ, tRanges);
        }

        benchmark.recordEndTime();
        return benchmark;
    }

    private void runOperation(Benchmark b, IOMode mode, int[][] ranges) throws Exception {
        BenchmarkOperation op = createOp(b, mode);
        ExecutorService executor = Executors.newFixedThreadPool(config.numThreads);
        List<Future<?>> futures = new ArrayList<>();

        final IOAction action = switch (config.ioEngine) {
            case LEGACY -> switch (mode) {
                case WRITE -> (s) -> s.measureWriteLegacy(blockSize, config.numBlocks, blockArr, this);
                case READ -> (s) -> s.measureReadLegacy(blockSize, config.numBlocks, blockArr, this);
            };
            case MODERN -> switch (mode) {
                case WRITE -> (s) -> s.measureWrite(blockSize, config.numBlocks, this);
                case READ -> (s) -> s.measureRead(blockSize, config.numBlocks, this);
            };
        };
        
        for (int[] range : ranges) {
            futures.add(executor.submit(() -> {
                for (int s = range[0]; s < range[1] && !listener.isCancelled(); s++) {
                    Sample.Type type = mode == IOMode.WRITE ? Sample.Type.WRITE : Sample.Type.READ;
                    Sample sample = new Sample(type, s);
                    try {
                        action.perform(sample);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                        throw new RuntimeException(ex);
                    }
                    
                    //TODO: review for putting into onSampleComplete
                    App.updateMetrics(sample);
                    // Update op-level cumulative stats
                    op.bwMax = sample.cumMax;
                    op.bwMin = sample.cumMin;
                    op.bwAvg = sample.cumAvg;
                    op.accAvg = sample.cumAccTimeMs;
                    op.add(sample);
                    
                    switch (mode) {
                        case WRITE -> writeUnitsComplete.increment();
                        case READ -> readUnitsComplete.increment();
                    }
                    
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
        op.blockOrder = config.blockOrder;
        op.numSamples = config.numSamples;
        op.numBlocks = config.numBlocks;
        op.blockSize = config.blockSize;
        op.txSize = config.txSize;
        op.numThreads = config.numThreads;
        if (mode == IOMode.WRITE) {
            op.setWriteSyncEnabled(config.writeSyncEnabled);
        }
        b.getOperations().add(op);
        return op;
    }
    
    private void mapSystemInfo(Benchmark b, String model, String partId, DiskUsageInfo u) {
        b.systemInfo.processorName = App.processorName;
        b.systemInfo.os = App.os;
        b.systemInfo.arch = App.arch;
        b.systemInfo.jdk = App.jdk;
        b.systemInfo.locationDir = App.locationDir.toString();
        
        b.driveInfo.driveModel = model;
        b.driveInfo.partitionId = partId;
        b.driveInfo.percentUsed = u.percentUsed;
        b.driveInfo.usedGb = u.usedGb;
        b.driveInfo.totalGb = u.totalGb;
    }
}