package jdiskmark;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs {@code smartctl} in an elevated child process on Windows via a UAC prompt,
 * passing the JSON result back to the non-elevated caller through a temp file in
 * {@code %LOCALAPPDATA%\JDiskMark\}.
 *
 * <p>Both the elevated helper and the non-elevated main process share the same
 * {@code %LOCALAPPDATA%} path because they run under the same Windows user account
 * (just different privilege tokens), so the IPC file is readable by both.
 *
 * <p>The UAC dialog will show "Windows PowerShell" as the requesting application.
 * A future native helper exe with an embedded {@code requireAdministrator} manifest
 * would display "JDiskMark" instead.
 */
public class SmartEscalation {

    private static final Logger LOGGER = Logger.getLogger(SmartEscalation.class.getName());

    /** Maximum time to wait for the elevated helper to complete. */
    private static final int TIMEOUT_SECONDS = 45;

    /**
     * Runs {@code smartctl} for the given Windows device using UAC elevation.
     *
     * <ol>
     *   <li>Writes a PowerShell helper script to {@code %LOCALAPPDATA%\JDiskMark\}.</li>
     *   <li>Launches the script elevated via {@code Start-Process -Verb RunAs -Wait}.</li>
     *   <li>Reads the JSON result written by the elevated helper.</li>
     * </ol>
     *
     * @param device       Windows device name, e.g. {@code pd0}
     * @param smartctlPath absolute path to {@code smartctl.exe}
     * @return raw JSON string from smartctl, or {@code null} if the UAC prompt was
     *         cancelled or the elevated helper failed
     * @throws IOException          if the IPC directory or script file cannot be created
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static String runElevated(String device, String smartctlPath)
            throws IOException, InterruptedException {

        Path ipcDir = resolveIpcDir();
        Files.createDirectories(ipcDir);

        Path scriptFile = ipcDir.resolve("smart-helper.ps1");
        Path outputFile = ipcDir.resolve("smart-ipc-" + device + ".json");
        Path cancelFile = ipcDir.resolve("smart-ipc-" + device + ".cancelled");

        // Remove stale artifacts from any previous run
        Files.deleteIfExists(outputFile);
        Files.deleteIfExists(cancelFile);

        writeHelperScript(scriptFile, smartctlPath, device, outputFile, cancelFile);

        // Launch script elevated. Start-Process -Verb RunAs triggers UAC.
        // -Wait blocks the launcher until the elevated powershell exits.
        String launchCmd = String.format(
                "Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden "
                + "-ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"%s\"'",
                scriptFile.toString().replace("\"", "`\""));

        LOGGER.info("SmartEscalation: launching elevated helper for device: " + device);
        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", launchCmd);
        pb.redirectErrorStream(true);
        Process launcher = pb.start();

        // Drain launcher stdout/stderr to prevent pipe-full stalls
        launcher.getInputStream().transferTo(OutputStream.nullOutputStream());

        if (!launcher.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            launcher.destroyForcibly();
            LOGGER.warning("SmartEscalation: launcher timed out for device: " + device);
            return null;
        }

        if (Files.exists(cancelFile)) {
            LOGGER.info("SmartEscalation: UAC cancelled by user for device: " + device);
            Files.deleteIfExists(cancelFile);
            return null;
        }

        if (!Files.exists(outputFile)) {
            LOGGER.warning("SmartEscalation: output file not created for device: " + device
                    + " (UAC may have been cancelled or helper failed)");
            return null;
        }

        String json = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
        Files.deleteIfExists(outputFile);

        if (json.isEmpty()) {
            LOGGER.warning("SmartEscalation: empty output for device: " + device);
            return null;
        }

        LOGGER.info("SmartEscalation: received " + json.length() + " bytes for device: " + device);
        return json;
    }

    /**
     * Writes the PowerShell helper script that will run inside the elevated process.
     * The script runs smartctl and writes the JSON to {@code outputFile}, or writes
     * a sentinel {@code cancelFile} if smartctl cannot be executed.
     */
    private static void writeHelperScript(Path scriptFile, String smartctlPath,
            String device, Path outputFile, Path cancelFile) throws IOException {

        // Single-quote PS strings; escape embedded single-quotes by doubling them.
        String smartctlPs = smartctlPath.replace("'", "''");
        String outputPs   = outputFile.toString().replace("'", "''");
        String cancelPs   = cancelFile.toString().replace("'", "''");

        String script = String.join("\r\n",
            "# JDiskMark SMART elevation helper -- auto-generated, do not edit",
            "param()",
            "Set-StrictMode -Off",
            "try {",
            "    $out = & '" + smartctlPs + "' --json -a '/dev/" + device + "' 2>&1",
            "    $out | Out-File -FilePath '" + outputPs + "' -Encoding utf8 -NoNewline",
            "} catch {",
            "    # smartctl not found or access denied -- write cancel sentinel",
            "    $_.Exception.Message | Out-File -FilePath '" + cancelPs
                    + "' -Encoding utf8 -NoNewline",
            "}"
        );
        Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
    }

    /** Returns the IPC directory path: {@code %LOCALAPPDATA%\JDiskMark}. */
    private static Path resolveIpcDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) {
            localAppData = System.getProperty("java.io.tmpdir");
        }
        return Path.of(localAppData, "JDiskMark");
    }

    private SmartEscalation() {}
}
