package jdiskmark;

import static jdiskmark.GcDetector.MAX_GC_RETRIES;

import java.io.File;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import com.sun.nio.file.ExtendedOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import jdiskmark.App.IoEngine;
import jdiskmark.Benchmark.IOMode;
import static jdiskmark.Benchmark.IOMode.READ;
import static jdiskmark.Benchmark.IOMode.WRITE;
import org.metricus.jdm.os.UtilsLinuxOs;
import org.metricus.jdm.util.LockedTestData;

public class BenchmarkRunner {
    
    /** How to proceed when Direct I/O would keep test files locked until exit (#257). */
    public enum DirectLockChoice { CANCEL, BUFFERED, KEEP }
    
    // Listener interface to decouple from Swing/CLI
    public interface BenchmarkListener {
        void onSampleComplete(Sample sample);
        void onProgressUpdate(long completed, long total);
        boolean isCancelled();
        /**
         * Clears the OS disk cache before a READ operation.
         * @return false if the cache could not be cleared and READ results
         *         would measure memory instead of the drive
         */
        boolean attemptCacheDrop();

        /**
         * Called after each operation (write or read) completes.
         * GUI listeners use this to flush buffered samples in PER_OPERATION
         * render mode. Default is a no-op so CLI and other callers are
         * unaffected.
         */
        default void onOperationComplete() {}

        /**
         * Called before the run when Direct I/O would keep the test files
         * locked until JDiskMark exits (#257). Defaults to the
         * {@code --direct-lock} preference; {@code null} means none was given.
         */
        default DirectLockChoice onDirectIoLocked(String location, String fileSystem) {
            return App.directLockChoice;
        }
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
    /** First sample number of this run; single-file regions are offset from it. */
    int firstSample;
    /** Sample regions per single-file data file; 0 when one file holds them all (#253). */
    int samplesPerFile = 0;
    byte[] blockArr; // random write pattern, also the legacy io buffer
    final AtomicBoolean directDisabled = new AtomicBoolean(false);
    /** Badge reason from the first disableDirectIo() call, re-applied after the run's mark reset. */
    private volatile String directDisabledMsg = null;
    final AtomicBoolean flushNoted = new AtomicBoolean(false);

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
        checkDirectIoLock();
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
        
        // incompressible data so compressing filesystems (btrfs, zfs, ntfs) do not inflate results
        blockArr = new byte[(int)blockSize];
        ThreadLocalRandom.current().nextBytes(blockArr);

        File configDir = new File(config.testDir);
        String partitionId = Util.getPartitionId(configDir.toPath());
        DiskUsageInfo usageInfo = Util.getDiskUsage(configDir.getAbsolutePath());
        String driveInterface = Util.getDriveInterface(configDir);
        boolean isUsb = "USB".equalsIgnoreCase(driveInterface);
        String driveInterfaceVersion = isUsb
                ? Util.getUsbVersion(configDir.toPath())
                : null;
        Util.UsbIds usbIds = isUsb
                ? Util.getUsbIds(configDir.toPath())
                : null;
        String driveModel = Util.getDriveModel(configDir, usbIds);

        // Initialize Benchmark
        
        Benchmark benchmark = new Benchmark(config);
        mapEnvironment(benchmark, driveModel, partitionId, usageInfo,
                driveInterface, driveInterfaceVersion, usbIds);
        // capture the render mode chosen at the time this run starts
        benchmark.setRenderMode(App.rmOption);
        Gui.resetAutoAdjusted(benchmark);
        // Less likely path: checkDirectIoLock() runs before the reset above, so
        // re-mark a Direct I/O change it made. Normally disableDirectIo() marks
        // the badge itself, since it is called after this reset.
        if (directDisabledMsg != null) {
            Gui.markDirectIoAuto(directDisabledMsg);
        }

