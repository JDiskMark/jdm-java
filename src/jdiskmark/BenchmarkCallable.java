package jdiskmark;

import jakarta.persistence.EntityManager;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import static jdiskmark.App.KILOBYTE;
import static jdiskmark.App.MEGABYTE;
import static jdiskmark.App.blockSizeKb;
import static jdiskmark.App.dataDir;
import static jdiskmark.App.locationDir;
import static jdiskmark.App.msg;
import static jdiskmark.App.numOfBlocks;
import static jdiskmark.App.numOfSamples;
import static jdiskmark.App.testFile;
import static jdiskmark.Benchmark.BlockSequence;
import static jdiskmark.Benchmark.IOMode;
import static jdiskmark.BenchmarkLogic.divideIntoRanges;
import static jdiskmark.Sample.Type.READ;
import static jdiskmark.Sample.Type.WRITE;

public class BenchmarkCallable implements Callable<Benchmark> {
    static final int CLI_BAR_LENGTH = 50;
    Benchmark benchmark;    
    // this is for rendering a progress bar in the cli
    private void drawProgressBar(int percent, int totalSamples) {
        // Ensure percent is capped at 100 for display (in case of >100% on final unit)
        int displayPercent = Math.min(100, percent); 
        int numChars = (int) Math.floor((double) displayPercent / 100 * CLI_BAR_LENGTH);
        String bar = "[";
        for (int i = 0; i < CLI_BAR_LENGTH; i++) {
            bar += (i < numChars) ? "#" : " ";
        }
        bar += "]";
        // Print the current progress bar using carriage return (\r)
        System.out.printf("\rProgress: %s %3d%% (%d total operations) ", bar, displayPercent, totalSamples);
        System.out.flush();
    }
    
