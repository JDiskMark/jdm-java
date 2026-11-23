

package jdiskmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.filechooser.FileSystemView;
import org.metricus.jdm.os.UtilsLinuxOs;
import org.metricus.jdm.os.UtilsMacOs;
import org.metricus.jdm.os.UtilsWinOs;

/**
 * Utility methods for JDiskMark
 */
public class Util {
    
    static final DecimalFormat DF = new DecimalFormat("###.##");
    
    static final String ERROR_DRIVE_INFO = "unable to detect drive info";
    
    /**
     * Deletes the Directory and all files within
     * @param path
     * @return {@code true} if the directory no longer exists
     */
    static public boolean deleteDirectory(File path) {
        return deleteDirectoryReason(path) == null;
    }

    /**
     * Deletes {@code path} and everything in it.
     *
     * @param path directory to delete
     * @return {@code null} on success, otherwise the first failure and its reason
     */
    static public String deleteDirectoryReason(File path) {
        if (path == null || !path.exists()) return null;
        String failure = null;
        File[] files = path.listFiles();
        if (files != null) {
            for (File file : files) {
                String f = file.isDirectory()
                        ? deleteDirectoryReason(file)
                        : deletePathReason(file.toPath());
                if (failure == null) failure = f;
            }
        }
        return failure != null ? failure : deletePathReason(path.toPath());
    }

    private static String deletePathReason(Path p) {
        try {
            Files.deleteIfExists(p);
            return null;
        } catch (DirectoryNotEmptyException e) {
            String[] left = p.toFile().list();
            String names = (left == null || left.length == 0) ? "" : " " + String.join(", ", left);
            return p + ": still contains" + names + " (in use or pending delete)";
        } catch (IOException e) {
            return p + ": " + describeIoError(e);
        }
    }

    /** Short human-readable reason for a file operation failure. */
    public static String describeIoError(Exception e) {
        return switch (e) {
            case AccessDeniedException ade -> "access denied (file may be in use or pending delete)";
            case NoSuchFileException nsf -> "file not found";
            case FileSystemException fse when fse.getReason() != null -> fse.getReason();
            default -> e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        };
    }

    /**
     * Creates {@code dir} if needed and checks that a file can be created in it.
     * A folder deleted while another program has it open (e.g. File Explorer)
     * stays pending delete and vanishes once that program lets go, so this waits
     * up to 3 seconds for such a folder to disappear before re-creating it.
     *
     * @return {@code null} when the directory is ready, otherwise the reason
     */
    static public String prepareDirectory(File dir) {
        Path p = dir.toPath();
        String reason = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                Files.createDirectories(p);
                Files.delete(Files.createTempFile(p, ".jdm-ready-", ".tmp"));
                return null;
            } catch (IOException e) {
                reason = describeIoError(e);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return dir + ": " + reason + " (if another program has this folder open, close it)";
    }

    private static final Set<Path> REMOVE_AFTER_EXIT = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean REMOVE_HOOK_ADDED = new AtomicBoolean(false);

    /**
     * Windows only: removes {@code dir} after JDiskMark exits, if it is empty by
     * then. Test files Java keeps locked (Direct I/O on FAT32, #257) are only
     * deleted by Windows when the JVM's handles close, which is after shutdown
     * hooks run, so a hidden PowerShell waits for this process to end and then
     * removes the emptied folder. Folders that still contain files are left alone.
     */
    static public void removeDirectoryAfterExit(File dir) {
        removeAfterExit(dir);
    }

    /**
     * Windows only: deletes {@code file} after JDiskMark exits, for files that
     * could not be deleted while it was running (e.g. a leftover probe file).
     */
    static public void removeFileAfterExit(File file) {
        removeAfterExit(file);
    }

