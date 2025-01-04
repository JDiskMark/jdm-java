
package jdiskmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
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
     * Get disk model info based on the drive the path is mapped to.
     * 
     * @param dataDir the data directory being used in the run.
     * @return Disk info if available.
     */
    public static String getDriveModel(File dataDir) {
        System.out.println("os: " + System.getProperty("os.name"));
        Path dataDirPath = Paths.get(dataDir.getAbsolutePath());
        String osName = System.getProperty("os.name");
        String deviceModel;
        if (osName.contains("Linux")) {
            // get disk info for linux
            String partition = UtilOs.getPartitionFromFilePathLinux(dataDirPath);
            List<String> deviceNames = UtilOs.getDeviceNamesFromPartitionLinux(partition);
            
            // handle single physical drive
            if (deviceNames.size() == 1) {
                String devicePath = "/dev/" + deviceNames.getFirst();
                return UtilOs.getDeviceModelLinux(devicePath);
            }
            
            // GH-3 handle multiple drives (LVM or RAID partitions)
            if (deviceNames.size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (String dName : deviceNames) {
                    String devicePath = "/dev/" + dName;
                    deviceModel = UtilOs.getDeviceModelLinux(devicePath);
                    if (sb.length() > 0) {
                        sb.append(":");
                    }
                    sb.append(deviceModel);
                }
                return "Multiple drives: " + sb.toString();
            }
            
            return ERROR_DRIVE_INFO;
        } else if (osName.contains("Mac OS")) {
            // get disk info for os x
            String devicePath = UtilOs.getDeviceFromPathMacOs(dataDirPath);
            System.out.println("devicePath=" + devicePath);
            deviceModel = UtilOs.getDeviceModelMacOs(devicePath);
            System.out.println("deviceModel=" + deviceModel);
            return deviceModel;
        } else if (osName.contains("Windows")) {
            // get disk info for windows
            String driveLetter = dataDirPath.getRoot().toFile().toString().split(":")[0];
            if (driveLetter.length() == 1 && Character.isLetter(driveLetter.charAt(0))) {
                // Only proceed if the driveLetter is a single character and a letter
                deviceModel = UtilOs.getDriveModelWindows(driveLetter);
                System.out.println("deviceModel=" + deviceModel);
                return deviceModel;
            }
            return ERROR_DRIVE_INFO;
        }
        return "OS not supported";
    }
    
    /*
     * Example input win11 (english):
     *
     * C:\Users\james>cmd.exe /c fsutil volume diskfree c:\\users\james
     * Total free bytes                : 3,421,135,929,344 (  3.1 TB)
     * Total bytes                     : 3,999,857,111,040 (  3.6 TB)
     * Total quota free bytes          : 3,421,135,929,344 (  3.1 TB)
     * Unavailable pool bytes          :                 0 (  0.0 KB)
     * Quota unavailable pool bytes    :                 0 (  0.0 KB)
     * Used bytes                      :   575,413,735,424 (535.9 GB)
     * Total Reserved bytes            :     3,307,446,272 (  3.1 GB)
     * Volume storage reserved bytes   :     2,739,572,736 (  2.6 GB)
     * Available committed bytes       :                 0 (  0.0 KB)
     * Pool available bytes            :                 0 (  0.0 KB)
     * 
     * Example input (spanish):
     *
     * fsutil volume diskfree e:\
     * Total de bytes libres:  26,021,392,384 ( 24.2 GB)
     * Total de bytes: 512,108,785,664 (476.9 GB)
     * Cuota total de bytes libres:  26,021,392,384 ( 24.2 GB)
     */
    public static DiskUsageInfo getDiskUsage(String diskPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();

        // Choose the appropriate command for the operating system:
        if (App.os.startsWith("Windows")) {
            pb.command("cmd.exe", "/c", "fsutil volume diskfree " + diskPath);
        } else {
            // command is same for linux and mac os
            pb.command("df", "-h", diskPath);
        }

        Process process = pb.start();
        
        // Capture the output from the command:
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        List<String> outputLines = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            outputLines.add(line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            if (App.os.startsWith("Windows")) {
                /* GH-21 windows parsing handles non NTFS partitions like
                 * FAT32 used for USB sticks
                 */
                System.out.println("exit code: " + exitCode);
            } else if (App.os.contains("Mac OS")) {
                throw new IOException("Command execution failed with exit code: " + exitCode);
            } else if (App.os.contains("Linux")) {
                throw new IOException("Command execution failed with exit code: " + exitCode);
            }
        }

        if (App.os.startsWith("Windows")) {
            // GH-22 non local support for capacity reporting
            return UtilOs.getCapacityWindows(UtilOs.getDriveLetterWindows(Paths.get(diskPath)));
            // Original capicity implementation w english and spanish support
            //return UtilOs.parseDiskUsageInfoWindows(outputLines);
        } else if (App.os.contains("Mac OS")) {
            return UtilOs.parseDiskUsageInfoMacOs(outputLines);
        } else if (App.os.contains("Linux")) {
            return UtilOs.parseDiskUsageInfoLinux(outputLines);
        }
        return new DiskUsageInfo();
    }

    public static String getPartitionId(Path path) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            String driveLetter = UtilOs.getDriveLetterWindows(path);
            return driveLetter;
        } else {
            String partitionPath = UtilOs.getPartitionFromFilePathLinux(path);
            if (partitionPath.contains("/dev/")) {
                return partitionPath.split("/dev/")[1];
            }
            return partitionPath;
        }
    }
}
