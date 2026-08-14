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
 * Provides elevated {@code smartctl} execution on Windows via a persistent
 * UAC-elevated PowerShell agent.
 *
 * <p>On the <em>first</em> call the user sees exactly one UAC prompt.  The
 * elevated PowerShell agent then runs as a background process for the lifetime
 * of the JVM, servicing subsequent SMART queries through file-based IPC in
 * {@code %LOCALAPPDATA%\JDiskMark\} — no further prompts.
 *
 * <p>IPC protocol (all files in the IPC directory):
 * <ul>
 *   <li>{@code smart-agent-ready.txt} — written by the agent at startup to
 *       signal it is ready; deleted by Java after detection.</li>
 *   <li>{@code smart-req-&lt;device&gt;.txt} — request written by Java; contains
 *       the bare device name (e.g. {@code pd0}).</li>
 *   <li>{@code smart-ipc-&lt;device&gt;.json} — JSON response written by the agent
 *       on success.</li>
 *   <li>{@code smart-ipc-&lt;device&gt;.status} — error string written by the agent
 *       when no JSON could be produced.</li>
 *   <li>{@code smart-agent-stop.txt} — written by the JVM shutdown hook to ask
 *       the agent to exit cleanly.</li>
 * </ul>
 *
 * <p>The UAC dialog will show "Windows PowerShell" as the requesting application.
 * A future native helper exe with an embedded {@code requireAdministrator} manifest
 * would display "JDiskMark" instead.
 */
public class SmartEscalation {

    private static final Logger LOGGER = Logger.getLogger(SmartEscalation.class.getName());

    /** Maximum seconds to wait for the UAC prompt to be accepted/cancelled. */
    private static final int UAC_TIMEOUT_SECONDS = 45;

    /** Maximum seconds to poll for the agent ready file after UAC acceptance. */
    private static final int AGENT_READY_TIMEOUT_SECONDS = 15;

    /** Maximum seconds to wait for the agent to respond to a single query. */
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    /** {@code true} once the persistent elevated agent has written its ready file. */
    private static volatile boolean agentReady = false;

    /** Guards concurrent agent startup so only one UAC prompt is shown. */
    private static final Object agentLock = new Object();

    /** Prevents registering the shutdown hook more than once. */
    private static boolean shutdownHookRegistered = false;

    /**
     * Returns {@code true} if the persistent elevated agent is already running.
     * Callers may use this to suppress the "UAC prompt will appear" status
     * message on subsequent invocations.
     */
    public static boolean isAgentReady() {
        return agentReady;
    }

