
package jdiskmark;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker.StateValue;
import static javax.swing.SwingWorker.StateValue.STARTED;

/**
 * Primary class for global variables.
 */
public class App {
    
    public static final String APP_CACHE_DIR_NAME = System.getProperty("user.home") + File.separator + ".jDiskMark";
    public static final File APP_CACHE_DIR = new File(APP_CACHE_DIR_NAME);
    public static final String PROPERTIES_FILENAME = "jdm.properties";
    public static final File PROPERTIES_FILE = new File(APP_CACHE_DIR_NAME + File.separator + PROPERTIES_FILENAME);
    public static final String BUILD_TOKEN_FILENAME = "build.properties";
    public static final String DATADIRNAME = "jDiskMarkData";
    public static final String ESBL_EXE = "EmptyStandbyList.exe";
    
    public static final int MEGABYTE = 1024 * 1024;
    public static final int KILOBYTE = 1024;
    public static final int IDLE_STATE = 0;
    public static final int DISK_TEST_STATE = 1;

    public static enum State { IDLE_STATE, DISK_TEST_STATE };
    public static State state = State.IDLE_STATE;
    
    public static Properties p;
    public static File locationDir = null;
    public static File dataDir = null;
    public static File testFile = null;
    
    // system info
    public static String os;
    public static String arch;
    public static String processorName;
    
    // elevated priviledges
    public static boolean isRoot = false;
    public static boolean isAdmin = false;
    
    // options
    public static boolean multiFile = true;
    public static boolean autoRemoveData = false;
    public static boolean autoReset = true;
    public static boolean showMaxMin = true;
    public static boolean showDriveAccess = true;
    public static boolean writeSyncEnable = true;
    
    // run configuration
    public static boolean readTest = false;
    public static boolean writeTest = true;
    public static Benchmark.BlockSequence blockSequence = Benchmark.BlockSequence.SEQUENTIAL;
    public static int numOfSamples = 200;   // desired number of samples
    public static int numOfBlocks = 32;     // desired number of blocks
    public static int blockSizeKb = 512;    // size of a block in KBs
    
    public static BenchmarkWorker worker = null;
    public static int nextSampleNumber = 1;   // number of the next sample
    public static double wMax = -1, wMin = -1, wAvg = -1, wAcc = -1;
    public static double rMax = -1, rMin = -1, rAvg = -1, rAcc = -1;
    
