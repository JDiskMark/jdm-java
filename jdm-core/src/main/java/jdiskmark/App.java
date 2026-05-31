package jdiskmark;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

import javax.swing.SwingUtilities;

import jdiskmark.App.IoEngine;
import jdiskmark.App.SectorAlignment;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;

/**
 * Primary class for global variables.
 */
public class App {
    public static final String VERSION = getVersion();
    public static final String APP_CACHE_DIR_NAME = System.getProperty("user.home") + File.separator + ".jdm"
            + File.separator + VERSION;
    public static final File APP_CACHE_DIR = new File(APP_CACHE_DIR_NAME);
    public static final String PROPERTIES_FILENAME = "jdm.properties";
    public static final File PROPERTIES_FILE = new File(APP_CACHE_DIR_NAME + File.separator + PROPERTIES_FILENAME);
    public static final String BUILD_TOKEN_FILENAME = "build.properties";
    public static final String DATADIRNAME = "jdm-data";
    public static final String SLASH_DATADIRNAME = "/" + DATADIRNAME;
    public static final String ESBL_EXE = "EmptyStandbyList.exe";
    // error messages
    public static final String LOCATION_NOT_SELECTED_ERROR = "Location has not been selected";
    // Standard Binary Units (Power of 2), longs to avoid overflow
    public static final long KILOBYTE = 1_024L;
    public static final long MEGABYTE = 1_024L * KILOBYTE;
    public static final long GIGABYTE = 1_024L * MEGABYTE;
    // numeric constants
    public static final int IDLE_STATE = 0;
    public static final int DISK_TEST_STATE = 1;

    // command line or graphical mode
    public enum Mode {
        CLI, GUI
    }

    // benchmark state
    public static enum State {
        IDLE_STATE, DISK_TEST_STATE
    };

    // io api, modern introduced w jdk 25 lts
    public enum IoEngine {
        MODERN("Modern (FFM API)"),
        LEGACY("Legacy (RandomAccessFile)");

        private final String display;

