package jdiskmark;

import static jdiskmark.App.MEGABYTE;
import static jdiskmark.Benchmark.BlockSequence.RANDOM;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates raw-device (physical drive) read benchmarking.
 * Requires admin/root privileges to open the device.
 */
public class DriveReader {

    private static final Logger LOG = Logger.getLogger(DriveReader.class.getName());

    private static final double USABLE_DEVICE_FRACTION = 0.9;

    private static Boolean cachedAdminStatus;

    // -----------------------------------------------------------------------
    // Admin detection
    // -----------------------------------------------------------------------

    public static boolean isRunningAsAdmin() {
        if (cachedAdminStatus != null) return cachedAdminStatus;
        if (App.isWindows()) {
            cachedAdminStatus = isAdminWindows();
        } else {
            cachedAdminStatus = "root".equals(System.getProperty("user.name"));
        }
        return cachedAdminStatus;
    }

    private static boolean isAdminWindows() {
        try {
            ProcessBuilder pb = new ProcessBuilder("net", "session");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // drain output to prevent blocking
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                while (r.readLine() != null) { /* discard */ }
            }
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            LOG.log(Level.FINE, "Admin check failed", e);
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Device path resolution
    // -----------------------------------------------------------------------

    public static String resolveDevicePath(Path dataDir) {
        if (App.isWindows()) {
            return resolveDevicePathWindows(dataDir);
        } else if (App.isMacOs()) {
            return resolveDevicePathMacOs(dataDir);
        } else {
            return resolveDevicePathLinux(dataDir);
        }
    }

    private static String resolveDevicePathWindows(Path dataDir) {
        String letter = UtilOs.getDriveLetterWindows(dataDir);
        if (letter == null || "unknown".equals(letter)) {
            LOG.warning("Could not resolve drive letter for: " + dataDir);
            return null;
        }
        String driveNum = UtilOs.getPhysicalDriveNumberWindows(letter);
        if (driveNum == null) {
            LOG.warning("Could not resolve physical drive number for letter: " + letter);
            return null;
        }
        return "\\\\.\\PhysicalDrive" + driveNum;
    }

    private static String resolveDevicePathLinux(Path dataDir) {
        String partition = UtilOs.getPartitionFromFilePathLinux(dataDir);
        if (partition == null) {
            LOG.warning("Could not resolve partition for: " + dataDir);
            return null;
        }
        List<String> parents = UtilOs.getDeviceNamesFromPartitionLinux(partition);
        if (parents != null && !parents.isEmpty()) {
            String devName = parents.get(0).trim();
            if (!devName.isEmpty()) {
                return "/dev/" + devName;
            }
        }
        // fallback: strip trailing partition digits from partition path
        // e.g. /dev/sda1 -> /dev/sda, /dev/nvme0n1p1 -> /dev/nvme0n1
        return stripPartitionSuffix(partition);
    }

    private static String resolveDevicePathMacOs(Path dataDir) {
        String partition = UtilOs.getPartitionFromFilePathLinux(dataDir);
        if (partition == null) {
            LOG.warning("Could not resolve partition for: " + dataDir);
            return null;
        }
        // macOS: /dev/disk2s1 -> /dev/rdisk2
        // strip the slice suffix (s1, s2, etc.)
        String base = partition.replaceAll("s\\d+$", "");
        // switch from buffered to raw character device
        return base.replace("/dev/disk", "/dev/rdisk");
    }

    static String stripPartitionSuffix(String partition) {
        if (partition == null) return null;
        // NVMe: /dev/nvme0n1p1 -> /dev/nvme0n1
        if (partition.matches(".*/nvme\\d+n\\d+p\\d+$")) {
            return partition.replaceAll("p\\d+$", "");
        }
        // SCSI/SATA: /dev/sda1 -> /dev/sda
        return partition.replaceAll("\\d+$", "");
    }

    // -----------------------------------------------------------------------
    // Device size query
    // -----------------------------------------------------------------------

    public static long getDeviceSizeBytes(String devicePath) {
        if (App.isWindows()) {
            return getDeviceSizeWindows(devicePath);
        } else {
            return getDeviceSizeUnix(devicePath);
        }
    }

    private static long getDeviceSizeUnix(String devicePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("blockdev", "--getsize64", devicePath);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                p.waitFor();
                if (line != null) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "blockdev --getsize64 failed for " + devicePath, e);
        }
        return -1;
    }

    private static long getDeviceSizeWindows(String devicePath) {
        // Use RandomAccessFile — FileChannel.open(Path.of(...)) does not support
        // Windows device namespace paths like \\.\PhysicalDriveN
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(devicePath, "r")) {
            long size = raf.length();
            if (size > 0) return size;
        } catch (Exception e) {
            LOG.log(Level.FINE, "RandomAccessFile.length() failed for " + devicePath, e);
        }
        // Fallback: PowerShell Get-Disk
        String driveNum = devicePath.replaceAll(".*PhysicalDrive", "");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "(Get-Disk -Number " + driveNum + ").Size");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                p.waitFor();
                if (line != null) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "PowerShell Get-Disk failed for " + devicePath, e);
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // Timed raw-device read
    // -----------------------------------------------------------------------

    /**
     * Opens the raw device via {@link java.io.RandomAccessFile} (which works
     * with Windows device-namespace paths like {@code \\.\PhysicalDrive0} and
     * Unix device nodes like {@code /dev/sda}), then reads blocks through
     * the FFM API for sector-aligned I/O.
     *
     * <p>{@code ExtendedOpenOption.DIRECT} is <em>not</em> used here — raw
     * device reads already bypass the filesystem page cache on all platforms.
     */
    public void measureRead(Sample sample, long blockSize, int numBlocks,
                            BenchmarkRunner bRunner) {
        String devicePath = bRunner.config.getDevicePath();
        long byteAlignment = bRunner.effectiveAlignment;

        long deviceSize = getDeviceSizeBytes(devicePath);
        if (deviceSize <= 0) {
            App.err("Cannot determine device size for: " + devicePath);
            LOG.severe("Device size query failed for " + devicePath);
            return;
        }
        long maxOffset = (long)(deviceSize * USABLE_DEVICE_FRACTION) - blockSize;
        if (maxOffset < 0) maxOffset = 0;

        long startTime = System.nanoTime();
        long totalBytesRead = 0;

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(devicePath, "r");
             FileChannel fc = raf.getChannel();
             Arena arena = Arena.ofConfined()) {

            MemorySegment segment = arena.allocate(blockSize, byteAlignment);
            for (int b = 0; b < numBlocks; b++) {
                if (bRunner.listener.isCancelled()) break;
                long byteOffset;
                if (bRunner.config.blockOrder == RANDOM) {
                    byteOffset = alignToBlockSize(
                            ThreadLocalRandom.current().nextLong(maxOffset + 1),
                            byteAlignment);
                } else {
                    byteOffset = ((long) b * blockSize) % (maxOffset + 1);
                }
                int read = fc.read(segment.asByteBuffer(), byteOffset);
                if (read > 0) totalBytesRead += read;
                bRunner.updateReadProgress();
            }
        } catch (java.io.IOException e) {
            LOG.log(Level.SEVERE, "Device read failed for " + devicePath, e);
            App.err("Device read failed: " + e.getMessage());
        }

        long elapsedTimeNs = System.nanoTime() - startTime;
        sample.accessTimeMs = (elapsedTimeNs / 1_000_000f) / (float) numBlocks;
        double sec = (double) elapsedTimeNs / 1_000_000_000d;
        sample.bwMbSec = ((double) totalBytesRead / (double) MEGABYTE) / sec;
    }

    private static long alignToBlockSize(long offset, long alignment) {
        return (offset / alignment) * alignment;
    }
}

