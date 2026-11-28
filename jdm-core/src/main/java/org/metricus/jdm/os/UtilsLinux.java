package org.metricus.jdm.os;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jdiskmark.App;
import jdiskmark.DiskUsageInfo;
import org.metricus.jdm.util.Util;

/**
 * Linux-specific utility methods for JDiskMark.
 */
public final class UtilsLinux {

    private static final Logger LOGGER = Logger.getLogger(UtilsLinux.class.getName());

    private UtilsLinux() {}

    /**
     * Returns the motherboard and system vendor/model from the DMI sysfs
     * files. See {@link #getMotherboard(Path, Path)} for the board fallbacks.
     */
    public static Util.HardwareInfo getHardwareInfo() {
        Path dmiDir = Path.of("/sys/class/dmi/id");
        return new Util.HardwareInfo(
                getMotherboard(dmiDir, Path.of("/proc/device-tree/model")),
                Util.SystemModel.of(
                        readFirstLine(dmiDir.resolve("sys_vendor")),
                        readFirstLine(dmiDir.resolve("product_name"))));
    }

    /**
     * Returns the motherboard vendor and model from the DMI sysfs files.
     * Falls back to the system vendor/product, then to the device tree model
     * on ARM boards without DMI. The serial number is never read.
     */
    static Util.Motherboard getMotherboard(Path dmiDir, Path deviceTreeModel) {
        Util.Motherboard board = Util.Motherboard.of(
                readFirstLine(dmiDir.resolve("board_vendor")),
                readFirstLine(dmiDir.resolve("board_name")));
        if (board.model() == null) {
            Util.Motherboard product = Util.Motherboard.of(
                    readFirstLine(dmiDir.resolve("sys_vendor")),
                    readFirstLine(dmiDir.resolve("product_name")));
            if (product.model() != null) {
                board = product;
            }
        }
        if (board.isEmpty()) {
            board = Util.Motherboard.of(null, readFirstLine(deviceTreeModel));
        }
        return board;
    }

    private static String readFirstLine(Path file) {
        try {
            return Files.isReadable(file) ? Files.readString(file).trim() : null;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Unable to read " + file, e);
            return null;
        }
    }

    /**
     * Returns the negotiated USB link version (e.g. {@code "3.0"}, {@code "3.2 Gen 2"})
     * from sysfs for a Linux path, or {@code null} when not USB-attached.
     */
    public static String getUsbVersion(Path path) {
        Path usbDir = findUsbSysfsDir(path);
        if (usbDir == null) return null;
        try {
            Path speedFile = usbDir.resolve("speed");
            if (Files.isRegularFile(speedFile)) {
                String speed = Files.readString(speedFile).trim();
                return mapUsbSpeed(speed, readUsbLanes(usbDir));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "USB version detection failed for " + usbDir, e);
        }
        return null;
    }