    private static void removeAfterExit(File path) {
        if (path == null || !App.isWindows()) return;
        REMOVE_AFTER_EXIT.add(path.toPath().toAbsolutePath());
        if (REMOVE_HOOK_ADDED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(Util::launchRemoveAfterExit, "jdm-remove-after-exit"));
        }
    }

    private static void launchRemoveAfterExit() {
        StringBuilder dirs = new StringBuilder();
        for (Path p : REMOVE_AFTER_EXIT) {
            if (dirs.length() > 0) dirs.append(',');
            dirs.append('\'').append(p.toString().replace("'", "''")).append('\'');
        }
        if (dirs.length() == 0) return;
        String script = """
                Wait-Process -Id %d -Timeout 120 -ErrorAction SilentlyContinue
                foreach ($d in @(%s)) {
                    for ($i = 0; $i -lt 20 -and (Test-Path -LiteralPath $d); $i++) {
                        try {
                            if (Test-Path -LiteralPath $d -PathType Leaf) {
                                Remove-Item -LiteralPath $d -Force -ErrorAction Stop
                            } else {
                                [System.IO.Directory]::Delete($d)
                            }
                        } catch { Start-Sleep -Milliseconds 250 }
                    }
                }
                """.formatted(ProcessHandle.current().pid(), dirs);
        String encoded = Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-EncodedCommand", encoded)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            // best effort; the next run removes the empty folder anyway
        }
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
     * Get disk model info based on the drive the path is mapped to,
     * appending {@code [vid:pid]} when the drive is USB-attached.
     *
     * @param dataDir the data directory being used in the run.
     * @return Disk info if available.
     */
    public static String getDriveModel(File dataDir) {
        String model = getRawDriveModel(dataDir);
        if (!isModelDetected(model)) {
            return model;
        }
        if ("USB".equalsIgnoreCase(getDriveInterface(dataDir))) {
            return formatDriveModelWithUsbIds(model, getUsbIds(dataDir.toPath()));
        }
        return model;
    }

    /**
     * Same as {@link #getDriveModel(File)} but reuses USB IDs the caller has
     * already resolved, avoiding repeated interface and USB lookups.
     *
     * @param dataDir the data directory being used in the run.
     * @param usbIds  previously resolved USB IDs, or {@code null} for non-USB drives.
     */
    public static String getDriveModel(File dataDir, UsbIds usbIds) {
        String model = getRawDriveModel(dataDir);
        return isModelDetected(model) ? formatDriveModelWithUsbIds(model, usbIds) : model;
    }

    private static boolean isModelDetected(String model) {
        return model != null && !model.isBlank() && !ERROR_DRIVE_INFO.equals(model)
                && !model.startsWith("Multiple drives:") && !"OS not supported".equals(model);
    }

    private static String getRawDriveModel(File dataDir) {
        //System.out.println("os: " + System.getProperty("os.name"));
        Path dataDirPath = Paths.get(dataDir.getAbsolutePath());
        String deviceModel;
        if (App.isLinux()) {
            // get disk info for linux
            String partition = UtilsLinuxOs.getPartitionFromFilePath(dataDirPath);
            if (partition == null) {
                return ERROR_DRIVE_INFO;
            }
            List<String> deviceNames = UtilsLinuxOs.getDeviceNamesFromPartition(partition);
            
            // handle whole-disk filesystem (no partition table) — pkname is empty
            if (deviceNames.isEmpty()) {
                String vendorModel = UtilOs.getVendorModelLinux(partition);
                return vendorModel != null ? vendorModel : ERROR_DRIVE_INFO;
            }
            
            // handle single physical drive
            if (deviceNames.size() == 1) {
                String devicePath = "/dev/" + deviceNames.getFirst();
                return UtilOs.getVendorModelLinux(devicePath);
            }
            
            // GH-3 handle multiple drives (LVM or RAID partitions)
            if (deviceNames.size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (String dName : deviceNames) {
                    String devicePath = "/dev/" + dName;
                    deviceModel = UtilOs.getVendorModelLinux(devicePath);
                    if (sb.length() > 0) {
                        sb.append(":");
                    }
                    sb.append(deviceModel);
                }
                return "Multiple drives: " + sb.toString();
            }
            
            return ERROR_DRIVE_INFO;
        } else if (App.isMacOs()) {
            // get disk info for os x
            String devicePath = UtilsMacOs.getDeviceFromPath(dataDirPath);
            System.out.println("devicePath=" + devicePath);
            deviceModel = UtilsMacOs.getDeviceModel(devicePath);
            //System.out.println("deviceModel=" + deviceModel);
            return deviceModel;
        } else if (App.isWindows()) {
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
    
    /**
     * Get the storage bus interface (NVMe, SATA, USB, etc.) for the drive
     * the path is mapped to. Returns null if the interface cannot be
     * determined.
     *
     * @param dataDir the data directory being used in the run.
     * @return interface string (e.g. "NVMe", "SATA", "USB") or null
     */
    public static String getDriveInterface(File dataDir) {
        Path dataDirPath = Paths.get(dataDir.getAbsolutePath());
        String iface = null;
        try {
            if (App.isLinux()) {
                String partition = UtilsLinuxOs.getPartitionFromFilePath(dataDirPath);
                if (partition != null) {
                    List<String> deviceNames = UtilsLinuxOs.getBaseDisks(partition);
                    if (deviceNames.isEmpty()) {
                        deviceNames = UtilsLinuxOs.getDeviceNamesFromPartition(partition);
                    }
                    if (deviceNames.size() > 1) {
                        StringBuilder sb = new StringBuilder();
                        for (String dName : deviceNames) {
                            String dIface = UtilOs.getDriveInterfaceLinux("/dev/" + dName);
                            if (dIface != null) {
                                if (sb.length() > 0) sb.append(":");
                                sb.append(dIface);
                            }
                        }
                        iface = (sb.length() > 0) ? "Multiple drives: " + sb : null;
                    } else {
                        String devicePath = deviceNames.isEmpty()
                                ? partition
                                : "/dev/" + deviceNames.getFirst();
                        iface = UtilOs.getDriveInterfaceLinux(devicePath);
                    }
                }
            } else if (App.isMacOs()) {
                String devicePath = UtilsMacOs.getDeviceFromPath(dataDirPath);
                iface = UtilsMacOs.getDriveInterfaceMacOs(devicePath);
            } else if (App.isWindows()) {
                String driveLetter = dataDirPath.getRoot().toFile().toString().split(":")[0];
                if (driveLetter.length() == 1 && Character.isLetter(driveLetter.charAt(0))) {
                    iface = UtilOs.getDriveInterfaceWindows(driveLetter);
                }
            }
        } catch (Exception e) {
            System.out.println("Could not detect drive interface: " + e.getMessage());
        }
        return iface;
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
        if (App.isWindows()) {
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
            if (App.isWindows()) {
                /* GH-21 windows parsing handles non NTFS partitions like
                 * FAT32 used for USB sticks
                 */
                System.out.println("exit code: " + exitCode);
            } else if (App.isMacOs()) {
                throw new IOException("Command execution failed with exit code: " + exitCode);
            } else if (App.isLinux()) {
                throw new IOException("Command execution failed with exit code: " + exitCode);
            }
        }

        if (App.isWindows()) {
            // GH-22 non local support for capacity reporting
            return UtilOs.getCapacityWindows(UtilOs.getDriveLetterWindows(Paths.get(diskPath)));
            // Original capicity implementation w english and spanish support
            //return UtilOs.parseDiskUsageInfoWindows(outputLines);
        } else if (App.isMacOs()) {
            return UtilsMacOs.parseDiskUsageInfo(outputLines);
        } else if (App.isLinux()) {
            return UtilOs.parseDiskUsageInfoLinux(outputLines);
        }
        return new DiskUsageInfo();
    }

    public static String getPartitionId(Path path) {
        if (App.isWindows()) {
            String driveLetter = UtilOs.getDriveLetterWindows(path);
            return driveLetter;
        } else if (App.isMacOs()) {
            String devicePath = UtilsMacOs.getDeviceFromPath(path);
            if (devicePath != null && devicePath.contains("/dev/")) {
                return devicePath.split("/dev/")[1];
            }
            return devicePath;
        } else {
            String partitionPath = UtilsLinuxOs.getPartitionFromFilePath(path);
            if (partitionPath.contains("/dev/")) {
                return partitionPath.split("/dev/")[1];
            }
            return partitionPath;
        }
    }
    
    public static String getJvmInfo() {
        StringBuilder sb = new StringBuilder();
        String vendor = System.getProperty("java.runtime.name");
        String version = System.getProperty("java.version");
        sb.append(vendor).append(" ").append(version);
        return sb.toString();
    }
    
    public static String getProcessorName() {
        if (App.isWindows()) {
            return UtilOs.getProcessorNameWindows();
        } else if (App.isMacOs()) {
            return UtilsMacOs.getProcessorName();
        } else if (App.isLinux()) {
            return UtilOs.getProcessorNameLinux();
        }
        return "processor name unknown";
    }

    /**
     * Returns the filesystem type for the volume containing {@code path}
     * (e.g. "NTFS", "ext4"). Windows and Linux supported.
     */
    public static String getFilesystem(Path path) {
        if (App.isWindows()) {
            String driveLetter = UtilOs.getDriveLetterWindows(path);
            if (driveLetter != null) return UtilOs.getFilesystemWindows(driveLetter);
        } else if (App.isMacOs()) {
            return UtilsMacOs.getFilesystem(path);
        } else if (App.isLinux()) {
            return UtilOs.getFilesystemLinux(path);
        }
        return null;
    }

    /**
     * Returns the drive interface / bus type for the volume containing {@code path}
     * (e.g. "NVMe", "SATA", "USB"). Windows and Linux supported.
     */
    public static String getBusType(Path path) {
        if (App.isWindows()) {
            String driveLetter = UtilOs.getDriveLetterWindows(path);
            if (driveLetter != null) return UtilOs.getBusTypeWindows(driveLetter);
        } else if (App.isMacOs()) {
            return UtilsMacOs.getBusType(path);
        } else if (App.isLinux()) {
            return UtilOs.getBusTypeLinux(path);
        }
        return null;
    }

    /** USB Vendor ID and Product ID pair, each formatted as 4-character lowercase hex. */
    public record UsbIds(String vendorId, String productId) {}

    /**
     * Returns the USB version string for the device at {@code path}
     * (e.g. "3.0", "3.2 Gen 2"). Linux and macOS supported; returns {@code null}
     * on other platforms or when the device is not USB-attached.
     */
    public static String getUsbVersion(Path path) {
        if (App.isLinux()) {
            return UtilsLinuxOs.getUsbVersion(path);
        } else if (App.isMacOs()) {
            return UtilsMacOs.getUsbVersion(path);
        }
        return null;
    }

    /**
     * Returns the USB Vendor ID and Product ID for the USB-attached device at {@code path},
     * or {@code null} when unavailable or not USB-attached.
     */
    public static UsbIds getUsbIds(Path path) {
        if (path == null) return null;
        if (App.isLinux()) {
            return UtilsLinuxOs.getUsbIds(path);
        } else if (App.isMacOs()) {
            return UtilsMacOs.getUsbIds(path);
        } else if (App.isWindows()) {
            String driveLetter = UtilOs.getDriveLetterWindows(path);
            if (driveLetter != null) return UtilsWinOs.getUsbIds(driveLetter);
        }
        return null;
    }

    /**
     * Appends {@code [vid:pid]} to {@code driveModel} when both IDs are present
     * and not already included in the model string.
     */
    public static String formatDriveModelWithUsbIds(String driveModel, UsbIds usbIds) {
        if (driveModel == null || driveModel.isBlank() || usbIds == null) {
            return driveModel;
        }
        if (usbIds.vendorId() == null || usbIds.vendorId().isBlank()
                || usbIds.productId() == null || usbIds.productId().isBlank()) {
            return driveModel;
        }
        String suffix = "[" + usbIds.vendorId() + ":" + usbIds.productId() + "]";
        if (driveModel.contains(suffix)) {
            return driveModel;
        }
        return driveModel + " " + suffix;
    }

    /**
     * Returns the sector size for the volume containing {@code path}
     * (e.g. "512 B", "512 B / 4096 B"). Windows and Linux supported.
     */
    public static String getSectorSize(Path path) {
        if (App.isWindows()) {
            String driveLetter = UtilOs.getDriveLetterWindows(path);
            if (driveLetter != null) return UtilOs.getSectorSizeWindows(driveLetter);
        } else if (App.isMacOs()) {
            return UtilsMacOs.getSectorSize(path);
        } else if (App.isLinux()) {
            return UtilOs.getSectorSizeLinux(path);
        }
        return null;
    }
}
