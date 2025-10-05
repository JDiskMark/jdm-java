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
import static jdiskmark.RenderFrequencyMode.*;

/**
 * Thread running the disk benchmarking. only one of these threads can run at once.
 */
public class BenchmarkWorker extends SwingWorker<Boolean, Sample> {

    private final RenderFrequencyMode renderMode = Gui.mainFrame.getRenderMode();
    private final List<Sample> sampleBuffer = new ArrayList<>();
    private long lastRenderTimeMillis = System.currentTimeMillis() - 1000;
    private long lastUpdateTime = System.currentTimeMillis() - 1000;

    public static int[][] divideIntoRanges(int startIndex, int endIndex, int numThreads) {
        if (numThreads <= 0 || endIndex < startIndex) {
            return new int[0][0];
        }
        int numElements = endIndex - startIndex + 1;
        int[][] ranges = new int[numThreads][2];
        int rangeSize = numElements / numThreads;
        int remainder = numElements % numThreads;
        int start = startIndex;

        for (int i = 0; i < numThreads; i++) {
            int end = start + rangeSize - 1;
            if (remainder > 0) {
                end++;
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

        RenderFrequencyMode currentMode = Gui.mainFrame.getRenderMode();
        List<Sample> renderBuffer = new ArrayList<>();

        System.out.println("*** starting new worker thread");
        msg("Running readTest " + App.isReadEnabled() + "   writeTest " + App.isWriteEnabled());
        msg("num samples: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
                + ", blk size (kb): " + App.blockSizeKb + ", blockSequence: "
                + App.blockSequence);

        final int[] wUnitsComplete = {0};
        final int[] rUnitsComplete = {0};
        final int[] unitsComplete = {0};

        int wUnitsTotal = App.isWriteEnabled() ? numOfBlocks * numOfSamples : 0;
        int rUnitsTotal = App.isReadEnabled() ? numOfBlocks * numOfSamples : 0;
        int unitsTotal = wUnitsTotal + rUnitsTotal;

        int blockSize = blockSizeKb * KILOBYTE;
        byte[] blockArr = new byte[blockSize];
        for (int b = 0; b < blockArr.length; b++) {
            if (b % 2 == 0) blockArr[b] = (byte) 0xFF;
        }

        Gui.updateLegendAndAxis();

        if (App.autoReset) {
            App.resetTestData();
            Gui.resetBenchmarkData();
            Gui.updateLegendAndAxis();
        }

        String driveModel = Util.getDriveModel(locationDir);
        String partitionId = Util.getPartitionId(locationDir.toPath());
        DiskUsageInfo usageInfo = new DiskUsageInfo();
        try {
            usageInfo = Util.getDiskUsage(locationDir.toString());
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
        }
        msg("drive model=" + driveModel + " partitionId=" + partitionId
                + " usage=" + usageInfo.toDisplayString());

        int sIndex = App.nextSampleNumber;
        int eIndex = sIndex + numOfSamples;
        int[][] tRanges = divideIntoRanges(sIndex, eIndex, App.numOfThreads);

        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        Benchmark benchmark = new Benchmark(App.benchmarkType);

        benchmark.processorName = App.processorName;
        benchmark.os = App.os;
        benchmark.arch = App.arch;
        benchmark.driveModel = driveModel;
        benchmark.partitionId = partitionId;
        benchmark.percentUsed = usageInfo.percentUsed;
        benchmark.usedGb = usageInfo.usedGb;
        benchmark.totalGb = usageInfo.totalGb;

        Gui.chart.getTitle().setText(benchmark.getDriveInfo());
        Gui.chart.getTitle().setVisible(true);

        /* ========================= WRITE LOOP ========================= */
        if (App.isWriteEnabled()) {
            BenchmarkOperation wOperation = new BenchmarkOperation();
            wOperation.setBenchmark(benchmark);
            wOperation.ioMode = BenchmarkOperation.IOMode.WRITE;
            wOperation.setRenderMode(currentMode);   
            wOperation.blockOrder = App.blockSequence;
            wOperation.numSamples = App.numOfSamples;
            wOperation.numBlocks = App.numOfBlocks;
            wOperation.blockSize = App.blockSizeKb;
            wOperation.txSize = App.targetTxSizeKb();
            wOperation.numThreads = App.numOfThreads;
            wOperation.setWriteSyncEnabled(App.writeSyncEnable);
            benchmark.getOperations().add(wOperation);

            if (!App.multiFile) {
                testFile = new File(dataDir.getAbsolutePath() + File.separator + "testdata.jdm");
            }

            ExecutorService executorService = Executors.newFixedThreadPool(App.numOfThreads);
            for (int[] range : tRanges) {
                final int startSample = range[0];
                final int endSample = range[1];

                futures.add(executorService.submit(() -> {
                    for (int s = startSample; s <= endSample && !isCancelled(); s++) {

                        if (App.multiFile) {
                            testFile = new File(dataDir.getAbsolutePath()
                                    + File.separator + "testdata" + s + ".jdm");
                        }
                        Sample sample = new Sample(WRITE, s);
                        long startTime = System.nanoTime();
                        long totalBytesWrittenInSample = 0;
                        String mode = (App.writeSyncEnable) ? "rwd" : "rw";

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
                                    float percentComplete = (float) unitsComplete[0] / unitsTotal * 100f;
                                    setProgress((int) percentComplete);
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        long endTime = System.nanoTime();
                        sample.accessTimeMs = ((endTime - startTime) / 1_000_000f) / numOfBlocks;
                        double sec = (double) (endTime - startTime) / 1_000_000_000d;
                        double mbWritten = (double) totalBytesWrittenInSample / MEGABYTE;
                        sample.bwMbSec = mbWritten / sec;

                        /* Mode-specific rendering */
                        switch (currentMode) {
                            case PER_SAMPLE:
                                App.updateMetrics(sample); 
                                publish(sample);
                                wOperation.bwMax = sample.cumMax;
                                wOperation.bwMin = sample.cumMin;
                                wOperation.bwAvg = sample.cumAvg;
                                wOperation.accAvg = sample.cumAccTimeMs;
                                wOperation.add(sample);
                                break;

                                case PER_OPERATION:
                                    renderBuffer.add(sample);
                                    // flush once this thread finishes its assigned range
                                    if (s == endSample) {
                                        synchronized (renderBuffer) {
                                            for (Sample buffered : renderBuffer) {
                                                App.updateMetrics(buffered);
                                                publish(buffered);
                                                wOperation.add(buffered);
                                            }
                                            renderBuffer.clear();
                                        }
                                    }
                                    break;

                                case PER_100MS:
                                case PER_500MS:
                                case PER_1000MS:
                                    renderBuffer.add(sample);
                                    long now = System.currentTimeMillis();
                                    synchronized (renderBuffer) {
                                        if (now - lastUpdateTime >= getIntervalMillis(currentMode)) {
                                            for (Sample buffered : renderBuffer) {
                                                App.updateMetrics(buffered);
                                                publish(buffered);
                                                wOperation.add(buffered);
                                            }
                                            renderBuffer.clear();
                                            lastUpdateTime = now;
                                        }
                                    }
                                    break;
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


                wOperation.endTime = LocalDateTime.now();
                wOperation.setTotalOps(wUnitsComplete[0]);
                App.wIops = wOperation.iops;
                Gui.mainFrame.refreshWriteMetrics();
            }
        
        /* ========================= READ LOOP ========================= */
        if (App.isReadEnabled()) {
            BenchmarkOperation rOperation = new BenchmarkOperation();
            rOperation.setBenchmark(benchmark);
            rOperation.ioMode = BenchmarkOperation.IOMode.READ;
            rOperation.setRenderMode(currentMode);
            rOperation.blockOrder = App.blockSequence;
            rOperation.numSamples = App.numOfSamples;
            rOperation.numBlocks = App.numOfBlocks;
            rOperation.blockSize = App.blockSizeKb;
            rOperation.txSize = App.targetTxSizeKb();
            rOperation.numThreads = App.numOfThreads;
            rOperation.setWriteSyncEnabled(null);
            benchmark.getOperations().add(rOperation);

            ExecutorService executorService = Executors.newFixedThreadPool(App.numOfThreads);
            for (int[] range : tRanges) {
                final int startSample = range[0];
                final int endSample = range[1];

                futures.add(executorService.submit(() -> {
                    for (int s = startSample; s <= endSample && !isCancelled(); s++) {
                        if (App.multiFile) {
                            testFile = new File(dataDir.getAbsolutePath()
                                    + File.separator + "testdata" + s + ".jdm");
                        }
                        Sample sample = new Sample(READ, s);
                        long startTime = System.nanoTime();
                        long totalBytesRead = 0;

                        try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, "r")) {
                            for (int b = 0; b < numOfBlocks; b++) {
                                if (App.blockSequence == BenchmarkOperation.BlockSequence.RANDOM) {
                                    int rLoc = Util.randInt(0, numOfBlocks - 1);
                                    rAccFile.seek(rLoc * blockSize);
                                } else {
                                    rAccFile.seek(b * blockSize);
                                }
                                rAccFile.readFully(blockArr, 0, blockSize);
                                totalBytesRead += blockSize;
                                synchronized (BenchmarkWorker.this) {
                                    rUnitsComplete[0]++;
                                    unitsComplete[0] = rUnitsComplete[0] + wUnitsComplete[0];
                                    float percentComplete = (float) unitsComplete[0] / unitsTotal * 100f;
                                    setProgress((int) percentComplete);
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        long endTime = System.nanoTime();
                        sample.accessTimeMs = ((endTime - startTime) / 1_000_000f) / numOfBlocks;
                        double sec = (double) (endTime - startTime) / 1_000_000_000d;
                        double mbRead = (double) totalBytesRead / MEGABYTE;
                        sample.bwMbSec = mbRead / sec;

                        /* Mode-specific rendering */
                        switch (currentMode) {
                            case PER_SAMPLE:
                                App.updateMetrics(sample);
                                publish(sample);
                                rOperation.add(sample);
                                break;

                            case PER_OPERATION:
                                renderBuffer.add(sample);
                                if (s == endSample) {
                                    for (Sample buffered : renderBuffer) {
                                        App.updateMetrics(buffered);
                                        publish(buffered);
                                        rOperation.add(buffered);
                                    }
                                    renderBuffer.clear();
                                }
                                break;

                            case PER_100MS:
                            case PER_500MS:
                            case PER_1000MS:
                                renderBuffer.add(sample);
                                long now = System.currentTimeMillis();
                                if (now - lastUpdateTime >= getIntervalMillis(currentMode)) {
                                    for (Sample buffered : renderBuffer) {
                                        App.updateMetrics(buffered);
                                        publish(buffered);
                                        rOperation.add(buffered);
                                    }
                                    renderBuffer.clear();
                                    lastUpdateTime = now;
                                }
                                break;                               
                        }
                    }
                }));
            }
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            
            // --- Final stats sync for READ ---
            if (!rOperation.getSamples().isEmpty()) {
                Sample last = rOperation.getSamples().get(rOperation.getSamples().size() - 1);
                rOperation.bwMax = last.cumMax;
                rOperation.bwMin = last.cumMin;
                rOperation.bwAvg = last.cumAvg;
                rOperation.accAvg = last.cumAccTimeMs;
            }
            rOperation.endTime = LocalDateTime.now();
            rOperation.setTotalOps(rUnitsComplete[0]);
            App.rIops = rOperation.iops;
            Gui.mainFrame.refreshReadMetrics();
            
            benchmark.endTime = LocalDateTime.now();
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(benchmark);
            em.getTransaction().commit();
        }

        /* ========================= FINALIZE BENCHMARK ========================= */
        App.benchmarks.put(benchmark.getStartTimeString(), benchmark);
        for (BenchmarkOperation o : benchmark.getOperations()) {
            App.operations.put(o.getStartTimeString(), o);
        }
        Gui.runPanel.addRun(benchmark);

        App.nextSampleNumber += App.numOfSamples;
        return true;  // ✅ THIS FIXES THE "MISSING RETURN STATEMENT"
    } 
    
    private long getIntervalMillis(RenderFrequencyMode mode) {
        return switch (mode) {
            case PER_100MS -> 100;
            case PER_500MS -> 500;
            case PER_1000MS -> 1000;
            default -> 500;
        };
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
