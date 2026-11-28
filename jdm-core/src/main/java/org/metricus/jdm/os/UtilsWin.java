package org.metricus.jdm.os;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
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

import jdiskmark.App;
import jdiskmark.DiskUsageInfo;
import jdiskmark.Smart;
import jdiskmark.SmartEscalation;
import static org.metricus.jdm.os.UtilOs.CAPACITY_PS_FILENAME;
import static org.metricus.jdm.os.UtilOs.DISK_MODEL_PS_FILENAME;
import org.metricus.jdm.util.Util;


/**
 * Windows-specific utility methods for JDiskMark.
 */
public final class UtilsWin {

    private static final Logger LOGGER = Logger.getLogger(UtilsWin.class.getName());
    private static final long TIMEOUT_SECONDS = 15;

    private UtilsWin() {}

    /** Reads HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid (no admin required). */
    public static String readMachineGuid() throws IOException, InterruptedException {
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
    
    /* Not used kept here for reference. */
    static public void readPhysicalDrive() throws FileNotFoundException, IOException {
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
    public static String getDriveModelLegacy(String driveLetter) {
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
    
    public static String getDriveLetter(Path dataDirPath) {
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
    public static String getDriveModel(String driveLetter) {
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
    public static String getDriveInterface(String driveLetter) {
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
    
    public static DiskUsageInfo getCapacity(String driveLetter) {
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
    
    public static boolean isRunningAsAdmin() {
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
    
    static public void emptyStandbyList(File esblExe) {

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
    public static DiskUsageInfo parseDiskUsageInfo(List<String> outputLines) {
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
    
    public static String getProcessorName() {
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
            return getProcessorNamePowerShell();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }

        return ""; // Return an empty string if no processor name was found
    }

    static String getProcessorNamePowerShell() {
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

    // -----------------------------------------------------------------------
    // Drive attributes — Windows (no admin required)
    // -----------------------------------------------------------------------

    /**
     * Returns the filesystem type for the given drive letter (e.g. "NTFS", "FAT32").
     * Uses PowerShell {@code Get-Volume}. No admin required.
     *
     * @param driveLetter single letter, e.g. "C"
     * @return filesystem type or {@code null} on failure
     */
    public static String getFilesystem(String driveLetter) {
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
    public static String getBusType(String driveLetter) {
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
    public static String getSectorSize(String driveLetter) {
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
    public static String getPhysicalDriveNumber(String driveLetter) {
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
    
    /**
     * Windows: clears the standby list (disk cache) with EmptyStandbyList.exe
     * through the elevated SMART agent, so the UAC prompt appears at most once
     * per session and is shared with SMART (#258).
     *
     * @return {@code UNAVAILABLE} when the agent or EmptyStandbyList.exe is not
     *         installed (e.g. IDE runs), {@code DENIED} when UAC is cancelled or
     *         the tool fails
     */
    public static UtilOs.CacheDropResult dropCacheElevated() {
        if (!SmartEscalation.isInstalled()) {
            return UtilOs.CacheDropResult.UNAVAILABLE;
        }
        String smartctlPath = Smart.resolveSmartctlPath();
        if (!SmartEscalation.isAgentReady()) {
            App.msg("Requesting administrator privileges to clear the disk cache...");
        }
        try {
            // GH-2: delays in place of flushing the cache, as in the admin path
            Thread.sleep(1300);
            Integer code = SmartEscalation.dropCacheElevated(smartctlPath);
            if (code == null) {
                return UtilOs.CacheDropResult.DENIED;
            }
            if (code == SmartEscalation.DROP_CACHE_MISSING) {
                LOGGER.warning("EmptyStandbyList.exe is not installed next to the elevated agent");
                return UtilOs.CacheDropResult.UNAVAILABLE;
            }
            if (code != 0) {
                LOGGER.log(Level.WARNING, "Elevated cache drop failed, exit={0}", code);
                return UtilOs.CacheDropResult.DENIED;
            }
            Thread.sleep(700);
            App.msg("Disk cache cleared");
            return UtilOs.CacheDropResult.CLEARED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UtilOs.CacheDropResult.DENIED;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Elevated cache drop failed", e);
            return UtilOs.CacheDropResult.DENIED;
        }
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
}
