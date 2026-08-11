package jdiskmark;

import static jdiskmark.GcDetector.MAX_GC_RETRIES;

import java.io.File;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import jdiskmark.App.IoEngine;
import jdiskmark.Benchmark.IOMode;
import static jdiskmark.Benchmark.IOMode.READ;
import static jdiskmark.Benchmark.IOMode.WRITE;

public class BenchmarkRunner {
    
    // Listener interface to decouple from Swing/CLI
    public interface BenchmarkListener {
        void onSampleComplete(Sample sample);
        void onProgressUpdate(long completed, long total);
        boolean isCancelled();
        void attemptCacheDrop();

        /**
         * Called after each operation (write or read) completes.
         * GUI listeners use this to flush buffered samples in PER_OPERATION
         * render mode. Default is a no-op so CLI and other callers are
         * unaffected.
         */
        default void onOperationComplete() {}
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
    long effectiveAlignment;
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
        long blocksPerPhase = (long) config.numBlocks * config.numSamples;

        long wUnitsTotal = config.hasWriteOperation() ? blocksPerPhase : 0L;
        long rUnitsTotal = config.hasReadOperation() ? blocksPerPhase : 0L;

        // #132 Handle the Read-Preparation phase for Read-Only benchmarks
        if (config.benchmarkType == Benchmark.BenchmarkType.READ) {
            // We set wUnitsTotal to blocksPerPhase because prepareRead() 
            // calls updateWriteProgress()
            wUnitsTotal = blocksPerPhase;
        }

        // Final total units for the progress bar denominator
        unitsTotal = wUnitsTotal + rUnitsTotal;
        
        blockSize = config.blockSize;
        effectiveAlignment = resolveAlignment(config);
        
        if (config.ioEngine == IoEngine.LEGACY) {
            blockArr = new byte[(int)blockSize];
            for (int b = 0; b < blockArr.length; b++) {
                if (b % 2 == 0) blockArr[b] = (byte) 0xFF;
            }
        }

        File configDir = new File(config.testDir);
        String driveModel = Util.getDriveModel(configDir);
        String partitionId = Util.getPartitionId(configDir.toPath());
        DiskUsageInfo usageInfo = Util.getDiskUsage(configDir.getAbsolutePath());

        // Initialize Benchmark
        
        Benchmark benchmark = new Benchmark(config);
        mapEnvironment(benchmark, driveModel, partitionId, usageInfo);
        // capture the render mode chosen at the time this run starts
        benchmark.setRenderMode(App.rmOption);

        int startingSample = App.nextSampleNumber;
        int endingSample = App.nextSampleNumber + config.numSamples;
        int[][] tRanges = divideIntoRanges(startingSample, endingSample, config.numThreads);

        if (config.gcHintsEnabled && !listener.isCancelled()) {
            GcDetector.triggerAndWait(); // Initial cleanup
        }
        
        // Fetch SMART data before the benchmark starts (Linux, macOS, and Windows, non-fatal if it fails).
        if (Smart.smartEnable) {
            Smart smart = null;
            try {
                Path path = App.locationDir.toPath();
                if (App.isLinux() || App.isMacOs()) {
                    String partition = UtilOs.getPartitionFromFilePathLinux(path);
                    List<String> devices = UtilOs.getDeviceNamesFromPartitionLinux(partition);
                    if (devices != null && !devices.isEmpty()) {
                        String device = devices.get(0);
                        if (Smart.process == null || !Smart.process.isAlive()) {
                            Smart.startPrivilegedShell();
                            Smart.startHeartbeat();
                        }
                        smart = Smart.getSmart(device);
                    }
                } else if (App.isWindows()) {
                    String driveLetter = UtilOs.getDriveLetterWindows(path);
                    String driveNum = UtilOs.getPhysicalDriveNumberWindows(driveLetter);
                    if (driveNum != null) {
                        smart = Smart.getSmart("pd" + driveNum);
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to fetch SMART data for benchmark", ex);
            }
            if (smart != null) {
                benchmark.setSmartData(smart);
            }

            // Update the SMART tab if running in GUI mode, without re-triggering SMART retrieval.
            if (Gui.mainFrame != null && Gui.smartPanel != null) {
                if (smart != null) {
                    final Smart finalSmart = smart;
                    String devName = (smart.getDevice() != null) ? smart.getDevice().getName() : null;
                    Gui.lastSmartData = smart;
                    Gui.lastSmartDeviceName = devName;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        Gui.smartPanel.populate(finalSmart);
                        Gui.smartPanel.onDataLoaded(devName != null ? devName : "unknown");
                    });
                } else {
                    Gui.runSmart();
                }
            }
        }
        
        benchmark.recordStartTime();
        
        // Execution Loops
        if (config.hasWriteOperation()) {
            runOperation(benchmark, IOMode.WRITE, tRanges);
            listener.onOperationComplete();
        } else if (config.hasReadOperation()) {
            // #132 this is a read without a write so we need to generate files
            runReadPreparation(tRanges);
        }
        
        throttledProgressUpdate(true);
        
        // cache reset if
        // 1. not cancelled
        // 2. read operation
        // 3. direct I/O not enabled (direct I/O bypasses cache on all platforms)
        if (!listener.isCancelled() && config.hasReadOperation() &&
                !config.getDirectIoEnabled()) {
            listener.attemptCacheDrop();
        }
        
        // If we are doing both, clear the heap between them
        if (config.gcHintsEnabled && !listener.isCancelled() && 
                config.hasWriteOperation() && config.hasReadOperation()) {
            GcDetector.triggerAndWait();
        }
        
