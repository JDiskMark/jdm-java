
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
public class BenchmarkWorker extends SwingWorker <Boolean, Sample> {
    
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
        msg("Running readTest " + App.readTest + "   writeTest " + App.writeTest);
        msg("num samples: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
                + ", blk size (kb): " + App.blockSizeKb + ", blockSequence: "
                + App.blockSequence);
        
        // GH-20 final to aid w lambda usage
        final int[] wUnitsComplete = {0};
        final int[] rUnitsComplete = {0};
        final int[] unitsComplete = {0};
        
        int wUnitsTotal = App.writeTest ? numOfBlocks * numOfSamples : 0;
        int rUnitsTotal = App.readTest ? numOfBlocks * numOfSamples : 0;
        int unitsTotal = wUnitsTotal + rUnitsTotal;
        
        int blockSize = blockSizeKb * KILOBYTE;
        byte [] blockArr = new byte [blockSize];
        for (int b = 0; b < blockArr.length; b++) {
            if (b % 2 == 0) {
                blockArr[b]=(byte)0xFF;
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
        msg("drive model=" + driveModel + " partitionId=" + partitionId 
                + " usage=" + usageInfo.toDisplayString());
        
        // GH-20 calculate ranges for concurrent thread IO
        int sIndex = App.nextSampleNumber;
        int eIndex = sIndex + numOfSamples;
        int[][] tRanges = divideIntoRanges(sIndex, eIndex, App.numOfThreads);
        
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        
        if (App.writeTest) {
            Benchmark run = new Benchmark(Benchmark.IOMode.WRITE, App.blockSequence);
            
            // system info
            run.processorName = App.processorName;
            run.os = App.os;
            run.arch = App.arch;
            // drive information
            run.driveModel = driveModel;
            run.partitionId = partitionId;
            run.percentUsed = usageInfo.percentUsed;
            run.usedGb = usageInfo.usedGb;
            run.totalGb = usageInfo.totalGb;
            // benchmark parameters
            run.numSamples = App.numOfSamples;
            run.numBlocks = App.numOfBlocks;
            run.blockSize = App.blockSizeKb;
            run.txSize = App.targetTxSizeKb();

            Gui.chart.getTitle().setVisible(true);
            Gui.chart.getTitle().setText(run.getDriveInfo());
            
            if (App.multiFile == false) {
                testFile = new File(dataDir.getAbsolutePath() + File.separator + "testdata.jdm");
            }
            
            // TODO: GH-20 instantiate threads to operate on each range
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
                                    if (App.blockSequence == Benchmark.BlockSequence.RANDOM) {
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
                                        float percentComplete = (float)unitsComplete[0] / (float)unitsTotal * 100f;
                                        setProgress((int)percentComplete);
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        long endTime = System.nanoTime();
                        long elapsedTimeNs = endTime - startTime;
                        sample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / numOfBlocks;
                        double sec = (double)elapsedTimeNs / 1_000_000_000d;
                        double mbWritten = (double)totalBytesWrittenInSample / (double)MEGABYTE;
                        sample.bwMbSec = mbWritten / sec;
                        msg("s:" + s + " write IO is " + sample.getBwMbSecDisplay() + " MB/s   "
                                + "(" + Util.displayString(mbWritten) + "MB written in "
                                + Util.displayString(sec) + " sec)");
                        App.updateMetrics(sample);
                        publish(sample);

                        run.bwMax = sample.cumMax;
                        run.bwMin = sample.cumMin;
                        run.bwAvg = sample.cumAvg;
                        run.accAvg = sample.cumAccTimeMs;
                        run.add(sample);
                    }
                }));
            }
            
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            // GH-10 file IOPS processing
            run.endTime = LocalDateTime.now();
            run.setTotalOps(wUnitsComplete[0]);
            App.wIops = run.iops;
            Gui.mainFrame.refreshWriteMetrics();
            
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(run);
            em.getTransaction().commit();
            App.benchmarks.put(run.getStartTimeString(), run);
            Gui.runPanel.addRun(run);
        }
        
        // try renaming all files to clear catch
        if (App.readTest && App.writeTest && !isCancelled()) {
            Gui.dropCache();
        }
        
        if (App.readTest) {
            Benchmark run = new Benchmark(Benchmark.IOMode.READ, App.blockSequence);
            
            // system info
            run.processorName = App.processorName;
            run.os = App.os;
            run.arch = App.arch;
            // drive information
            run.driveModel = driveModel;
            run.partitionId = partitionId;
            run.percentUsed = usageInfo.percentUsed;
            run.usedGb = usageInfo.usedGb;
            run.totalGb = usageInfo.totalGb;
            // benchmark parameters
            run.numSamples = App.numOfSamples;
            run.numBlocks = App.numOfBlocks;
            run.blockSize = App.blockSizeKb;
            run.txSize = App.targetTxSizeKb();

            Gui.chart.getTitle().setVisible(true);
            Gui.chart.getTitle().setText(run.getDriveInfo());
            
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
                                for (int b=0; b < numOfBlocks; b++) {
                                    if (App.blockSequence == Benchmark.BlockSequence.RANDOM) {
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
                                        float percentComplete = (float)unitsComplete[0] / (float)unitsTotal * 100f;
                                        setProgress((int)percentComplete);
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        long endTime = System.nanoTime();
                        long elapsedTimeNs = endTime - startTime;
                        sample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float)numOfBlocks;
                        double sec = (double)elapsedTimeNs / 1_000_000_000d;
                        double mbRead = (double)totalBytesReadInMark / (double)MEGABYTE;
                        sample.bwMbSec = mbRead / sec;
                        msg("s:" + s + " READ IO is " + sample.bwMbSec + " MB/s    "
                                + "(MBread " + mbRead + " in " + sec + " sec)");
                        App.updateMetrics(sample);
                        publish(sample);

                        run.bwMax = sample.cumMax;
                        run.bwMin = sample.cumMin;
                        run.bwAvg = sample.cumAvg;
                        run.accAvg = sample.cumAccTimeMs;

                        run.add(sample);
                    }
                }));
            }
            
            // GH-10 file IOPS processing
            run.endTime = LocalDateTime.now();
            run.setTotalOps(rUnitsComplete[0]);
            App.rIops = run.iops;
            Gui.mainFrame.refreshReadMetrics();
            
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(run);
            em.getTransaction().commit();
            App.benchmarks.put(run.getStartTimeString(), run);
            Gui.runPanel.addRun(run);
        }
        App.nextSampleNumber += App.numOfSamples;
        return true;
    }
    
    @Override
    protected void process(List<Sample> sampleList) {
        sampleList.stream().forEach((Sample s) -> {
            if (s.type == Sample.Type.WRITE) {
                Gui.addWriteSample(s);
            } else {
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