    // Constructor to pass any necessary data to the task
    public BenchmarkCallable() {
    }
    @Override
    public Benchmark call() throws Exception {
        System.out.println(App.benchmarkType + " benchmark started...");
        
        // The main, long-running logic goes here
        long start = System.currentTimeMillis();
        
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

        if (App.autoReset == true) {
            App.resetTestData();
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
        benchmark.systemInfo.processorName = App.processorName;
        benchmark.systemInfo.os = App.os;
        benchmark.systemInfo.arch = App.arch;
        benchmark.systemInfo.jdk = App.jdk;
        benchmark.systemInfo.locationDir = App.locationDir.toString();
        // drive information
        benchmark.driveInfo.driveModel = driveModel;
        benchmark.driveInfo.partitionId = partitionId;
        benchmark.driveInfo.percentUsed = usageInfo.percentUsed;
        benchmark.driveInfo.usedGb = usageInfo.usedGb;
        benchmark.driveInfo.totalGb = usageInfo.totalGb;
        
        if (App.isWriteEnabled()) {
            BenchmarkOperation wOperation = new BenchmarkOperation();
            wOperation.setBenchmark(benchmark);
            wOperation.ioMode = IOMode.WRITE;
            wOperation.blockOrder = App.blockSequence;
            wOperation.numSamples = App.numOfSamples;
            wOperation.numBlocks = App.numOfSamples;
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

                futures.add(executorService.submit(() -> {

                    for (int s = startSample; s <= endSample; s++) {

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
                                    synchronized (BenchmarkCallable.this) {
                                        wUnitsComplete[0]++;
                                        unitsComplete[0] = rUnitsComplete[0] + wUnitsComplete[0];
                                        float percentComplete = (float)unitsComplete[0] / (float) unitsTotal * 100f;
                                        int newProgress = (int) percentComplete;
                                        drawProgressBar(newProgress, unitsTotal);
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkCallable.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        long endTime = System.nanoTime();
                        long elapsedTimeNs = endTime - startTime;
                        sample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / numOfBlocks;
                        double sec = (double) elapsedTimeNs / 1_000_000_000d;
                        double mbWritten = (double) totalBytesWrittenInSample / (double) MEGABYTE;
                        sample.bwMbSec = mbWritten / sec;
                        App.updateMetrics(sample);
                        if (App.verbose) {
                            switch (sample.type) {
                                case WRITE -> System.out.println("w: " + s);
                                case READ -> System.out.println("r: " + s);
                            }
                        }
                        wOperation.bwMax = sample.cumMax;
                        wOperation.bwMin = sample.cumMin;
                        wOperation.bwAvg = sample.cumAvg;
                        wOperation.accAvg = sample.cumAccTimeMs;
                        wOperation.add(sample);
                    }
                }));
            }
            // stop accepting new task
            executorService.shutdown();
            // block until all tasks are complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException ex) {
                    // The primary benchmark thread was interrupted
                    Thread.currentThread().interrupt(); 
                    throw ex; // Re-throw to stop the benchmark
                } catch (ExecutionException ex) {
                    Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, "Worker range thread failed.", ex.getCause());
                    throw new Exception("Benchmark operation failed in worker thread.", ex.getCause());
                }
            }
            // GH-10 file IOPS processing
            wOperation.endTime = LocalDateTime.now();
            wOperation.setTotalOps(wUnitsComplete[0]);
            App.wIops = wOperation.iops;
        }

        // TODO: review renaming all files to clear catch
        if (App.isReadEnabled() && App.isWriteEnabled()) {
            Cli.dropCache();
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
                    for (int s = startSample; s <= endSample; s++) {
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
                                    synchronized (BenchmarkCallable.this) {
                                        rUnitsComplete[0]++;
                                        unitsComplete[0] = rUnitsComplete[0] + wUnitsComplete[0];
                                        float percentComplete = (float)unitsComplete[0] / (float) unitsTotal * 100f;
                                        int newProgress = (int) percentComplete;
                                        drawProgressBar(newProgress, unitsTotal);
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkCallable.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        long endTime = System.nanoTime();
                        long elapsedTimeNs = endTime - startTime;
                        sample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numOfBlocks;
                        double sec = (double) elapsedTimeNs / 1_000_000_000d;
                        double mbRead = (double) totalBytesReadInMark / (double) MEGABYTE;
                        sample.bwMbSec = mbRead / sec;
                        App.updateMetrics(sample);
                        if (App.verbose) {
                            switch (sample.type) {
                                case WRITE -> System.out.println("w: " + s);
                                case READ -> System.out.println("r: " + s);
                            }
                        }
                        rOperation.bwMax = sample.cumMax;
                        rOperation.bwMin = sample.cumMin;
                        rOperation.bwAvg = sample.cumAvg;
                        rOperation.accAvg = sample.cumAccTimeMs;
                        rOperation.add(sample);
                    }
                }));
            }
            // stop accepting new task
            executorService.shutdown();
            // block until all tasks are complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    // Primary benchmark thread interrupted
                    Thread.currentThread().interrupt(); 
                    throw e; // Re-throw to stop the benchmark
                } catch (ExecutionException e) {
                    Logger.getLogger(BenchmarkCallable.class.getName()).log(Level.SEVERE, "Range thread failed.", e.getCause());
                    throw new RuntimeException("Benchmark operation failed in worker thread.", e.getCause());
                }
            }
            // GH-10 file IOPS processing
            rOperation.endTime = LocalDateTime.now();
            rOperation.setTotalOps(rUnitsComplete[0]);
            App.rIops = rOperation.iops;
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
        
        // process json export if path was set
        if (App.exportPath != null) {
            JsonExporter.writeBenchmarkToJson(benchmark, App.exportPath.getAbsolutePath());
        }
        
        App.nextSampleNumber += App.numOfSamples;
        long duration = System.currentTimeMillis() - start;
        System.out.println();
        System.out.println(App.benchmarkType + " benchmark finished after " + duration + "ms.");
        return benchmark;
    }
}
