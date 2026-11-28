package org.metricus.jdm.os;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdiskmark.Util;

/**
 * Windows-specific utility methods for JDiskMark.
 */
public final class UtilsWin {

    private static final Logger LOGGER = Logger.getLogger(UtilsWin.class.getName());
    private static final long TIMEOUT_SECONDS = 15;

    private UtilsWin() {}

    /**
     * Get the USB Vendor ID and Product ID for a Windows drive letter by
     * walking up the PnP parent chain from {@code Win32_DiskDrive.PNPDeviceID}.
     * ({@code Get-Disk}/MSFT_Disk does not expose a PNPDeviceID property.)
     *
     * @param driveLetter single drive letter (e.g. "E")
     * @return USB VID/PID pair, or {@code null} if unavailable
     */
    public static Util.UsbIds getUsbIds(String driveLetter) {
        String letter = normalizeDriveLetter(driveLetter);
        if (letter == null) return null;
        String script = """
                $n = (Get-Partition -DriveLetter %s).DiskNumber
                $id = (Get-CimInstance Win32_DiskDrive | Where-Object Index -eq $n).PNPDeviceID
                for ($i = 0; $i -lt 4 -and $id; $i++) {
                    if ($id -match 'VID_[0-9A-Fa-f]{4}&PID_[0-9A-Fa-f]{4}') { $id; break }
                    $id = (Get-PnpDeviceProperty -InstanceId $id -KeyName DEVPKEY_Device_Parent -ErrorAction SilentlyContinue).Data
                }
                """.formatted(letter);
        for (String line : runPowerShellLines(script, "USB VID/PID lookup for drive " + letter)) {
            Util.UsbIds ids = parseUsbIdsFromPnpId(line.trim());
            if (ids != null) {
                return ids;
            }
        }
        return null;
    }

    /**
     * Returns the baseboard ({@code Win32_BaseBoard}) and system
     * ({@code Win32_ComputerSystem}) vendor/model in a single PowerShell
     * call, bounded by {@link #TIMEOUT_SECONDS}. Parts that cannot be read
     * are {@code null}.
     */
    public static Util.HardwareInfo getHardwareInfo() {
        String script = """
                $b = Get-CimInstance Win32_BaseBoard | Select-Object -First 1
                $s = Get-CimInstance Win32_ComputerSystem | Select-Object -First 1
                "BoardManufacturer=$($b.Manufacturer)"
                "BoardProduct=$($b.Product)"
                "SystemManufacturer=$($s.Manufacturer)"
                "SystemModel=$($s.Model)"
                """;
        return parseHardwareInfo(runPowerShellLines(script, "hardware lookup"));
    }