        int startingSample = App.nextSampleNumber;
        int endingSample = App.nextSampleNumber + config.numSamples;
        firstSample = startingSample;
        planSingleFileSplit();
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
                    String partition = UtilsLinuxOs.getPartitionFromFilePath(path);
                    List<String> devices = UtilsLinuxOs.getDeviceNamesFromPartition(partition);
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
        
        if (config.ioEngine == IoEngine.LEGACY && Boolean.TRUE.equals(config.getDirectIoEnabled())) {
            disableDirectIo("Direct I/O is not supported by the legacy IO engine");
        }
        
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
        // 3. direct I/O not in effect (direct I/O bypasses cache on all platforms)
        boolean readAllowed = true;
        if (!listener.isCancelled() && config.hasReadOperation()) {
            if (Boolean.TRUE.equals(config.getDirectIoEnabled()) && !probeDirectRead(tRanges)) {
                disableDirectIo("Direct I/O read probe failed");
            }
            if (!Boolean.TRUE.equals(config.getDirectIoEnabled())) {
                readAllowed = listener.attemptCacheDrop();
            }
        }
        
        // If we are doing both, clear the heap between them
        if (readAllowed && config.gcHintsEnabled && !listener.isCancelled() && 
                config.hasWriteOperation() && config.hasReadOperation()) {
            GcDetector.triggerAndWait();
        }
        
        if (config.hasReadOperation() && !listener.isCancelled()) {
            if (readAllowed) {
                runOperation(benchmark, IOMode.READ, tRanges);
            } else {
                recordFailedOp(benchmark, IOMode.READ,
                        BenchmarkOperation.FailReason.CACHE_NOT_CLEARED);
                String msg = "READ skipped: disk cache could not be cleared, "
                        + "read results would measure memory instead of the drive";
                logger.warning(msg);
                App.warn(msg);
            }
            listener.onOperationComplete();
        }

        benchmark.recordEndTime();
        
        if (config.gcHintsEnabled) { System.gc(); } // clear heap no wait
        
        return benchmark;
    }

    /**
     * Turns Direct I/O off for the rest of this run and for subsequent runs
     * when it is found not to work. Safe to call from multiple threads; only
     * the first call logs and updates state.
     */
    void disableDirectIo(String reason) {
        if (!directDisabled.compareAndSet(false, true)) return;
        config.setDirectIoEnabled(false);
        App.directEnable = false;
        String msg = reason + ". Direct I/O disabled, using buffered I/O.";
        directDisabledMsg = msg;
        Gui.markDirectIoAuto(msg);
        Gui.syncIoOptionMenus();
        logger.warning(msg);
        App.warn(msg);
    }

    /**
     * Release probe result per location and volume; probed once per session.
     * The volume serial is part of the key so a different drive mounted on the
     * same letter is probed again instead of reusing the previous drive's result.
     * Locations whose volume serial can't be read are probed on every run.
     */
    private static final Map<DirectProbeKey, Boolean> DIRECT_RELEASE_PROBES = new ConcurrentHashMap<>();

    record DirectProbeKey(Path location, Object volumeSerial) {}

    /** Key for {@link #DIRECT_RELEASE_PROBES}; the serial is {@code null} when it can't be read. */
    static DirectProbeKey directProbeKey(Path location) {
        Object serial;
        try {
            serial = Files.getFileStore(location).getAttribute("volume:vsn");
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            serial = null;
        }
        return new DirectProbeKey(location, serial);
    }

    private static final String DIRECT_LOCK_REASON = "Direct I/O works on %s, but %s "
            + "Java keeps Direct I/O test files locked until JDiskMark closes so they can't be "
            + "deleted (Windows only; NTFS is not affected)";

    private boolean directLockKept = false;

    /** True when Direct I/O was kept although it leaves the test files locked until exit. */
    public boolean isDirectLockKept() { return directLockKept; }

