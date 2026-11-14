package jdiskmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OS Specific Utility methods for JDiskMark
 */
public class UtilOs {
    
    public static final Logger LOGGER = Logger.getLogger(UtilOs.class.getName());

    // --- OS detection primitives ---
    // Accept an explicit osName string so these can be used before App.os is
    // populated (e.g. in CLI mode or very early in main()).

    /** Returns {@code true} when {@code osName} identifies macOS. */
    public static boolean isMacOs(String osName) {
        return osName != null && osName.contains("Mac OS");
    }

    /** Returns {@code true} when {@code osName} identifies Windows. */
    public static boolean isWindows(String osName) {
        return osName != null && osName.startsWith("Windows");
    }

    /** Returns {@code true} when {@code osName} identifies Linux. */
    public static boolean isLinux(String osName) {
        return osName != null && osName.contains("Linux");
    }

    /**
     * Returns {@code true} when running inside a Flatpak sandbox.
     * Flatpak always creates {@code /.flatpak-info} inside the container.
     */
    public static boolean isFlatpak() {
        return java.nio.file.Files.exists(java.nio.file.Path.of("/.flatpak-info"));
    }

    /**
     * Returns {@code true} when the current packaging context supports SMART.
     *
     * <p>SMART requires {@code smartctl} plus privilege escalation
     * ({@code pkexec} on Linux, UAC on Windows, {@code sudo} on macOS).
     * Flatpak sandboxing blocks {@code pkexec} and direct block-device
     * access, so SMART is disabled there until a host-escape mechanism
     * (e.g.&nbsp;{@code flatpak-spawn --host}) is implemented.
     */
    public static boolean isSmartSupported(String osName) {
        if (isMacOs(osName) || isWindows(osName)) {
            return true;
        }
        if (isLinux(osName)) {
            return !isFlatpak();
        }
        return false;
    }

    /** The disk model power shell utility. */
    public static final String DISK_MODEL_PS_FILENAME = "disk-model.ps1";
    
    /** The capacity power shell utility. */
    public static final String CAPACITY_PS_FILENAME = "capacity.ps1";
    
    /* Not used kept here for reference. */
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
     * * Get the drive model description based on the windows drive letter. 
     * Uses the powershell script disk-model.ps1
     * * This appears to be the output of the original ps script before the update:
     * * d:\>powershell -ExecutionPolicy ByPass -File tmp.ps1

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
     * * @param driveLetter The single character drive letter.
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
     * * Parses output such as the following:
     * * DiskModel                          DriveLetter
     * ---------                          -----------
     * ST31500341AS ATA Device            D:         
     * Samsung SSD 850 EVO 1TB ATA Device C:         
     * * Tested on Windows 10 on 3/6/2017
     * * @param driveLetter as a string
     * @return the model as a string
     */
    public static String getDriveModelWindows(String driveLetter) {
        // match powershell uppercase output
        driveLetter = driveLetter.toUpperCase();
        File diskModelPsFile = new File(DISK_MODEL_PS_FILENAME);
        if (!diskModelPsFile.exists()) {
            diskModelPsFile = new File(".//app//" + DISK_MODEL_PS_FILENAME);
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-ExecutionPolicy", 
                    "ByPass", "-File", diskModelPsFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (App.verbose) {
                    System.out.println(line);
                }
                if (line.trim().endsWith(driveLetter + ":")) {
                    String model = line.split(driveLetter + ":")[0].trim();
                    return model;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO exception getting model", e);
        }
        return null;
    }
    
    /**
     * Get the storage bus interface for a Windows drive letter using
     * PowerShell's Get-Partition / Get-Disk pipeline.
     *
     * <p>Returns the BusType string from {@code Get-Disk}, e.g.

     * {@code NVMe}, {@code SATA}, {@code USB}, {@code RAID}, {@code SAS}.
     *
     * @param driveLetter single drive letter (e.g. "C")
     * @return the bus type string, or null if unavailable
     */
    public static String getDriveInterfaceWindows(String driveLetter) {
        driveLetter = driveLetter.toUpperCase();
        try {
            // Get-Partition maps a drive letter to a DiskNumber,
            // then Get-Disk gives us the BusType.
            String script = String.format(
                    "Get-Partition -DriveLetter %s | Get-Disk | Select-Object -ExpandProperty BusType",
                    driveLetter);
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-Command", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        return line;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not detect drive interface on Windows", e);
        }
        return null;
    }
    