    public static HashMap<String, Benchmark> benchmarks = new HashMap<>();
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(App::init);
    }
    
    /**
     * Get the version from the build properties. Defaults to 0.0 if not found.
     * @return 
     */
    public static String getVersion() {
        Properties bp = new Properties();
        String version = "0.0";
        if (Files.exists(Paths.get(BUILD_TOKEN_FILENAME))) {
            // ide and zip release
            try {
                bp.load(new FileInputStream(BUILD_TOKEN_FILENAME));
            } catch (IOException ex) {
                System.err.println("If in NetBeans please do a "
                        + "Clean and Build Project from the Run Menu or press F11");
                Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
            }            
        } else {
            // GH-14 jpackage windows environment
            try {
                bp.load(new FileInputStream("app/" + BUILD_TOKEN_FILENAME));
            } catch (IOException ex) {
                Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        version = bp.getProperty("version", version);        
        return version;
    }
    
    /**
     * Initialize the GUI Application.
     */
    public static void init() {
        
        App.os = System.getProperty("os.name");
        App.arch = System.getProperty("os.arch");
        if (App.os.startsWith("Windows")) {
            App.processorName = UtilOs.getProcessorNameWindows();
        } else if (App.os.startsWith("Mac OS")) {
            App.processorName = UtilOs.getProcessorNameMacOS();
        } else if (App.os.contains("Linux")) {
            App.processorName = UtilOs.getProcessorNameLinux();
        }
        
        checkPermission();
        if (!APP_CACHE_DIR.exists()) {
            APP_CACHE_DIR.mkdirs();
        }
        loadConfig();
        
        // initialize data dir if necessary
        if (App.locationDir == null) {
            App.locationDir = new File(System.getProperty("user.home"));
            App.dataDir = new File(App.locationDir.getAbsolutePath()
                    + File.separator + App.DATADIRNAME);
        }
        
        Gui.configureLaf();
        
        Gui.mainFrame = new MainFrame();
        Gui.selFrame = new SelectDriveFrame();
        System.out.println(App.getConfigString());
        Gui.mainFrame.loadConfig();
        Gui.mainFrame.setLocationRelativeTo(null);
        Gui.progressBar = Gui.mainFrame.getProgressBar();
        
        // configure the embedded DB in .jDiskMark
        System.setProperty("derby.system.home", APP_CACHE_DIR_NAME);
        loadBenchmarks();
        
        // load current drive
        Gui.updateDiskInfo();
        
        Gui.mainFrame.setVisible(true);
        
        // save configuration on exit...
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() { App.saveConfig(); }
        });
    }
    
    public static void checkPermission() {
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            isRoot = UtilOs.isRunningAsRootLinux();
        } else if (osName.contains("Mac OS")) {
            isRoot = UtilOs.isRunningAsRootMacOs();
        } else if (osName.contains("Windows")) {
            isAdmin = UtilOs.isRunningAsAdminWindows();
        }
        if (isRoot || isAdmin) {
            System.out.println("Running w elevated priviledges");
        }
    }
    
    public static void loadConfig() {
        
        if (PROPERTIES_FILE.exists()) {
            System.out.println("loading: " + PROPERTIES_FILE.getAbsolutePath());
        } else {
            // generate default properties file if it does not exist
            System.out.println(PROPERTIES_FILE + " does not exist generating...");
            saveConfig(); 
        }

        // read properties file
        if (p == null) { p = new Properties(); }
        try {
            InputStream in = new FileInputStream(PROPERTIES_FILE);
            p.load(in);
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // configure settings from properties
        String value;
        value = p.getProperty("multiFile", String.valueOf(multiFile));
        multiFile = Boolean.parseBoolean(value);
        value = p.getProperty("autoRemoveData", String.valueOf(autoRemoveData));
        autoRemoveData = Boolean.parseBoolean(value);
        value = p.getProperty("autoReset", String.valueOf(autoReset));
        autoReset = Boolean.parseBoolean(value);
        value = p.getProperty("blockSequence", String.valueOf(blockSequence));
        blockSequence = Benchmark.BlockSequence.valueOf(value);
        value = p.getProperty("showMaxMin", String.valueOf(showMaxMin));
        showMaxMin = Boolean.parseBoolean(value);
        value = p.getProperty("showDriveAccess", String.valueOf(showDriveAccess));
        showDriveAccess = Boolean.parseBoolean(value);
        value = p.getProperty("numOfSamples", String.valueOf(numOfSamples));
        numOfSamples = Integer.parseInt(value);
        value = p.getProperty("numOfBlocks", String.valueOf(numOfBlocks));
        numOfBlocks = Integer.parseInt(value);
        value = p.getProperty("blockSizeKb", String.valueOf(blockSizeKb));
        blockSizeKb = Integer.parseInt(value);
        value = p.getProperty("writeTest", String.valueOf(writeTest));
        writeTest = Boolean.parseBoolean(value);
        value = p.getProperty("readTest", String.valueOf(readTest));
        readTest = Boolean.parseBoolean(value);
        value = p.getProperty("writeSyncEnable", String.valueOf(writeSyncEnable));
        writeSyncEnable = Boolean.parseBoolean(value);
        value = p.getProperty("palette", String.valueOf(Gui.palette));
        Gui.palette = Gui.Palette.valueOf(value);
    }
    
    public static void saveConfig() {
        if (p == null) { p = new Properties(); }
        
        // configure properties
        p.setProperty("multiFile", String.valueOf(multiFile));
        p.setProperty("autoRemoveData", String.valueOf(autoRemoveData));
        p.setProperty("autoReset", String.valueOf(autoReset));
        p.setProperty("blockSequence", String.valueOf(blockSequence));
        p.setProperty("showMaxMin", String.valueOf(showMaxMin));
        p.setProperty("showDriveAccess", String.valueOf(showDriveAccess));
        p.setProperty("numOfSamples", String.valueOf(numOfSamples));
        p.setProperty("numOfBlocks", String.valueOf(numOfBlocks));
        p.setProperty("blockSizeKb", String.valueOf(blockSizeKb));
        p.setProperty("writeTest", String.valueOf(writeTest));
        p.setProperty("readTest", String.valueOf(readTest));
        p.setProperty("writeSyncEnable", String.valueOf(writeSyncEnable));
        p.setProperty("palette", String.valueOf(Gui.palette));
        
        // write properties file
        try {
            OutputStream out = new FileOutputStream(PROPERTIES_FILE);
            p.store(out, "jDiskMark Properties File");
        } catch (IOException ex) {
            Logger.getLogger(SelectDriveFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static String getConfigString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Config for Java Disk Mark ").append(getVersion()).append('\n');
        sb.append("readTest: ").append(readTest).append('\n');
        sb.append("writeTest: ").append(writeTest).append('\n');
        sb.append("locationDir: ").append(locationDir).append('\n');
        sb.append("multiFile: ").append(multiFile).append('\n');
        sb.append("autoRemoveData: ").append(autoRemoveData).append('\n');
        sb.append("autoReset: ").append(autoReset).append('\n');
        sb.append("blockSequence: ").append(blockSequence).append('\n');
        sb.append("showMaxMin: ").append(showMaxMin).append('\n');
        sb.append("numOfFiles: ").append(numOfSamples).append('\n');
        sb.append("numOfBlocks: ").append(numOfBlocks).append('\n');
        sb.append("blockSizeKb: ").append(blockSizeKb).append('\n');
        sb.append("palette: ").append(Gui.palette).append('\n');
        return sb.toString();
    }
    
    public static void loadBenchmarks() {
        Gui.runPanel.clearTable();
        
        // populate run table with saved runs from db
        System.out.println("loading benchmarks");
        Benchmark.findAll().stream().forEach((Benchmark run) -> {
            benchmarks.put(run.getStartTimeString(), run);
            Gui.runPanel.addRun(run);
        });
    }
    
    public static void clearSavedBenchmarks() {
        Benchmark.deleteAll();
        App.benchmarks.clear();
        loadBenchmarks();
    }
    
    public static void msg(String message) {
        Gui.mainFrame.msg(message);
    }
    
    public static void cancelBenchmark() {
        if (worker == null) { 
            msg("worker is null abort..."); 
            return;
        }
        worker.cancel(true);
    }
    
    public static void startBenchmark() {
        
        // 1. check that there isn't already a worker in progress
        if (state == State.DISK_TEST_STATE) {
            //if (!worker.isCancelled() && !worker.isDone()) {
                msg("Test in progress, aborting...");
                return;
            //}
        }
        
        // 2. check can write to location
        if (locationDir.canWrite() == false) {
            msg("Selected directory can not be written to... aborting");
            return;
        }
        
        // 3. update state
        state = State.DISK_TEST_STATE;
        Gui.mainFrame.adjustSensitivity();
        
        // 4. create data dir reference
        dataDir = new File (locationDir.getAbsolutePath() + File.separator + DATADIRNAME);
        
        // 5. remove existing test data if exist
        if (App.autoRemoveData && dataDir.exists()) {
            if (dataDir.delete()) {
                msg("removed existing data dir");
            } else {
                msg("unable to remove existing data dir");
            }
        }
        
        // 6. create data dir if not already present
        if (dataDir.exists() == false) { dataDir.mkdirs(); }
        
        // 7. start disk worker thread
        worker = new BenchmarkWorker();
        worker.addPropertyChangeListener((final var event) -> {
            switch (event.getPropertyName()) {
                case "progress" -> {
                    int value = (Integer) event.getNewValue();
                    Gui.progressBar.setValue(value);
                    long kbProcessed = (value) * App.targetTxSizeKb() / 100;
                    Gui.progressBar.setString(String.valueOf(kbProcessed) + " / " + String.valueOf(App.targetTxSizeKb()));
                }
                case "state" -> {
                    switch ((StateValue) event.getNewValue()) {
                        case STARTED -> Gui.progressBar.setString("0 / " + String.valueOf(App.targetTxSizeKb()));
                        case DONE -> {}
                    } // end inner switch
                }
            }
        });
        worker.execute();
    }
    
    public static long targetMarkSizeKb() {
        return blockSizeKb * numOfBlocks;
    }
    
    public static long targetTxSizeKb() {
        return blockSizeKb * numOfBlocks * numOfSamples;
    }
    
    public static void updateMetrics(Sample s) {
        if (s.type == Sample.Type.WRITE) {
            if (wMax == -1 || wMax < s.bwMbSec) {
                wMax = s.bwMbSec;
            }
            if (wMin == -1 || wMin > s.bwMbSec) {
                wMin = s.bwMbSec;
            }
            
            // cumulative average bw
            if (wAvg == -1) {
                wAvg = s.bwMbSec;
            } else {
                int n = s.sampleNum;
                wAvg = (((double)(n - 1) * wAvg) + s.bwMbSec) / (double)n;
            }
            
            // cumulative access time
            if (wAcc == -1) {
                wAcc = s.accessTimeMs;
            } else {
                int n = s.sampleNum;
                wAcc = (((double)(n - 1) * wAcc) + s.accessTimeMs) / (double)n;
            }
            
            s.cumAvg = wAvg;
            s.cumMax = wMax;
            s.cumMin = wMin;
            s.cumAccTimeMs = wAcc;
        } else {
            if (rMax == -1 || rMax < s.bwMbSec) {
                rMax = s.bwMbSec;
            }
            if (rMin == -1 || rMin > s.bwMbSec) {
                rMin = s.bwMbSec;
            }
            
            // cumulative bw
            if (rAvg == -1) {
                rAvg = s.bwMbSec;
            } else {
                int n = s.sampleNum;
                rAvg = (((double)(n-1) * rAvg) + s.bwMbSec) / (double)n;
            }
            
            // cumulative access time
            if (rAcc == -1) {
                rAcc = s.accessTimeMs;
            } else {
                int n = s.sampleNum;
                rAcc = (((double)(n - 1) * rAcc) + s.accessTimeMs) / (double)n;
            }
            
            s.cumAvg = rAvg;
            s.cumMax = rMax;
            s.cumMin = rMin;
            s.cumAccTimeMs = rAcc;
        }
    }
    
    static public void resetSequence() {
        nextSampleNumber = 1;
    }
    
    static public void resetTestData() {
        nextSampleNumber = 1;
        wAvg = -1;
        wMax = -1;
        wMin = -1;
        wAcc = -1;
        rAvg = -1;
        rMax = -1;
        rMin = -1;
        rAcc = -1;
    }
    
    /**
     * Get a string summary of current drive capacity info
     * @return String summarizing the drive information.
     */
    static public String getDriveInfo() {
        if (locationDir == null) {
            return "Location has not been selected";
        }
        
        String driveModel = Util.getDriveModel(locationDir);
        String partitionId = Util.getPartitionId(locationDir.toPath());
        DiskUsageInfo usageInfo = new DiskUsageInfo(); // init to prevent null ref
        try {
            usageInfo = Util.getDiskUsage(locationDir.toString());
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, null, ex);
        }
        return driveModel + " - " + partitionId + ": " + usageInfo.getUsageTitleDisplay();
    }
    
    /**
     * This sets the location directory and configures the data directory within it.
     * @param directory the dir to store 
     */
    static public void setLocationDir(File directory) {
        locationDir = directory;
        dataDir = new File (locationDir.getAbsolutePath() + File.separator + DATADIRNAME);
    }
}