    /**
     * Parses the labeled lines emitted by {@link #getHardwareInfo()}
     * ({@code BoardManufacturer=}, {@code BoardProduct=},
     * {@code SystemManufacturer=}, {@code SystemModel=}); other lines are ignored.
     */
    public static Util.HardwareInfo parseHardwareInfo(List<String> lines) {
        String boardVendor = null;
        String boardModel = null;
        String systemVendor = null;
        String systemModel = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("BoardManufacturer=")) {
                boardVendor = trimmed.substring("BoardManufacturer=".length());
            } else if (trimmed.startsWith("BoardProduct=")) {
                boardModel = trimmed.substring("BoardProduct=".length());
            } else if (trimmed.startsWith("SystemManufacturer=")) {
                systemVendor = trimmed.substring("SystemManufacturer=".length());
            } else if (trimmed.startsWith("SystemModel=")) {
                systemModel = trimmed.substring("SystemModel=".length());
            }
        }
        return new Util.HardwareInfo(
                Util.Motherboard.of(boardVendor, boardModel),
                Util.SystemModel.of(systemVendor, systemModel));
    }

    /**
     * Returns the negotiated USB interface version (e.g. {@code "2.0"}, {@code "3.0"},
     * {@code "3.2 Gen 2"}) for a Windows drive letter by querying its parent USB hub
     * via {@code IOCTL_USB_GET_NODE_CONNECTION_INFORMATION_EX} and {@code _V2}.
     *
     * @param driveLetter single drive letter (e.g. "E")
     * @return USB version string, or {@code null} if unavailable or not USB-attached
     */
    public static String getUsbVersion(String driveLetter) {
        String letter = normalizeDriveLetter(driveLetter);
        if (letter == null) return null;
        String script = """
                $n = (Get-Partition -DriveLetter %s).DiskNumber
                $id = (Get-CimInstance Win32_DiskDrive | Where-Object Index -eq $n).PNPDeviceID
                for ($i = 0; $i -lt 4 -and $id; $i++) {
                    if ($id -match 'VID_[0-9A-Fa-f]{4}&PID_[0-9A-Fa-f]{4}') { break }
                    $id = (Get-PnpDeviceProperty -InstanceId $id -KeyName DEVPKEY_Device_Parent -ErrorAction SilentlyContinue).Data
                }
                if (-not ($id -match 'VID_[0-9A-Fa-f]{4}&PID_[0-9A-Fa-f]{4}')) { return }
                $hub = (Get-PnpDeviceProperty -InstanceId $id -KeyName DEVPKEY_Device_Parent -ErrorAction SilentlyContinue).Data
                $port = (Get-PnpDeviceProperty -InstanceId $id -KeyName DEVPKEY_Device_Address -ErrorAction SilentlyContinue).Data
                if (-not $hub -or -not $port) { return }
                Add-Type -TypeDefinition @'
                using System;
                using System.Runtime.InteropServices;
                using Microsoft.Win32.SafeHandles;
                public static class JdmUsbIoctl {
                    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
                    public static extern SafeFileHandle CreateFile(string lpFileName, uint dwDesiredAccess, uint dwShareMode, IntPtr lpSecurityAttributes, uint dwCreationDisposition, uint dwFlagsAndAttributes, IntPtr hTemplateFile);
                    [DllImport("kernel32.dll", SetLastError = true)]
                    public static extern bool DeviceIoControl(SafeFileHandle hDevice, uint dwIoControlCode, IntPtr lpInBuffer, int nInBufferSize, IntPtr lpOutBuffer, int nOutBufferSize, out int lpBytesReturned, IntPtr lpOverlapped);
                    public static string Query(string hubId, int port) {
                        string path = "\\\\\\\\.\\\\" + hubId.Replace('\\\\', '#') + "#{f18a0e88-c30c-11d0-8815-00a0c906bed8}";
                        using (SafeFileHandle h = CreateFile(path, 0xC0000000u, 3u, IntPtr.Zero, 3u, 0u, IntPtr.Zero)) {
                            if (h.IsInvalid) return null;
                            IntPtr b1 = Marshal.AllocHGlobal(256);
                            try {
                                for (int i = 0; i < 256; i++) Marshal.WriteByte(b1, i, 0);
                                Marshal.WriteInt32(b1, 0, port);
                                int ret;
                                if (!DeviceIoControl(h, 0x00220448u, b1, 256, b1, 256, out ret, IntPtr.Zero)) return null;
                                byte speed = Marshal.ReadByte(b1, 23);
                                int flags = 0;
                                int v2 = 0;
                                IntPtr b2 = Marshal.AllocHGlobal(16);
                                try {
                                    for (int i = 0; i < 16; i++) Marshal.WriteByte(b2, i, 0);
                                    Marshal.WriteInt32(b2, 0, port);
                                    Marshal.WriteInt32(b2, 4, 16);
                                    Marshal.WriteInt32(b2, 8, 0x07);
                                    if (DeviceIoControl(h, 0x0022045Cu, b2, 16, b2, 16, out ret, IntPtr.Zero)) {
                                        flags = Marshal.ReadInt32(b2, 12);
                                        v2 = 1;
                                    }
                                } finally { Marshal.FreeHGlobal(b2); }
                                return speed + " " + flags + " " + v2;
                            } finally { Marshal.FreeHGlobal(b1); }
                        }
                    }
                }
                '@
                [JdmUsbIoctl]::Query($hub, [int]$port)
                """.formatted(letter);
        for (String line : runPowerShellLines(script, "USB version lookup for drive " + letter)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 3) {
                try {
                    int speed = Integer.parseInt(parts[0]);
                    int flags = Integer.parseInt(parts[1]);
                    boolean v2Ok = "1".equals(parts[2]);
                    String version = mapUsbVersion(speed, flags, v2Ok);
                    if (version != null) {
                        return version;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric diagnostic lines
                }
            }
        }
        return null;
    }

    /**
     * Maps {@code USB_NODE_CONNECTION_INFORMATION_EX.Speed} and
     * {@code USB_NODE_CONNECTION_INFORMATION_EX_V2.Flags} to a USB version string.
     * Windows reports SuperSpeed devices as high speed in the V1 structure, so
     * high speed is only trusted as USB 2.0 when the V2 query succeeded.
     */
    public static String mapUsbVersion(int speed, int v2Flags, boolean v2Ok) {
        if (v2Ok && (v2Flags & 0x04) != 0) {
            return "3.2 Gen 2";
        }
        if (v2Ok && (v2Flags & 0x01) != 0) {
            return "3.0";
        }
        return switch (speed) {
            case 3 -> "3.0";
            case 2 -> v2Ok ? "2.0" : null;
            case 1 -> "1.1";
            case 0 -> "1.0";
            default -> null;
        };
    }

    private static String normalizeDriveLetter(String driveLetter) {
        if (driveLetter == null) return null;
        String trimmed = driveLetter.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() != 1 || trimmed.charAt(0) < 'A' || trimmed.charAt(0) > 'Z') {
            return null;
        }
        return trimmed;
    }

    private static List<String> runPowerShellLines(String script, String context) {
        Process process = null;
        try {
            String encoded = Base64.getEncoder()
                    .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            process = p;
            CompletableFuture<List<String>> output = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader = p.inputReader()) {
                    return reader.lines().toList();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.log(Level.WARNING, "{0} timed out", context);
                return List.of();
            }
            return output.get(1, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, context + " failed", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return List.of();
    }

    /**
     * Extracts a lowercase 4-digit hex {@link Util.UsbIds} pair from a Windows
     * PnP device instance ID (e.g.{@code USB\VID_0781&PID_5583\...}).
     * @param pnpId
     * @return usb ids
     */
    public static Util.UsbIds parseUsbIdsFromPnpId(String pnpId) {
        if (pnpId == null || pnpId.isBlank()) return null;
        Matcher m = Pattern.compile("VID_([0-9A-Fa-f]{4})&PID_([0-9A-Fa-f]{4})",
                Pattern.CASE_INSENSITIVE).matcher(pnpId);
        if (m.find()) {
            return new Util.UsbIds(m.group(1).toLowerCase(), m.group(2).toLowerCase());
        }
        return null;
    }
}
