
package jdiskmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OS Specific Utility methods for jDiskMark
 */
public class UtilOs {
    
    /*
     * Not used kept here for reference.
     */
    static public void readPhysicalDriveWindows() throws FileNotFoundException, IOException {
        File diskRoot = new File ("\\\\.\\PhysicalDrive0");
        RandomAccessFile diskAccess = new RandomAccessFile (diskRoot, "r");
        byte[] content = new byte[1024];
        diskAccess.readFully (content);
        System.out.println("done reading fully");
        System.out.println("content " + Arrays.toString(content));
    }
    
    /**
     * This method became obsolete with an updated version of windows 10. 
     * A newer version of the method is used.
     * 
     * Get the drive model description based on the windows drive letter. 
     * Uses the powershell script disk-model.ps1
     * 
     * This appears to be the output of the original ps script before the update:
     * 
     * d:\>powershell -ExecutionPolicy ByPass -File tmp.ps1

        DiskSize    : 128034708480
        RawSize     : 117894545408
        FreeSpace   : 44036825088
        Disk        : \\.\PHYSICALDRIVE1
        DriveLetter : C:
        DiskModel   : SanDisk SD6SF1M128G
        VolumeName  : OS_Install
        Size        : 117894541312
        Partition   : Disk #1, Partition #2

        DiskSize    : 320070320640
        RawSize     : 320070836224
        FreeSpace   : 29038071808
        Disk        : \\.\PHYSICALDRIVE2
        DriveLetter : E:
        DiskModel   : TOSHIBA External USB 3.0 USB Device
        VolumeName  : TOSHIBA EXT
        Size        : 320070832128
        Partition   : Disk #2, Partition #0

     * We should be able to modify the new parser to detect the 
     * output type and adjust parsing as needed.
     * 
     * @param driveLetter The single character drive letter.
     * @return Disk Drive Model description or empty string if not found.
     */
    @Deprecated
    public static String getDriveModelLegacyWindows(String driveLetter) {
        try {
            Process p = Runtime.getRuntime().exec("powershell -ExecutionPolicy ByPass -File disk-model.ps1");
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();

            String curDriveLetter = null;
            String curDiskModel = null;
            while (line != null) {
                System.out.println(line);
                if (line.trim().isEmpty()) {
                    if (curDriveLetter != null && curDiskModel != null &&
                            curDriveLetter.equalsIgnoreCase(driveLetter)) {
                        return curDiskModel;
                    }
                }
                if (line.contains("DriveLetter : ")) {
                    curDriveLetter = line.split(" : ")[1].substring(0, 1);
                    System.out.println("current letter=" + curDriveLetter);
                }
                if (line.contains("DiskModel   : ")) {
                    curDiskModel = line.split(" : ")[1];
                    System.out.println("current model=" + curDiskModel);
                }
                line = reader.readLine();
            }
        }
        catch(IOException | InterruptedException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    public static String getDriveLetterWindows(Path dataDirPath) {
        // get disk info for windows
        String driveLetter = dataDirPath.getRoot().toFile().toString().split(":")[0];
        if (driveLetter.length() == 1 && Character.isLetter(driveLetter.charAt(0))) {
            // Only proceed if the driveLetter is a single character and a letter
            return driveLetter;
        }
        return "unknown";
    }
    
    /**
     * Get the drive model description based on the windows drive letter. 
     * Uses the powershell script disk-model.ps1
     * 
     * Parses output such as the following:
     * 
     * DiskModel                          DriveLetter
     * ---------                          -----------
     * ST31500341AS ATA Device            D:         
     * Samsung SSD 850 EVO 1TB ATA Device C:         
     * 
     * Tested on Windows 10 on 3/6/2017
     * 
     * @param driveLetter as a string
     * @return the model as a string
     */
    public static String getDriveModelWindows(String driveLetter) {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-ExecutionPolicy", 
                    "ByPass", "-File", "disk-model.ps1");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.trim().endsWith(driveLetter + ":")) {
                    String model = line.split(driveLetter + ":")[0].trim();
                    System.out.println("model is: " + model);
                    return model;
                }
            }
        } catch (IOException e) {
            System.err.println("IO exception retrieving disk info: " + e.getLocalizedMessage());
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    /**
     * On Linux OS get the device path when given a file path.
     * eg.  filePath = /home/james/Desktop/jDiskMarkData
     *      devicePath = /dev/sda
     *      
     * Example command and output:
     * $ df /home/james/jDiskMarkData
     * Filesystem     1K-blocks     Used Available Use% Mounted on
     * /dev/sda2      238737052 54179492 172357524  24% /
     * 
     * @param path the file path
     * @return the device path
     */
    static public String getPartitionFromFilePathLinux(Path path) {
        System.out.println("filePath=" + path.toString());
        try {
            ProcessBuilder pb = new ProcessBuilder("df", path.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String curPartition;
            while ((line = reader.readLine()) != null) {
                System.out.println("curLine=" + line);
                if (line.contains("/dev/")) {
                    curPartition = line.split(" ")[0];
                    return curPartition;
                }
            }
        } catch (IOException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    /**
     * This method returns a list to handle multiple physical drives
     * in case the partition is part of an LVM or RAID in Linux
     * @param partition the partition to look up
     * @return list of physical drives
     */
    static public List<String> getDeviceNamesFromPartitionLinux(String partition) {
        List<String> deviceNames = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", "-no", "pkname", partition);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // detect multiple lines and if so indicate it is an LVM
            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println("devName=" + line);
                deviceNames.add(line);
            }
        } catch (IOException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
        return deviceNames;
    }
    
    /**
     * On Linux OS use the lsblk command to get the disk model number for a 
     * specific Device ie. /dev/sda
     * 
     * Example output of command:
     * ~$ lsblk /dev/sda --output MODEL
     * MODEL
     * Samsung SSD 860 EVO M.2 250GB
     * 
     * @param devicePath path of the device
     * @return the disk model number
     */
    static public String getDeviceModelLinux(String devicePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", devicePath, "--output", "MODEL");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line  = reader.readLine()) != null) {
                // return the first line that does not contain the header
                if (!line.equals("MODEL") && !line.trim().isEmpty()) {
                    return line.trim();
                }
            }
        } catch (IOException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    /**
     * On Linux OS use the lsblk command to get the disk size for a 
     * specific Device ie. /dev/sda
     * 
     * The full command is:
     * 
     * $ lsblk /dev/sda
     * NAME   MAJ:MIN RM   SIZE RO TYPE MOUNTPOINTS
     * sda      8:0    0 232.9G  0 disk 
     * ├─sda1   8:1    0   512M  0 part /boot/efi
     * └─sda2   8:2    0 232.4G  0 part /var/snap/firefox/common/host-hunspell
     * 
     * Retrieving just the size column is:
     * 
     * $ lsblk /dev/sda --output SIZE
     *   SIZE
     * 232.9G
     *   512M
     * 232.4G
     * 
     * @param devicePath path of the device
     * @return the size of the device
     */
    static public String getDeviceSizeLinux(String devicePath) {
        System.out.println("getting size of " + devicePath);
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", devicePath, "--output", "SIZE");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            // return the first entry which is not the column header
            while ((line = reader.readLine()) != null) {
                if (!line.contains("SIZE") && !line.trim().isEmpty()) {
                    return line;
                }
            }
        } catch (IOException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    static public String getDeviceFromPathMacOs(Path path) {
        try {
            ProcessBuilder pb = new ProcessBuilder("df", path.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains("/dev/")) {
                    return line.split(" ")[0];
                }
            }
        } catch(IOException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
static public String getDeviceModelMacOs(String devicePath) {

    if (devicePath == null || devicePath.isEmpty()) {
        throw new IllegalArgumentException("Invalid device path");
    }

    try {
        ProcessBuilder pb = new ProcessBuilder("diskutil", "info", devicePath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("Device / Media Name:")) {
                return line.split("Device / Media Name:")[1].trim();
            }
        }
    } catch (IOException e) {
        Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
    }

    String deviceId = devicePath;
    if (deviceId.contains("/dev/")) {
        deviceId = deviceId.split("/dev/")[1];
    }

    try {
        ProcessBuilder pb = new ProcessBuilder("system_profiler", "SPStorageDataType");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(deviceId)) {
                // Lines after deviceId
                String lineAfterId;
                while ((lineAfterId = reader.readLine()) != null) {
                    if (lineAfterId.contains("Device Name: ")) {
                        return lineAfterId.split("Device Name: ")[1];
                    }
                }
            }
        }
    } catch (IOException e) {
        Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
    }

    return "Model unavailable for " + deviceId;
}
    
    static public void flushDataToDriveMacOs() {
        flushDataToDriveLinux();
    }
    
    /**
     * GH-2 flush data to disk
     */
    static public void flushDataToDriveLinux() {
        String[] command = {"sync"};
        System.out.println("running: " + Arrays.toString(command));

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();
            int exitValue = process.waitFor();

            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                System.out.println("Standard Output:");
                while ((line = outputReader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                System.err.println("Standard Error:");
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }
            }

            System.out.println("EXIT VALUE: " + exitValue);

        } catch (IOException | InterruptedException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, null, e);
        }
    }
    
    static public void dropWriteCacheMacOs() {

        String[] command = {"purge"};
        System.out.println("running: " + Arrays.toString(command));

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();
            int exitValue = process.waitFor();

            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                System.out.println("Standard Output:");
                while ((line = outputReader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                System.err.println("Standard Error:");
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }
            }

            System.out.println("EXIT VALUE: " + exitValue);

        } catch (IOException | InterruptedException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, "Error executing command", e);
        }
    }
    
    /**
     * GH-2 Drop the write catch, used to prevent invlaid read measurement
     */
    static public void dropWriteCacheLinux() {

        String[] command = {"/bin/sh", "-c", "echo 1 > /proc/sys/vm/drop_caches"};
        System.out.println("running: " + Arrays.toString(command));

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();
            int exitValue = process.waitFor();

            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                System.out.println("Standard Output:");
                while ((line = outputReader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                System.err.println("Standard Error:");
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }
            }

            System.out.println("EXIT VALUE: " + exitValue);

        } catch (IOException | InterruptedException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, "Error executing command", e);
        }
    }
    
    public static boolean isRunningAsRootMacOs() {
        return isRunningAsRootLinux();
    }
    
    public static boolean isRunningAsRootLinux() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("id", "-u");
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    int uid = Integer.parseInt(line);
                    return uid == 0;
                }
            }
        } catch (IOException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, "Error executing command", e);
            return false;
        }
        return false;
    }
    
    static boolean isRunningAsAdminWindows() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "net session");
            // Redirect output and error streams to avoid hanging if admin privileges are missing
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, "Error executing command", e);
            return false;
        }
    }
    
    static public void emptyStandbyListWindows() {

        // there seem to be some testing issues with only doing the standbylist
        //String[] command = { ".\\EmptyStandbyList.exe", "standbylist" };
        String[] command = { ".\\EmptyStandbyList.exe"};
        System.out.println("running: " + Arrays.toString(command));

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();
            int exitValue = process.waitFor();

            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                System.out.println("Standard Output:");
                while ((line = outputReader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                System.err.println("Standard Error:");
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }
            }
            System.out.println("EXIT VALUE: " + exitValue);

        } catch (IOException | InterruptedException e) {
            Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE, "Error executing command", e);
        }
    }
    
    /**
     * $ df -h /home/james
     * Filesystem      Size  Used Avail Use% Mounted on
     * /dev/sda2       228G   52G  165G  24% /
     * 
     * @param outputLines
     * @return usage object
     */
    static DiskUsageInfo parseDiskUsageInfoLinux(List<String> outputLines) {
        String usageLine = outputLines.get(1); // Assuming the relevant information is on the second line
        String[] parts = usageLine.trim().split("\\s+");

        double usedGb = Double.parseDouble(parts[2].replace("G", ""));
        double totalGb = Double.parseDouble(parts[1].replace("G", ""));
        double percentUsed = usedGb / totalGb * 100;

        return new DiskUsageInfo(percentUsed, usedGb, totalGb);
    }
    
    /**
     * $ df -h /Users/james
     * Filesystem     Size   Used  Avail Capacity iused               ifree %iused  Mounted on
     * /dev/disk1s1  466Gi  191Gi  273Gi    42%  947563 9223372036853828244    0%   /
     * 
     * @param outputLines
     * @return usage object
     */
    static DiskUsageInfo parseDiskUsageInfoMacOs(List<String> outputLines) {
        String usageLine = outputLines.get(1); // Assuming the relevant information is on the second line
        String[] parts = usageLine.trim().split("\\s+");

        double usedGb = Double.parseDouble(parts[2].replace("Gi", ""));
        double totalGb = Double.parseDouble(parts[1].replace("Gi", ""));
        double percentUsed = usedGb / totalGb * 100;

        return new DiskUsageInfo(percentUsed, usedGb, totalGb);
    }
    
    /**
     * This parses disk usage on windows, tested on w11.
     * 
     * >cmd.exe /c fsutil volume diskfree c:\Users\james
     * Total free bytes                :  35,466,014,720 ( 33.0 GB)
     * Total bytes                     : 511,324,794,880 (476.2 GB)
     * Total quota free bytes          :  35,466,014,720 ( 33.0 GB)
     * Unavailable pool bytes          :               0 (  0.0 KB)
     * Quota unavailable pool bytes    :               0 (  0.0 KB)
     * Used bytes                      : 475,832,217,600 (443.2 GB)
     * Total Reserved bytes            :      26,562,560 ( 25.3 MB)
     * Volume storage reserved bytes   :               0 (  0.0 KB)
     * Available committed bytes       :               0 (  0.0 KB)
     * Pool available bytes            :               0 (  0.0 KB)
     * 
     * @param outputLines lines to parse
     * @return A data structure with disk usage
     */
    static DiskUsageInfo parseDiskUsageInfoWindows(List<String> outputLines) {
        double usedGb = 0;
        double totalGb = 0;
        for (int i = 0; i < outputLines.size(); i++) {
            String line = outputLines.get(i);
            if (line.contains("Total bytes")) {
                line = line.split(":")[1].trim().split("\\s+")[0];
                String bytes = line.replace(",", "");
                long totalBytes = Long.parseLong(bytes);
                totalGb = (double) totalBytes / (double) (1024.0 * 1024.0 * 1024.0);
            } else if (line.contains("Used bytes")) {
                line = line.split(":")[1].trim().split("\\s+")[0];
                String bytes = line.replace(",", "");
                long usedBytes = Long.parseLong(bytes);
                usedGb = (double) usedBytes / (double) (1024.0 * 1024.0 * 1024.0);
            }
        }
        double percentUsed = usedGb / totalGb * 100;
        System.out.println("usedGb=" + usedGb);
        System.out.println("totalGb=" + totalGb);
        System.out.println("percentUsed=" + percentUsed);
        return new DiskUsageInfo(percentUsed, usedGb, totalGb);
    }
}