        IoEngine(String label) {
            this.display = label;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public enum SectorAlignment {
        NONE(-1, "None (OS Default)"),
        ALIGN_512(512, "512 B (Legacy)"),
        ALIGN_4K(4096, "4 KB (Standard)"),
        ALIGN_8K(8192, "8 KB (Enterprise)"),
        ALIGN_16K(16384, "16 KB (High-End)"),
        ALIGN_64K(65536, "64 KB (RAID/Stripe)");

        public final int bytes;
        public final String display;

        SectorAlignment(int bytes, String label) {
            this.bytes = bytes;
            this.display = label;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    /**
     * Branding icon variants for the application window, taskbar, and installer.
     * Each variant declares the PNG sizes available in jdm-core resources.
     * Change {@link #activeIcon} to switch the icon across all display contexts.
     */
    public enum AppIcon {
        /** Blue/orange circle — the beta brand. Single resolution. */
        BETA(new String[]{"/icons/icon-jdm-beta.png"}),
        /** Custom JDiskMark turtle logo — the default project brand. */
        TURTLE(new String[]{
            "/icons/jdm-turtle-logo-16x16.png",
            "/icons/jdm-turtle-logo-20x20.png",
            "/icons/jdm-turtle-logo-24x24.png",
            "/icons/jdm-turtle-logo-32x32.png",
            "/icons/jdm-turtle-logo-40x40.png",
            "/icons/jdm-turtle-logo-48x48.png",
            "/icons/jdm-turtle-logo-64x64.png",
            "/icons/jdm-turtle-logo-96x96.png",
            "/icons/jdm-turtle-logo-128x128.png",
            "/icons/jdm-turtle-logo-256x256.png",
            "/icons/jdm-turtle-logo-512x512.png",
            "/icons/jdm-turtle-logo-1024x1024.png"
        }),
        /** Duke, the BSD-licensed Java mascot from the OpenJDK project. */
        DUKE(new String[]{"/icons/icon-duke.png"});

        /** All resource paths for this icon variant, from smallest to largest. */
        public final String[] resourcePaths;

        AppIcon(String[] resourcePaths) {
            this.resourcePaths = resourcePaths;
        }

        /**
         * Load all available sizes as a list of Images for use with
         * {@link java.awt.Window#setIconImages(java.util.List)}.
         * Java picks the best-fit size per display context (title bar, taskbar, Alt+Tab).
         * Missing resources are silently skipped.
         */
        public java.util.List<java.awt.Image> loadAll() {
            java.util.List<java.awt.Image> images = new java.util.ArrayList<>();
            for (String path : resourcePaths) {
                try (java.io.InputStream is = App.class.getResourceAsStream(path)) {
                    if (is != null) {
                        images.add(new javax.swing.ImageIcon(is.readAllBytes()).getImage());
                    }
                } catch (java.io.IOException e) {
                    java.util.logging.Logger.getLogger(App.class.getName()).log(
                            java.util.logging.Level.WARNING, "Could not load icon: " + path, e);
                }
            }
            return images;
        }

        /**
         * Load the largest available size as an ImageIcon (used by the About dialog).
         * Returns {@code null} if no resource is found.
         */
        public javax.swing.ImageIcon load() {
            String path = resourcePaths[resourcePaths.length - 1];
            try (java.io.InputStream is = App.class.getResourceAsStream(path)) {
                if (is == null) {
                    java.util.logging.Logger.getLogger(App.class.getName()).warning(
                            "Icon resource not found: " + path);
                    return null;
                }
                return new javax.swing.ImageIcon(is.readAllBytes());
            } catch (java.io.IOException e) {
                java.util.logging.Logger.getLogger(App.class.getName()).log(
                        java.util.logging.Level.WARNING, "Could not load icon: " + path, e);
                return null;
            }
        }

        /**
         * Load the best pre-rendered PNG at or nearest to {@code targetSize} pixels.
         * Prefers the smallest size that is &gt;= targetSize; falls back to the largest
         * available. For single-resolution variants the only image is returned as-is.
         * Returns {@code null} if no resource is found.
         */
        public javax.swing.ImageIcon loadSize(int targetSize) {
            // Parse pixel widths from filenames like "/icons/jdm-turtle-logo-256x256.png".
            // For paths without a size suffix (e.g. "/icons/icon-jdm-beta.png") the regex
            // won't match and the path is treated as an unknown size.
            java.util.regex.Pattern sizePattern = java.util.regex.Pattern.compile("-(\\d+)x\\d+\\.png$");
            String bestPath = resourcePaths[resourcePaths.length - 1]; // default: largest
            int bestDiff = Integer.MAX_VALUE;
            for (String path : resourcePaths) {
                java.util.regex.Matcher m = sizePattern.matcher(path);
                if (m.find()) {
                    int size = Integer.parseInt(m.group(1));
                    int diff = size - targetSize;
                    // Prefer smallest size >= targetSize; accept smaller only if nothing larger found.
                    if (diff >= 0 && diff < bestDiff) {
                        bestDiff = diff;
                        bestPath = path;
                    }
                }
            }
            try (java.io.InputStream is = App.class.getResourceAsStream(bestPath)) {
                if (is == null) {
                    java.util.logging.Logger.getLogger(App.class.getName()).warning(
                            "Icon resource not found: " + bestPath);
                    return null;
                }
                return new javax.swing.ImageIcon(is.readAllBytes());
            } catch (java.io.IOException e) {
                java.util.logging.Logger.getLogger(App.class.getName()).log(
                        java.util.logging.Level.WARNING, "Could not load icon: " + bestPath, e);
                return null;
            }
        }
    }

    /**
     * Active branding icon — change this single line to switch the icon
     * used for the window title bar, taskbar, and About dialog.
     */
    public static AppIcon activeIcon = AppIcon.TURTLE;

    // application mode
    public static Mode mode = Mode.CLI;

    // Single-instance enforcement via NIO FileLock.
    // The OS releases this lock automatically when the JVM exits by any means
    // (normal, exception, SIGKILL, OOM crash) — stale locks are impossible.
    // Kept as static fields so GC never closes the channel while the app runs.
    private static java.nio.channels.FileChannel instanceLockChannel;
    private static java.nio.channels.FileLock instanceLock;
    // elevated priviledges
    public static boolean isRoot = false;
    public static boolean isAdmin = false;
    // system info
    public static String os;
    public static String arch;
    public static String processorName;
    public static String jdk;
    public static String username;
    // benchmark options
    public static Properties p;
    public static File locationDir = null;
    public static File exportPath = null;
    public static File dataDir = null; // refactor to dataPath after all branches merged
    public static File testFile = null; // still used for cli
    public static boolean autoSave = false;
    public static boolean sharePortal = false;
    // True if sharePortal was enabled in the last session; used to offer a
    // one-click
    // re-enable prompt at startup rather than silently resuming network activity.
    public static boolean sharePortalPreviouslyEnabled = false;
    public static boolean verbose = false; // affects cli output
    public static boolean multiFile = true;
    public static boolean autoRemoveData = false;
    public static boolean autoReset = true;
    public static boolean directEnable = false;
    public static boolean writeSyncEnable = false;

    // benchmark io options
    public static IoEngine ioEngine = IoEngine.MODERN;
    public static SectorAlignment sectorAlignment = SectorAlignment.ALIGN_4K;
    // benchmark configuration
    public static BenchmarkProfile activeProfile = BenchmarkProfile.QUICK_TEST;
    public static boolean profileModified = false;
    public static BenchmarkType benchmarkType = BenchmarkType.WRITE;
    public static BlockSequence blockSequence = BlockSequence.SEQUENTIAL;
    public static int numOfSamples = 200; // desired number of samples
    public static int numOfBlocks = 32; // desired number of blocks
    public static int blockSizeKb = 512; // size of a block in KBs
    public static int numOfThreads = 1; // number of threads
    // active benchmark state
    public static State state = State.IDLE_STATE;
    public static int nextSampleNumber = 1; // number of the next sample
    public static double wMax = -1, wMin = -1, wAvg = -1, wAcc = -1;
    public static double rMax = -1, rMin = -1, rAvg = -1, rAcc = -1;
    public static long wIops = -1;
    public static long rIops = -1;
    // benchmark result containers
    public static BenchmarkWorker worker = null;
    public static Future<Benchmark> cliResult = null;
    // completed benchmarks and operations
    public static Benchmark benchmark; // last or loaded benchmark
    public static BenchmarkOperation operation; // last loaded operation
    public static HashMap<String, Benchmark> benchmarks = new LinkedHashMap<>();
    public static HashMap<String, BenchmarkOperation> operations = new LinkedHashMap<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static String formatWithTimestamp(String message) {
        return DATE_FORMATTER.format(LocalDateTime.now()) + ": " + message;
    }

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
                // App.verbose = true; // force verbose to true
                if (!acquireInstanceLock()) {
                    return; // another instance is already running — exit
                }
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
     * 
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
        } else if (Files.exists(Paths.get(BUILD_TOKEN_FILENAME))) { // ide and zip release
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

        GcDetector.printActive();

        username = System.getProperty("user.name");

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
            Gui.init();
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
                public void run() {
                    App.saveConfig();
                }
            });
            // If portal upload was active last session, offer a one-click re-enable.
            // This avoids silent outbound network activity while keeping dev workflow
            // smooth.
            if (sharePortalPreviouslyEnabled) {
                javax.swing.SwingUtilities.invokeLater(App::promptResumePortalUpload);
            }
        }
    }