    /**
     * Returns the USB Vendor ID and Product ID from sysfs for a Linux path,
     * or {@code null} when not USB-attached or unavailable.
     */
    public static Util.UsbIds getUsbIds(Path path) {
        Path usbDir = findUsbSysfsDir(path);
        if (usbDir == null) return null;
        try {
            Path vidFile = usbDir.resolve("idVendor");
            Path pidFile = usbDir.resolve("idProduct");
            if (Files.isRegularFile(vidFile) && Files.isRegularFile(pidFile)) {
                String vid = normalizeHexId(Files.readString(vidFile));
                String pid = normalizeHexId(Files.readString(pidFile));
                if (vid != null && pid != null) {
                    return new Util.UsbIds(vid, pid);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "USB VID/PID detection failed for " + usbDir, e);
        }
        return null;
    }

    /**
     * Walks up the sysfs device hierarchy from {@code /sys/block/<dev>} to find
     * the nearest ancestor directory representing the USB device (containing {@code idVendor}).
     */
    private static Path findUsbSysfsDir(Path path) {
        String partition = getPartitionFromFilePath(path);
        if (partition == null || partition.isBlank()) return null;

        List<String> disks = getBaseDisks(partition);
        if (disks.isEmpty()) {
            disks = getDeviceNamesFromPartition(partition);
        }
        String devName = disks.isEmpty()
                ? partition.replace("/dev/", "")
                : disks.getFirst().trim();
        if (devName.isEmpty() || devName.contains("/")) return null;

        try {
            Path sysPath = Path.of("/sys/block", devName);
            if (!Files.exists(sysPath)) return null;
            Path current = sysPath.toRealPath();
            while (current != null && current.getNameCount() > 0) {
                if (Files.isRegularFile(current.resolve("idVendor"))) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "USB sysfs lookup failed for " + devName, e);
        }
        return null;
    }

    /**
     * Normalizes a sysfs USB hex ID (e.g. {@code "0x0781"}, {@code "781"})
     * to a 4-character lowercase hex string.
     */
    public static String normalizeHexId(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        if (s.startsWith("0x")) {
            s = s.substring(2);
        }
        if (s.isEmpty() || !s.matches("[0-9a-f]{1,4}")) {
            return null;
        }
        return String.format("%4s", s).replace(' ', '0');
    }

    /** Reads {@code rx_lanes} (kernel 4.18+); defaults to 1 when absent. */
    private static int readUsbLanes(Path usbDevice) {
        try {
            Path lanes = usbDevice.resolve("rx_lanes");
            if (Files.isRegularFile(lanes)) {
                return Integer.parseInt(Files.readString(lanes).trim());
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
     * On Linux OS get the device path when given a file path.
     * e.g. {@code filePath = /home/james/Desktop/jdm-data} -> {@code /dev/sda2}
     *
     * @param path the file path
     * @return the device path, or {@code null} on failure
     */
    public static String getPartitionFromFilePath(Path path) {
        if (App.verbose) {
            System.out.println("filePath=" + path.toString());
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("df", "-k", path.toString());
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");
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
     * Returns a list of physical drive names for a partition, handling multiple
     * physical drives in case the partition is part of an LVM or RAID on Linux.
     *
     * @param partition the partition to look up
     * @return list of physical drives
     */
    public static List<String> getDeviceNamesFromPartition(String partition) {
        List<String> deviceNames = new ArrayList<>();
        if (partition == null) {
            return deviceNames;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("lsblk", "-no", "pkname", partition);
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
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
     * @return kernel names such as {@co    public static boolean isRunningAsRootLinux() {
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
    }de sda} or {@code nvme0n1}; empty on failure
     */
    public static List<String> getBaseDisks(String device) {
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
    
    public static boolean isRunningAsRoot() {
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
    public static String getFilesystem(Path path) {
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
    public static String getBusType(Path path) {
        String partition = UtilsLinux.getPartitionFromFilePath(path);
        if (partition == null || partition.isBlank()) return null;
        for (String disk : UtilsLinux.getBaseDisks(partition)) {
            String diskTran = lsblkTran("/dev/" + disk);
            if (diskTran != null) return diskTran;
        }
        // TRAN is only reported on the parent disk device, not on partitions.
        // Try the partition first; if empty, resolve the parent device and retry.
        String tran = lsblkTran(partition);
        if (tran != null) return tran;

        List<String> parents = UtilsLinux.getDeviceNamesFromPartition(partition);
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
     * Returns the sector size for the given path on Linux
     * (e.g. "512 B", "512 B / 4096 B").
     * Uses {@code lsblk -no LOG-SEC,PHY-SEC}. No admin required.
     *
     * @param path path on the target filesystem
     * @return sector size string or {@code null} on failure
     */
    public static String getSectorSize(Path path) {
        String partition = UtilsLinux.getPartitionFromFilePath(path);
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
    
    /**
     * Returns the Linux distribution name from {@code PRETTY_NAME} in
     * os-release. {@code /run/host/os-release} is checked first so Flatpak
     * reports the host distro instead of the sandbox runtime.
     */
    static String getDistroName() {
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
    
    /**
     * GH-2 flush data to disk
     */
    public static void flushDataToDrive() {
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
    public static boolean dropWriteCache() {

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
    
    /**
     * $ df -h /home/james
     * Filesystem      Size  Used Avail Use% Mounted on
     * /dev/sda2       228G   52G  165G  24% /
     * * @param outputLines
     * @return usage object
     */
    public static DiskUsageInfo parseDiskUsageInfo(List<String> outputLines) {
        String usageLine = outputLines.get(1); // Assuming the relevant information is on the second line
        String[] parts = usageLine.trim().split("\\s+");

        /* Grab relevant bits from df output and convert from kilobytes to gigabytes. - JSL 2024-01-06 */
        double usedGb = Double.parseDouble(parts[2])/Math.pow(2,20);
        double totalGb = Double.parseDouble(parts[1])/Math.pow(2,20);
        double percentUsed = usedGb / totalGb * 100;

        return new DiskUsageInfo(percentUsed, usedGb, totalGb);
    }
    
    public static String getProcessorName() {
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
    static public String getDeviceModel(String devicePath) {
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
    static public String getVendorModel(String devicePath) {
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
    static public String getDeviceSize(String devicePath) {
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
}
