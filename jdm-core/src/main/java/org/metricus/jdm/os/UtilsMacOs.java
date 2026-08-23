package org.metricus.jdm.os;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdiskmark.DiskUsageInfo;
import jdiskmark.UtilOs;

/**
 * macOS-specific utility methods for JDiskMark.
 */
public final class UtilsMacOs {

    private static final Logger LOGGER = Logger.getLogger(UtilsMacOs.class.getName());

    private UtilsMacOs() {}

    // -----------------------------------------------------------------------
    // Volume / device resolution
    // -----------------------------------------------------------------------

    /**
     * Returns the /dev/diskXsY device path for the volume containing
     * {@code path} by parsing {@code df -k} output.
     *
     * @param path any path on the target volume
     * @return device path (e.g. {@code /dev/disk4s1}) or {@code null} on failure
     */
    public static String getDeviceFromPath(Path path) {
        try {
            ProcessBuilder pb = new ProcessBuilder("df", "-k", path.toString());
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");
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
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }
        return null;
    }

    /**
     * Extracts the whole-disk device name from a macOS partition path.
     *
     * <p>macOS partitions follow the {@code /dev/disk<N>s<P>} convention, where
     * APFS volumes can be nested further (e.g. {@code /dev/disk1s5s1}).
     * {@code smartctl} requires the whole disk ({@code disk0}, {@code disk1}, …),
     * so this method strips everything after the first {@code s} suffix.
     *
     * @param partitionPath full device path from {@code df}, e.g. {@code /dev/disk1s5s1}
     * @return the whole-disk identifier, e.g. {@code disk1}, or {@code null} on parse failure
     */
    public static String getWholeDeviceName(String partitionPath) {
        if (partitionPath == null) return null;
        String dev = partitionPath.startsWith("/dev/") ? partitionPath.substring(5) : partitionPath;
        Matcher m = Pattern.compile("^(disk\\d+)").matcher(dev);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Returns the device model for the given macOS device path
     * (e.g. {@code /dev/disk4} or {@code /dev/disk4s1}).
     * Tries {@code diskutil info} first, falls back to {@code system_profiler}.
     *
     * @param devicePath the device path
     * @return device model string, or a fallback message on failure
     */
    public static String getDeviceModel(String devicePath) {
        if (devicePath == null || devicePath.isEmpty()) {
            throw new IllegalArgumentException("Invalid device path");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("diskutil", "info", devicePath);
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");
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
            LOGGER.log(Level.SEVERE, null, e);
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
                    String lineAfterId;
                    while ((lineAfterId = reader.readLine()) != null) {
                        if (lineAfterId.contains("Device Name: ")) {
                            return lineAfterId.split("Device Name: ")[1];
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }

        return "Model unavailable for " + deviceId;
    }

    /**
     * Returns the storage bus interface for the given macOS device path
     * by parsing the {@code Protocol} field from {@code diskutil info}.
     *
     * <p>Common values: {@code PCI-Express} (NVMe), {@code SATA},
     * {@code USB}, {@code Apple Fabric}.
     *
     * @param devicePath the device path (e.g. {@code /dev/disk4s1})
     * @return the protocol string, or null if unavailable
     */
    public static String getDriveInterfaceMacOs(String devicePath) {
        if (devicePath == null || devicePath.isEmpty()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("diskutil", "info", devicePath);
            Map<String, String> env = pb.environment();
            env.put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Protocol:")) {
                    String protocol = line.split("Protocol:")[1].trim();
                    // Normalise common macOS protocol values
                    if (protocol.contains("PCI-Express") || protocol.contains("PCI")) {
                        return "NVMe";
                    }
                    return protocol;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not detect drive interface on macOS", e);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Cache flush / drop
    // -----------------------------------------------------------------------

    /** Flushes pending writes to disk (delegates to the shared sync implementation). */
    public static void flushDataToDrive() {
        UtilOs.flushDataToDriveLinux();
    }

    /** Drops the OS write cache via {@code purge}. */
    public static void dropWriteCache() {
        String[] command = {"purge"};
        System.out.println("running: " + java.util.Arrays.toString(command));

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();
            int exitValue = process.waitFor();

            try (BufferedReader outputReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                System.out.println("Standard Output:");
                while ((line = outputReader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
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

    // -----------------------------------------------------------------------
    // Privilege detection
    // -----------------------------------------------------------------------

    /** Returns {@code true} when the process is running as root on macOS. */
    public static boolean isRunningAsRoot() {
        return UtilOs.isRunningAsRootLinux();
    }

    // -----------------------------------------------------------------------
    // Disk usage parsing
    // -----------------------------------------------------------------------

    /**
     * Parses macOS {@code df -k} output into a {@link DiskUsageInfo}.
     *
     * <p>Example:
     * <pre>
     * Filesystem     Size   Used  Avail Capacity iused               ifree %iused  Mounted on
     * /dev/disk1s1  466Gi  191Gi  273Gi    42%  947563 9223372036853828244   0%   /
     * </pre>
     *
     * @param outputLines lines from {@code df -k}
     * @return parsed usage object
     */
    public static DiskUsageInfo parseDiskUsageInfo(List<String> outputLines) {
        String usageLine = outputLines.get(1);
        String[] parts = usageLine.trim().split("\\s+");

        double usedGb  = Double.parseDouble(parts[2]) / Math.pow(2, 20);
        double totalGb = Double.parseDouble(parts[1]) / Math.pow(2, 20);
        double percentUsed = usedGb / totalGb * 100;

        return new DiskUsageInfo(percentUsed, usedGb, totalGb);
    }

    // -----------------------------------------------------------------------
    // Processor name
    // -----------------------------------------------------------------------

    /** Returns the CPU model string via {@code sysctl -n machdep.cpu.brand_string}. */
    public static String getProcessorName() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string");
            pb.environment().put("LC_ALL", "C");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line.trim();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }
        return "";
    }

    // -----------------------------------------------------------------------
    // Volume enumeration (new)
    // -----------------------------------------------------------------------

    /**
     * Returns all user-visible mounted volumes on macOS by parsing {@code mount}
     * output. System-internal volumes tagged {@code nobrowse} are excluded.
     * Analogous to {@link UtilOs#getMountedDrivesLinux()}.
     *
     * <p>Example {@code mount} lines:
     * <pre>
     * /dev/disk3s1s1 on / (apfs, sealed, local, read-only, journaled)
     * /dev/disk4s1 on /Volumes/SANDISK USB (msdos, local, nodev, nosuid, ...)
     * /dev/disk3s5 on /System/Volumes/Data (apfs, local, journaled, nobrowse, ...)
     * </pre>
     *
     * @return list of mount-point {@link File}s with non-zero total space
     */
    public static List<File> getMountedDrives() {
        List<File> mounts = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("mount");
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("/dev/disk")) continue;
                    if (line.contains("nobrowse")) continue;

                    // Format: /dev/diskXsY on /mount/point (options)
                    int onIdx    = line.indexOf(" on ");
                    int parenIdx = line.indexOf(" (", onIdx);
                    if (onIdx < 0 || parenIdx < 0) continue;

                    String mountPoint = line.substring(onIdx + 4, parenIdx);
                    File mountDir = new File(mountPoint);
                    if (mountDir.getTotalSpace() == 0) continue;
                    mounts.add(mountDir);
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to read mount output on macOS", e);
        }
        return mounts;
    }

    // -----------------------------------------------------------------------
    // Drive attributes (new)
    // -----------------------------------------------------------------------

    /**
     * Returns the bus/interface type for the volume containing {@code path}
     * (e.g. {@code "USB"}, {@code "NVMe"}, {@code "SATA"}).
     * Uses {@code diskutil info} on the whole disk. No admin required.
     *
     * @param path any path on the target volume
     * @return bus type or {@code null} on failure
     */
    public static String getBusType(Path path) {
        String partition = getDeviceFromPath(path);
        if (partition == null) return null;
        String wholeDisk = getWholeDeviceName(partition);
        if (wholeDisk == null) return null;
        for (String line : runDiskutilInfo("/dev/" + wholeDisk)) {
            if (line.contains("Protocol:")) {
                return line.split("Protocol:")[1].trim();
            }
        }
        return null;
    }

    /**
     * Returns the filesystem type for the volume containing {@code path}
     * (e.g. {@code "MS-DOS FAT32"}, {@code "APFS"}).
     * Uses {@code diskutil info} on the partition. No admin required.
     *
     * @param path any path on the target volume
     * @return filesystem name or {@code null} on failure
     */
    public static String getFilesystem(Path path) {
        String partition = getDeviceFromPath(path);
        if (partition == null) return null;
        for (String line : runDiskutilInfo(partition)) {
            if (line.contains("File System Personality:")) {
                return line.split("File System Personality:")[1].trim();
            }
        }
        return null;
    }

    /**
     * Returns the logical sector size for the volume containing {@code path}
     * (e.g. {@code "512 B"}).
     * Uses {@code diskutil info} on the partition. No admin required.
     *
     * @param path any path on the target volume
     * @return sector size string or {@code null} on failure
     */
    public static String getSectorSize(Path path) {
        String partition = getDeviceFromPath(path);
        if (partition == null) return null;
        for (String line : runDiskutilInfo(partition)) {
            if (line.contains("Device Block Size:")) {
                // "   Device Block Size:         512 Bytes"
                String raw = line.split("Device Block Size:")[1].trim();
                String[] parts = raw.split("\\s+");
                return parts[0] + " B";
            }
        }
        return null;
    }

    /**
     * Returns the USB version string for the device at {@code path}
     * (e.g. {@code "3.2 Gen 1"}, {@code "3.2 Gen 2"}), or {@code null} when
     * the device is not USB-attached or no generation string is found.
     *
     * <p>Parses the {@code Device / Media Name:} field from {@code diskutil info}
     * on the whole disk (e.g. {@code "SanDisk 3.2Gen1"}) — the only source
     * available without admin/IOKit access.
     *
     * @param path any path on the target volume
     * @return USB version string or {@code null}
     */
    public static String getUsbVersion(Path path) {
        String partition = getDeviceFromPath(path);
        if (partition == null) return null;
        String wholeDisk = getWholeDeviceName(partition);
        if (wholeDisk == null) return null;
        for (String line : runDiskutilInfo("/dev/" + wholeDisk)) {
            if (line.contains("Device / Media Name:")) {
                String name = line.split("Device / Media Name:")[1].trim();
                return parseUsbVersionFromName(name);
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Runs {@code diskutil info <device>} and returns all output lines.
     * Returns an empty list on any error.
     */
    private static List<String> runDiskutilInfo(String device) {
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("diskutil", "info", device);
            pb.environment().put("LC_ALL", "C");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "diskutil info failed for " + device, e);
        }
        return lines;
    }

    /**
     * Extracts a human-readable USB version string from a device media name.
     *
     * <p>Handles patterns such as:
     * <ul>
     *   <li>{@code "SanDisk 3.2Gen1"}  → {@code "3.2 Gen 1"}</li>
     *   <li>{@code "Kingston 3.2Gen2"} → {@code "3.2 Gen 2"}</li>
     *   <li>{@code "USB 3.0 Flash"}    → {@code "3.0"}</li>
     * </ul>
     *
     * @param name the {@code Device / Media Name} string from {@code diskutil info}
     * @return USB version string, or {@code null} if not recognisable
     */
    private static String parseUsbVersionFromName(String name) {
        if (name == null || name.isBlank()) return null;
        // e.g. "3.2Gen1", "3.2Gen2", "3.2 Gen 2x2"
        Matcher m = Pattern.compile("(\\d\\.\\d)\\s*Gen\\s*(\\d(?:x\\d)?)",
                Pattern.CASE_INSENSITIVE).matcher(name);
        if (m.find()) {
            return m.group(1) + " Gen " + m.group(2);
        }
        // Fallback: bare "USB 3.0" or "USB3.0" style
        m = Pattern.compile("USB\\s*(\\d\\.\\d)", Pattern.CASE_INSENSITIVE).matcher(name);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
