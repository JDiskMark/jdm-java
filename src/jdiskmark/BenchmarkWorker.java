package jdiskmark;
// constants
import static jdiskmark.App.KILOBYTE;
import static jdiskmark.Benchmark.IOMode;
import static jdiskmark.Sample.Type.READ;
import static jdiskmark.Sample.Type.WRITE;
// global variables
import static jdiskmark.App.blockSizeKb;
import static jdiskmark.App.ioEngine;
import static jdiskmark.App.locationDir;
import static jdiskmark.App.msg;
import static jdiskmark.App.numOfBlocks;
import static jdiskmark.App.numOfSamples;
import static jdiskmark.App.dataDir;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import jdiskmark.App.IoEngine;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class BenchmarkWorker extends SwingWorker<Benchmark, Sample> {

    // Minimum milliseconds between progress updates to avoid excessive UI refreshes
    private static final long UPDATE_INTERVAL = 25;
    private final AtomicLong lastUpdateMs = new AtomicLong(0);

    Benchmark benchmark;
    
    // GH-20 final to aid w lambda usage
    private final LongAdder writeUnitsComplete = new LongAdder();
    private final LongAdder readUnitsComplete = new LongAdder();
    
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
                setProgress(clampedProgress);
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
    
    @Override
    protected Benchmark doInBackground() throws Exception {

        if (App.verbose) {
            msg("*** starting new worker thread");
            msg("Running readTest " + App.isReadEnabled() + "   writeTest " + App.isWriteEnabled());
            msg("num samples: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
                    + ", blk size (kb): " + App.blockSizeKb + ", blockSequence: "
                    + App.blockSequence);
        }

        int wUnitsTotal = App.isWriteEnabled() ? numOfBlocks * numOfSamples : 0;
        int rUnitsTotal = App.isReadEnabled() ? numOfBlocks * numOfSamples : 0;
        unitsTotal = wUnitsTotal + rUnitsTotal;
        blockSize = blockSizeKb * KILOBYTE;
        if (ioEngine == IoEngine.LEGACY) {
            blockArr = new byte[blockSize];
            for (int b = 0; b < blockArr.length; b++) {
                if (b % 2 == 0) {
                    blockArr[b] = (byte) 0xFF;
                }
            }
        }
        Gui.updateLegendAndAxis();

        if (App.autoReset == true) {
            App.resetTestData();
            Gui.resetBenchmarkData();
            Gui.updateLegendAndAxis();
        }

        String driveModel = Util.getDriveModel(locationDir);
        String partitionId = Util.getPartitionId(locationDir.toPath());
        DiskUsageInfo usageInfo = new DiskUsageInfo(); // init to prevent null ref
        try {
            usageInfo = Util.getDiskUsage(locationDir.toString());
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (App.verbose) {
            msg("drive model=" + driveModel + " partitionId=" + partitionId
                    + " usage=" + usageInfo.toDisplayString());
        }
        
        // GH-20 calculate ranges for concurrent thread IO
        int sIndex = App.nextSampleNumber;
        int eIndex = sIndex + numOfSamples;
        int[][] tRanges = divideIntoRanges(sIndex, eIndex, App.numOfThreads);

        // configure the benchmark
        benchmark = new Benchmark(App.benchmarkType);
        // system info
        benchmark.processorName = App.processorName;
        benchmark.os = App.os;
        benchmark.arch = App.arch;
        benchmark.jdk = App.jdk;
        benchmark.locationDir = App.locationDir.toString();
        // drive information
        benchmark.driveModel = driveModel;
        benchmark.partitionId = partitionId;
        benchmark.percentUsed = usageInfo.percentUsed;
        benchmark.usedGb = usageInfo.usedGb;
        benchmark.totalGb = usageInfo.totalGb;
        
        // update gui title
        Gui.chart.getTitle().setText(benchmark.getDriveInfo());
        Gui.chart.getTitle().setVisible(true);
        
        if (App.isWriteEnabled()) {
            BenchmarkOperation wOperation = new BenchmarkOperation();
            wOperation.setBenchmark(benchmark);
            wOperation.ioMode = IOMode.WRITE;
            wOperation.blockOrder = App.blockSequence;
            wOperation.numSamples = App.numOfSamples;
            wOperation.numBlocks = App.numOfBlocks;
            wOperation.blockSize = App.blockSizeKb;
            wOperation.txSize = App.targetTxSizeKb();
            wOperation.numThreads = App.numOfThreads;
            // persist whether write sync was enabled for this run
            wOperation.setWriteSyncEnabled(App.writeSyncEnable);
            benchmark.getOperations().add(wOperation);

            // GH-20 instantiate threads to operate on each range
            ExecutorService executorService = Executors.newFixedThreadPool(App.numOfThreads);
            List<Future<?>> futures = new ArrayList<>();

            for (int[] range : tRanges) {
                final int startSample = range[0];
                final int endSample = range[1];
                // submit inline range task thread
                futures.add(executorService.submit(() -> {

                    for (int s = startSample; s < endSample && !isCancelled(); s++) {
                        
                        Sample sample = new Sample(WRITE, s);
                        if (ioEngine == IoEngine.LEGACY) {
                            sample.measureWriteLegacy(blockSize, numOfBlocks, blockArr, this);
                        } else {
                            sample.measureWrite(blockSize, numOfBlocks, this);
                        }
                        
                        // calculate the sample statistics and store in sample
                        App.updateMetrics(sample);
                        publish(sample);

                        wOperation.bwMax = sample.cumMax;
                        wOperation.bwMin = sample.cumMin;
                        wOperation.bwAvg = sample.cumAvg;
                        wOperation.accAvg = sample.cumAccTimeMs;
                        wOperation.add(sample);
                    }
                }));
            }

            executorService.shutdown(); // stop accepting new task
            // block until all tasks are complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    // The primary benchmark thread was interrupted
                    Thread.currentThread().interrupt(); 
                    throw e; // Re-throw to stop the benchmark
                } catch (ExecutionException e) {
                    Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, "Worker range thread failed.", e.getCause());
                    throw new Exception("Benchmark operation failed in worker thread.", e.getCause());
                }
            }

            // GH-10 file IOPS processing
            wOperation.endTime = LocalDateTime.now();
            wOperation.setTotalOps(writeUnitsComplete.longValue());
            App.wIops = wOperation.iops;
            Gui.mainFrame.refreshWriteMetrics();
        }
        
        // TODO: review renaming all files to clear catch
        if (App.isReadEnabled() && App.isWriteEnabled() && !isCancelled()) {
            throttledProgressUpdate(true); // update at the half way point
            // TODO: review refactor to App.dropCache() & Gui.dropCache()
            Gui.dropCache();
        }

        if (App.isReadEnabled()) {
            BenchmarkOperation rOperation = new BenchmarkOperation();
            rOperation.setBenchmark(benchmark);
            // operation parameters
            rOperation.ioMode = IOMode.READ;
            rOperation.blockOrder = App.blockSequence;
            rOperation.numSamples = App.numOfSamples;
            rOperation.numBlocks = App.numOfBlocks;
            rOperation.blockSize = App.blockSizeKb;
            rOperation.txSize = App.targetTxSizeKb();
            rOperation.numThreads = App.numOfThreads;
            // write sync does not apply to pure read benchmarks
            rOperation.setWriteSyncEnabled(null);
            benchmark.getOperations().add(rOperation);

            ExecutorService executorService = Executors.newFixedThreadPool(App.numOfThreads);
            List<Future<?>> futures = new ArrayList<>();

            for (int[] range : tRanges) {
                final int startSample = range[0];
                final int endSample = range[1];

                futures.add(executorService.submit(() -> {
                    for (int s = startSample; s < endSample && !isCancelled(); s++) {

                        Sample sample = new Sample(READ, s);
                        if (ioEngine == IoEngine.LEGACY) {
                            sample.measureReadLegacy(blockSize, numOfBlocks, blockArr, this);
                        } else {
                            sample.measureRead(blockSize, numOfBlocks, this);
                        }
                        
                        // calculate the sample statistics and store in sample
                        App.updateMetrics(sample);
                        publish(sample);
                        
                        rOperation.bwMax = sample.cumMax;
                        rOperation.bwMin = sample.cumMin;
                        rOperation.bwAvg = sample.cumAvg;
                        rOperation.accAvg = sample.cumAccTimeMs;
                        rOperation.add(sample);
                    }
                }));
            }

            executorService.shutdown(); // stop accepting new task
            // block until all tasks are complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    // primary benchmark thread was interrupted while waiting
                    Thread.currentThread().interrupt(); 
                    throw ex; // Re-throw to stop the benchmark
                } catch (ExecutionException ex) {
                    Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, "Worker range thread failed.", ex.getCause());
                    throw new Exception("Benchmark operation failed in worker thread.", ex.getCause());
                }
            }

            // GH-10 file IOPS processing
            rOperation.endTime = LocalDateTime.now();
            rOperation.setTotalOps(readUnitsComplete.longValue());
            App.rIops = rOperation.iops;
            Gui.mainFrame.refreshReadMetrics();
        }
        benchmark.endTime = LocalDateTime.now();
        if (App.autoSave) {
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(benchmark);
            em.getTransaction().commit();
            App.benchmarks.put(benchmark.getStartTimeString(), benchmark);
            for (BenchmarkOperation o : benchmark.getOperations()) {
                App.operations.put(o.getStartTimeString(), o);
            }
        }
        // #67 upload to community portal (in progress)
        if (App.sharePortal) {
            Portal.upload(benchmark);
        }
        if (App.exportPath != null) {
            JsonExporter.writeBenchmarkToJson(benchmark, App.exportPath.getAbsolutePath());
        }
        Gui.runPanel.addRun(benchmark);
        App.nextSampleNumber += App.numOfSamples;
        return benchmark;
    }

    @Override
    protected void process(List<Sample> sampleList) {
        sampleList.stream().forEach((Sample s) -> {
            switch (s.type) {
                case WRITE -> Gui.addWriteSample(s);
                case READ -> Gui.addReadSample(s);
            }
        });
    }

    @Override
    protected void done() {
        throttledProgressUpdate(true);
        if (App.autoRemoveData) {
            Util.deleteDirectory(dataDir);
        }
        App.state = App.State.IDLE_STATE;
        Gui.mainFrame.adjustSensitivity();
    }
}
