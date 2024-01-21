
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
public class OsUtil {
    
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
    
    /**
     * GH-2 Drop catch is a work in progress.
     */
    static public void dropWriteCachingLinux() {
        System.out.println("===== DROP WRITE ========= ");
        try {
            String command = "sudo sh -c 'sync; echo 1 > /proc/sys/vm/drop_caches'";
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
            System.out.println("EXIT VALUE: " + p.exitValue());
            //System.out.println("ran " + command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            while (line != null) {               
                System.out.println(line);
                line = reader.readLine();
            }
            BufferedReader eReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            line = eReader.readLine();
            while (line != null) {
                System.err.println(line);
                line = reader.readLine();
            }
        } catch(IOException | InterruptedException e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }
    }
}