    /**
     * On Windows, FAT32 and exFAT drives keep a file opened with
     * {@link ExtendedOpenOption#DIRECT} locked inside the JVM after the channel
     * is closed (the JDK's setDirect0 leaks a ReOpenFile handle), so it can't
     * be deleted or re-created until JDiskMark exits.
     * Probes once per location and volume, and asks the listener how to proceed.
     *
     * @throws CancellationException when the benchmark should not run
     */
    private void checkDirectIoLock() {
        if (!App.isWindows() || config.ioEngine != IoEngine.MODERN
                || !Boolean.TRUE.equals(config.getDirectIoEnabled())) {
            return;
        }
        Path testDir = Path.of(config.testDir).toAbsolutePath();
        // the drive location, also when the test folder is jdm-data/run-N (#268)
        Path location = LockedTestData.locationOf(testDir);
        DirectProbeKey probeKey = directProbeKey(location);
        boolean released = probeKey.volumeSerial() == null
                ? probeDirectRelease(location)
                : DIRECT_RELEASE_PROBES.computeIfAbsent(probeKey,
                        key -> probeDirectRelease(key.location()));
        if (released) {
            return;
        }
        String fileSystem = fileSystemType(location);
        String onDrives = fileSystem != null ? "on " + fileSystem + " drives" : "on this drive";
        String reason = DIRECT_LOCK_REASON.formatted(location, onDrives);
        DirectLockChoice choice = listener.onDirectIoLocked(location.toString(), fileSystem);
        if (choice == null) {
            throw new CancellationException(App.mode == App.Mode.CLI
                    ? "Benchmark aborted: " + reason + ". Rerun with --direct-lock=buffered "
                            + "(Direct I/O off; clear the disk cache before READ, needs admin) "
                            + "or --direct-lock=keep (keep Direct I/O; test data is removed "
                            + "when JDiskMark exits)."
                    : "Skipped: " + reason + ". Turn Direct I/O off for this batch or "
                            + "benchmark this drive on its own.");
        }
        switch (choice) {
            case BUFFERED -> disableDirectIo(reason);
            case KEEP -> {
                directLockKept = true;
                String msg = reason + ". Keeping Direct I/O as requested; the test data is "
                        + "removed when JDiskMark closes.";
                logger.warning(msg);
                App.warn(msg);
            }
            case CANCEL -> throw new CancellationException("Benchmark cancelled: " + reason + ".");
        }
    }

