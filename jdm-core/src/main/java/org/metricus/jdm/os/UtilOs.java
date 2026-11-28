package org.metricus.jdm.os;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jdiskmark.App;
import jdiskmark.Smart;


/**
 * OS Specific Utility methods for JDiskMark
 */
public class UtilOs {
    
    public static final Logger LOGGER = Logger.getLogger(UtilOs.class.getName());

    // --- OS detection primitives ---
    // Accept an explicit osName string so these can be used before App.os is
    // populated (e.g. in CLI mode or very early in main()).

    /** Returns {@code true} when {@code osName} identifies macOS. */
    public static boolean isMacOs(String osName) {
        return osName != null && osName.contains("Mac OS");
    }

    /** Returns {@code true} when {@code osName} identifies Windows. */
    public static boolean isWindows(String osName) {
        return osName != null && osName.startsWith("Windows");
    }

    /** Returns {@code true} when {@code osName} identifies Linux. */
    public static boolean isLinux(String osName) {
        return osName != null && osName.contains("Linux");
    }

    /**
     * Returns {@code true} when running inside a Flatpak sandbox.
     * Flatpak always creates {@code /.flatpak-info} inside the container.
     */
    public static boolean isFlatpak() {
        return java.nio.file.Files.exists(java.nio.file.Path.of("/.flatpak-info"));
    }

    /**
     * Returns {@code true} when the current packaging context supports SMART.
     *
     * <p>SMART requires {@code smartctl} plus privilege escalation
     * ({@code pkexec} on Linux, UAC on Windows, {@code sudo} on macOS).
     * Flatpak sandboxing blocks {@code pkexec} and direct block-device
     * access, so SMART is disabled there until a host-escape mechanism
     * (e.g.&nbsp;{@code flatpak-spawn --host}) is implemented.
     */
    public static boolean isSmartSupported(String osName) {
        if (isMacOs(osName) || isWindows(osName)) {
            return true;
        }
        if (isLinux(osName)) {
            return !isFlatpak();
        }
        return false;
    }

    /** The disk model power shell utility. */
    public static final String DISK_MODEL_PS_FILENAME = "disk-model.ps1";
    
    /** The capacity power shell utility. */
    public static final String CAPACITY_PS_FILENAME = "capacity.ps1";

    /** Outcome of an elevated disk cache drop attempt. */
    public enum CacheDropResult {
        /** Cache was dropped. */
        CLEARED,
        /** Escalation was cancelled, refused, or the command failed. */
        DENIED,
        /** No password prompt is possible in this environment (e.g. Flatpak). */
        UNAVAILABLE
    }

    private static final String DROP_SENTINEL = "---DROP_DONE:";
    // allows time for the user to enter their password
    private static final long DROP_TIMEOUT_SECONDS = 120;
    // pkexec: 127 = not authorized or no authentication agent, 126 = dialog dismissed
    private static final int PKEXEC_NOT_AUTHORIZED = 127;