        if (config.hasReadOperation() && !listener.isCancelled()) {
            runOperation(benchmark, IOMode.READ, tRanges);
            listener.onOperationComplete();
        }

        benchmark.recordEndTime();
        
        if (config.gcHintsEnabled) { System.gc(); } // clear heap no wait
        
        return benchmark;
    }

    private void runOperation(Benchmark b, IOMode mode, int[][] ranges) throws Exception {
        BenchmarkOperation op = createOp(b, mode);
        ExecutorService executor = Executors.newFixedThreadPool(config.numThreads);
        List<Future<?>> futures = new ArrayList<>();

        // use action to avoid adding a field in sample object
        final IOAction ioAction = switch (config.ioEngine) {
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
                GcDetector gcDetector = config.gcRetryEnabled ? new GcDetector() : null;
                if (gcDetector != null) gcDetector.start();
                try {
                    for (int s = range[0]; s < range[1] && !listener.isCancelled(); s++) {
                        Sample.Type type = mode == IOMode.WRITE ? Sample.Type.WRITE : Sample.Type.READ;
                        Sample sample = new Sample(type, s);
                        int retries = 0;
                        do {
                            if (gcDetector != null) gcDetector.reset();
                            try {
                                ioAction.perform(sample);
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, null, e);
                                throw new RuntimeException(e);
                            }
                            if (gcDetector != null && gcDetector.isGcDetected() && retries < MAX_GC_RETRIES) {
                                retries++;
                                synchronized (op) {
                                    op.gcRetriedSamples.add(s);
                                }
                                logger.log(Level.INFO,
                                        "GC detected during {0} sample {1}, retrying ({2}/{3})",
                                        new Object[]{mode, s, retries, MAX_GC_RETRIES});
                                App.msg("gc detected on sample " + s + " retrying...");
                                // reset progress by num blocks per sample
                                long resetUnits = (long)(config.numBlocks);
                                switch (mode) {
                                    case WRITE -> writeUnitsComplete.add(-resetUnits);
                                    case READ -> readUnitsComplete.add(-resetUnits);
                                }
                            } else {
                                // no gc retry enabled || no detection || max retries exceeded
                                break;
                            }
                        } while (true);

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
                } finally {
                    if (gcDetector != null) gcDetector.stop();
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
    
    private void runReadPreparation(int[][] ranges) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(config.numThreads);
        List<Future<?>> futures = new ArrayList<>();
        for (int[] range : ranges) {
            futures.add(executor.submit(() -> {
                for (int s = range[0]; s < range[1] && !listener.isCancelled(); s++) {
                    Sample sample = new Sample(Sample.Type.READ, s);
                    sample.prepareRead(blockSize, config.numBlocks, this);
                }
            }));
        }
        executor.shutdown();
        try {
            for (Future<?> f : futures) f.get(); // Wait and propagate exceptions
        } catch (ExecutionException e) {
            throw new Exception("read prep failed", e.getCause());
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
    
    private void mapEnvironment(Benchmark b, String model, String partId, DiskUsageInfo u) {
        b.systemId = (App.systemId != null) ? App.systemId : "";

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

    /**
     * Determines the effective byte alignment for Direct IO. When Direct IO is
     * enabled, the alignment must be at least the filesystem block size or the
     * write/read will fail with an {@code IOException}. This is critical for
     * vfat (FAT32) which typically uses 32 KB blocks.
     */
    private static long resolveAlignment(BenchmarkConfig config) {
        long userAlign = config.sectorAlignment.bytes;
        if (userAlign <= 0) {
            return MemoryLayout.sequenceLayout(
                    config.blockSize, ValueLayout.JAVA_BYTE).byteAlignment();
        }
        if (config.getDirectIoEnabled() == null || !config.getDirectIoEnabled()) {
            return userAlign;
        }
        try {
            long fsBlockSize = java.nio.file.Files.getFileStore(
                    Path.of(config.testDir)).getBlockSize();

            // Java's ExtendedOpenOption.DIRECT enforces that every I/O
            // transfer is a multiple of FileStore.getBlockSize(), which on
            // vfat returns the cluster size (e.g. 32 KB) rather than the
            // device sector size (512 B).  The Linux kernel itself only
            // requires sector-size alignment, but Java's NIO layer is more
            // restrictive.  When the user-chosen block size is smaller than
            // the cluster size, disable Direct IO upfront and inform the
            // user rather than silently retrying on every sample.
            if (config.blockSize < fsBlockSize) {
                App.msg("Direct I/O disabled: block size ("
                        + (config.blockSize / 1024) + " KB) is smaller than "
                        + "the filesystem block size ("
                        + (fsBlockSize / 1024) + " KB).");
                config.setDirectIoEnabled(false);
                App.directEnable = false;
                Gui.refreshChartBadges();
                return userAlign;
            }

            if (fsBlockSize > userAlign) {
                App.msg("Direct I/O: adjusting alignment from "
                        + userAlign + " to " + fsBlockSize
                        + " (filesystem block size)");
                // Update config so the benchmark record reflects
                // the actual alignment used during this run.
                for (App.SectorAlignment sa : App.SectorAlignment.values()) {
                    if (sa.bytes == fsBlockSize) {
                        config.setSectorAlignment(sa);
                        // Also update the live global so the GUI badge
                        // shows the effective alignment during this run.
                        App.sectorAlignment = sa;
                        Gui.refreshChartBadges();
                        break;
                    }
                }
                return fsBlockSize;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not query filesystem block size", e);
        }
        return userAlign;
    }
}