    /**
     * Submits a SMART query to the persistent elevated agent, starting it
     * (one UAC prompt) if not already running.
     *
     * @param device       Windows device name, e.g. {@code pd0}
     * @param smartctlPath absolute path to {@code smartctl.exe}
     * @return raw JSON string from smartctl, or {@code null} on failure
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
            LOGGER.warning("SmartEscalation: elevated agent unavailable (UAC cancelled or timed out)");
            return null;
        }

        // Submit the request by writing a small file the agent will detect.
        Path reqFile  = ipcDir.resolve("smart-req-" + device + ".txt");
        Path outFile  = ipcDir.resolve("smart-ipc-" + device + ".json");
        Path statFile = ipcDir.resolve("smart-ipc-" + device + ".status");

        Files.deleteIfExists(outFile);
        Files.deleteIfExists(statFile);
        Files.writeString(reqFile, device, StandardCharsets.UTF_8);
        LOGGER.info("SmartEscalation: submitted request for device: " + device);

        // Poll for the agent's response within the query timeout.
        long deadline = System.currentTimeMillis() + QUERY_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(outFile)) {
                String json = Files.readString(outFile, StandardCharsets.UTF_8).trim();
                Files.deleteIfExists(outFile);
                if (json.startsWith("\uFEFF")) json = json.substring(1).trim();
                if (!json.isEmpty() && json.startsWith("{")) {
                    LOGGER.info("SmartEscalation: received " + json.length() + " bytes for device: " + device);
                    return json;
                }
                LOGGER.warning("SmartEscalation: response is not JSON: "
                        + json.substring(0, Math.min(200, json.length())));
                return null;
            }
            if (Files.exists(statFile)) {
                String status = Files.readString(statFile, StandardCharsets.UTF_8).trim();
                Files.deleteIfExists(statFile);
                LOGGER.warning("SmartEscalation: agent could not read device: " + status);
                return null;
            }
            Thread.sleep(100);
        }

        // Timed out — agent may have died; force a restart on the next call.
        LOGGER.warning("SmartEscalation: query timed out for device: " + device + " — resetting agent state");
        Files.deleteIfExists(reqFile);
        agentReady = false;
        return null;
    }

    /**
     * Ensures the persistent elevated agent is running.  On the first call
     * this triggers a single UAC prompt; subsequent calls return immediately.
     *
     * @return {@code true} if the agent is ready, {@code false} if UAC was
     *         cancelled or the agent did not signal ready in time
     */
    private static boolean ensureAgentRunning(String smartctlPath, Path ipcDir)
            throws IOException, InterruptedException {

        if (agentReady) return true;

        synchronized (agentLock) {
            if (agentReady) return true; // double-checked after acquiring lock

            Path readyFile = ipcDir.resolve("smart-agent-ready.txt");
            Files.deleteIfExists(readyFile);

            String ipcPs  = ipcDir.toString().replace("'", "''");
            String sctlPs = smartctlPath.replace("'", "''");
            String script = buildAgentScript(sctlPs, ipcPs);
            byte[] utf16le = script.getBytes(StandardCharsets.UTF_16LE);
            String b64     = Base64.getEncoder().encodeToString(utf16le);

            // Start the elevated agent without -Wait so the outer PS exits as soon as
            // UAC is resolved, letting us proceed to poll for the ready file.
            String outerCmd = "Start-Process powershell"
                    + " -Verb RunAs"
                    + " -WindowStyle Hidden"
                    + " -ArgumentList '-NoProfile -NonInteractive -EncodedCommand " + b64 + "'";

            LOGGER.info("SmartEscalation: launching persistent elevated agent via UAC...");
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", outerCmd);
            pb.redirectErrorStream(true);
            Process launcher = pb.start();

            Thread.startVirtualThread(() -> {
                try { launcher.getInputStream().transferTo(OutputStream.nullOutputStream()); } catch (IOException ignored) {}
            });

            // Wait for the outer PS to finish (it returns after the UAC prompt is resolved).
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

            // Poll for the ready file written by the agent right after it starts.
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
     * Builds the PowerShell script for the persistent elevated agent.
     *
     * <p>The agent writes a ready file on startup, then loops every 100 ms
     * picking up {@code smart-req-*.txt} request files, running smartctl
     * against three device path candidates, and writing the JSON result or
     * a status file.  It exits cleanly when {@code smart-agent-stop.txt}
     * appears.
     */
    private static String buildAgentScript(String smartctlPs, String ipcPs) {
        return String.join("\r\n",
            "$ErrorActionPreference = 'Continue'",
            "$utf8NoBom = New-Object System.Text.UTF8Encoding($false)",
            "$ipcDir = '" + ipcPs + "'",
            "$smartctlPath = '" + smartctlPs + "'",
            "",
            "# Signal to the Java process that the agent is up and ready",
            "[System.IO.File]::WriteAllText((Join-Path $ipcDir 'smart-agent-ready.txt'), 'ready', $utf8NoBom)",
            "",
            "while ($true) {",
            "    if (Test-Path (Join-Path $ipcDir 'smart-agent-stop.txt')) { break }",
            "    $reqs = Get-ChildItem (Join-Path $ipcDir 'smart-req-*.txt') -ErrorAction SilentlyContinue",
            "    foreach ($req in $reqs) {",
            "        $device = (Get-Content $req.FullName -Raw -ErrorAction SilentlyContinue).Trim()",
            "        Remove-Item $req.FullName -Force -ErrorAction SilentlyContinue",
            "        if (-not $device) { continue }",
            "        $outFile  = Join-Path $ipcDir \"smart-ipc-$device.json\"",
            "        $statFile = Join-Path $ipcDir \"smart-ipc-$device.status\"",
            "        $candidates = @(\"/dev/$device\", $device)",
            "        if ($device -match '^pd(\\d+)$') {",
            "            $candidates += \"\\\\.\\PhysicalDrive$($Matches[1])\"",
            "        }",
            "        $written = $false",
            "        $out = $null",
            "        foreach ($d in $candidates) {",
            "            $out = & $smartctlPath --json -a $d 2>&1",
            "            $text = ($out | ForEach-Object { $_.ToString() }) -join \"`n\"",
            "            if ($text.TrimStart().StartsWith('{')) {",
            "                [System.IO.File]::WriteAllText($outFile, $text, $utf8NoBom)",
            "                $written = $true",
            "                break",
            "            }",
            "        }",
            "        if (-not $written) {",
            "            $msg = 'no-json: ' + ($out -join '; ')",
            "            [System.IO.File]::WriteAllText($statFile, $msg, $utf8NoBom)",
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
                Files.writeString(ipcDir.resolve("smart-agent-stop.txt"),
                        "stop", StandardCharsets.UTF_8);
                Thread.sleep(400); // give the agent one polling cycle to see the file
            } catch (Exception ignored) {}
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
