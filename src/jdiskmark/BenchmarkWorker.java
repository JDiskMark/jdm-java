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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import static jdiskmark.App.locationDir;
import static jdiskmark.App.numOfSamples;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class BenchmarkWorker extends SwingWorker<Boolean, Sample> {

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
    protected Boolean doInBackground() throws Exception {

        System.out.println("*** starting new worker thread");
        msg("Running readTest " + App.isReadEnabled() + "   writeTest " + App.isWriteEnabled());
        msg("num samples: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
                + ", blk size (kb): " + App.blockSizeKb + ", blockSequence: "
                + App.blockSequence);

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
        RenderFrequencyMode renderMode = App.rmOption; // selected from Advanced Options
        final java.util.concurrent.atomic.AtomicLong lastUpdateTime =
                new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());


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
        msg("drive model=" + driveModel + " partitionId=" + partitionId
                + " usage=" + usageInfo.toDisplayString());

        // GH-20 calculate ranges for concurrent thread IO
        int sIndex = App.nextSampleNumber;
        int eIndex = sIndex + numOfSamples;
        int[][] tRanges = divideIntoRanges(sIndex, eIndex, App.numOfThreads);

        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        Benchmark benchmark = new Benchmark(App.benchmarkType);

        // system info
        benchmark.processorName = App.processorName;
        benchmark.os = App.os;
        benchmark.arch = App.arch;
        // drive information
        benchmark.driveModel = driveModel;
        benchmark.partitionId = partitionId;
        benchmark.percentUsed = usageInfo.percentUsed;
        benchmark.usedGb = usageInfo.usedGb;
        benchmark.totalGb = usageInfo.totalGb;        
        
        Gui.chart.getTitle().setText(benchmark.getDriveInfo());
        Gui.chart.getTitle().setVisible(true);
        
        if (App.isWriteEnabled()) {
            BenchmarkOperation wOperation = new BenchmarkOperation();
            wOperation.setBenchmark(benchmark);
            wOperation.ioMode = BenchmarkOperation.IOMode.WRITE;
            wOperation.setRenderMode(renderMode);   //persist user-selected mode
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

            for (int[] range : tRanges) {
                final int startSample = range[0];
                final int endSample = range[1];

                futures.add(executorService.submit(() -> {

                    for (int s = startSample; s <= endSample && !isCancelled(); s++) {

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
                                    if (App.blockSequence == BenchmarkOperation.BlockSequence.RANDOM) {
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
                                        setProgress((int) percentComplete);
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
                        // Determine when to publish based on render mode                                                                

                        switch (renderMode) {
                            case PER_SAMPLE -> {
                                App.updateMetrics(sample);
                                publish(sample);
                                wOperation.add(sample);
                            }
                            case PER_OPERATION -> {
                                wOperation.add(sample);
                            }
                            case PER_100MS, PER_500MS, PER_1000MS -> {
                                long interval = renderMode.getIntervalMillis();
                                long now = System.currentTimeMillis();
                                if (now - lastUpdateTime.get() >= interval) {
                                    App.updateMetrics(sample);
                                    publish(sample);
                                    lastUpdateTime.set(now);
                                }
                                wOperation.add(sample);
                            }
                        }                                                  
                    }
                }));
            }

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            
            if (!wOperation.getSamples().isEmpty()) {
                Sample last = wOperation.getSamples().get(wOperation.getSamples().size() - 1);
                wOperation.bwMax = last.cumMax;
                wOperation.bwMin = last.cumMin;
                wOperation.bwAvg = last.cumAvg;
                wOperation.accAvg = last.cumAccTimeMs;
            } 

            if (renderMode == RenderFrequencyMode.PER_OPERATION) {
                for (Sample s : wOperation.getSamples()) {
                    App.updateMetrics(s);
                    publish(s);
                }
            }

            // GH-10 file IOPS processing
            wOperation.endTime = LocalDateTime.now();
            wOperation.setTotalOps(wUnitsComplete[0]);
            App.wIops = wOperation.iops;
            Gui.mainFrame.refreshWriteMetrics();
        }

        // try renaming all files to clear catch
        if (App.isReadEnabled() && App.isWriteEnabled() && !isCancelled()) {
            Gui.dropCache();
        }

        if (App.isReadEnabled()) {
            BenchmarkOperation rOperation = new BenchmarkOperation();
            rOperation.setBenchmark(benchmark);
            // operation parameters
            rOperation.ioMode = BenchmarkOperation.IOMode.READ;
            rOperation.setRenderMode(renderMode);   //persist user-selected mode
            rOperation.blockOrder = App.blockSequence;
            rOperation.numSamples = App.numOfSamples;
            rOperation.numBlocks = App.numOfBlocks;
            rOperation.blockSize = App.blockSizeKb;
            rOperation.txSize = App.targetTxSizeKb();
            rOperation.numThreads = App.numOfThreads;
            // write sync does not apply to pure read benchmarks
            rOperation.setWriteSyncEnabled(null);
            benchmark.getOperations().add(rOperation);
            
            //reset interval timer so READ updates are not throttled by WRITE timestamps
            lastUpdateTime.set(System.currentTimeMillis());

            ExecutorService executorService = Executors.newFixedThreadPool(App.numOfThreads);

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
                                    if (App.blockSequence == BenchmarkOperation.BlockSequence.RANDOM) {
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
                                        setProgress((int) percentComplete);
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
                        switch (renderMode) {
                            case PER_SAMPLE -> {
                                App.updateMetrics(sample);
                                publish(sample);
                                rOperation.add(sample); 
                            }
                            case PER_OPERATION -> {
                                rOperation.add(sample);
                            }
                            case PER_100MS, PER_500MS, PER_1000MS -> {
                                long interval = renderMode.getIntervalMillis();
                                long now = System.currentTimeMillis();
                                if (now - lastUpdateTime.get() >= interval) {
                                    App.updateMetrics(sample);
                                    publish(sample);
                                    lastUpdateTime.set(now);
                                }
                                rOperation.add(sample);
                            }
                        }



                        
                        if (!rOperation.getSamples().isEmpty()) {
                            Sample last = rOperation.getSamples().get(rOperation.getSamples().size() - 1);
                            rOperation.bwMax = last.cumMax;
                            rOperation.bwMin = last.cumMin;
                            rOperation.bwAvg = last.cumAvg;
                            rOperation.accAvg = last.cumAccTimeMs;
                        }   
                    }
                }));
        }

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            if (renderMode == RenderFrequencyMode.PER_OPERATION) {
                for (Sample s : rOperation.getSamples()) {
                    App.updateMetrics(s);
                    publish(s);
                }
            }

            // GH-10 file IOPS processing
            rOperation.endTime = LocalDateTime.now();
            rOperation.setTotalOps(rUnitsComplete[0]);
            App.rIops = rOperation.iops;
            Gui.mainFrame.refreshReadMetrics();
        }
        benchmark.endTime = LocalDateTime.now();
        EntityManager em = EM.getEntityManager();
        em.getTransaction().begin();
        em.persist(benchmark);
        em.getTransaction().commit();
        App.benchmarks.put(benchmark.getStartTimeString(), benchmark);
        for (BenchmarkOperation o : benchmark.getOperations()) {
            App.operations.put(o.getStartTimeString(), o);
        }
        Gui.runPanel.addRun(benchmark);
        
        App.nextSampleNumber += App.numOfSamples;
        return true;
    }

    @Override
    protected void process(List<Sample> sampleList) {
        sampleList.stream().forEach((Sample s) -> {
            switch (s.type) {
                case Sample.Type.WRITE ->
                    Gui.addWriteSample(s);
                case Sample.Type.READ ->
                    Gui.addReadSample(s);
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
