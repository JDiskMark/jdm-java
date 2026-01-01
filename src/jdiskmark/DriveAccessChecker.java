package jdiskmark;

import javax.swing.JOptionPane;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DriveAccessChecker {

    /**
     * Validates a target directory for benchmarking.
     * by checking if "JDiskMarkData" folder is missing and
     * checking read/write permissions.
     */

    public static boolean validateTargetDirectory(File targetLocation, boolean showPopup) {

        if (targetLocation == null) {
            String msg = "Target location is null";
            Logger.getLogger(DriveAccessChecker.class.getName()).log(Level.SEVERE, msg);

            if (showPopup) {
                JOptionPane.showMessageDialog(
                        Gui.mainFrame, msg,
                        "Target location access error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
            return false;
        }

        File dataDir = new File(targetLocation, App.DATADIRNAME);

        if (!dataDir.exists() && !dataDir.mkdirs()) {
            String msg = "Cannot create data directory at: " + dataDir +
                    "\nCheck permissions and try again.";
            Logger.getLogger(DriveAccessChecker.class.getName()).log(Level.SEVERE, msg);
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

            Logger.getLogger(DriveAccessChecker.class.getName()).log(Level.SEVERE, msg);
            if (showPopup) {
                JOptionPane.showMessageDialog(Gui.mainFrame, msg,
                        "Target location access error", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
        return true;
    }
}
