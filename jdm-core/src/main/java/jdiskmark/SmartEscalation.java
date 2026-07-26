package jdiskmark;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs {@code smartctl} in an elevated child process on Windows via a UAC prompt,
 * passing the JSON result back to the non-elevated caller through a temp file in
 * {@code %LOCALAPPDATA%\JDiskMark\}.
 *
 * <p>The elevated script is delivered via PowerShell's {@code -EncodedCommand}
 * (UTF-16LE base64), which avoids all script-file-path / space-in-username quoting
 * issues that arise when using {@code -File}.
 *
 * <p>Both the elevated helper and the non-elevated main process share the same
 * {@code %LOCALAPPDATA%} path because they run under the same Windows user account
 * (just different privilege tokens), so the IPC file is accessible to both.
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
     *   <li>Builds a PowerShell script inline and encodes it as UTF-16LE base64.</li>
     *   <li>Launches an elevated {@code powershell.exe} with {@code -EncodedCommand}
     *       via {@code Start-Process -Verb RunAs -Wait}.</li>
     *   <li>Reads the JSON result written by the elevated helper.</li>
     * </ol>
     *
     * @param device       Windows device name, e.g. {@code pd0}
     * @param smartctlPath absolute path to {@code smartctl.exe}
     * @return raw JSON string from smartctl, or {@code null} if the UAC prompt was
     *         cancelled or the elevated helper failed
     * @throws IOException          if the IPC directory cannot be created
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static String runElevated(String device, String smartctlPath)
            throws IOException, InterruptedException {

        Path ipcDir = resolveIpcDir();
        Files.createDirectories(ipcDir);

        Path outputFile = ipcDir.resolve("smart-ipc-" + device + ".json");
        Path statusFile = ipcDir.resolve("smart-ipc-" + device + ".status");

        // Remove stale artifacts from any previous run
        Files.deleteIfExists(outputFile);
        Files.deleteIfExists(statusFile);

        // ── Build the elevated script ─────────────────────────────────────────
        // Single-quote PS string escaping (double any embedded single-quotes).
        String smartctlPs = smartctlPath.replace("'", "''");
        String outputPs   = outputFile.toString().replace("'", "''");
        String statusPs   = statusFile.toString().replace("'", "''");

        // The script tries /dev/<device> first, then the bare device name.
        // Uses [System.IO.File]::WriteAllText which handles paths with spaces.
        // Writes a status file if smartctl doesn't produce JSON (for diagnostics).
        String innerScript = String.join("\r\n",
            "$ErrorActionPreference = 'Continue'",
            "$written = $false",
            "foreach ($d in @('/dev/" + device + "', '" + device + "')) {",
            "    $out = & '" + smartctlPs + "' --json -a $d 2>&1",
            "    $text = ($out | ForEach-Object { $_.ToString() }) -join \"`n\"",
            "    if ($text.TrimStart().StartsWith('{')) {",
            "        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)",
            "        [System.IO.File]::WriteAllText('" + outputPs + "', $text, $utf8NoBom)",
            "        $written = $true",
            "        break",
            "    }",
            "}",
            "if (-not $written) {",
            "    $msg = 'no-json: ' + ($out -join '; ')",
            "    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)",
            "    [System.IO.File]::WriteAllText('" + statusPs + "', $msg, $utf8NoBom)",
            "}"
        );

        // Encode script as UTF-16LE for PowerShell -EncodedCommand
        byte[] utf16le = innerScript.getBytes(StandardCharsets.UTF_16LE);
        String b64 = Base64.getEncoder().encodeToString(utf16le);

        LOGGER.info("SmartEscalation: launching elevated helper for device: " + device);
        LOGGER.info("SmartEscalation: smartctlPath=" + smartctlPath);
        LOGGER.info("SmartEscalation: outputFile=" + outputFile);

        // ── Launch elevated helper ────────────────────────────────────────────
        // The outer (non-elevated) PS starts an elevated PS with the encoded command.
        // -EncodedCommand has no spaces / path quoting issues.
        String outerCmd = "Start-Process powershell"
                + " -Verb RunAs"
                + " -Wait"
                + " -WindowStyle Hidden"
                + " -ArgumentList '-NoProfile -NonInteractive -EncodedCommand " + b64 + "'";

        ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-Command", outerCmd);
        pb.redirectErrorStream(true);
        Process launcher = pb.start();

        // Drain stdout/stderr to prevent pipe-full stalls
        launcher.getInputStream().transferTo(OutputStream.nullOutputStream());

        if (!launcher.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            launcher.destroyForcibly();
            LOGGER.warning("SmartEscalation: launcher timed out for device: " + device);
            return null;
        }

        int exitCode = launcher.exitValue();
        LOGGER.info("SmartEscalation: launcher exited with code: " + exitCode);

        // ── Read result ───────────────────────────────────────────────────────
        if (Files.exists(statusFile)) {
            String status = Files.readString(statusFile, StandardCharsets.UTF_8).trim();
            LOGGER.warning("SmartEscalation: helper status (no JSON produced): " + status);
            Files.deleteIfExists(statusFile);
            return null;
        }

        if (!Files.exists(outputFile)) {
            LOGGER.warning("SmartEscalation: output file missing — UAC likely cancelled for device: " + device);
            return null;
        }

        String json = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
        Files.deleteIfExists(outputFile);
        // Strip UTF-8 BOM (U+FEFF) if present — .NET's Encoding.UTF8 includes a BOM by default
        if (json.startsWith("\uFEFF")) {
            json = json.substring(1).trim();
        }

        if (json.isEmpty() || !json.startsWith("{")) {
            LOGGER.warning("SmartEscalation: unexpected output (not JSON): "
                    + json.substring(0, Math.min(200, json.length())));
            return null;
        }

        LOGGER.info("SmartEscalation: received " + json.length() + " bytes for device: " + device);
        return json;
    }

    /** Returns the IPC directory: {@code %LOCALAPPDATA%\JDiskMark}. */
    private static Path resolveIpcDir() {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null) base = System.getProperty("java.io.tmpdir");
        return Path.of(base, "JDiskMark");
    }

    private SmartEscalation() {}
}
