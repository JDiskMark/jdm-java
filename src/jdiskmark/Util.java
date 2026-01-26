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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.filechooser.FileSystemView;

/**
 * Utility methods for JDiskMark
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
     * @see Random#nextInt(int)
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
        //System.out.println("os: " + System.getProperty("os.name"));
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
            //System.out.println("deviceModel=" + deviceModel);
            return deviceModel;
        } else if (osName.contains("Windows")) {
            // get disk info for windows
            String driveLetter = dataDirPath.getRoot().toFile().toString().split(":")[0];
            if (driveLetter.length() == 1 && Character.isLetter(driveLetter.charAt(0))) {
                // Only proceed if the driveLetter is a single character and a letter
                deviceModel = UtilOs.getDriveModelWindows(driveLetter);
                //System.out.println("deviceModel=" + deviceModel);
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
            pb.command("df", "-k", diskPath);
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

        String os=System.getProperty("os.name");

        if (os.startsWith("Windows")) {
           return UtilOs.getDriveLetterWindows(path);
        }
        if (os.startsWith("Mac")) {
            String partitionPath=UtilOs.getDeviceFromPathMacOs(path);
            if(partitionPath.startsWith("/dev/")){
               return partitionPath.substring("/dev/".length());
            }
            return partitionPath;
        }

        else if (os.startsWith("Linux"))  {
            String partitionPath = UtilOs.getPartitionFromFilePathLinux(path);
            if (partitionPath.contains("/dev/")) {
                return partitionPath.split("/dev/")[1];
            }
            return partitionPath;
        }

        return "Os Not Found";
    }
    
    public static String getJvmInfo() {
        StringBuilder sb = new StringBuilder();
        String vendor = System.getProperty("java.runtime.name");
        String version = System.getProperty("java.version");
        sb.append(vendor).append(" ").append(version);
        return sb.toString();
    }
    
    public static String getProcessorName() {
        if (App.os.startsWith("Windows")) {
            return UtilOs.getProcessorNameWindows();
        } else if (App.os.startsWith("Mac OS")) {
            return UtilOs.getProcessorNameMacOS();
        } else if (App.os.contains("Linux")) {
            return UtilOs.getProcessorNameLinux();
        }
        return "processor name unknown";
    }

    /**
     * Get the motherboard / baseboard name of the current system.
     *
     * This method executes OS-specific system commands to retrieve
     * motherboard identification information.
     *
     * Commands used:
     *
     * Windows:
     *   wmic baseboard get Product
     *
     *   Example output:
     *     Product
     *     B450M PRO-VDH MAX
     *
     * Linux:
     *   cat /sys/devices/virtual/dmi/id/board_name
     *
     *   Example output:
     *     B450M PRO-VDH MAX
     *
     * macOS:
     *   system_profiler SPHardwareDataType
     *
     *   Example output snippet:
     *     Model Identifier: MacBookPro15,2
     *
     * Note:
     * - macOS does not expose a direct motherboard name.
     * - For macOS, the "Model Identifier" is returned instead.
     *
     * @return motherboard name as a String, or null if unavailable
     */
    public static String getMotherBoardName() {

        Process process = null;

        try {
            if (App.os.startsWith("Windows")) {
                process = new ProcessBuilder("wmic", "baseboard", "get", "Product").start();
            } else if (App.os.contains("Linux")) {
                process = new ProcessBuilder("cat", "/sys/devices/virtual/dmi/id/board_name").start();
            } else if (App.os.startsWith("Mac OS")) {
                process = new ProcessBuilder("system_profiler", "SPHardwareDataType").start();
            }
        } catch (IOException e) {
            Logger.getLogger(Util.class.getName())
                    .log(Level.FINE, "Unable to get Motherboard Name", e);
            return null;
        }

        // If no command was set (unsupported OS)
        if (process == null) { return null; }

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {

                line=line.trim();

                // If OS is mac return Model Identifier as MotherBoard name is not explicitly provided
                if (line.startsWith("Model Identifier:")) {

                    return line.split(":",2)[1].trim();
                }

                if (line.equalsIgnoreCase("Product")) { continue;}
                builder.append(line).append(" ");
            }

            return builder.toString().trim();

        } catch (IOException e) {
            Logger.getLogger(Util.class.getName())
                    .log(Level.FINE, "Unable to read Motherboard Name", e);
            return null;
        }
    }

    /**
     * Get the physical interface (bus / transport) type of a disk or partition.
     *
     * This method determines the underlying storage interface (e.g. SATA, NVMe,
     * USB) by executing OS-specific system commands and parsing their output.
     *
     * Commands used:
     *
     * Windows:
     *   powershell -Command
     *   Get-Partition -DriveLetter <partitionId> | Get-Disk | Select -Expand BusType
     *
     *   Example output:
     *     SATA
     *     NVMe
     *     USB
     *
     * Linux:
     *   lsblk -d -o TRAN /dev/<partitionId>
     *
     *   Example output:
     *     TRAN
     *     sata
     *
     * macOS:
     *   diskutil info <partitionId>
     *
     *   Example output snippet:
     *     Protocol: SATA
     *
     * Notes:
     * - On Windows, the BusType field is returned.
     * - On Linux, the TRAN (transport) column is used.
     * - On macOS, the interface type is parsed from the "Protocol" field.
     *
     * @param partitionId drive letter (Windows) or device identifier (Linux/macOS)
     * @return interface type as a String, or "OS not supported" if unavailable
     */
    public static String getInterfaceType(String partitionId){
        Process process = null;

        try {
            if (App.os.startsWith("Windows")) {
                process = new ProcessBuilder( "powershell", "-Command", "Get-Partition -DriveLetter " +  partitionId + " | Get-Disk | Select -Expand BusType").start();
            } else if (App.os.contains("Linux")) {
                process = new ProcessBuilder("lsblk", "-d", "-o", "TRAN", "/dev/" + partitionId).start();
            } else if (App.os.startsWith("Mac OS")) {
                process = new ProcessBuilder("diskutil", "info", partitionId).start();
            }
        } catch (IOException e) {
            Logger.getLogger(Util.class.getName())
                    .log(Level.FINE, "Unable to get Interface Type", e);
            return "OS not supported";
        }

        if (process == null)  { return  "OS not supported"; }

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            String line;

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty()) { continue;}

                if (line.equalsIgnoreCase("InterfaceType") || line.equalsIgnoreCase("TRAN")|| line.equalsIgnoreCase("NAME")) { continue;}

                // In case of MacOs parse the output for interface type
                if (line.startsWith("Protocol:")) {

                    return line.split(":",2)[1].trim();
                }

                return line;

            }

        } catch (IOException e) {
            Logger.getLogger(Util.class.getName())
                    .log(Level.FINE, "Unable to read Interface Type", e);
            return  "OS not supported";
        }

        return  "OS not supported";
    }

}