    /**
     * Attempts to acquire an OS-level advisory lock on a file in the per-version
     * cache directory. Called once at startup in GUI mode, before {@link #init()}.
     *
     * <p>The lock is held by a {@link java.nio.channels.FileLock} whose lifecycle
     * is tied to the JVM process: the OS kernel releases it automatically when the
     * process exits by <em>any</em> means (normal exit, uncaught exception,
     * {@code SIGKILL}, OOM crash). Stale lock files left behind after a crash are
     * therefore impossible — the next launch will always succeed.
     *
     * <p>If another instance already holds the lock a user-friendly dialog is shown
     * and the method returns {@code false}, allowing {@code main()} to exit cleanly
     * without opening any window or touching the Derby database.
     *
     * @return {@code true} if the lock was acquired and this instance may continue;
     *         {@code false} if another instance is running (caller should exit).
     */
    public static boolean acquireInstanceLock() {
        // Ensure the cache directory exists before we try to create the lock file.
        if (!APP_CACHE_DIR.exists()) {
            APP_CACHE_DIR.mkdirs();
        }
        java.io.File lockFile = new java.io.File(APP_CACHE_DIR, "jdm.lock");
        try {
            // Open (or create) the lock file. StandardOpenOption.CREATE ensures the
            // file exists; WRITE is required for FileLock.
            instanceLockChannel = java.nio.channels.FileChannel.open(
                    lockFile.toPath(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE);
            // tryLock() returns null (non-blocking) if another process holds the lock.
            instanceLock = instanceLockChannel.tryLock();
        } catch (java.io.IOException e) {
            Logger.getLogger(App.class.getName()).log(Level.WARNING,
                    "Could not open instance lock file: " + lockFile, e);
            // If we cannot even open the file (e.g. permissions), allow the app to
            // start rather than refusing to run on a technicality.
            return true;
        }

        if (instanceLock == null) {
            // Lock is held by another process — show a concise dialog, then bail.
            try {
                instanceLockChannel.close();
            } catch (java.io.IOException ignored) {}
            instanceLockChannel = null;

            // Show the dialog on the EDT (we have no window yet, so null parent is fine).
            javax.swing.SwingUtilities.invokeLater(() -> {
                javax.swing.JOptionPane.showMessageDialog(
                        null,
                        "JDiskMark is already running.\n"
                        + "Only one instance can be open at a time.",
                        "JDiskMark — Already Running",
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                System.exit(0);
            });
            return false;
        }
        return true;
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

    /**
     * Offers a one-click prompt to re-enable portal upload when it was active
     * in the previous session. Called after the main window is visible so the
     * dialog has a proper parent. This avoids silent outbound network activity
     * while keeping the dev workflow convenient (no password re-entry required).
     */
    public static void promptResumePortalUpload() {
        int choice = javax.swing.JOptionPane.showConfirmDialog(
                Gui.mainFrame,
                "Portal upload was enabled in your last session.\nResume uploading benchmarks to "
                        + Portal.getUploadUrl() + "?",
                "Resume Portal Upload?",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE);
        if (choice == javax.swing.JOptionPane.YES_OPTION) {
            sharePortal = true;
            msg("Portal upload resumed.");
        } else {
            sharePortal = false;
            sharePortalPreviouslyEnabled = false; // clear so we don't prompt again next launch
            msg("Portal upload not resumed.");
            saveConfig(); // persist the cleared state
        }
        // sync the menu checkbox to reflect the resolved state
        if (Gui.mainFrame != null) {
            Gui.mainFrame.loadPropertiesConfig();
        }
    }

    public static void loadProfile(BenchmarkProfile profile) {
        try {
            activeProfile = profile;
            profileModified = false;

            // TODO: later relocate into a BenchmarkConfiguration.java
            benchmarkType = profile.getBenchmarkType();
            blockSequence = profile.getBlockSequence();
            numOfThreads = profile.getNumThreads();
            numOfSamples = profile.getNumSamples();
            numOfBlocks = profile.getNumBlocks();
            blockSizeKb = profile.getBlockSizeKb();
            ioEngine = profile.getIoEngine();
            directEnable = profile.isDirectEnable();
            writeSyncEnable = profile.isWriteSyncEnable();
            sectorAlignment = profile.getSectorAlignment();
            multiFile = profile.isMultiFile();
        } finally {
            saveConfig();
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
        if (p == null) {
            p = new Properties();
        }
        try {
            InputStream in = new FileInputStream(PROPERTIES_FILE);
            p.load(in);
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }

        // configure settings from properties
        String value;

        // Never silently re-enable portal upload on startup — network activity must
        // always be explicitly user-confirmed each session. We remember the previous
        // state only to offer a convenient one-click re-enable prompt.
        value = p.getProperty("sharePortal", "false");
        sharePortalPreviouslyEnabled = Boolean.parseBoolean(value);
        sharePortal = false; // always start disabled; prompt offered after window visible

        Portal.uploadResourceLocator = p.getProperty("uploadResourceLocator", Portal.uploadResourceLocator);
        Portal.uploadProtocol = p.getProperty("uploadProtocol", Portal.uploadProtocol);

        value = p.getProperty("activeProfile", activeProfile.name());
        BenchmarkProfile previousActiveProfile = activeProfile;
        try {
            activeProfile = BenchmarkProfile.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            Logger.getLogger(App.class.getName()).log(
                    Level.WARNING,
                    "Invalid activeProfile value in properties file: \"{0}\". Falling back to default: {1}",
                    new Object[] { value, previousActiveProfile.name() });
            activeProfile = previousActiveProfile;
        }

        value = p.getProperty("profileModified", String.valueOf(profileModified));
        profileModified = Boolean.parseBoolean(value);

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
        } catch (IllegalArgumentException e) {
            Logger.getLogger(App.class.getName()).log(
                    Level.WARNING,
                    "Invalid ioEngine value in properties: " + value + ", using default: " + ioEngine.name(),
                    e);
        }

        value = p.getProperty("writeSyncEnable", String.valueOf(writeSyncEnable));
        writeSyncEnable = Boolean.parseBoolean(value);

        value = p.getProperty("directEnable", String.valueOf(directEnable));
        directEnable = Boolean.parseBoolean(value);

        value = p.getProperty("sectorAlignment", sectorAlignment.name());
        try {
            sectorAlignment = SectorAlignment.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            Logger.getLogger(App.class.getName()).log(
                    Level.WARNING,
                    "Invalid sectorAlignment value in properties: \"{0}\", using default: {1}",
                    new Object[] { value, sectorAlignment.name() });
        }

        value = p.getProperty("gcRetryEnabled", String.valueOf(GcDetector.gcRetryEnabled));
        GcDetector.gcRetryEnabled = Boolean.parseBoolean(value);

        value = p.getProperty("gcHintsEnabled", String.valueOf(GcDetector.gcHintsEnabled));
        GcDetector.gcHintsEnabled = Boolean.parseBoolean(value);

        value = p.getProperty("theme", Gui.theme.name());
        try {
            Gui.theme = Gui.Theme.valueOf(value);
        } catch (IllegalArgumentException e) {
            Logger.getLogger(App.class.getName()).log(
                    Level.WARNING,
                    "Invalid theme value in properties: \"{0}\", using default: {1}",
                    new Object[] { value, Gui.theme.name() });
        }

        value = p.getProperty("palette", String.valueOf(Gui.palette));
        Gui.palette = Gui.Palette.valueOf(value);

        value = p.getProperty("showMaxMin", String.valueOf(Gui.showMaxMin));
        Gui.showMaxMin = Boolean.parseBoolean(value);

        value = p.getProperty("showDriveAccess", String.valueOf(Gui.showDriveAccess));
        Gui.showDriveAccess = Boolean.parseBoolean(value);

        value = p.getProperty("showSingleOp", String.valueOf(Gui.showSingleOp));
        Gui.showSingleOp = Boolean.parseBoolean(value);
    }

    public static void saveConfig() {
        if (p == null) {
            p = new Properties();
        }

        // configure properties
        p.setProperty("sharePortal", String.valueOf(sharePortal));
        p.setProperty("uploadResourceLocator", Portal.uploadResourceLocator);
        p.setProperty("uploadProtocol", Portal.uploadProtocol);
        p.setProperty("activeProfile", activeProfile.name());
        p.setProperty("profileModified", String.valueOf(profileModified));
        p.setProperty("benchmarkType", benchmarkType.name());
        p.setProperty("multiFile", String.valueOf(multiFile));
        p.setProperty("autoRemoveData", String.valueOf(autoRemoveData));
        p.setProperty("autoReset", String.valueOf(autoReset));
        p.setProperty("blockSequence", blockSequence.name());
        p.setProperty("numOfSamples", String.valueOf(numOfSamples));
        p.setProperty("numOfBlocks", String.valueOf(numOfBlocks));
        p.setProperty("blockSizeKb", String.valueOf(blockSizeKb));
        p.setProperty("numOfThreads", String.valueOf(numOfThreads));
        p.setProperty("ioEngine", ioEngine.name());
        p.setProperty("writeSyncEnable", String.valueOf(writeSyncEnable));
        p.setProperty("directEnable", String.valueOf(directEnable));
        p.setProperty("sectorAlignment", sectorAlignment.name());
        p.setProperty("gcRetryEnabled", String.valueOf(GcDetector.gcRetryEnabled));
        p.setProperty("gcHintsEnabled", String.valueOf(GcDetector.gcHintsEnabled));
        // display properties
        p.setProperty("theme", Gui.theme.name());
        p.setProperty("palette", Gui.palette.name());
        p.setProperty("showMaxMin", String.valueOf(Gui.showMaxMin));
        p.setProperty("showDriveAccess", String.valueOf(Gui.showDriveAccess));
        p.setProperty("showSingleOp", String.valueOf(Gui.showSingleOp));

        // write properties file
        try {
            OutputStream out = new FileOutputStream(PROPERTIES_FILE);
            p.store(out, "JDiskMark " + VERSION + " Properties File");
        } catch (IOException ex) {
            Logger.getLogger(SelectDriveFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Creates a point-in-time snapshot of the current settings.
     * 
     * @return the configuration for benchmarking
     */
    public static BenchmarkConfig getConfig() {
        BenchmarkConfig config = new BenchmarkConfig();
        config.appVersion = VERSION;
        config.profile = activeProfile;
        config.profileModified = profileModified;
        config.benchmarkType = benchmarkType;
        config.blockOrder = blockSequence;
        config.numBlocks = numOfBlocks;
        config.blockSize = (long) blockSizeKb * KILOBYTE;
        config.numSamples = numOfSamples;
        config.numThreads = numOfThreads;
        config.txSize = targetOperationTxSizeKb();
        config.ioEngine = ioEngine;
        config.directIoEnabled = directEnable;
        config.writeSyncEnabled = writeSyncEnable;
        config.sectorAlignment = sectorAlignment;
        config.gcRetryEnabled = GcDetector.gcRetryEnabled;
        config.gcHintsEnabled = GcDetector.gcHintsEnabled;
        config.multiFileEnabled = multiFile;
        config.testDir = dataDir.getAbsolutePath();
        return config;
    }

    public static boolean hasReadOperation() {
        return benchmarkType == BenchmarkType.READ || benchmarkType == BenchmarkType.READ_WRITE;
    }

    public static boolean hasWriteOperation() {
        return benchmarkType == BenchmarkType.WRITE || benchmarkType == BenchmarkType.READ_WRITE;
    }

    public static String getConfigString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Config for JDiskMark ").append(VERSION).append('\n');
        sb.append("readTest: ").append(hasReadOperation()).append('\n');
        sb.append("writeTest: ").append(hasWriteOperation()).append('\n');
        sb.append("locationDir: ").append(locationDir).append('\n');
        sb.append("multiFile: ").append(multiFile).append('\n');
        sb.append("autoRemoveData: ").append(autoRemoveData).append('\n');
        sb.append("autoReset: ").append(autoReset).append('\n');
        sb.append("blockSequence: ").append(blockSequence).append('\n');
        sb.append("numOfFiles: ").append(numOfSamples).append('\n');
        sb.append("numOfBlocks: ").append(numOfBlocks).append('\n');
        sb.append("blockSizeKb: ").append(blockSizeKb).append('\n');
        sb.append("numOfThreads: ").append(numOfThreads).append('\n');
        sb.append("benchmarkType: ").append(benchmarkType).append('\n');
        sb.append("ioEngine: ").append(ioEngine).append('\n');
        sb.append("writeSyncEnable: ").append(writeSyncEnable).append('\n');
        sb.append("directEnable: ").append(directEnable).append('\n');
        sb.append("palette: ").append(Gui.palette).append('\n');
        sb.append("showMaxMin: ").append(Gui.showMaxMin).append('\n');
        return sb.toString();
    }

    public static void loadBenchmarks() {
        if (verbose) {
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
        benchmarks.clear(); // clear the cache
        loadBenchmarks();
    }

    public static void err(String message) {
        String formattedMsg = formatWithTimestamp(message);
        switch (mode) {
            case GUI -> {
                System.err.println(formattedMsg);
                if (Gui.mainFrame != null) {
                    SwingUtilities.invokeLater(() -> Gui.mainFrame.msg(formattedMsg));
                }
            }
            case CLI -> System.err.println(formattedMsg);
        }
    }

    public static void msg(String message) {
        String formattedMsg = formatWithTimestamp(message);
        switch (mode) {
            case GUI -> {
                if (Gui.mainFrame != null) {
                    Gui.mainFrame.msg(formattedMsg);
                } else {
                    System.out.println(formattedMsg);
                }
            }
            case CLI -> System.out.println(formattedMsg);
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

        // clear the selected benchmark and operation
        App.benchmark = null;
        App.operation = null;

        if (!validateTargetDirectory(locationDir, false)) {
            return;
        }

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
        dataDir = new File(locationDir.getAbsolutePath() + File.separator + DATADIRNAME);

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
        if (dataDir.exists() == false) {
            dataDir.mkdirs();
        }

        // 7. start benchmark job thread
        switch (mode) {
            case GUI -> {
                worker = new BenchmarkWorker();
                worker.addPropertyChangeListener(new Gui.WorkerProgressListener());
                worker.execute();
            }
            case CLI -> {
                ExecutorService executor = Executors.newFixedThreadPool(1);
                BenchmarkCallable benchmarkCallable = new BenchmarkCallable();
                cliResult = executor.submit(benchmarkCallable);
            }
        }
    }

    // currently only used by cli implementation, assess if gui switch should
    // be removed or a common blocking pattern is recommended
    public static void waitBenchmarkDone() {
        switch (mode) {
            case GUI -> {
                try {
                    benchmark = worker.get();
                } catch (InterruptedException | ExecutionException e) {
                    Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
                }
            }
            case CLI -> {
                try {
                    benchmark = cliResult.get();
                } catch (InterruptedException | ExecutionException e) {
                    Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
                }
            }
        }
        if (benchmark != null) {
            msg(benchmark.toResultString());
        }
    }

    public static long targetSampleSizeKb() {
        return (long) blockSizeKb * numOfBlocks;
    }

    public static long targetOperationTxSizeKb() {
        return (long) blockSizeKb * numOfBlocks * numOfSamples;
    }

    public static long targetBenchmarkTxSizeKb() {
        long operationTxSize = targetOperationTxSizeKb();
        switch (benchmarkType) {
            case WRITE -> {
                return operationTxSize;
            }
            case READ, READ_WRITE -> {
                return 2L * operationTxSize;
            }
            default -> throw new IllegalStateException("Unexpected value: " + benchmarkType);
        }
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
                wAvg = (((double) (n - 1) * wAvg) + s.bwMbSec) / (double) n;
            }
            // cumulative access time
            if (wAcc == -1) {
                wAcc = s.accessTimeMs;
            } else {
                int n = s.sampleNum;
                wAcc = (((double) (n - 1) * wAcc) + s.accessTimeMs) / (double) n;
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
                rAvg = (((double) (n - 1) * rAvg) + s.bwMbSec) / (double) n;
            }
            // cumulative access time
            if (rAcc == -1) {
                rAcc = s.accessTimeMs;
            } else {
                int n = s.sampleNum;
                rAcc = (((double) (n - 1) * rAcc) + s.accessTimeMs) / (double) n;
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
     * 
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
     * 
     * @param directory the dir to store
     */
    static public void setLocationDir(File directory) {
        locationDir = directory;
        dataDir = new File(locationDir.getAbsolutePath() + File.separator + DATADIRNAME);
    }
}