    /**
     * Get the storage transport for a Linux device using {@code lsblk --output TRAN}.
     *
     * <p>Returns values like {@code sata}, {@code nvme}, {@code usb}, or null.
     *
     * @param devicePath the device path (e.g. "/dev/sda", "/dev/nvme0n1")
     * @return the transport string, or null if unavailable
     */
    public static String getDriveInterfaceLinux(String devicePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "lsblk", devicePath, "--nodeps", "--noheadings", "--output", "TRAN");
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        // Normalise common values
                        switch (line.toLowerCase()) {
                            case "nvme": return "NVMe";
                            case "sata": return "SATA";
                            case "usb":  return "USB";
                            case "sas":  return "SAS";
                            case "spi":  return "SPI";
                            default:     return line;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not detect drive interface on Linux", e);
        }
        return null;
    }
    
    public static DiskUsageInfo getCapacityWindows(String driveLetter) {
        File capacityPsFile = new File(CAPACITY_PS_FILENAME);
        if (!capacityPsFile.exists()) {
            capacityPsFile = new File(".//app//" + CAPACITY_PS_FILENAME);
        }
        
        DiskUsageInfo usageInfo = new DiskUsageInfo();
        
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-ExecutionPolicy", 
                    "ByPass", "-File", capacityPsFile.getAbsolutePath(), driveLetter);
            
            // FIX: Set to false so error messages don't get mixed into the JSON output
            pb.redirectErrorStream(false); 
            
            final Process process = pb.start();
            
            // Create a thread to handle the error stream
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        LOGGER.log(Level.SEVERE, "PowerShell script error: {0}", errorLine);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error reading from error stream", e);
                }
            });
            errorThread.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
                
                String jsonOutput = jsonBuilder.toString().trim();
                
                // FIX: Guard clause. Only parse if it actually looks like JSON.
                if (jsonOutput.isEmpty() || !jsonOutput.startsWith("{")) {
                    LOGGER.log(Level.WARNING, "PowerShell returned non-JSON data: {0}", jsonOutput);
                    return usageInfo; // Returns default 0 values to prevent a UI crash
                }
                
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonOutput);
                usageInfo.totalGb = rootNode.get("TotalSpaceGb").asDouble(); 
                usageInfo.freeGb = rootNode.get("FreeSpaceGb").asDouble();
                usageInfo.usedGb = rootNode.get("UsedSpaceGb").asDouble();
                usageInfo.calcPercentageUsed(); 
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Exception retrieving disk capacity: " + e.getLocalizedMessage(), e);
        }
        return usageInfo;
    }
    
    /**
     * Mount-point prefixes that belong to the Flatpak sandbox itself
     * (runtime, app bundle, host passthrough, per-app data) rather than to
     * user storage. Bind mounts under these paths share the host block device
     * and would otherwise appear as duplicate drives (GH-229).
     */
    private static final List<String> FLATPAK_SANDBOX_PREFIXES = List.of(
            "/usr", "/app", "/etc", "/run/host", "/run/flatpak", "/run/user",
            "/proc", "/sys", "/dev", "/var", "/tmp");

    /**
     * Returns mount points for real block-device-backed filesystems on Linux
     * by reading {@code /proc/mounts}. Virtual filesystems (procfs, sysfs,
     * tmpfs, etc.), snap loopback mounts, and boot partitions are excluded.
     *
     * <p>This replaces {@code File.listRoots()} for Linux drive enumeration,
     * since {@code listRoots()} only returns {@code /} on Linux and never
     * discovers additional mounted drives.
     *
     * <p>Inside a Flatpak sandbox {@code /} is a tmpfs and the runtime is
     * bind-mounted from the host, so a sandbox-aware filter is applied
     * instead; see {@link #filterFlatpakMounts(List)}.
     *
     * @return list of mount-point directories; never empty on a running
     *         Linux system
     */
    static public List<File> getMountedDrivesLinux() {
        List<String[]> entries = new ArrayList<>();
        try {
            List<String> lines = java.nio.file.Files.readAllLines(
                    java.nio.file.Path.of("/proc/mounts"));
            for (String line : lines) {
                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;

                String device     = parts[0];
                String mountPoint = unescapeMountField(parts[1]);

                // Only real block devices
                if (!device.startsWith("/dev/")) continue;

                // Exclude snap loopback mounts (Ubuntu)
                if (mountPoint.startsWith("/snap/")) continue;

                // Exclude boot partitions
                if (mountPoint.startsWith("/boot/") || mountPoint.equals("/boot")) continue;

                File mountDir = new File(mountPoint);
                // Single-file bind mounts (e.g. /etc/resolv.conf) are not drives
                if (!mountDir.isDirectory()) continue;
                if (mountDir.getTotalSpace() == 0) continue;

                entries.add(new String[]{ device, mountPoint });
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read /proc/mounts", e);
        }

        return isFlatpak() ? filterFlatpakMounts(entries) : filterNativeMounts(entries);
    }

    /** Native Linux: every qualifying mount, with {@code /} guaranteed present. */
    private static List<File> filterNativeMounts(List<String[]> entries) {
        List<File> mounts = new ArrayList<>();
        for (String[] e : entries) {
            mounts.add(new File(e[1]));
        }
        if (mounts.stream().noneMatch(f -> f.getAbsolutePath().equals("/"))) {
            mounts.addFirst(new File("/"));
        }
        return mounts;
    }

    /**
     * Flatpak: drop the tmpfs root and sandbox bind mounts, then keep a single
     * entry per block device (the shortest mount path, e.g. {@code /home/deck}
     * rather than {@code /home/deck/.var/app/...}). Falls back to the user's
     * home directory when nothing remains.
     */
    private static List<File> filterFlatpakMounts(List<String[]> entries) {
        String home = System.getProperty("user.home", "/");
        Map<String, String> byDevice = new java.util.LinkedHashMap<>();
        for (String[] e : entries) {
            String device = e[0];
            String mountPoint = e[1];
            if (mountPoint.equals("/")) continue;
            // Home may itself sit under /var (Fedora Atomic: /var/home/<user>)
            boolean underHome = isUnderAny(mountPoint, List.of(home));
            if (!underHome && isUnderAny(mountPoint, FLATPAK_SANDBOX_PREFIXES)) continue;
            byDevice.merge(device, mountPoint,
                    (prev, cur) -> cur.length() < prev.length() ? cur : prev);
        }

        List<File> mounts = new ArrayList<>();
        for (String mountPoint : byDevice.values()) {
            mounts.add(new File(mountPoint));
        }
        if (mounts.isEmpty()) {
            File homeDir = new File(home);
            mounts.add(homeDir.getTotalSpace() > 0 ? homeDir : new File("/"));
        }
        return mounts;
    }

    private static boolean isUnderAny(String path, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Decodes the octal escapes used by {@code /proc/mounts}. */
    private static String unescapeMountField(String field) {
        return field.replace("\\040", " ")
                .replace("\\011", "\t")
                .replace("\\012", "\n")
                .replace("\\134", "\\");
    }

    /**
     * On Linux OS get the device path when given a file path.
     * eg.  filePath = /home/james/Desktop/jdm-data
     * devicePath = /dev/sda
     * * Example command and output:
     * $ df /home/james/jdm-data
     * Filesystem     1K-blocks     Used Available Use% Mounted on
     * /dev/sda2      238737052 54179492 172357524  24% /
     * * @param path the file path
     * @return the device path
     */
    static public String getPartitionFromFilePathLinux(Path path) {
        if (App.verbose) {
            System.out.println("filePath=" + path.toString());
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("df", "-k", path.toString());
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C"); // set language to english
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String curPartition = null;
            while ((line = reader.readLine()) != null) {
                if (App.verbose) {
                    System.out.println("curLine=" + line);
                }
                if (line.contains("/dev/")) {
                    curPartition = line.split(" ")[0];
                    break;
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0 && curPartition != null) {
                return curPartition;
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.SEVERE, null, e);
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
        if (partition == null) {
            return deviceNames;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", "-no", "pkname", partition);
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C"); // set language to english
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // detect multiple lines and if so indicate it is an LVM
            String line;
            while ((line = reader.readLine()) != null) {
                if (App.verbose) {
                    System.err.println("devName=" + line);
                }
                if (!line.trim().isEmpty()) {
                    deviceNames.add(line.trim());
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                deviceNames.clear();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.SEVERE, null, e);
        }
        return deviceNames;
    }

    /**
     * Resolves a block device to the whole disk(s) beneath it, walking down
     * through partition, dm-crypt (LUKS), LVM and md RAID layers. Unlike
     * {@code lsblk -no pkname}, which stops one layer down (e.g. at the LUKS
     * partition), this always reaches devices of TYPE {@code disk}.
     *
     * @param device device path, e.g. {@code /dev/mapper/vg-root}
     * @return kernel names such as {@code sda} or {@code nvme0n1}; empty on failure
     */
    static List<String> getBaseDisksLinux(String device) {
        Set<String> disks = new LinkedHashSet<>();
        if (device == null || device.isBlank()) return new ArrayList<>();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", "--inverse", "--noheadings",
                    "--raw", "--output", "KNAME,TYPE", device);
            pb.environment().put("LC_ALL", "C");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            process = p;
            java.util.concurrent.CompletableFuture<List<String>> output =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = p.inputReader()) {
                    return reader.lines().toList();
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.log(Level.WARNING, "lsblk --inverse timed out for {0}", device);
                return new ArrayList<>();
            }
            if (p.exitValue() != 0) {
                return new ArrayList<>();
            }
            for (String line : output.get(1, java.util.concurrent.TimeUnit.SECONDS)) {
                String[] cols = line.trim().split("\\s+");
                if (cols.length >= 2 && "disk".equals(cols[1]) && !cols[0].isEmpty()) {
                    disks.add(cols[0]);
                }
            }
        } catch (IOException | InterruptedException
                | java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "lsblk --inverse failed for " + device, e);
            disks.clear();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return new ArrayList<>(disks);
    }
    
    /**
     * On Linux OS use the lsblk command to get the disk model number for a 
     * specific Device ie. /dev/sda
     * * Example output of command:
     * ~$ lsblk /dev/sda --output MODEL
     * MODEL
     * Samsung SSD 860 EVO M.2 250GB
     * * @param devicePath path of the device
     * @return the disk model number
     */
    static public String getDeviceModelLinux(String devicePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", devicePath, "--output", "MODEL");
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C"); // set language to english
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
            LOGGER.log(Level.SEVERE, null, e);
        }
        return null;
    }

    /**
     * On Linux OS use the lsblk command to get the combined vendor and model
     * for a specific device (e.g. /dev/sda, /dev/sdb).
     *
     * <p>For NVMe drives, the MODEL column already includes the manufacturer
     * (e.g. "SAMSUNG MZVLB512HBJQ-000L7"), so the vendor is not prepended.
     * For USB drives, the VENDOR and MODEL columns are separate
     * (e.g. VENDOR="Lexar", MODEL="USB Flash Drive"), so they are combined
     * into "Lexar USB Flash Drive".
     *
     * @param devicePath path of the device (e.g. "/dev/sdb")
     * @return the combined vendor and model string, or null if unavailable
     */
    static public String getVendorModelLinux(String devicePath) {
        String result = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "lsblk", devicePath, "--nodeps", "--output", "VENDOR,MODEL");
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new
                    InputStreamReader(process.getInputStream()))) {
                String headerLine = reader.readLine();
                int modelOffset = headerLine != null ?
                        headerLine.indexOf("MODEL") : -1;
                if (modelOffset >= 0) {
                    String dataLine = reader.readLine();
                    if (dataLine != null && !dataLine.trim().isEmpty()) {
                        String vendor = dataLine.length() > modelOffset
                                ? dataLine.substring(0, modelOffset).trim() :
                                "";
                        String model  = dataLine.length() > modelOffset
                                ? dataLine.substring(modelOffset).trim() :
                                dataLine.trim();
                        if (!vendor.isEmpty() && !model.toUpperCase()
                                .startsWith(vendor.toUpperCase())) {
                            result = vendor + " " + model;
                        } else if (!model.isEmpty()) {
                            result = model;
                        }
                    }
                }
            }
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.SEVERE, null, e);
        }
        return result;
    }
    
    /**
     * On Linux OS use the lsblk command to get the disk size for a 
     * specific Device ie. /dev/sda
     * * The full command is:
     * * $ lsblk /dev/sda
     * NAME   MAJ:MIN RM   SIZE RO TYPE MOUNTPOINTS
     * sda      8:0    0 232.9G  0 disk 
     * ├─sda1   8:1    0   512M  0 part /boot/efi
     * └─sda2   8:2    0 232.4G  0 part /var/snap/firefox/common/host-hunspell
     * * Retrieving just the size column is:
     * * $ lsblk /dev/sda --output SIZE
     * SIZE
     * 232.9G
     * 512M
     * 232.4G
     * * @param devicePath path of the device
     * @return the size of the device
     */
    static public String getDeviceSizeLinux(String devicePath) {
        System.out.println("getting size of " + devicePath);
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", devicePath, "--output", "SIZE");
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C"); // set language to english
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
            LOGGER.log(Level.SEVERE, null, e);
        }
        return null;
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
            boolean interrupted = false;
            boolean finished = false;
            // prevent interruption from interfering with flush
            while (!finished) {
                try {
                    int exitValue = process.waitFor();

                    try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = outputReader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }

                    StringBuilder stderr = new StringBuilder();
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            stderr.append(line).append(System.lineSeparator());
                        }
                    }

                    if (!stderr.isEmpty() || exitValue != 0) {
                        String errMsg = "sync failed (exit=" + exitValue + ")"
                                + (stderr.isEmpty() ? "" : ": " + stderr.toString().trim());
                        LOGGER.log(Level.WARNING, errMsg);
                        App.err(errMsg);
                    }
                    System.out.println("EXIT VALUE: " + exitValue);
                    finished = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                    LOGGER.log(Level.WARNING, "Interrupted while waiting for sync, retrying", e);
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
            App.err("sync command failed: " + e.getMessage());
        }
    }
    
    
    /**
     * GH-2 Drop the write cache, used to prevent invalid read measurement
     * @return true if the cache was dropped
     */
    static public boolean dropWriteCacheLinux() {

        String[] command = {"/bin/sh", "-c", "echo 1 > /proc/sys/vm/drop_caches"};
        System.out.println("running: " + Arrays.toString(command));
        boolean success = false;

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();
            boolean interrupted = false;
            boolean finished = false;
            // prevent interruption from interfering with cleaning cache
            while (!finished) {
                try {
                    int exitValue = process.waitFor();

                    try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = outputReader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }

                    StringBuilder stderr = new StringBuilder();
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            stderr.append(line).append(System.lineSeparator());
                        }
                    }

                    if (!stderr.isEmpty() || exitValue != 0) {
                        String errMsg = "drop_caches failed (exit=" + exitValue + ")"
                                + (stderr.isEmpty() ? "" : ": " + stderr.toString().trim());
                        LOGGER.log(Level.WARNING, errMsg);
                        App.err(errMsg);
                    }
                    success = exitValue == 0;
                    System.out.println("EXIT VALUE: " + exitValue);
                    finished = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                    LOGGER.log(Level.WARNING, "Interrupted while waiting for drop_caches, retrying", e);
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error executing command", e);
            App.err("drop_caches command failed: " + e.getMessage());
        }
        return success;
    }

    /** Outcome of an elevated disk cache drop attempt. */
    public enum CacheDropResult {
        /** Cache was dropped. */
        CLEARED,
        /** Escalation was cancelled, refused, or the command failed. */
        DENIED,
        /** No password prompt is possible in this environment (e.g. Flatpak). */
        UNAVAILABLE
    }

    private static final String DROP_SENTINEL = "---DROP_DONE:";
    // allows time for the user to enter their password
    private static final long DROP_TIMEOUT_SECONDS = 120;

    /**
     * Flushes and drops the OS disk cache on Linux or macOS through the
     * privileged shell shared with SMART ({@code pkexec bash} on Linux,
     * {@code osascript} + {@code sudo -S bash} on macOS). The user is
     * prompted for their password only if the shell is not already running.
     *
     * @return the outcome of the attempt
     */
    public static CacheDropResult dropCacheElevated() {
        String command;
        if (App.isLinux()) {
            if (isFlatpak() || !isOnPath("pkexec")) {
                return CacheDropResult.UNAVAILABLE;
            }
            command = "sync; echo 1 > /proc/sys/vm/drop_caches";
        } else if (App.isMacOs()) {
            command = "sync; purge";
        } else {
            return CacheDropResult.UNAVAILABLE;
        }

        try {
            if (Smart.process == null || !Smart.process.isAlive()) {
                App.msg("Requesting administrator privileges to clear the disk cache...");
                Smart.startPrivilegedShell();
                Smart.startHeartbeat();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Privilege escalation for cache drop failed", e);
            return CacheDropResult.DENIED;
        }

        synchronized (Smart.pLock) {
            Process shell = Smart.process;
            if (shell == null || Smart.shellWriter == null || Smart.shellReader == null) {
                return CacheDropResult.DENIED;
            }
            CompletableFuture<Integer> exitCode = new CompletableFuture<>();
            Thread reader = Thread.startVirtualThread(() -> {
                try {
                    String line;
                    while ((line = Smart.shellReader.readLine()) != null) {
                        if (line.startsWith(DROP_SENTINEL)) {
                            String code = line.substring(DROP_SENTINEL.length()).replace("---", "").trim();
                            exitCode.complete(Integer.parseInt(code));
                            return;
                        }
                    }
                    exitCode.complete(-1); // shell exited, escalation refused
                } catch (IOException | NumberFormatException e) {
                    exitCode.completeExceptionally(e);
                }
            });
            try {
                Smart.shellWriter.write(command + "; echo \"" + DROP_SENTINEL + "$?---\"\n");
                Smart.shellWriter.flush();
                int code = exitCode.get(DROP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (code == 0) {
                    App.msg("Disk cache cleared");
                    return CacheDropResult.CLEARED;
                }
                LOGGER.log(Level.WARNING, "Elevated cache drop failed, exit={0}", code);
                return CacheDropResult.DENIED;
            } catch (TimeoutException e) {
                LOGGER.warning("Elevated cache drop timed out, stopping privileged shell");
                shell.destroyForcibly();
                reader.interrupt();
                return CacheDropResult.DENIED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                shell.destroyForcibly();
                return CacheDropResult.DENIED;
            } catch (IOException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "Elevated cache drop failed", e);
                return CacheDropResult.DENIED;
            }
        }
    }

    private static boolean isOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, executable).canExecute()) return true;
        }
        return false;
    }
    
    
    public static boolean isRunningAsRootLinux() {
        try {
            ProcessBuilder pb = new ProcessBuilder("id", "-u");
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    int uid = Integer.parseInt(line);
                    return uid == 0;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error executing command", e);
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
            LOGGER.log(Level.SEVERE, "Error executing command", e);
            return false;
        }
    }
    
    static public void emptyStandbyListWindows(File esblExe) {

        // there seem to be some testing issues with only doing the standbylist
        //String[] command = { ".\\EmptyStandbyList.exe", "standbylist" };
        
        //String[] command = {".\\EmptyStandbyList.exe"};
        String[] command = { esblExe.getAbsolutePath() };
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
            LOGGER.log(Level.SEVERE, "Error executing command", e);
        }
    }
    
    /**
     * $ df -h /home/james
     * Filesystem      Size  Used Avail Use% Mounted on
     * /dev/sda2       228G   52G  165G  24% /
     * * @param outputLines
     * @return usage object
     */
    static DiskUsageInfo parseDiskUsageInfoLinux(List<String> outputLines) {
        String usageLine = outputLines.get(1); // Assuming the relevant information is on the second line
        String[] parts = usageLine.trim().split("\\s+");

        /* Grab relevant bits from df output and convert from kilobytes to gigabytes. - JSL 2024-01-06 */
        double usedGb = Double.parseDouble(parts[2])/Math.pow(2,20);
        double totalGb = Double.parseDouble(parts[1])/Math.pow(2,20);
        double percentUsed = usedGb / totalGb * 100;

        return new DiskUsageInfo(percentUsed, usedGb, totalGb);
    }
    
    
    /**
     * This parses disk usage on windows, tested on w11.
     * * >cmd.exe /c fsutil volume diskfree c:\Users\james
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
     * * @param outputLines lines to parse
     * @return A data structure with disk usage
     */
    @Deprecated
    public static DiskUsageInfo parseDiskUsageInfoWindows(List<String> outputLines) {
        double freeGb = 0;
        double usedGb = 0;
        double totalGb = 0;
        boolean usedBytesDetected = false;
        for (int i = 0; i < outputLines.size(); i++) {
            String line = outputLines.get(i);
            if (line.contains("Total bytes")
                    || line.contains("Total de bytes:") // spanish
                    ) {
                line = line.split(":")[1].trim().split("\\s+")[0];
                String bytes = line.replace(",", "");
                long totalBytes = Long.parseLong(bytes);
                totalGb = (double) totalBytes / (double) (1024.0 * 1024.0 * 1024.0);
            } else if (line.contains("Used bytes")) {
                line = line.split(":")[1].trim().split("\\s+")[0];
                String bytes = line.replace(",", "");
                long usedBytes = Long.parseLong(bytes);
                usedGb = (double) usedBytes / (double) (1024.0 * 1024.0 * 1024.0);
                usedBytesDetected = true;
            } else if (line.contains("Total free bytes")
                    || line.contains("Total de bytes:") // spanish
                    ) {
                line = line.split(":")[1].trim().split("\\s+")[0];
                String bytes = line.replace(",", "");
                long freeBytes = Long.parseLong(bytes);
                freeGb = (double) freeBytes / (double) (1024.0 * 1024.0 * 1024.0);
            }
        }
        if (!usedBytesDetected) {
            usedGb = totalGb - freeGb;
        }
        double percentUsed = usedGb / totalGb * 100;
        System.out.println("-------------------------------------");
        System.out.println("freeGb=" + freeGb);
        System.out.println("usedGb=" + usedGb);
        System.out.println("totalGb=" + totalGb);
        System.out.println("percentUsed=" + percentUsed);
        return new DiskUsageInfo(percentUsed, freeGb, usedGb, totalGb);
    }
    
    public static String getProcessorNameWindows() {
        try {
            ProcessBuilder pb = new ProcessBuilder("wmic", "cpu", "get", "Name");
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("Name") && !line.trim().isEmpty()) {
                        return line.trim();
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Handle error if the process didn't exit successfully
                LOGGER.log(Level.SEVERE,
                        "Failed to get processor name. Exit code: {0}", exitCode);
            }
        } catch (IOException e) {
            // wmic may not be available on newer Windows versions (e.g. Windows 11)
            LOGGER.log(Level.INFO, "wmic not available, falling back to PowerShell: {0}", e.getMessage());
            return getProcessorNameWindowsPowerShell();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }

        return ""; // Return an empty string if no processor name was found
    }

    static String getProcessorNameWindowsPowerShell() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-Command",
                    "Get-CimInstance -Class Win32_Processor | Select-Object -ExpandProperty Name");
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        return line.trim();
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.log(Level.SEVERE,
                        "Failed to get processor name via PowerShell. Exit code: {0}", exitCode);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }
        return "";
    }
    
    
    public static String getProcessorNameLinux() {
        try {
            // Use lscpu command to get details about the CPU
            ProcessBuilder pb = new ProcessBuilder("lscpu");
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C"); // set language to english
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Search for the line starting with "Model name:"
                    if (line.startsWith("Model name:")) {
                        // Extract the processor name after the colon
                        return line.substring(line.indexOf(":") + 2).trim();
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Handle error if the process didn't exit successfully
                Logger.getLogger(UtilOs.class.getName()).log(Level.SEVERE,
                       "Failed to get processor name. Exit code: {0}", exitCode);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }

        return ""; // Return an empty string if no processor name was found
    }

    // ─── System ID ────────────────────────────────────────────────────────────

    /**
     * Returns a stable, non-PII system identifier suitable for anonymous
     * benchmark attribution.
     *
     * <p>Strategy (first successful source wins):
     * <ol>
     *   <li>Windows &mdash; {@code MachineGuid} from the Cryptography registry key
     *       (readable without admin)</li>
     *   <li>Linux   &mdash; {@code /etc/machine-id} (world-readable)</li>
     *   <li>macOS   &mdash; {@code IOPlatformUUID} via {@code ioreg} (no admin)</li>
     *   <li>Fallback &mdash; the previously persisted {@code systemId} from
     *       {@code jdm.properties}, or a freshly generated {@link java.util.UUID}</li>
     * </ol>
     *
     * <p>The raw OS value is SHA-256 hashed and the first 32 hex characters are
     * returned, so the original system identifier is never stored or transmitted.
     *
     * @param osName      the value of {@code System.getProperty("os.name")}
     * @param persistedId the value already stored in {@code jdm.properties}
     *                    (may be {@code null} or blank on first run)
     * @return a 32-character lowercase hex string identifying this system
     */
    public static String getMachineSystemId(String osName, String persistedId) {
        String raw = null;
        try {
            if (isWindows(osName)) {
                raw = readWindowsMachineGuid();
            } else if (isLinux(osName)) {
                raw = readLinuxMachineId();
            } else if (isMacOs(osName)) {
                raw = readMacOsPlatformUuid();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "getMachineSystemId: OS source failed, using fallback", e);
        }

        if (raw != null && !raw.isBlank()) {
            return sha256Hex32(raw);
        }

        // Fallback: reuse persisted id (survives across sessions) or generate once.
        if (persistedId != null && !persistedId.isBlank()) {
            return persistedId;
        }
        LOGGER.warning("getMachineSystemId: all sources failed, generating a random id");
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    /** Reads HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid (no admin required). */
    private static String readWindowsMachineGuid() throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
                "reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                "/v", "MachineGuid")
                .redirectErrorStream(true)
                .start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("MachineGuid")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        return parts[parts.length - 1].trim();
                    }
                }
            }
        }
        p.waitFor();
        return null;
    }

    /** Reads /etc/machine-id (world-readable on all mainstream Linux distros). */
    private static String readLinuxMachineId() throws IOException {
        java.nio.file.Path mid = java.nio.file.Paths.get("/etc/machine-id");
        if (java.nio.file.Files.exists(mid)) {
            return java.nio.file.Files.readString(mid).trim();
        }
        java.nio.file.Path dbus = java.nio.file.Paths.get("/var/lib/dbus/machine-id");
        if (java.nio.file.Files.exists(dbus)) {
            return java.nio.file.Files.readString(dbus).trim();
        }
        return null;
    }

    /** Reads IOPlatformUUID via ioreg (no admin required on macOS). */
    private static String readMacOsPlatformUuid() throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
                "ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                .redirectErrorStream(true)
                .start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("IOPlatformUUID")) {
                    int eq = line.indexOf('=');
                    if (eq >= 0) {
                        return line.substring(eq + 1).trim().replace("\"", "");
                    }
                }
            }
        }
        p.waitFor();
        return null;
    }

    /** Returns the first 32 hex characters of the SHA-256 hash of {@code input}. */
    private static String sha256Hex32(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // SHA-256 is mandatory in every JVM
        }
    }

    // -----------------------------------------------------------------------
    // Drive attributes — Windows and Linux (no admin required)
    // -----------------------------------------------------------------------

    /**
     * Returns the filesystem type for the given drive letter (e.g. "NTFS", "FAT32").
     * Uses PowerShell {@code Get-Volume}. No admin required.
     *
     * @param driveLetter single letter, e.g. "C"
     * @return filesystem type or {@code null} on failure
     */
    static String getFilesystemWindows(String driveLetter) {
        String psCmd = "(Get-Volume -DriveLetter '" + driveLetter + "').FileSystemType";
        return runPowerShellOneLiner(psCmd);
    }

    /**
     * Returns the bus/interface type for the given drive letter (e.g. "NVMe", "SATA", "USB").
     * Uses PowerShell {@code Get-Partition | Get-Disk}. No admin required.
     *
     * @param driveLetter single letter, e.g. "C"
     * @return bus type or {@code null} on failure
     */
    static String getBusTypeWindows(String driveLetter) {
        String psCmd = "Get-Partition -DriveLetter '" + driveLetter
                + "' | Get-Disk | Select-Object -ExpandProperty BusType";
        return runPowerShellOneLiner(psCmd);
    }

    /**
     * Returns the sector size for the given drive letter (e.g. "512 B", "512 B / 4096 B").
     * When logical and physical sector sizes differ, both are shown.
     * Uses PowerShell {@code Get-Partition | Get-Disk}. No admin required.
     *
     * @param driveLetter single letter, e.g. "C"
     * @return sector size string or {@code null} on failure
     */
    static String getSectorSizeWindows(String driveLetter) {
        String logicalStr = runPowerShellOneLiner(
                "Get-Partition -DriveLetter '" + driveLetter
                        + "' | Get-Disk | Select-Object -ExpandProperty LogicalSectorSize");
        String physicalStr = runPowerShellOneLiner(
                "Get-Partition -DriveLetter '" + driveLetter
                        + "' | Get-Disk | Select-Object -ExpandProperty PhysicalSectorSize");
        if (logicalStr == null && physicalStr == null) return null;

        // Format like pydiskmark: "512 B" or "512 B / 4096 B"
        if (logicalStr != null && physicalStr != null && !logicalStr.equals(physicalStr)) {
            return logicalStr + " B / " + physicalStr + " B";
        } else if (logicalStr != null) {
            return logicalStr + " B";
        } else {
            return physicalStr + " B";
        }
    }

    /**
     * Returns the physical drive number for the given drive letter (e.g. "1" or "0").
     * Uses PowerShell {@code Get-Partition -DriveLetter <Letter>}. Falls back to WMI if needed.
     *
     * @param driveLetter single letter, e.g. "C"
     * @return physical drive number string or {@code null} on failure
     */
    public static String getPhysicalDriveNumberWindows(String driveLetter) {
        if (driveLetter == null || driveLetter.trim().isEmpty()) {
            return null;
        }
        String letter = driveLetter.trim().substring(0, 1).toUpperCase();
        
        // 1. Try using Get-Partition
        String diskNum = runPowerShellOneLiner(
                "Get-Partition -DriveLetter '" + letter + "' | Select-Object -ExpandProperty DiskNumber");
        if (diskNum != null) {
            diskNum = diskNum.trim();
            if (diskNum.matches("\\d+")) {
                return diskNum;
            }
        }
        
        // 2. Fallback using WMI / Get-CimInstance
        String fallbackNum = runPowerShellOneLiner(
                "Get-CimInstance Win32_LogicalDiskToPartition | " +
                "Where-Object { `$_.Dependent.DeviceId -eq '" + letter + ":' } | " +
                "ForEach-Object { `$_.Antecedent.DeviceId }");
        if (fallbackNum != null && fallbackNum.contains("Disk #")) {
            String parts[] = fallbackNum.split("Disk #");
            if (parts.length > 1) {
                String diskNumPart = parts[1].split(",")[0].trim();
                if (diskNumPart.matches("\\d+")) {
                    return diskNumPart;
                }
            }
        }
        
        return null;
    }


    /**
     * Runs a single PowerShell command and returns the first non-blank line of
     * output, or {@code null} on any error. Timeout: 15 seconds.
     */
    private static String runPowerShellOneLiner(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                        return trimmed;
                    }
                }
            }
            process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "PowerShell command failed: " + command, e);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Drive attributes — Linux
    // -----------------------------------------------------------------------

    /**
     * Returns the filesystem type for the given path on Linux (e.g. "ext4", "xfs").
     * Uses {@code df -T}. No admin required.
     *
     * <p>Example output:
     * <pre>
     * Filesystem     Type  1K-blocks     Used Available Use% Mounted on
     * /dev/sda2      ext4  238737052 54179492 172357524  24% /
     * </pre>
     *
     * @param path path on the target filesystem
     * @return filesystem type or {@code null} on failure
     */
    static String getFilesystemLinux(Path path) {
        try {
            ProcessBuilder pb = new ProcessBuilder("df", "-T", path.toString());
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip the header line; data lines start with /dev/ or a device name
                    if (line.startsWith("/dev/") || line.contains("/dev/")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            return parts[1]; // Type column
                        }
                    }
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "df -T failed for " + path, e);
        }
        return null;
    }

    /**
     * Returns the bus/interface type for the given path on Linux (e.g. "nvme", "sata", "usb").
     * Uses {@code lsblk -no TRAN}. No admin required.
     *
     * @param path path on the target filesystem
     * @return bus type (uppercased) or {@code null} on failure
     */
    static String getBusTypeLinux(Path path) {
        String partition = getPartitionFromFilePathLinux(path);
        if (partition == null || partition.isBlank()) return null;
        for (String disk : getBaseDisksLinux(partition)) {
            String diskTran = lsblkTran("/dev/" + disk);
            if (diskTran != null) return diskTran;
        }
        // TRAN is only reported on the parent disk device, not on partitions.
        // Try the partition first; if empty, resolve the parent device and retry.
        String tran = lsblkTran(partition);
        if (tran != null) return tran;

        List<String> parents = getDeviceNamesFromPartitionLinux(partition);
        if (!parents.isEmpty()) {
            tran = lsblkTran("/dev/" + parents.getFirst());
            if (tran != null) return tran;
        }
        return null;
    }

    private static String lsblkTran(String device) {
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", "-no", "TRAN", device);
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed.toUpperCase();
                    }
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "lsblk TRAN failed for " + device, e);
        }
        return null;
    }

    /**
     * Detects the negotiated USB link speed for a block device by reading
     * the {@code speed} file from its sysfs USB ancestor. Returns a
     * human-readable USB version string (e.g. {@code "3.0"}), or
     * {@code null} if the device is not USB-attached or detection fails.
     *
     * @param path path on the target filesystem
     * @return USB version string or {@code null}
     */
    static String getUsbVersionLinux(Path path) {
        String partition = getPartitionFromFilePathLinux(path);
        if (partition == null || partition.isBlank()) return null;

        List<String> disks = getBaseDisksLinux(partition);
        if (disks.isEmpty()) {
            disks = getDeviceNamesFromPartitionLinux(partition);
        }
        String devName = disks.isEmpty()
                ? partition.replace("/dev/", "")
                : disks.getFirst().trim();
        if (devName.isEmpty() || devName.contains("/")) return null;

        try {
            java.nio.file.Path sysPath = java.nio.file.Path.of("/sys/block", devName);
            if (!java.nio.file.Files.exists(sysPath)) return null;
            java.nio.file.Path realPath = sysPath.toRealPath();

            // The nearest ancestor with idVendor is the USB device itself; its
            // speed file holds the negotiated link rate in Mbps.
            java.nio.file.Path current = realPath;
            while (current != null && current.getNameCount() > 0) {
                java.nio.file.Path speedFile = current.resolve("speed");
                if (java.nio.file.Files.isRegularFile(speedFile)
                        && java.nio.file.Files.isRegularFile(current.resolve("idVendor"))) {
                    String speed = java.nio.file.Files.readString(speedFile).trim();
                    return mapUsbSpeed(speed, readUsbLanes(current));
                }
                current = current.getParent();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "USB version detection failed for " + devName, e);
        }
        return null;
    }

    /** Reads {@code rx_lanes} (kernel 4.18+); defaults to 1 when absent. */
    private static int readUsbLanes(java.nio.file.Path usbDevice) {
        try {
            java.nio.file.Path lanes = usbDevice.resolve("rx_lanes");
            if (java.nio.file.Files.isRegularFile(lanes)) {
                return Integer.parseInt(java.nio.file.Files.readString(lanes).trim());
            }
        } catch (IOException | NumberFormatException e) {
            LOGGER.log(Level.FINE, "rx_lanes unreadable for " + usbDevice, e);
        }
        return 1;
    }

    static String mapUsbSpeed(String speedMbps, int lanes) {
        if (speedMbps == null) return null;
        return switch (speedMbps.trim()) {
            case "1.5"   -> "1.0";
            case "12"    -> "1.1";
            case "480"   -> "2.0";
            case "5000"  -> "3.0";
            case "10000" -> lanes == 2 ? "3.2 Gen 1x2" : "3.2 Gen 2";
            case "20000" -> "3.2 Gen 2x2";
            default      -> null;
        };
    }

    /**
     * Returns the detailed OS release label stored as {@code osLabel}, e.g.
     * {@code "Ubuntu 24.04.1 LTS"}, {@code "macOS 15.1"} or
     * {@code "Windows 11 23H2 (build 26100)"}. Falls back to {@code osName}.
     *
     * @param osName the value of {@code System.getProperty("os.name")}
     * @return the OS label, never {@code null} when {@code osName} is non-null
     */
    static String getOsLabel(String osName) {
        String label = null;
        if (isLinux(osName)) {
            label = getLinuxDistroName();
        } else if (isMacOs(osName)) {
            label = getMacOsLabel();
        } else if (isWindows(osName)) {
            label = getWindowsLabel(osName);
        }
        return label != null ? label : osName;
    }

    /**
     * Formats an OS for display. Linux shows the distro in parentheses
     * (e.g. {@code "Linux (Ubuntu 24.04.1 LTS)"}); other platforms show the
     * label alone since it already names the OS.
     */
    static String formatOsDisplay(String os, String osLabel) {
        if (osLabel == null || osLabel.isBlank() || osLabel.equals(os)) return os;
        if (os == null) return osLabel;
        return isLinux(os) ? os + " (" + osLabel + ")" : osLabel;
    }

    /**
     * Returns the Linux distribution name from {@code PRETTY_NAME} in
     * os-release. {@code /run/host/os-release} is checked first so Flatpak
     * reports the host distro instead of the sandbox runtime.
     */
    static String getLinuxDistroName() {
        for (String file : List.of("/run/host/os-release", "/etc/os-release", "/usr/lib/os-release")) {
            Path osRelease = Path.of(file);
            if (!java.nio.file.Files.isReadable(osRelease)) continue;
            try {
                for (String line : java.nio.file.Files.readAllLines(osRelease)) {
                    if (line.startsWith("PRETTY_NAME=")) {
                        String value = line.substring("PRETTY_NAME=".length()).trim();
                        if (value.length() >= 2
                                && (value.startsWith("\"") || value.startsWith("'"))
                                && value.endsWith(value.substring(0, 1))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (!value.isBlank()) return value;
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read " + file, e);
            }
        }
        return null;
    }

    /** Returns e.g. {@code "macOS 15.1"} via {@code sw_vers}, or {@code null}. */
    static String getMacOsLabel() {
        String name = runForFirstLine("sw_vers", "-productName");
        String version = runForFirstLine("sw_vers", "-productVersion");
        if (version == null) version = System.getProperty("os.version");
        if (version == null || version.isBlank()) return null;
        return (name != null ? name : "macOS") + " " + version;
    }

    /**
     * Returns e.g. {@code "Windows 11 23H2 (build 26100)"} using the
     * registry, or {@code null} if no release details are available.
     * The registry {@code ProductName} is not used because it still reports
     * "Windows 10" on Windows 11.
     */
    static String getWindowsLabel(String osName) {
        String key = "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";
        String release = readWindowsRegistryValue(key, "DisplayVersion");
        if (release == null) release = readWindowsRegistryValue(key, "ReleaseId");
        String build = readWindowsRegistryValue(key, "CurrentBuild");
        if (release == null && build == null) return null;
        StringBuilder sb = new StringBuilder(osName);
        if (release != null) sb.append(' ').append(release);
        if (build != null) sb.append(" (build ").append(build).append(')');
        return sb.toString();
    }

    private static String readWindowsRegistryValue(String key, String name) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", key, "/v", name);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String value = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith(name) && trimmed.contains("REG_")) {
                        String[] parts = trimmed.split("\\s+", 3);
                        if (parts.length == 3 && !parts[2].isBlank()) {
                            value = parts[2].trim();
                        }
                    }
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            return value;
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "reg query failed for " + name, e);
            return null;
        }
    }

    private static String runForFirstLine(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String first;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                first = reader.readLine();
            }
            boolean done = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) process.destroy();
            if (!done || process.exitValue() != 0 || first == null || first.isBlank()) {
                return null;
            }
            return first.trim();
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Command failed: " + String.join(" ", command), e);
            return null;
        }
    }

    /**
     * Returns the sector size for the given path on Linux
     * (e.g. "512 B", "512 B / 4096 B").
     * Uses {@code lsblk -no LOG-SEC,PHY-SEC}. No admin required.
     *
     * @param path path on the target filesystem
     * @return sector size string or {@code null} on failure
     */
    static String getSectorSizeLinux(Path path) {
        String partition = getPartitionFromFilePathLinux(path);
        if (partition == null || partition.isBlank()) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", "-no", "LOG-SEC,PHY-SEC", partition);
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        String logical = parts[0];
                        String physical = parts[1];
                        if (logical.equals(physical)) {
                            return logical + " B";
                        }
                        return logical + " B / " + physical + " B";
                    } else if (parts.length == 1 && !parts[0].isEmpty()) {
                        return parts[0] + " B";
                    }
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "lsblk sector size failed for " + partition, e);
        }
        return null;
    }
}

