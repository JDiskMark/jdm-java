package jdiskmark;

import jakarta.persistence.EntityManager;
import static jdiskmark.App.KILOBYTE;
import static jdiskmark.App.MEGABYTE;
import static jdiskmark.App.blockSizeKb;
import static jdiskmark.App.msg;
import static jdiskmark.App.numOfBlocks;
import static jdiskmark.App.testFile;
import static jdiskmark.App.dataDir;
import static jdiskmark.Sample.Type.READ;
import static jdiskmark.Sample.Type.WRITE;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import static jdiskmark.App.locationDir;
import static jdiskmark.App.numOfSamples;
import jdiskmark.Benchmark.BlockSequence;
import jdiskmark.Benchmark.IOMode;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class BenchmarkWorker extends SwingWorker<Benchmark, Sample> {

    Benchmark benchmark;
    
    public static int[][] divideIntoRanges(int startIndex, int endIndex, int numThreads) {
        if (numThreads <= 0 || endIndex < startIndex) {
            return new int[0][0]; // Handle invalid input
        }

        int numElements = endIndex - startIndex + 1; // Calculate the total number of elements
        int[][] ranges = new int[numThreads][2];
        int rangeSize = numElements / numThreads;
        int remainder = numElements % numThreads;
        int start = startIndex;

        for (int i = 0; i < numThreads; i++) {
            int end = start + rangeSize - 1;
            if (remainder > 0) {
                end++; // Distribute the remainder
                remainder--;
            }
            ranges[i][0] = start;
            ranges[i][1] = end;
            start = end + 1;
        }
        return ranges;
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

        // GH-20 final to aid w lambda usage
        final int[] wUnitsComplete = {0};
        final int[] rUnitsComplete = {0};
        final int[] unitsComplete = {0};

        int wUnitsTotal = App.isWriteEnabled() ? numOfBlocks * numOfSamples : 0;
        int rUnitsTotal = App.isReadEnabled() ? numOfBlocks * numOfSamples : 0;
        int unitsTotal = wUnitsTotal + rUnitsTotal;
        int blockSize = blockSizeKb * KILOBYTE;
        byte[] blockArr = new byte[blockSize];
        for (int b = 0; b < blockArr.length; b++) {
            if (b % 2 == 0) {
                blockArr[b] = (byte) 0xFF;
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

            if (App.multiFile == false) {
                testFile = new File(dataDir.getAbsolutePath() + File.separator + "testdata.jdm");
            }

            // GH-20 instantiate threads to operate on each range
            ExecutorService executorService = Executors.newFixedThreadPool(App.numOfThreads);
            List<Future<?>> futures = new ArrayList<>();

            for (int[] range : tRanges) {
                final int startSample = range[0];
                final int endSample = range[1];
                // submit inline range task thread
                futures.add(executorService.submit(() -> {

                    for (int s = startSample; s < endSample && !isCancelled(); s++) {

                        if (App.multiFile == true) {
                            testFile = new File(dataDir.getAbsolutePath()
                                    + File.separator + "testdata" + s + ".jdm");
                        }
                        Sample sample = new Sample(WRITE, s);
                        long startTime = System.nanoTime();
                        long totalBytesWrittenInSample = 0;
                        String mode = (App.writeSyncEnable) ? "rwd" : "rw";
                        try {
                            try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, mode)) {
                                for (int b = 0; b < numOfBlocks; b++) {
                                    if (App.blockSequence == BlockSequence.RANDOM) {
                                        int rLoc = Util.randInt(0, numOfBlocks - 1);
                                        rAccFile.seek(rLoc * blockSize);
                                    } else {
                                        rAccFile.seek(b * blockSize);
                                    }
                                    rAccFile.write(blockArr, 0, blockSize);
                                    totalBytesWrittenInSample += blockSize;
                                    synchronized (BenchmarkWorker.this) {
                                        wUnitsComplete[0]++;
                                        unitsComplete[0] = rUnitsComplete[0] + wUnitsComplete[0];
                                        float percentComplete = (float)unitsComplete[0] / (float) unitsTotal * 100f;
                                        int newProgress = (int) percentComplete;
                                        if (0 <= newProgress && newProgress <= 100) {
                                            setProgress(newProgress);
                                        }
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        long endTime = System.nanoTime();
                        long elapsedTimeNs = endTime - startTime;
                        sample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / numOfBlocks;
                        double sec = (double) elapsedTimeNs / 1_000_000_000d;
                        double mbWritten = (double) totalBytesWrittenInSample / (double) MEGABYTE;
                        sample.bwMbSec = mbWritten / sec;
//                        msg("s:" + s + " write IO is " + sample.getBwMbSecDisplay() + " MB/s   "
//                                + "(" + Util.displayString(mbWritten) + "MB written in "
//                                + Util.displayString(sec) + " sec) elapsedNs: " + elapsedTimeNs);
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
            wOperation.setTotalOps(wUnitsComplete[0]);
            App.wIops = wOperation.iops;
            Gui.mainFrame.refreshWriteMetrics();
        }

        // TODO: review renaming all files to clear catch
        if (App.isReadEnabled() && App.isWriteEnabled() && !isCancelled()) {
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
                    for (int s = startSample; s <= endSample && !isCancelled(); s++) {
                        if (App.multiFile == true) {
                            testFile = new File(dataDir.getAbsolutePath()
                                    + File.separator + "testdata" + s + ".jdm");
                        }
                        Sample sample = new Sample(READ, s);
                        long startTime = System.nanoTime();
                        long totalBytesReadInMark = 0;
                        try {
                            try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, "r")) {
                                for (int b = 0; b < numOfBlocks; b++) {
                                    if (App.blockSequence == BlockSequence.RANDOM) {
                                        int rLoc = Util.randInt(0, numOfBlocks - 1);
                                        rAccFile.seek(rLoc * blockSize);
                                    } else {
                                        rAccFile.seek(b * blockSize);
                                    }
                                    rAccFile.readFully(blockArr, 0, blockSize);
                                    totalBytesReadInMark += blockSize;
                                    synchronized (BenchmarkWorker.this) {
                                        rUnitsComplete[0]++;
                                        unitsComplete[0] = rUnitsComplete[0] + wUnitsComplete[0];
                                        float percentComplete = (float)unitsComplete[0] / (float) unitsTotal * 100f;
                                        int newProgress = (int) percentComplete;
                                        if (0 <= newProgress && newProgress <= 100) {
                                            setProgress(newProgress);
                                        }
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        long endTime = System.nanoTime();
                        long elapsedTimeNs = endTime - startTime;
                        sample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numOfBlocks;
                        double sec = (double) elapsedTimeNs / 1_000_000_000d;
                        double mbRead = (double) totalBytesReadInMark / (double) MEGABYTE;
                        sample.bwMbSec = mbRead / sec;
//                        msg("s:" + s + " read IO is " + sample.getBwMbSecDisplay() + " MB/s   "
//                                + "(" + Util.displayString(mbRead) + "MB read in "
//                                + Util.displayString(sec) + " sec) elapsedNs: " + elapsedTimeNs);
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
            rOperation.setTotalOps(rUnitsComplete[0]);
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
                case Sample.Type.WRITE -> Gui.addWriteSample(s);
                case Sample.Type.READ -> Gui.addReadSample(s);
            }
        });
    }

    @Override
    protected void done() {
        if (App.autoRemoveData) {
            Util.deleteDirectory(dataDir);
        }
        App.state = App.State.IDLE_STATE;
        Gui.mainFrame.adjustSensitivity();
    }
}
