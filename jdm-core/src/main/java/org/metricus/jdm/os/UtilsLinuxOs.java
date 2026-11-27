package org.metricus.jdm.os;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdiskmark.App;
import jdiskmark.Util;

/**
 * Linux-specific utility methods for JDiskMark.
 */
public final class UtilsLinuxOs {

    private static final Logger LOGGER = Logger.getLogger(UtilsLinuxOs.class.getName());

    private UtilsLinuxOs() {}

    /**
     * Returns the motherboard vendor and model from the DMI sysfs files.
     * Falls back to the system vendor/product, then to the device tree model
     * on ARM boards without DMI. The serial number is never read.
     */
    public static Util.Motherboard getMotherboard() {
        return getMotherboard(Path.of("/sys/class/dmi/id"), Path.of("/proc/device-tree/model"));
    }

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
     * @return kernel names such as {@code sda} or {@code nvme0n1}; empty on failure
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
}
