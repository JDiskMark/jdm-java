
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import static jdiskmark.App.numOfSamples;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class BenchmarkWorker extends SwingWorker <Boolean, Sample> {
    
    @Override
    protected Boolean doInBackground() throws Exception {
        
        System.out.println("*** starting new worker thread");
        msg("Running readTest " + App.readTest + "   writeTest " + App.writeTest);
        msg("num samples: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
                + ", blk size (kb): " + App.blockSizeKb + ", blockSequence: "
                + App.blockSequence);
        
        int wUnitsComplete = 0;
        int rUnitsComplete = 0;
        int unitsComplete;
        int wUnitsTotal = App.writeTest ? numOfBlocks * numOfSamples : 0;
        int rUnitsTotal = App.readTest ? numOfBlocks * numOfSamples : 0;
        int unitsTotal = wUnitsTotal + rUnitsTotal;
        float percentComplete;
        
        int blockSize = blockSizeKb * KILOBYTE;
        byte [] blockArr = new byte [blockSize];
        for (int b = 0; b < blockArr.length; b++) {
            if (b % 2 == 0) {
                blockArr[b]=(byte)0xFF;
            }
        }
   
        Sample wSample;
        Sample rSample;
        
        Gui.updateLegendAndAxis();
        
        if (App.autoReset == true) {
            App.resetTestData();
            Gui.resetBenchmarkData();
            Gui.updateLegendAndAxis();
        }
        
        int startFileNum = App.nextSampleNumber;
        
        String driveModel = Util.getDriveModel(dataDir);
        String partitionId = Util.getPartitionId(dataDir.toPath());
        Util.DiskUsageInfo dInfo = null;
        try {
            dInfo = Util.getDiskUsage(dataDir.toString());
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
        }
        msg("drive model=" + driveModel + " usage=" + dInfo.toDisplayString());
        
        if (App.writeTest) {
            Benchmark run = new Benchmark(Benchmark.IOMode.WRITE, App.blockSequence);
            
            run.driveModel = driveModel;
            run.partitionId = partitionId;
            run.percentUsed = dInfo.percentUsed;
            run.usedGb = dInfo.usedGb;
            run.totalGb = dInfo.totalGb;
            
            run.numSamples = App.numOfSamples;
            run.numBlocks = App.numOfBlocks;
            run.blockSize = App.blockSizeKb;
            run.txSize = App.targetTxSizeKb();

            Gui.chart.getTitle().setVisible(true);
            Gui.chart.getTitle().setText(run.getDriveInfo());
            
            if (App.multiFile == false) {
                testFile = new File(dataDir.getAbsolutePath() + File.separator + "testdata.jdm");
            }
            for (int s = startFileNum; s < startFileNum + App.numOfSamples && !isCancelled(); s++) {
                
                if (App.multiFile == true) {
                    testFile = new File(dataDir.getAbsolutePath()
                            + File.separator + "testdata" + s + ".jdm");
                }
                wSample = new Sample(WRITE, s);
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
                            wUnitsComplete++;
                            unitsComplete = rUnitsComplete + wUnitsComplete;
                            percentComplete = (float)unitsComplete / (float)unitsTotal * 100f;
                            setProgress((int)percentComplete);
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                }
                long endTime = System.nanoTime();
                long elapsedTimeNs = endTime - startTime;
                wSample.accessTimeMs = (elapsedTimeNs / 1000000f) / numOfBlocks;
                double sec = (double)elapsedTimeNs / (double)1000000000;
                double mbWritten = (double)totalBytesWrittenInSample / (double)MEGABYTE;
                wSample.bwMbSec = mbWritten / sec;
                msg("s:" + s + " write IO is " + wSample.getBwMbSecDisplay() + " MB/s   "
                        + "(" + Util.displayString(mbWritten) + "MB written in "
                        + Util.displayString(sec) + " sec)");
                App.updateMetrics(wSample);
                publish(wSample);
                
                run.runMax = wSample.cumMax;
                run.runMin = wSample.cumMin;
                run.runAvg = wSample.cumAvg;
                run.accAvg = wSample.cumAccTimeMs;
                run.endTime = LocalDateTime.now();
                run.add(wSample);
            }
            
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
            
            run.driveModel = driveModel;
            run.partitionId = partitionId;
            run.percentUsed = dInfo.percentUsed;
            run.usedGb = dInfo.usedGb;
            run.totalGb = dInfo.totalGb;
            
            run.numSamples = App.numOfSamples;
            run.numBlocks = App.numOfBlocks;
            run.blockSize = App.blockSizeKb;
            run.txSize = App.targetTxSizeKb();

            Gui.chart.getTitle().setVisible(true);
            Gui.chart.getTitle().setText(run.getDriveInfo());
            
            for (int s = startFileNum; s < startFileNum + App.numOfSamples && !isCancelled(); s++) {
                
                if (App.multiFile == true) {
                    testFile = new File(dataDir.getAbsolutePath()
                            + File.separator + "testdata" + s + ".jdm");
                }
                rSample = new Sample(READ, s);
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
                            rUnitsComplete++;
                            unitsComplete = rUnitsComplete + wUnitsComplete;
                            percentComplete = (float)unitsComplete / (float)unitsTotal * 100f;
                            setProgress((int)percentComplete);
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
                }
                long endTime = System.nanoTime();
                long elapsedTimeNs = endTime - startTime;
                rSample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float)numOfBlocks;
                double sec = (double)elapsedTimeNs / (double)1_000_000_000;
                double mbRead = (double) totalBytesReadInMark / (double) MEGABYTE;
                rSample.bwMbSec = mbRead / sec;
                msg("s:" + s + " READ IO is " + rSample.bwMbSec + " MB/s    "
                        + "(MBread " + mbRead + " in " + sec + " sec)");
                App.updateMetrics(rSample);
                publish(rSample);
                
                run.runMax = rSample.cumMax;
                run.runMin = rSample.cumMin;
                run.runAvg = rSample.cumAvg;
                run.accAvg = rSample.cumAccTimeMs;
                run.endTime = LocalDateTime.now();
                run.add(rSample);
            }
            
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
