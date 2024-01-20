
package jdiskmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    static public void readPhysicalDrive() throws FileNotFoundException, IOException {
        File diskRoot = new File ("\\\\.\\PhysicalDrive0");
        RandomAccessFile diskAccess = new RandomAccessFile (diskRoot, "r");
        byte[] content = new byte[1024];
        diskAccess.readFully (content);
        System.out.println("done reading fully");
        System.out.println("content " + Arrays.toString(content));
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
            String partition = Util.getPartitionFromPathLinux(dataDirPath);
            List<String> deviceNames = Util.getDeviceNamesFromPartitionLinux(partition);
            String deviceModel;
            String deviceSize;
            
            // handle single physical drive
            if (deviceNames.size() == 1) {
                String devicePath = "/dev/" + deviceNames.getFirst();
                deviceModel = Util.getDeviceModelLinux(devicePath);
                deviceSize = Util.getDeviceSizeLinux(devicePath);
                return deviceModel + " (" + deviceSize + ")";
            }
            
            // GH-3 handle multiple drives (LVM or RAID partitions)
            if (deviceNames.size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (String dName : deviceNames) {
                    String devicePath = "/dev/" + dName;
                    deviceModel = Util.getDeviceModelLinux(devicePath);
                    deviceSize = Util.getDeviceSizeLinux(devicePath);
                    if (sb.length() > 0) {
                        sb.append(":");
                    }
                    sb.append(deviceModel).append("(").append(deviceSize).append(")");
                }
                return "Multiple drives: " + sb.toString();
            }
            
            return ERROR_DRIVE_INFO;
        } else if (osName.contains("Mac OS X")) {
            // get disk info for os x
            String devicePath = Util.getDeviceFromPathOSX(dataDirPath);
            String deviceModel = Util.getDeviceModelOSX(devicePath);
            return deviceModel;
        } else if (osName.contains("Windows")) {
            // get disk info for windows
            String driveLetter = dataDirPath.getRoot().toFile().toString().split(":")[0];
            if (driveLetter.length() == 1 && Character.isLetter(driveLetter.charAt(0))) {
                // Only proceed if the driveLetter is a single character and a letter
                return Util.getModelFromLetterWindows(driveLetter);
            }
            return ERROR_DRIVE_INFO;
        }
        return "OS not supported";
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
    public static String getModelFromLetterLegacyWindows(String driveLetter) {
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
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
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
    public static String getModelFromLetterWindows(String driveLetter) {
        try {
            Process p = Runtime.getRuntime().exec("powershell -ExecutionPolicy ByPass -File disk-model.ps1");
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            
            while (line != null) {
                System.out.println(line);
                if (line.trim().endsWith(driveLetter + ":")) {
                    String model = line.split(driveLetter + ":")[0];
                    System.out.println("model is: " + model);
                    return model;
                }
                line = reader.readLine();
            }
        } catch(IOException | InterruptedException e) {
            System.err.println("IO exception retrieveing disk info: " + e.getLocalizedMessage());
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    /**
     * On Linux OS get the device path when given a file path.
     * eg.  filePath = /home/james/Desktop/jDiskMarkData
     *      devicePath = /dev/sda
     *      
     * @param path the file path
     * @return the device path
     */
    static public String getPartitionFromPathLinux(Path path) {
        try {
            Process p = Runtime.getRuntime().exec("df " + path.toString());
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            String curPartition;
            while (line != null) {
                // System.out.println(line);
                System.err.println("curLine=" + line);
                if (line.contains("/dev/")) {
                    curPartition = line.split(" ")[0];
                    return curPartition;
                }
                line = reader.readLine();
            }
        } catch (IOException | InterruptedException e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
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
        List<String> deviceNames = new ArrayList();
        try {
            Process p = Runtime.getRuntime().exec("lsblk -no pkname " + partition);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            // detect multiple lines and if so indicate it is an LVM
            String line = reader.readLine();
            while (line != null) {
                System.err.println("devName=" + line);
                deviceNames.add(line);
                line = reader.readLine();
            }
        } catch (IOException | InterruptedException e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
        return deviceNames;
    }
    
    /**
     * On Linux OS use the lsblk command to get the disk model number for a 
     * specific Device ie. /dev/sda
     * 
     * @param devicePath path of the device
     * @return the disk model number
     */
    static public String getDeviceModelLinux(String devicePath) {
        try {
            Process p = Runtime.getRuntime().exec("lsblk " + devicePath + " --output MODEL");
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            while (line != null) {
                //System.out.println(line);
                if (!line.equals("MODEL") && !line.trim().isEmpty()) {
                    return line;
                }
                line = reader.readLine();
            }
        } catch(IOException | InterruptedException e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    /**
     * On Linux OS use the lsblk command to get the disk size for a 
     * specific Device ie. /dev/sda
     * 
     * @param devicePath path of the device
     * @return the size of the device
     */
    static public String getDeviceSizeLinux(String devicePath) {
        try {
            Process p = Runtime.getRuntime().exec("lsblk "+devicePath+" --output SIZE");
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            while (line != null) {
                // System.out.println(line);
                if (!line.contains("SIZE") && !line.trim().isEmpty()) {
                    return line;
                }
                line = reader.readLine();
            }
        } catch(IOException | InterruptedException e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    static public String getDeviceFromPathOSX(Path path) {
        try {
            Process p = Runtime.getRuntime().exec("df " + path.toString());
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            String curDevice;
            while (line != null) {
                // System.out.println(line);
                if (line.contains("/dev/")) {
                    curDevice = line.split(" ")[0];
                    return curDevice;
                }
                line = reader.readLine();
            }
        } catch(IOException | InterruptedException e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
    
    static public String getDeviceModelOSX(String devicePath) {
        try {
            String command = "diskutil info " + devicePath;
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            while (line != null) {               
                if (line.contains("Device / Media Name:")) {
                    return line.split("Device / Media Name:")[1].trim();
                }
                line = reader.readLine();
            }
        } catch(IOException | InterruptedException e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }
}
