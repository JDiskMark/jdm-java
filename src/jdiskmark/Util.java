
package jdiskmark;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;
import javax.swing.filechooser.FileSystemView;

/**
 * Utility methods for jDiskMark
 */
public class Util {
    
    static final DecimalFormat DF = new DecimalFormat("###.##");
    
    static final String ERROR_DRIVE_INFO = "unable to detect drive info";
    
    /**
     * Deletes the Directory and all files within
     * @param path
     * @return 
     */
    static public boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return (path.delete());
    }
    
    /**
     * Returns a pseudo-random number between min and max, inclusive.
     * The difference between min and max can be at most
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param min Minimum value
     * @param max Maximum value.  Must be greater than min.
     * @return Integer between min and max, inclusive.
     * @see java.util.Random#nextInt(int)
     */
    public static int randInt(int min, int max) {

        // Usually this can be a field rather than a method variable
        Random rand = new Random();

        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive
        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }
    
    /*
     * Not used kept here for reference.
     */
    public static void sysStats() {
        /* Total number of processors or cores available to the JVM */
        System.out.println("Available processors (cores): " + 
                Runtime.getRuntime().availableProcessors());

        /* Total amount of free memory available to the JVM */
        System.out.println("Free memory (bytes): " + 
                Runtime.getRuntime().freeMemory());

        /* This will return Long.MAX_VALUE if there is no preset limit */
        long maxMemory = Runtime.getRuntime().maxMemory();
        
        /* Maximum amount of memory the JVM will attempt to use */
        System.out.println("Maximum memory (bytes): " + 
                (maxMemory == Long.MAX_VALUE ? "no limit" : maxMemory));

        /* Total memory currently available to the JVM */
        System.out.println("Total memory available to JVM (bytes): " + 
                Runtime.getRuntime().totalMemory());

        /* Get a list of all filesystem roots on this system */
        File[] roots = File.listRoots();

        /* For each filesystem root, print some info */
        for (File root : roots) {
            System.out.println("File system root: " + root.getAbsolutePath());
            System.out.println("Total space (bytes): " + root.getTotalSpace());
            System.out.println("Free space (bytes): " + root.getFreeSpace());
            System.out.println("Usable space (bytes): " + root.getUsableSpace());
            System.out.println("Drive Type: " + getDriveType(root));
        }
    }
    
    public static String displayString(double num) {
        return DF.format(num);
    }
    
    /**
     * Gets the drive type string for a root file such as C:\
     * 
     * @param file
     * @return 
     */
    public static String getDriveType(File file) {
        FileSystemView fsv = FileSystemView.getFileSystemView();
        return fsv.getSystemTypeDescription(file);
    }
    
    /**
     * Get OS specific disk info based on the drive the path is mapped to.
     * 
     * @param dataDir the data directory being used in the run.
     * @return Disk info if available.
     */
    public static String getDriveInfo(File dataDir) {
        System.out.println("os: " + System.getProperty("os.name"));
        Path dataDirPath = Paths.get(dataDir.getAbsolutePath());
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            // get disk info for linux
            String partition = UtilOs.getPartitionFromPathLinux(dataDirPath);
            List<String> deviceNames = UtilOs.getDeviceNamesFromPartitionLinux(partition);
            String deviceModel;
            String deviceSize;
            
            // handle single physical drive
            if (deviceNames.size() == 1) {
                String devicePath = "/dev/" + deviceNames.getFirst();
                deviceModel = UtilOs.getDeviceModelLinux(devicePath);
                deviceSize = UtilOs.getDeviceSizeLinux(devicePath);
                return deviceModel + " (" + deviceSize + ")";
            }
            
            // GH-3 handle multiple drives (LVM or RAID partitions)
            if (deviceNames.size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (String dName : deviceNames) {
                    String devicePath = "/dev/" + dName;
                    deviceModel = UtilOs.getDeviceModelLinux(devicePath);
                    deviceSize = UtilOs.getDeviceSizeLinux(devicePath);
                    if (sb.length() > 0) {
                        sb.append(":");
                    }
                    sb.append(deviceModel).append(" (").append(deviceSize).append(")");
                }
                return "Multiple drives: " + sb.toString();
            }
            
            return ERROR_DRIVE_INFO;
        } else if (osName.contains("Mac OS X")) {
            // get disk info for os x
            String devicePath = UtilOs.getDeviceFromPathMacOs(dataDirPath);
            System.out.println("devicePath=" + devicePath);
            String deviceModel = UtilOs.getDeviceModelMacOs(devicePath);
            System.out.println("deviceModel=" + deviceModel);
            return deviceModel;
        } else if (osName.contains("Windows")) {
            // get disk info for windows
            String driveLetter = dataDirPath.getRoot().toFile().toString().split(":")[0];
            if (driveLetter.length() == 1 && Character.isLetter(driveLetter.charAt(0))) {
                // Only proceed if the driveLetter is a single character and a letter
                return UtilOs.getModelFromLetterWindows(driveLetter);
            }
            return ERROR_DRIVE_INFO;
        }
        return "OS not supported";
    }
}
