package jdiskmark;

import javax.swing.JOptionPane;
import java.io.File;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.metricus.jdm.util.LockedTestData;

public class DriveChecker {

    private static final Logger LOG = Logger.getLogger(DriveChecker.class.getName());

    private static final double SPACE_MARGIN = 1.10;
    private static final long MIN_MARGIN_BYTES = 10L * 1024 * 1024; // 10 MB

    /**
     * Validates a target directory for benchmarking by checking if "jdm-data" 
     * folder is missing and checking read/write permissions.
     * @param targetLocation location to validate
     * @param showPopup use dialog popup
     * @return true if valid
     */
    public static boolean validateTargetDirectory(File targetLocation, boolean showPopup) {

        if (targetLocation == null) {
            String msg = "Target location is null";
            LOG.log(Level.SEVERE, msg);

            if (showPopup) {
                JOptionPane.showMessageDialog(
                        Gui.mainFrame, msg,
                        "Target location access error",
                        JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }

        File dataDir = new File(targetLocation, App.DATADIRNAME);

        if (!dataDir.exists() && !dataDir.mkdirs()) {
            String msg = "Cannot create data directory at: " + dataDir +
                    "\nCheck permissions and try again.";
            LOG.log(Level.SEVERE, msg);
            if (showPopup) {
                JOptionPane.showMessageDialog(Gui.mainFrame, msg,
                        "Target location access error", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }

        if (!dataDir.canRead() || !dataDir.canWrite()) {

            String msg = """
                      Target location does not allow drive access.
                      Read Permission : %b
                      Write Permission : %b
                    """.formatted(dataDir.canRead(), dataDir.canWrite());

            LOG.log(Level.SEVERE, msg);
            if (showPopup) {
                JOptionPane.showMessageDialog(Gui.mainFrame, msg,
                        "Target location access error", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
        return true;
    }

    /**
     * Checks whether the target location has enough usable disk space for the
     * configured benchmark.  Applies a 10% safety margin (minimum 10 MB) to
     * account for filesystem metadata, journaling, and rounding.
     *
     * @param locationDir the benchmark target directory
     * @param showPopup use dialog popup (GUI only; batch passes false)
     * @return {@code true} if there is enough space, {@code false} otherwise
     */
    public static boolean checkDiskSpace(File locationDir, boolean showPopup) {
        // single-file holds one region per sample, so both modes need the full size
        long requiredBytes = (long) App.blockSizeKb * App.numOfBlocks * App.numOfSamples * App.KILOBYTE;

        long margin = Math.max((long) (requiredBytes * (SPACE_MARGIN - 1.0)),
                               MIN_MARGIN_BYTES);
        long requiredWithMargin = requiredBytes + margin;

        long usableSpace = locationDir.getUsableSpace();

        if (usableSpace >= requiredWithMargin) {
            return true;
        }

        // Space held by Direct I/O test data Java keeps locked until exit (#268)
        long held = LockedTestData.lockedBytes(locationDir);
        if (held > 0 && usableSpace + held >= requiredWithMargin) {
            String msg = String.format(
                    "Not enough disk space to run benchmark.%n"
                  + "Required : %s (+ 10%% margin)%n"
                  + "Available: %s on %s%n"
                  + "Locked   : %s of earlier Direct I/O test data, freed when JDiskMark restarts",
                    formatBytes(requiredWithMargin),
                    formatBytes(usableSpace),
                    locationDir.getAbsolutePath(),
                    LockedTestData.describe(locationDir));
            LOG.log(Level.WARNING, msg);
            App.err(msg);
            if (showPopup && App.mode == App.Mode.GUI && Gui.mainFrame != null) {
                Gui.offerRestartForLockedData(locationDir, formatBytes(requiredWithMargin),
                        formatBytes(usableSpace));
            }
            return false;
        }

        String msg = String.format(
                "Not enough disk space to run benchmark.%n"
              + "Required : %s (+ 10%% margin)%n"
              + "Available: %s on %s",
                formatBytes(requiredWithMargin),
                formatBytes(usableSpace),
                locationDir.getAbsolutePath());

        LOG.log(Level.WARNING, msg);
        App.err(msg);

        if (showPopup && App.mode == App.Mode.GUI && Gui.mainFrame != null) {
            JOptionPane.showMessageDialog(Gui.mainFrame, msg,
                    "Insufficient disk space", JOptionPane.WARNING_MESSAGE);
        }
        return false;
    }

    /** FAT12/16/32 limit a file to 4 GiB - 1 bytes. */
    static final long FAT_MAX_FILE_BYTES = 4L * App.GIGABYTE - 1;

    /**
     * Largest file the file system type allows ({@link java.nio.file.FileStore#type()}:
     * "FAT32"/"FAT" on Windows, "vfat"/"msdos" on Linux, "msdos" on macOS),
     * or {@link Long#MAX_VALUE} when there is no practical limit.
     */
    static long maxFileBytes(String fsType) {
        if (fsType == null) return Long.MAX_VALUE;
        return switch (fsType.toLowerCase(Locale.ROOT)) {
            case "fat", "fat12", "fat16", "fat32", "vfat", "msdos" -> FAT_MAX_FILE_BYTES;
            default -> Long.MAX_VALUE;
        };
    }

    /**
     * Checks that one sample fits in the file system's maximum file size.
     * Single-file data larger than the limit is split by the runner, but a
     * single sample can't be split (#253).
     *
     * @param locationDir the benchmark target directory
     * @param showPopup use dialog popup (GUI only; batch passes false)
     * @return {@code true} if every test file fits, {@code false} otherwise
     */
    public static boolean checkMaxFileSize(File locationDir, boolean showPopup) {
        long sampleBytes = (long) App.blockSizeKb * App.numOfBlocks * App.KILOBYTE;
        String fsType = BenchmarkRunner.fileSystemType(locationDir.toPath());
        long maxBytes = maxFileBytes(fsType);
        if (sampleBytes <= maxBytes) {
            return true;
        }

        String msg = String.format(
                "Sample size exceeds the %s maximum file size.%n"
              + "Sample : %s (%d blocks x %d KB)%n"
              + "Maximum: %s on %s%n"
              + "Reduce the number of blocks or the block size.",
                fsType, formatBytes(sampleBytes), App.numOfBlocks, App.blockSizeKb,
                formatBytes(maxBytes), locationDir.getAbsolutePath());

        LOG.log(Level.WARNING, msg);
        App.err(msg);

        if (showPopup && App.mode == App.Mode.GUI && Gui.mainFrame != null) {
            JOptionPane.showMessageDialog(Gui.mainFrame, msg,
                    "File size limit", JOptionPane.WARNING_MESSAGE);
        }
        return false;
    }

    /**
     * Resolves a drive root (mount point) to a user-writable location suitable
     * for benchmark data.  On Linux the root filesystem ({@code /}) is not
     * writable by normal users, so when {@code root} equals the root component
     * of {@code user.home} (e.g., {@code /} or {@code C:\\}) we return the home
     * directory instead.
     * @param root the drive mount point
     * @return a writable directory on that drive, or {@code null} if none found
     */
    public static File resolveLocationForRoot(File root) {
        File home = new File(System.getProperty("user.home", ""));
        if (home.exists()) {
            java.nio.file.Path homeRoot = home.toPath().getRoot();
            if (homeRoot != null && homeRoot.equals(root.toPath())) {
                File candidate = new File(home, App.DATADIRNAME);
                if (candidate.exists() ? candidate.canWrite() : home.canWrite()) {
                    return home;
                }
            }
        }
        if (root.canRead() && root.canWrite()) return root;
        return null;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= App.GIGABYTE) {
            return String.format("%.1f GB", bytes / (double) App.GIGABYTE);
        } else if (bytes >= App.MEGABYTE) {
            return String.format("%.1f MB", bytes / (double) App.MEGABYTE);
        } else {
            return String.format("%.1f KB", bytes / (double) App.KILOBYTE);
        }
    }
}
