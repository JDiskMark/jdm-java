
package jdiskmark;

import static jdiskmark.Benchmark.BenchmarkType;
import static jdiskmark.Benchmark.BlockSequence;
import static jdiskmark.DriveAccessChecker.validateTargetDirectory;

import picocli.CommandLine;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker.StateValue;

/**
 * Primary class for global variables.
 */
public class App {
    public static final String VERSION = getVersion();
    public static final String APP_CACHE_DIR_NAME = System.getProperty("user.home") + File.separator + ".jdm" + File.separator + VERSION;
    public static final File APP_CACHE_DIR = new File(APP_CACHE_DIR_NAME);
    public static final String PROPERTIES_FILENAME = "jdm.properties";
    public static final File PROPERTIES_FILE = new File(APP_CACHE_DIR_NAME + File.separator + PROPERTIES_FILENAME);
    public static final String BUILD_TOKEN_FILENAME = "build.properties";
    public static final String DATADIRNAME = "jdm-data";
    public static final String SLASH_DATADIRNAME = "/" + DATADIRNAME;
    public static final String ESBL_EXE = "EmptyStandbyList.exe";
    // error messages
    public static final String LOCATION_NOT_SELECTED_ERROR = "Location has not been selected";
    // numeric constants
    public static final int MEGABYTE = 1024 * 1024;
    public static final int KILOBYTE = 1024;
    public static final int IDLE_STATE = 0;
    public static final int DISK_TEST_STATE = 1;
    // benchmark state
    public static enum State { IDLE_STATE, DISK_TEST_STATE };
    public static State state = State.IDLE_STATE;
    // app is in command line or graphical mode
    public enum Mode { CLI, GUI }
    public static Mode mode = Mode.CLI;
    // io api, modern introduced w jdk 25 lts
    public enum IoEngine {
        MODERN("Modern (FFM API)"),
        LEGACY("Legacy (RandomAccessFile)");
        private final String label;
        IoEngine(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public static IoEngine ioEngine = IoEngine.MODERN;
    
    public enum SectorAlignment {
        ALIGN_512(512, "512 B (Legacy)"),
        ALIGN_4K(4096, "4 KB (Standard)"),
        ALIGN_8K(8192, "8 KB (Enterprise)"),
        ALIGN_16K(16384, "16 KB (High-End)"),
        ALIGN_64K(65536, "64 KB (RAID/Stripe)");

        public final int bytes;
        public final String label;

        SectorAlignment(int bytes, String label) {
            this.bytes = bytes;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
    public static SectorAlignment sectorAlignment = SectorAlignment.ALIGN_4K;
    
    // member
    public static Properties p;
    public static File locationDir = null;
    public static File exportPath = null;
    public static File dataDir = null; // refactor to dataPath after all branches merged
    public static File testFile = null; // still used for cli
    // system info
    public static String os;
    public static String arch;
    public static String processorName;
    public static String jdk;
    // elevated priviledges
    public static boolean isRoot = false;
    public static boolean isAdmin = false;
    // benchmark options
    public static boolean autoSave = false;
    public static boolean verbose = false; // affects cli output
    public static boolean multiFile = true;
    public static boolean autoRemoveData = false;
    public static boolean autoReset = true;
    public static boolean showMaxMin = true;
    public static boolean showDriveAccess = true;
    public static boolean directEnable = false;
    public static boolean writeSyncEnable = false;
    // benchmark configuration
    public static BenchmarkProfile activeProfile = BenchmarkProfile.QUICK_TEST;
    public static BenchmarkType benchmarkType = BenchmarkType.WRITE;
    public static BlockSequence blockSequence = BlockSequence.SEQUENTIAL;
    public static int numOfSamples = 200;   // desired number of samples
    public static int numOfBlocks = 32;     // desired number of blocks
    public static int blockSizeKb = 512;    // size of a block in KBs
    public static int numOfThreads = 1;     // number of threads
    // benchmark result containers
    public static BenchmarkWorker worker = null;
    public static Future<Benchmark> cliResult = null;
    // benchmark primitives
    public static int nextSampleNumber = 1;   // number of the next sample
    public static double wMax = -1, wMin = -1, wAvg = -1, wAcc = -1;
    public static double rMax = -1, rMin = -1, rAvg = -1, rAcc = -1;
    public static long wIops = -1;
    public static long rIops = -1;
    // benchmarks and operations
    public static HashMap<String, Benchmark> benchmarks = new LinkedHashMap<>();
    public static HashMap<String, BenchmarkOperation> operations = new LinkedHashMap<>();
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        // no arguments = gui mode, otherwise cmd line interface
        mode = (args.length == 0) ? Mode.GUI : Mode.CLI;
        int exitCode = 0;
        
        switch (mode) {
            case Mode.GUI -> {
                App.autoSave = true;
                //App.verbose = true; // force verbose to true
                java.awt.EventQueue.invokeLater(App::init);
                return;
            }
            case Mode.CLI -> {
                Cli cli = new Cli();
                exitCode = new CommandLine(cli)
                        .setUsageHelpWidth(100)
                        .execute(args); // run command & exit w its code
            }
        }
        // If execution completes, exit the process.
        System.exit(exitCode);
    }
    
    /**
     * Get the version from the build properties. Defaults to 0.0 if not found.
     * @return 
     */
    public static String getVersion() {
        Properties bp = new Properties();
        String version = "0.0";
        InputStream input = App.class.getResourceAsStream("/META-INF/build.properties");
        if (input != null) {
            try (input) {
                bp.load(input);
            } catch (IOException e) {
                Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
            }
        } else if (Files.exists(Paths.get(BUILD_TOKEN_FILENAME))) {            // ide and zip release
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
        
        os = System.getProperty("os.name");
        arch = System.getProperty("os.arch");
        processorName = Util.getProcessorName();
        jdk = Util.getJvmInfo();
        
        checkPermission();
        if (!APP_CACHE_DIR.exists()) {
            APP_CACHE_DIR.mkdirs();
        }
        
        if (mode == Mode.GUI) {
            loadConfig();
        }
        
        // initialize data dir if necessary
        if (locationDir == null) {
            locationDir = new File(System.getProperty("user.home"));
            dataDir = new File(locationDir.getAbsolutePath()
                    + File.separator + DATADIRNAME);
        }
        
        if (mode == Mode.GUI) {
            Gui.configureLaf();
            Gui.mainFrame = new MainFrame();
            Gui.runPanel.hideFirstColumn();
            Gui.selFrame = new SelectDriveFrame();
            System.out.println(getConfigString());
            Gui.mainFrame.loadPropertiesConfig();
            Gui.mainFrame.setLocationRelativeTo(null);
            Gui.progressBar = Gui.mainFrame.getProgressBar();
        }
        
        if (App.autoSave) {
            // configure the embedded DB in .jdm
            System.setProperty("derby.system.home", APP_CACHE_DIR_NAME);
            loadBenchmarks();
        }

        if (mode == Mode.GUI) {
            // load current drive
            Gui.updateDiskInfo();
            Gui.mainFrame.setVisible(true);
            // save configuration on exit...
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() { App.saveConfig(); }
            });
        }
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

        value = p.getProperty("activeProfile", activeProfile.name());
        activeProfile = BenchmarkProfile.valueOf(value.toUpperCase());
        
        value = p.getProperty("benchmarkType", String.valueOf(benchmarkType));
        benchmarkType = BenchmarkType.valueOf(value.toUpperCase());
        
        value = p.getProperty("multiFile", String.valueOf(multiFile));
        multiFile = Boolean.parseBoolean(value);

        value = p.getProperty("autoRemoveData", String.valueOf(autoRemoveData));
        autoRemoveData = Boolean.parseBoolean(value);

        value = p.getProperty("autoReset", String.valueOf(autoReset));
        autoReset = Boolean.parseBoolean(value);

        value = p.getProperty("blockSequence", String.valueOf(blockSequence));
        blockSequence = BlockSequence.valueOf(value.toUpperCase());

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

        value = p.getProperty("numOfThreads", String.valueOf(numOfThreads));
        numOfThreads = Integer.parseInt(value);

        value = p.getProperty("ioEngine", ioEngine.name());
        try {
            ioEngine = IoEngine.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(App.class.getName()).log(
                    Level.WARNING,
                    "Invalid ioEngine value in properties: " + value + ", using default: " + ioEngine.name(),
                    ex
            );
        }
        
        value = p.getProperty("writeSyncEnable", String.valueOf(writeSyncEnable));
        writeSyncEnable = Boolean.parseBoolean(value);
        
        value = p.getProperty("directEnable", String.valueOf(directEnable));
        directEnable = Boolean.parseBoolean(value);
        
        value = p.getProperty("sectorAlignment", sectorAlignment.name());
        sectorAlignment = SectorAlignment.valueOf(value.toUpperCase());

        value = p.getProperty("palette", String.valueOf(Gui.palette));
        Gui.palette = Gui.Palette.valueOf(value);
    }
    
    public static void saveConfig() {
        if (p == null) { p = new Properties(); }
        
        // configure properties
        p.setProperty("activeProfile", activeProfile.name());
        p.setProperty("benchmarkType", benchmarkType.name());
        p.setProperty("multiFile", String.valueOf(multiFile));
        p.setProperty("autoRemoveData", String.valueOf(autoRemoveData));
        p.setProperty("autoReset", String.valueOf(autoReset));
        p.setProperty("blockSequence", blockSequence.name());
        p.setProperty("showMaxMin", String.valueOf(showMaxMin));
        p.setProperty("showDriveAccess", String.valueOf(showDriveAccess));
        p.setProperty("numOfSamples", String.valueOf(numOfSamples));
        p.setProperty("numOfBlocks", String.valueOf(numOfBlocks));
        p.setProperty("blockSizeKb", String.valueOf(blockSizeKb));
        p.setProperty("numOfThreads", String.valueOf(numOfThreads));
        p.setProperty("ioEngine", ioEngine.name());
        p.setProperty("writeSyncEnable", String.valueOf(writeSyncEnable));
        p.setProperty("directEnable", String.valueOf(directEnable));
        p.setProperty("sectorAlignment", sectorAlignment.name());
        p.setProperty("palette", Gui.palette.name());

        // write properties file
        try {
            OutputStream out = new FileOutputStream(PROPERTIES_FILE);
            p.store(out, "JDiskMark " + VERSION + " Properties File");
        } catch (IOException ex) {
            Logger.getLogger(SelectDriveFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static boolean isReadEnabled() {
        return benchmarkType == BenchmarkType.READ || benchmarkType == BenchmarkType.READ_WRITE;
    }

    public static boolean isWriteEnabled() {
        return benchmarkType == BenchmarkType.WRITE || benchmarkType == BenchmarkType.READ_WRITE;
    }
    
    public static String getConfigString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Config for JDiskMark ").append(VERSION).append('\n');
        sb.append("readTest: ").append(isReadEnabled()).append('\n');
        sb.append("writeTest: ").append(isWriteEnabled()).append('\n');
        sb.append("locationDir: ").append(locationDir).append('\n');
        sb.append("multiFile: ").append(multiFile).append('\n');
        sb.append("autoRemoveData: ").append(autoRemoveData).append('\n');
        sb.append("autoReset: ").append(autoReset).append('\n');
        sb.append("blockSequence: ").append(blockSequence).append('\n');
        sb.append("showMaxMin: ").append(showMaxMin).append('\n');
        sb.append("numOfFiles: ").append(numOfSamples).append('\n');
        sb.append("numOfBlocks: ").append(numOfBlocks).append('\n');
        sb.append("blockSizeKb: ").append(blockSizeKb).append('\n');
        sb.append("numOfThreads: ").append(numOfThreads).append('\n');
        sb.append("palette: ").append(Gui.palette).append('\n');
        sb.append("benchmarkType: ").append(benchmarkType).append('\n');
        sb.append("ioEngine: ").append(ioEngine).append('\n');
        sb.append("writeSyncEnable: ").append(writeSyncEnable).append('\n');
        sb.append("directEnable: ").append(directEnable).append('\n');
        return sb.toString();
    }
    
    public static void loadBenchmarks() {
        if (App.verbose) {
            System.out.println("loading benchmarks");
        }

        // populate benchmark and operation map w runs from db
        benchmarks.clear();
        operations.clear();
        Benchmark.findAll().stream().forEach((Benchmark run) -> {
            benchmarks.put(run.getStartTimeString(), run);
            for (BenchmarkOperation o : run.getOperations()) {
                operations.put(o.getStartTimeString(), o);
            }
        });

        // populate gui table
        if (mode == Mode.GUI) {
            Gui.runPanel.clearTable();
            for (Benchmark run : benchmarks.values()) {
                Gui.runPanel.addRun(run);
            }
        }
    }
    
    public static void deleteAllBenchmarks() {
        Benchmark.deleteAll();
        benchmarks.clear();
        loadBenchmarks();
    }
    
    // only tested for single delete but should work
    public static void deleteBenchmarks(List<UUID> benchmarkIds) {
        Benchmark.delete(benchmarkIds);
        benchmarks.clear();  // clear the cache
        loadBenchmarks();
    }
    
    public static void msg(String message) {
        switch(mode) {
            case GUI -> Gui.mainFrame.msg(message);
            case CLI -> System.out.println(message);
        }
    }
    public static void err(String message) {
        switch(mode) {
            case GUI -> {
                Gui.mainFrame.msg(message);
                System.err.println(message);
            }
            case CLI -> System.err.println(message);
        }
    }
    public static void cancelBenchmark() {
        if (worker == null) { 
            msg("worker is null abort..."); 
            return;
        }
        worker.cancel(true);
    }
    
    public static void startBenchmark() {

        if (!validateTargetDirectory(locationDir, false))  { return; }
        
        // 1. check that there isn't already a worker in progress
        if (state == State.DISK_TEST_STATE) {
            msg("Test in progress, aborting...");
            return;
        }
        
        // 2. check can write to location
        if (locationDir.canWrite() == false) {
            msg("Selected directory can not be written to... aborting");
            return;
        }
        
        // 3. update state
        state = State.DISK_TEST_STATE;
        if (mode == Mode.GUI) {
            Gui.mainFrame.adjustSensitivity();
        }
        
        // 4. create data dir reference
        dataDir = new File (locationDir.getAbsolutePath() + File.separator + DATADIRNAME);
        
        // 5. remove existing test data if exist
        if (autoRemoveData && dataDir.exists()) {
            if (dataDir.delete()) {
                if (verbose) {
                    msg("removed existing data dir");
                }
            } else {
                msg("unable to remove existing data dir");
            }
        }
        
        // 6. create data dir if not already present
        if (dataDir.exists() == false) { dataDir.mkdirs(); }
        
        // 7. start disk worker thread
        switch (mode) {
            case Mode.GUI -> {
                worker = new BenchmarkWorker();
                worker.addPropertyChangeListener((final var event) -> {
                    switch (event.getPropertyName()) {
                        case "progress" -> {
                            int value = (Integer)event.getNewValue();
                            Gui.progressBar.setValue(value);
                            long kbProcessed = value * App.targetTxSizeKb() / 100;
                            Gui.progressBar.setString(String.valueOf(kbProcessed) + " / " + String.valueOf(App.targetTxSizeKb()));
                        }
                        case "state" -> {
                            switch ((StateValue)event.getNewValue()) {
                                case STARTED -> Gui.progressBar.setString("0 / " + String.valueOf(App.targetTxSizeKb()));
                                case DONE -> {}
                            } // end inner switch
                        }
                    }
                });
                worker.execute();
            }
            case Mode.CLI -> {
                ExecutorService executor = Executors.newFixedThreadPool(1);
                BenchmarkCallable benchmarkCallable = new BenchmarkCallable();
                cliResult = executor.submit(benchmarkCallable);
            }
        }
    }
    
    public static void waitBenchmarkDone() {
        Benchmark benchmark = null;
        switch (mode) {
            case Mode.GUI -> {
                try {
                    benchmark = worker.get();
                } catch (InterruptedException | ExecutionException e) {
                    Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
                }
            }
            case Mode.CLI -> {
                try {
                    benchmark = cliResult.get();
                } catch (InterruptedException | ExecutionException e) {
                    Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
                }
            }
        }
        if (benchmark != null) {
            System.out.println(benchmark.toResultString());
        }
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
            // update sample
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
            // update sample
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
        wIops = -1;
        rAvg = -1;
        rMax = -1;
        rMin = -1;
        rAcc = -1;
        rIops = -1;
    }
    
    /**
     * Get a string summary of current drive capacity info
     * @return String summarizing the drive information.
     */
    static public String getDriveInfo() {
        if (locationDir == null) {
            return LOCATION_NOT_SELECTED_ERROR;
        }
        String driveModel = Util.getDriveModel(locationDir);
        String partitionId = Util.getPartitionId(locationDir.toPath());
        DiskUsageInfo usageInfo;
        try {
            usageInfo = Util.getDiskUsage(locationDir.toString());
        } catch (IOException | InterruptedException ex) {
            usageInfo = new DiskUsageInfo();
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
        return driveModel + " - " + partitionId + ": " + usageInfo.getUsageTitleDisplay();
    }
    
    static public String getDriveModel() {
        if (locationDir == null) {
            return LOCATION_NOT_SELECTED_ERROR;
        }
        return Util.getDriveModel(locationDir);
    }
    
    static public String getDriveCapacity() {
        if (locationDir == null) {
            return LOCATION_NOT_SELECTED_ERROR;
        }
        DiskUsageInfo usageInfo;
        try {
            usageInfo = Util.getDiskUsage(locationDir.toString());
        } catch (IOException | InterruptedException ex) {
            usageInfo = new DiskUsageInfo();
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
        return usageInfo.getUsageTitleDisplay();
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
