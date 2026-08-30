package jdiskmark;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs {@code smartctl} in a persistent elevated PowerShell agent on Windows,
 * prompting for UAC elevation only once per session.
 *
 * <p>On the first call a {@code -File} PowerShell script is written to
 * {@code %LOCALAPPDATA%\JDiskMark\smart-agent.ps1} and launched elevated via
 * {@code Start-Process -Verb RunAs}. Subsequent calls reuse the running agent
 * by dropping a {@code smart-req-<device>.txt} request file and polling for
 * the corresponding {@code smart-ipc-<device>.json} result file.
 *
 * <p>The agent probes device paths in two passes:
 * <ol>
 *   <li>Simple paths: {@code /dev/<device>} and bare {@code <device>}.</li>
 *   <li>Win32 path {@code \\.\PhysicalDriveN} with plain, {@code -d nvme},
 *       and {@code -d sat} type hints (covers Windows 11 NVMe controllers).</li>
 * </ol>
 * If all paths fail to open the device (smartctl exit code bit 1 set) the
 * best available error-JSON is returned as a fallback so the UI can still show
 * drive identity, firmware, and serial number.
 *
 * <p>Both the elevated agent and the non-elevated main process share the same
 * {@code %LOCALAPPDATA%} path because they run under the same Windows user
 * account (just different privilege tokens).
 */
public class SmartEscalation {

    private static final Logger LOGGER = Logger.getLogger(SmartEscalation.class.getName());

    /** Seconds to wait for the outer UAC launcher to exit. */
    private static final int UAC_TIMEOUT_SECONDS = 45;
    /** Seconds to poll for the agent-ready file after launching. */
    private static final int AGENT_READY_TIMEOUT_SECONDS = 20;
    /** Seconds to wait for a single SMART query result from the running agent. */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private static volatile boolean agentReady = false;
    private static volatile boolean shutdownHookRegistered = false;
    private static final Object agentLock = new Object();

