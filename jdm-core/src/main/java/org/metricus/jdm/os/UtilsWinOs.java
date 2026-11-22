package org.metricus.jdm.os;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    private UtilsWinOs() {}

    /**
     * Get the USB Vendor ID and Product ID for a Windows drive letter by
     * walking up the PnP parent chain from {@code Get-Disk.PNPDeviceID}.
     *
     * @param driveLetter single drive letter (e.g. "E")
     * @return USB VID/PID pair, or {@code null} if unavailable
     */
    public static Util.UsbIds getUsbIds(String driveLetter) {
        if (driveLetter == null || driveLetter.isBlank()) return null;
        driveLetter = driveLetter.toUpperCase();
        try {
            String script = String.format(
                    "$id = (Get-Partition -DriveLetter %s | Get-Disk).PNPDeviceID; "
                    + "for ($i = 0; $i -lt 4 -and $id; $i++) { "
                    + "if ($id -match 'VID_[0-9A-Fa-f]{4}&PID_[0-9A-Fa-f]{4}') { $id; break } "
                    + "$id = (Get-PnpDeviceProperty -InstanceId $id -KeyName DEVPKEY_Device_Parent -ErrorAction SilentlyContinue).Data "
                    + "}",
                    driveLetter);
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-Command", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Util.UsbIds ids = parseUsbIdsFromPnpId(line.trim());
                    if (ids != null) {
                        return ids;
                    }
                }
            } finally {
                process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "Could not detect USB VID/PID on Windows", e);
        }
        return null;
    }

    /**
     * Extracts a lowercase 4-digit hex {@link Util.UsbIds} pair from a Windows
     * PnP device instance ID (e.g. {@code USB\VID_0781&PID_5583\...}).
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