    /** File system type of {@code dir} (e.g. "FAT32"), or {@code null} when it can't be read. */
    static String fileSystemType(Path dir) {
        try {
            String type = Files.getFileStore(dir).type();
            return (type == null || type.isBlank()) ? null : type;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Single-file data larger than the file system's maximum file size (FAT32:
     * 4 GiB - 1) is split into several files, each holding whole sample regions.
     */
    private void planSingleFileSplit() {
        samplesPerFile = 0;
        if (App.multiFile) return;
        String fsType = fileSystemType(Path.of(config.testDir).toAbsolutePath());
        long maxBytes = DriveChecker.maxFileBytes(fsType);
        int perFile = samplesPerFile((long) config.numBlocks * blockSize, config.numSamples, maxBytes);
        if (perFile == 0) return;
        samplesPerFile = perFile;
        int files = (config.numSamples + perFile - 1) / perFile;
        String msg = fsType + " limits files to 4 GiB, so the single-file test data is split into "
                + files + " files of up to " + perFile + " samples.";
        logger.info(msg);
        App.msg(msg);
    }

    /** Sample regions per data file, or 0 when all of them fit in one file. */
    static int samplesPerFile(long regionBytes, int numSamples, long maxFileBytes) {
        if (regionBytes <= 0 || regionBytes * numSamples <= maxFileBytes) return 0;
        return (int) Math.max(1, maxFileBytes / regionBytes);
    }

    /** Single-file data file holding {@code sampleNum}; 0 is {@code testdata.jdm}. */
    int fileIndex(int sampleNum) {
        int region = sampleNum - firstSample;
        return samplesPerFile > 0 ? region / samplesPerFile : 0;
    }

    /** Region of {@code sampleNum} within its single-file data file. */
    int regionInFile(int sampleNum) {
        int region = sampleNum - firstSample;
        return samplesPerFile > 0 ? region % samplesPerFile : region;
    }

    /**
     * Returns {@code false} only for the JDK's Direct I/O handle leak (#257, #260):
     * the probe file deletes, but re-creating it is denied because it is still
     * held open. Any other failure is logged and does not count as the lock, so
     * the real error surfaces in the run instead of the lock dialog.
     */
    private static boolean probeDirectRelease(Path dir) {
        Path probe = dir.resolve(".jdm-dio-probe-" + ProcessHandle.current().pid() + ".tmp");
        try {
            FileChannel.open(probe, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    ExtendedOpenOption.DIRECT).close();
        } catch (IOException | UnsupportedOperationException e) {
            // Direct I/O itself fails here; the per-sample fallback reports that
            try { Files.deleteIfExists(probe); } catch (IOException ignored) { }
            return true;
        }
        try {
            Files.delete(probe);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Direct I/O probe file could not be deleted in {0}: {1}",
                    new Object[]{dir, e.toString()});
            Util.removeFileAfterExit(probe.toFile());
            return true;
        }
        try {
            FileChannel.open(probe, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close();
            Files.deleteIfExists(probe);
            return true;
        } catch (AccessDeniedException e) {
            // deleted but still held open: Windows removes it when the JVM exits
            logger.log(Level.INFO, "Direct I/O release probe failed in {0}: {1}",
                    new Object[]{dir, e.toString()});
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Direct I/O probe could not re-create its file in {0}: {1}",
                    new Object[]{dir, e.toString()});
            return true;
        }
    }

    /**
     * Without Direct I/O or Write Sync, writes land in the page cache and are
     * written back later, so each write sample ends with a flush (timed) to
     * measure the drive instead of memory.
     */
    boolean flushWriteSamples() {
        boolean flush = !Boolean.TRUE.equals(config.getDirectIoEnabled())
                && !Boolean.TRUE.equals(config.writeSyncEnabled);
        if (flush && flushNoted.compareAndSet(false, true)) {
            String msg = "Direct I/O and Write Sync are off. Each write sample is flushed to the drive.";
            logger.info(msg);
            App.msg(msg);
        }
        return flush;
    }

    /**
     * Reads one block of the first test file with O_DIRECT to confirm the
     * page cache is really bypassed before trusting Direct I/O for READ.
     */
    private boolean probeDirectRead(int[][] ranges) {
        if (ranges.length == 0) return false;
        File probeFile = new Sample(Sample.Type.READ, ranges[0][0]).getTestFile(this);
        try (FileChannel fc = FileChannel.open(probeFile.toPath(),
                    StandardOpenOption.READ, ExtendedOpenOption.DIRECT);
             Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(blockSize, effectiveAlignment);
            fc.read(segment.asByteBuffer(), 0);
            return true;
        } catch (Exception e) {
            logger.log(Level.INFO, "Direct I/O probe failed: {0}", e.toString());
            return false;
        }
    }