    /**
     * Flushes and drops the OS disk cache on Linux or macOS through the
     * privileged shell shared with SMART ({@code pkexec bash} on Linux,
     * {@code osascript} + {@code sudo -S bash} on macOS). The user is
     * prompted for their password only if the shell is not already running.
     *
     * @return the outcome of the attempt
     */
    public static CacheDropResult dropCacheElevated() {
        if (App.isWindows()) {
            return UtilsWin.dropCacheElevated();
        }
        String command;
        if (App.isLinux()) {
            if (isFlatpak() || !isOnPath("pkexec")) {
                return CacheDropResult.UNAVAILABLE;
            }
            command = "sync; echo 1 > /proc/sys/vm/drop_caches";
        } else if (App.isMacOs()) {
            command = "sync; purge";
        } else {
            return CacheDropResult.UNAVAILABLE;
        }

        try {
            if (Smart.process == null || !Smart.process.isAlive()) {
                App.msg("Requesting administrator privileges to clear the disk cache...");
                Smart.startPrivilegedShell("clear the disk cache using the \u201Csudo purge\u201D command");
                Smart.startHeartbeat();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Privilege escalation for cache drop failed", e);
            return CacheDropResult.DENIED;
        }

        synchronized (Smart.pLock) {
            Process shell = Smart.process;
            if (shell == null || Smart.shellWriter == null || Smart.shellReader == null) {
                return CacheDropResult.DENIED;
            }
            CompletableFuture<Integer> exitCode = new CompletableFuture<>();
            Thread reader = Thread.startVirtualThread(() -> {
                try {
                    String line;
                    while ((line = Smart.shellReader.readLine()) != null) {
                        if (line.startsWith(DROP_SENTINEL)) {
                            String code = line.substring(DROP_SENTINEL.length()).replace("---", "").trim();
                            exitCode.complete(Integer.parseInt(code));
                            return;
                        }
                    }
                    exitCode.complete(-1); // shell exited, escalation refused
                } catch (IOException | NumberFormatException e) {
                    exitCode.completeExceptionally(e);
                }
            });
            try {
                Smart.shellWriter.write(command + "; echo \"" + DROP_SENTINEL + "$?---\"\n");
                Smart.shellWriter.flush();
                int code = exitCode.get(DROP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (code == 0) {
                    App.msg("Disk cache cleared");
                    return CacheDropResult.CLEARED;
                }
                LOGGER.log(Level.WARNING, "Elevated cache drop failed, exit={0}", code);
                return code == -1 ? shellExitResult(shell) : CacheDropResult.DENIED;
            } catch (TimeoutException e) {
                LOGGER.warning("Elevated cache drop timed out, stopping privileged shell");
                shell.destroyForcibly();
                reader.interrupt();
                return CacheDropResult.DENIED;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                shell.destroyForcibly();
                return CacheDropResult.DENIED;
            } catch (IOException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "Elevated cache drop failed", e);
                return shellExitResult(shell);
            }
        }
    }
    
    private static boolean isOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, executable).canExecute()) return true;
        }
        return false;
    }
    
    /**
     * Classifies a privileged shell that exited before running the command.
     * pkexec exits 127 when no polkit agent can prompt or authorization was not
     * obtained; that falls back to the manual prompt instead of failing READ.
     */
    private static CacheDropResult shellExitResult(Process shell) {
        try {
            if (App.isLinux() && shell.waitFor(2, TimeUnit.SECONDS)
                    && shell.exitValue() == PKEXEC_NOT_AUTHORIZED) {
                LOGGER.warning("pkexec could not authorize (no polkit agent?), using manual prompt");
                return CacheDropResult.UNAVAILABLE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return CacheDropResult.DENIED;
    }

    // ─── System ID ────────────────────────────────────────────────────────────

    /**
     * Returns a stable, non-PII system identifier suitable for anonymous
     * benchmark attribution.
     *
     * <p>Strategy (first successful source wins):
     * <ol>
     *   <li>Windows &mdash; {@code MachineGuid} from the Cryptography registry key
     *       (readable without admin)</li>
     *   <li>Linux   &mdash; {@code /etc/machine-id} (world-readable)</li>
     *   <li>macOS   &mdash; {@code IOPlatformUUID} via {@code ioreg} (no admin)</li>
     *   <li>Fallback &mdash; the previously persisted {@code systemId} from
     *       {@code jdm.properties}, or a freshly generated {@link java.util.UUID}</li>
     * </ol>
     *
     * <p>The raw OS value is SHA-256 hashed and the first 32 hex characters are
     * returned, so the original system identifier is never stored or transmitted.
     *
     * @param osName      the value of {@code System.getProperty("os.name")}
     * @param persistedId the value already stored in {@code jdm.properties}
     *                    (may be {@code null} or blank on first run)
     * @return a 32-character lowercase hex string identifying this system
     */
    public static String getMachineSystemId(String osName, String persistedId) {
        String raw = null;
        try {
            if (isWindows(osName)) {
                raw = UtilsWin.readMachineGuid();
            } else if (isLinux(osName)) {
                raw = UtilsLinux.readMachineId();
            } else if (isMacOs(osName)) {
                raw = UtilsMac.readPlatformUuid();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "getMachineSystemId: OS source failed, using fallback", e);
        }

        if (raw != null && !raw.isBlank()) {
            return sha256Hex32(raw);
        }

        // Fallback: reuse persisted id (survives across sessions) or generate once.
        if (persistedId != null && !persistedId.isBlank()) {
            return persistedId;
        }
        LOGGER.warning("getMachineSystemId: all sources failed, generating a random id");
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    /** Returns the first 32 hex characters of the SHA-256 hash of {@code input}. */
    private static String sha256Hex32(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // SHA-256 is mandatory in every JVM
        }
    }

    /**
     * Returns the detailed OS release label stored as {@code osLabel}, e.g.
     * {@code "Ubuntu 24.04.1 LTS"}, {@code "macOS 15.1"} or
     * {@code "Windows 11 23H2 (build 26100)"}. Falls back to {@code osName}.
     *
     * @param osName the value of {@code System.getProperty("os.name")}
     * @return the OS label, never {@code null} when {@code osName} is non-null
     */
    public static String getOsLabel(String osName) {
        String label = null;
        if (isLinux(osName)) {
            label = UtilsLinux.getDistroName();
        } else if (isMacOs(osName)) {
            label = UtilsMac.getMacOsLabel();
        } else if (isWindows(osName)) {
            label = UtilsWin.getWindowsLabel(osName);
        }
        return label != null ? label : osName;
    }

    /**
     * Formats an OS for display. Linux shows the distro after a middle dot
     * (e.g. {@code "Linux · Fedora Linux 44 (Workstation Edition)"}) so distro
     * names with parentheses do not nest; other platforms show the label alone
     * since it already names the OS.
     */
    public static String formatOsDisplay(String os, String osLabel) {
        if (osLabel == null || osLabel.isBlank() || osLabel.equals(os)) return os;
        if (os == null) return osLabel;
        return isLinux(os) ? os + " \u00B7 " + osLabel : osLabel;
    }
}

