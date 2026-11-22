package org.metricus.jdm.os;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
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
public final class UtilsWinOs {

    private static final Logger LOGGER = Logger.getLogger(UtilsWinOs.class.getName());
    private static final long TIMEOUT_SECONDS = 15;

    private UtilsWinOs() {}

    /**
     * Get the USB Vendor ID and Product ID for a Windows drive letter by
     * walking up the PnP parent chain from {@code Win32_DiskDrive.PNPDeviceID}.
     * ({@code Get-Disk}/MSFT_Disk does not expose a PNPDeviceID property.)
     *
     * @param driveLetter single drive letter (e.g. "E")
     * @return USB VID/PID pair, or {@code null} if unavailable
     */
    public static Util.UsbIds getUsbIds(String driveLetter) {
        if (driveLetter == null || driveLetter.isBlank()) return null;
        driveLetter = driveLetter.toUpperCase();
        Process process = null;
        try {
            String script = String.format(
                    "$n = (Get-Partition -DriveLetter %s).DiskNumber; "
                    + "$id = (Get-CimInstance Win32_DiskDrive | Where-Object Index -eq $n).PNPDeviceID; "
                    + "for ($i = 0; $i -lt 4 -and $id; $i++) { "
                    + "if ($id -match 'VID_[0-9A-Fa-f]{4}&PID_[0-9A-Fa-f]{4}') { $id; break } "
                    + "$id = (Get-PnpDeviceProperty -InstanceId $id -KeyName DEVPKEY_Device_Parent -ErrorAction SilentlyContinue).Data "
                    + "}",
                    driveLetter);
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-Command", script);
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
                LOGGER.log(Level.WARNING, "USB VID/PID lookup timed out for drive {0}", driveLetter);
                return null;
            }
            for (String line : output.get(1, TimeUnit.SECONDS)) {
                Util.UsbIds ids = parseUsbIdsFromPnpId(line.trim());
                if (ids != null) {
                    return ids;
                }
            }
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "Could not detect USB VID/PID on Windows", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return null;
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