    private void recordFailedOp(Benchmark b, IOMode mode, BenchmarkOperation.FailReason reason) {
        BenchmarkOperation op = createOp(b, mode);
        op.setStatus(BenchmarkOperation.Status.FAILED);
        op.setFailReason(reason);
        op.bwAvg = -1;
        op.bwMax = -1;
        op.bwMin = -1;
        op.accAvg = -1;
        op.iops = -1;
        op.endTime = LocalDateTime.now();
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
                    for (int s = range[0]; s < range[1] && !isStopping(); s++) {
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
            stopWorkers(executor);
            throw new Exception("Threaded IO operation failed", e.getCause());
        } catch (InterruptedException e) {
            stopWorkers(executor);
            throw e;
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
                for (int s = range[0]; s < range[1] && !isStopping(); s++) {
                    Sample sample = new Sample(Sample.Type.READ, s);
                    sample.prepareRead(blockSize, config.numBlocks, this);
                }
            }));
        }
        executor.shutdown();
        try {
            for (Future<?> f : futures) f.get(); // Wait and propagate exceptions
        } catch (ExecutionException e) {
            stopWorkers(executor);
            throw new Exception("read prep failed", e.getCause());
        } catch (InterruptedException e) {
            stopWorkers(executor);
            throw e;
        }
    }

    /** Set when one worker fails so the others stop after their current block. */
    private final AtomicBoolean aborted = new AtomicBoolean(false);

    /** False when workers were still running after a failure, so their files must not be deleted. */
    private volatile boolean cleanupSafe = true;

    /** True when the run was cancelled by the user or aborted by a failed worker. */
    boolean isStopping() {
        return aborted.get() || listener.isCancelled();
    }

    /** True unless workers may still be using the test data (see {@link #stopWorkers}). */
    public boolean isCleanupSafe() {
        return cleanupSafe;
    }

    /** Workers of an earlier run that did not stop in time; their files must stay untouched. */
    private static volatile ExecutorService stuckWorkers = null;

    /** True while workers of an earlier run are still running, so a new run must not start. */
    public static boolean previousWorkersRunning() {
        ExecutorService stuck = stuckWorkers;
        return stuck != null && !stuck.isTerminated();
    }

    private static final long WORKER_STOP_TIMEOUT_SECONDS = 60;

    /**
     * After a worker failure or an interruption (user cancel), stops the
     * remaining workers cooperatively and waits for them, so test data cleanup
     * can't run while they still use it. Workers are not interrupted:
     * interrupting file I/O closes the channel and would be misreported as a
     * Direct I/O failure. If they don't stop in time, cleanup is marked unsafe.
     */
    private void stopWorkers(ExecutorService executor) {
        aborted.set(true);
        boolean interrupted = Thread.interrupted();
        try {
            if (!executor.awaitTermination(WORKER_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                cleanupSafe = false;
                stuckWorkers = executor;
                logger.warning("Benchmark workers did not stop within "
                        + WORKER_STOP_TIMEOUT_SECONDS + " s; test data will be kept");
            }
        } catch (InterruptedException ie) {
            cleanupSafe = false;
            stuckWorkers = executor;
            interrupted = true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
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
        op.setStatus(BenchmarkOperation.Status.COMPLETED);
        if (mode == IOMode.WRITE) {
            op.setWriteSyncEnabled(config.writeSyncEnabled);
        }
        b.getOperations().add(op);
        return op;
    }
    
    private void mapEnvironment(Benchmark b, String model, String partId, DiskUsageInfo u,
            String driveIface, String driveIfaceVersion, Util.UsbIds usbIds) {
        b.systemId = (App.systemId != null) ? App.systemId : "";

        b.systemInfo.processorName = App.processorName;
        b.systemInfo.os = App.os;
        b.systemInfo.arch = App.arch;
        b.systemInfo.jdk = App.jdk;
        b.systemInfo.osLabel = App.osLabel;
        b.systemInfo.locationDir = App.locationDir.toString();
        
        b.driveInfo.driveModel = model;
        b.driveInfo.partitionId = partId;
        b.driveInfo.percentUsed = u.percentUsed;
        b.driveInfo.usedGb = u.usedGb;
        b.driveInfo.totalGb = u.totalGb;
        b.driveInfo.driveInterface = driveIface;
        b.driveInfo.driveInterfaceVersion = driveIfaceVersion;
        if (usbIds != null) {
            b.driveInfo.usbVendorId = usbIds.vendorId();
            b.driveInfo.usbProductId = usbIds.productId();
        }
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
