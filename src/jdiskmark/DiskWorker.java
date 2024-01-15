
package jdiskmark;

import jakarta.persistence.EntityManager;
import static jdiskmark.App.KILOBYTE;
import static jdiskmark.App.MEGABYTE;
import static jdiskmark.App.blockSizeKb;
import static jdiskmark.App.msg;
import static jdiskmark.App.numOfBlocks;
import static jdiskmark.App.testFile;
import static jdiskmark.App.dataDir;
import static jdiskmark.DiskMark.MarkType.READ;
import static jdiskmark.DiskMark.MarkType.WRITE;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import static jdiskmark.App.numOfSamples;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class DiskWorker extends SwingWorker <Boolean, DiskMark> {
    
    @Override
    protected Boolean doInBackground() throws Exception {
        
        System.out.println("*** starting new worker thread");
        msg("Running readTest " + App.readTest + "   writeTest " + App.writeTest);
        msg("num files: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
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
   
        DiskMark wMark, rMark;
        
        Gui.updateLegend();
        
        if (App.autoReset == true) {
            App.resetTestData();
            Gui.resetTestData();
        }
        
        int startFileNum = App.nextMarkNumber;
        
        if (App.writeTest) {
            DiskRun run = new DiskRun(DiskRun.IOMode.WRITE, App.blockSequence);
            run.numMarks = App.numOfSamples;
            run.numBlocks = App.numOfBlocks;
            run.blockSize = App.blockSizeKb;
            run.txSize = App.targetTxSizeKb();
            run.setDiskInfo(Util.getDiskInfo(dataDir));
            
            msg("disk info: (" + run.getDiskInfo() + ")");
            
            Gui.chartPanel.getChart().getTitle().setVisible(true);
            Gui.chartPanel.getChart().getTitle().setText(run.getDiskInfo());
            
            if (App.multiFile == false) {
                testFile = new File(dataDir.getAbsolutePath()+File.separator + "testdata.jdm");
            }            
            for (int m = startFileNum; m < startFileNum+App.numOfSamples && !isCancelled(); m++) {
                
                if (App.multiFile == true) {
                    testFile = new File(dataDir.getAbsolutePath()
                            + File.separator + "testdata" + m + ".jdm");
                }   
                wMark = new DiskMark(WRITE);
                wMark.markNum = m;
                long startTime = System.nanoTime();
                long totalBytesWrittenInMark = 0;

                String mode = "rw";
                if (App.writeSyncEnable) { mode = "rwd"; }
                
                try {
                    try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, mode)) {
                        for (int b = 0; b < numOfBlocks; b++) {
                            if (App.blockSequence == DiskRun.BlockSequence.RANDOM) {
                                int rLoc = Util.randInt(0, numOfBlocks - 1);
                                rAccFile.seek(rLoc * blockSize);
                            } else {
                                rAccFile.seek(b * blockSize);
                            }
                            rAccFile.write(blockArr, 0, blockSize);
                            totalBytesWrittenInMark += blockSize;
                            wUnitsComplete++;
                            unitsComplete = rUnitsComplete + wUnitsComplete;
                            percentComplete = (float)unitsComplete / (float)unitsTotal * 100f;
                            setProgress((int)percentComplete);
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
                }
                long endTime = System.nanoTime();
                long elapsedTimeNs = endTime - startTime;
                wMark.elapsedTimeMs = (elapsedTimeNs / 1000000f) / numOfBlocks;
                double sec = (double)elapsedTimeNs / (double)1000000000;
                double mbWritten = (double)totalBytesWrittenInMark / (double)MEGABYTE;
                wMark.bwMbSec = mbWritten / sec;
                msg("m:" + m + " write IO is " + wMark.getBwMbSec() + " MB/s   "
                        + "(" + Util.displayString(mbWritten) + "MB written in "
                        + Util.displayString(sec) + " sec)");
                App.updateMetrics(wMark);
                publish(wMark);
                
                run.runMax = wMark.cumMax;
                run.runMin = wMark.cumMin;
                run.runAvg = wMark.cumAvg;
                run.endTime = LocalDateTime.now();
            }
            
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(run);
            em.getTransaction().commit();
            
            Gui.runPanel.addRun(run);
        }
        
        // try renaming all files to clear catch
        if (App.readTest && App.writeTest && !isCancelled()) {
            JOptionPane.showMessageDialog(Gui.mainFrame, 
                    """
                    For valid READ measurements please clear the disk cache by
                    using the included RAMMap.exe or flushmem.exe utilities.
                    Removable drives can be disconnected and reconnected.
                    For system drives use the WRITE and READ operations 
                    independantly by doing a cold reboot after the WRITE""",
                    "Clear Disk Cache Now",
                    JOptionPane.PLAIN_MESSAGE);
        }
        
        if (App.readTest) {
            DiskRun run = new DiskRun(DiskRun.IOMode.READ, App.blockSequence);
            run.numMarks = App.numOfSamples;
            run.numBlocks = App.numOfBlocks;
            run.blockSize = App.blockSizeKb;
            run.txSize = App.targetTxSizeKb();
            run.setDiskInfo(Util.getDiskInfo(dataDir));
              
            msg("disk info: (" + run.getDiskInfo() + ")");
            
            Gui.chartPanel.getChart().getTitle().setVisible(true);
            Gui.chartPanel.getChart().getTitle().setText(run.getDiskInfo());
            
            for (int m = startFileNum; m < startFileNum + App.numOfSamples && !isCancelled(); m++) {
                
                if (App.multiFile == true) {
                    testFile = new File(dataDir.getAbsolutePath()
                            + File.separator + "testdata" + m + ".jdm");
                }
                rMark = new DiskMark(READ);
                rMark.markNum = m;
                long startTime = System.nanoTime();
                long totalBytesReadInMark = 0;

                try {
                    try (RandomAccessFile rAccFile = new RandomAccessFile(testFile, "r")) {
                        for (int b=0; b < numOfBlocks; b++) {
                            if (App.blockSequence == DiskRun.BlockSequence.RANDOM) {
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
                    Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
                }
                long endTime = System.nanoTime();
                long elapsedTimeNs = endTime - startTime;
                rMark.elapsedTimeMs = (elapsedTimeNs / 1_000_000f) / (float)numOfBlocks;
                double sec = (double)elapsedTimeNs / (double)1_000_000_000;
                double mbRead = (double) totalBytesReadInMark / (double) MEGABYTE;
                rMark.bwMbSec = mbRead / sec;
                msg("m:" + m + " READ IO is " + rMark.bwMbSec + " MB/s    "
                        + "(MBread " + mbRead + " in " + sec + " sec)");
                App.updateMetrics(rMark);
                publish(rMark);
                
                run.runMax = rMark.cumMax;
                run.runMin = rMark.cumMin;
                run.runAvg = rMark.cumAvg;
                run.endTime = LocalDateTime.now();
            }
            
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(run);
            em.getTransaction().commit();
            
            Gui.runPanel.addRun(run);
        }
        App.nextMarkNumber += App.numOfSamples;      
        return true;
    }
    
    @Override
    protected void process(List<DiskMark> markList) {
        markList.stream().forEach((DiskMark m) -> {
            if (m.type == DiskMark.MarkType.WRITE) {
                Gui.addWriteMark(m);
            } else {
                Gui.addReadMark(m);
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