    /**
     * Runs {@code smartctl} for the given Windows device using a persistent
     * elevated agent, prompting for UAC elevation only on the first call.
     *
     * @param device       Windows device name, e.g. {@code pd0}
     * @param smartctlPath absolute path to {@code smartctl.exe}
     * @return raw JSON string from smartctl, or {@code null} if UAC was
     *         cancelled or the query failed
     * @throws IOException          if the IPC directory cannot be created
     * @throws InterruptedException if the calling thread is interrupted
     */
    public static String runElevated(String device, String smartctlPath)
            throws IOException, InterruptedException {

        if (device == null || !device.matches("[A-Za-z0-9._-]+")) {
            LOGGER.warning("SmartEscalation: invalid device name: " + device);
            return null;
        }

        Path ipcDir = resolveIpcDir();
        Files.createDirectories(ipcDir);

        if (!ensureAgentRunning(smartctlPath, ipcDir)) {
            LOGGER.warning("SmartEscalation: agent not ready — UAC may have been cancelled");
            return null;
        }

        // Drop a request file; the agent picks it up and writes the result.
        Path reqFile    = ipcDir.resolve("smart-req-" + device + ".txt");
        Path outputFile = ipcDir.resolve("smart-ipc-" + device + ".json");
        Path statusFile = ipcDir.resolve("smart-ipc-" + device + ".status");

        Files.deleteIfExists(outputFile);
        Files.deleteIfExists(statusFile);
        Files.writeString(reqFile, device, StandardCharsets.UTF_8);

        LOGGER.info("SmartEscalation: submitted request for device: " + device);

        // Poll for the result or status file.
        long deadline = System.currentTimeMillis() + QUERY_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(statusFile)) {
                String status = Files.readString(statusFile, StandardCharsets.UTF_8).trim();
                LOGGER.warning("SmartEscalation: agent status (no JSON): " + status);
                Files.deleteIfExists(statusFile);
                return null;
            }
            if (Files.exists(outputFile)) {
                String json = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
                Files.deleteIfExists(outputFile);
                if (json.startsWith("\uFEFF")) json = json.substring(1).trim();
                if (json.isEmpty() || !json.startsWith("{")) {
                    LOGGER.warning("SmartEscalation: unexpected output (not JSON): "
                            + json.substring(0, Math.min(200, json.length())));
                    return null;
                }
                LOGGER.info("SmartEscalation: received " + json.length() + " bytes for device: " + device);
                return json;
            }
            Thread.sleep(200);
        }

        // Timed out — agent may have died; force a restart on the next call.
        LOGGER.warning("SmartEscalation: query timed out for device: " + device + " — resetting agent state");
        Files.deleteIfExists(reqFile);
        agentReady = false;
        return null;
    }

    /**
     * Ensures the persistent elevated agent is running. On the first call
     * this triggers a single UAC prompt; subsequent calls return immediately.
     */
    private static boolean ensureAgentRunning(String smartctlPath, Path ipcDir)
            throws IOException, InterruptedException {

        if (agentReady) return true;

        synchronized (agentLock) {
            if (agentReady) return true;

            Path readyFile = ipcDir.resolve("smart-agent-ready.txt");
            Files.deleteIfExists(readyFile);
            Path stopFile = ipcDir.resolve("smart-agent-stop.txt");
            Files.deleteIfExists(stopFile);

            String ipcPs  = ipcDir.toString().replace("'", "''");
            String sctlPs = smartctlPath.replace("'", "''");
            String script = buildAgentScript(sctlPs, ipcPs);

            Path scriptFile = ipcDir.resolve("smart-agent.ps1");
            Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
            String scriptPs = scriptFile.toString().replace("'", "''");

            String outerCmd = "Start-Process powershell"
                    + " -Verb RunAs"
                    + " -WindowStyle Hidden"
                    + " -ArgumentList '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \\\"" + scriptPs + "\\\"'";

            LOGGER.info("SmartEscalation: launching persistent elevated agent via UAC...");
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", outerCmd);
            pb.redirectErrorStream(true);
            Process launcher = pb.start();

            Thread.startVirtualThread(() -> {
                try { launcher.getInputStream().transferTo(OutputStream.nullOutputStream()); } catch (IOException ignored) {}
            });

            if (!launcher.waitFor(UAC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                launcher.destroyForcibly();
                LOGGER.warning("SmartEscalation: UAC launcher timed out");
                return false;
            }

            int launcherExit = launcher.exitValue();
            LOGGER.info("SmartEscalation: UAC launcher exited with code: " + launcherExit);
            if (launcherExit != 0) {
                LOGGER.warning("SmartEscalation: UAC likely cancelled (launcher exit code: " + launcherExit + ")");
                return false;
            }

            long deadline = System.currentTimeMillis() + AGENT_READY_TIMEOUT_SECONDS * 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (Files.exists(readyFile)) {
                    Files.deleteIfExists(readyFile);
                    agentReady = true;
                    LOGGER.info("SmartEscalation: persistent elevated agent is ready");
                    registerShutdownHook(ipcDir);
                    return true;
                }
                Thread.sleep(200);
            }

            LOGGER.warning("SmartEscalation: agent did not signal ready within "
                    + AGENT_READY_TIMEOUT_SECONDS + "s");
            return false;
        }
    }

    /**
     * Builds the PowerShell agent script.
     * Pass 1: /dev/pdN and pdN. Pass 2: Win32 path with plain, -d nvme, -d sat.
     * Falls back to best error-JSON if all paths fail to open the device.
     */
    private static String buildAgentScript(String smartctlPs, String ipcPs) {
        return String.join("\r\n",
            "$ErrorActionPreference = 'Continue'",
            "$utf8NoBom = New-Object System.Text.UTF8Encoding($false)",
            "$ipcDir = '" + ipcPs + "'",
            "$smartctlPath = '" + smartctlPs + "'",
            "",
            "Remove-Item (Join-Path $ipcDir 'smart-agent-stop.txt') -Force -ErrorAction SilentlyContinue",
            "[System.IO.File]::WriteAllText((Join-Path $ipcDir 'smart-agent-ready.txt'), 'ready', $utf8NoBom)",
            "",
            "while ($true) {",
            "    if (Test-Path (Join-Path $ipcDir 'smart-agent-stop.txt')) { break }",
            "    $reqs = Get-ChildItem (Join-Path $ipcDir 'smart-req-*.txt') -ErrorAction SilentlyContinue",
            "    foreach ($req in $reqs) {",
            "        $device = (Get-Content $req.FullName -Raw -ErrorAction SilentlyContinue).Trim()",
            "        Remove-Item $req.FullName -Force -ErrorAction SilentlyContinue",
            "        if (-not $device) { continue }",
            "        $outFile     = Join-Path $ipcDir \"smart-ipc-$device.json\"",
            "        $statFile    = Join-Path $ipcDir \"smart-ipc-$device.status\"",
            "        $written     = $false",
            "        $fallbackOut = $null",
            "",
            "        # Pass 1 - simple paths",
            "        foreach ($d in @(\"/dev/$device\", $device)) {",
            "            $out  = & $smartctlPath --json -a $d 2>&1",
            "            $code = $LASTEXITCODE",
            "            $text = ($out | ForEach-Object { $_.ToString() }) -join \"`n\"",
            "            if (-not $text.TrimStart().StartsWith('{')) { continue }",
            "            if (($code -band 2) -ne 0) { if ($null -eq $fallbackOut) { $fallbackOut = $out }; continue }",
            "            [System.IO.File]::WriteAllText($outFile, $text, $utf8NoBom)",
            "            $written = $true; break",
            "        }",
            "",
            "        # Pass 2 - Win32 path with NVMe/SAT hints",
            "        if (-not $written -and $device -match '^pd(\\d+)$') {",
            "            $win32 = \"\\\\.\\PhysicalDrive$($Matches[1])\"",
            "            foreach ($hint in @('', '-d nvme', '-d sat')) {",
            "                $args2 = @('--json', '-a', $win32)",
            "                if ($hint) { $args2 += $hint.Split(' ') }",
            "                $out  = & $smartctlPath @args2 2>&1",
            "                $code = $LASTEXITCODE",
            "                $text = ($out | ForEach-Object { $_.ToString() }) -join \"`n\"",
            "                if (-not $text.TrimStart().StartsWith('{')) { continue }",
            "                if (($code -band 2) -ne 0) { if ($null -eq $fallbackOut) { $fallbackOut = $out }; continue }",
            "                [System.IO.File]::WriteAllText($outFile, $text, $utf8NoBom)",
            "                $written = $true; break",
            "            }",
            "        }",
            "",
            "        # Fallback - use first error-JSON so UI has drive identity",
            "        if (-not $written -and ($null -ne $fallbackOut)) {",
            "            $text = ($fallbackOut | ForEach-Object { $_.ToString() }) -join \"`n\"",
            "            [System.IO.File]::WriteAllText($outFile, $text, $utf8NoBom); $written = $true",
            "        }",
            "        if (-not $written) {",
            "            [System.IO.File]::WriteAllText($statFile, 'no-json: all candidates failed', $utf8NoBom)",
            "        }",
            "    }",
            "    Start-Sleep -Milliseconds 100",
            "}"
        );
    }

    /** Registers a JVM shutdown hook that writes the stop file to cleanly exit the agent. */
    private static void registerShutdownHook(Path ipcDir) {
        synchronized (agentLock) {
            if (shutdownHookRegistered) return;
            shutdownHookRegistered = true;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.writeString(ipcDir.resolve("smart-agent-stop.txt"), "stop", StandardCharsets.UTF_8);
                LOGGER.info("SmartEscalation: shutdown hook wrote stop file");
            } catch (IOException ex) {
                LOGGER.warning("SmartEscalation: shutdown hook failed to write stop file: " + ex.getMessage());
            }
        }, "smart-agent-stopper"));
    }

    /** Returns the IPC directory: {@code %LOCALAPPDATA%\JDiskMark}. */
    private static Path resolveIpcDir() {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null) base = System.getProperty("java.io.tmpdir");
        return Path.of(base, "JDiskMark");
    }

    private SmartEscalation() {}
}
