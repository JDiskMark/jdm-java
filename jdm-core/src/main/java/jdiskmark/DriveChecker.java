package jdiskmark;

import javax.swing.JOptionPane;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     * @return {@code true} if there is enough space, {@code false} otherwise
     */
    public static boolean checkDiskSpace(File locationDir) {
        long requiredBytes = App.multiFile
                ? (long) App.blockSizeKb * App.numOfBlocks * App.numOfSamples * App.KILOBYTE
                : (long) App.blockSizeKb * App.numOfBlocks * App.KILOBYTE;

        long margin = Math.max((long) (requiredBytes * (SPACE_MARGIN - 1.0)),
                               MIN_MARGIN_BYTES);
        long requiredWithMargin = requiredBytes + margin;

        long usableSpace = locationDir.getUsableSpace();

        if (usableSpace >= requiredWithMargin) {
            return true;
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

        if (App.mode == App.Mode.GUI && Gui.mainFrame != null) {
            JOptionPane.showMessageDialog(Gui.mainFrame, msg,
                    "Insufficient disk space", JOptionPane.WARNING_MESSAGE);
        }
        return false;
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
