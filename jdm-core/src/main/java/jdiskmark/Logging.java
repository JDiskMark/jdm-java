package jdiskmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Bootstraps file-based logging with rotation for production releases.
 *
 * <p>Called once as the very first statement in {@link App#main} so that all
 * {@code java.util.logging} output — including Hibernate, Derby, and every
 * class in this package — is captured to a rotating log file before any other
 * initialization runs.
 *
 * <h2>Log directories by environment</h2>
 * <ul>
 *   <li><b>Windows installer</b> — {@code %LOCALAPPDATA%\JDiskMark\logs\}</li>
 *   <li><b>Linux installer</b>  — {@code $XDG_STATE_HOME/jdiskmark/logs/} or
 *       {@code ~/.local/state/jdiskmark/logs/}</li>
 *   <li><b>macOS installer</b>  — {@code ~/Library/Logs/jdiskmark/}</li>
 *   <li><b>Portable / IDE</b>   — {@code ./logs/} (relative to working directory)</li>
 * </ul>
 *
 * <h2>Rotation policy</h2>
 * 5 MB per file, 3 files max (~15 MB ceiling). Files are named
 * {@code jdiskmark-0.log} through {@code jdiskmark-2.log}.
 *
 * <h2>Console handler</h2>
 * Removed from the root logger in GUI mode so no output leaks to a hidden
 * console window. Kept active in CLI mode so terminal users see output as
 * expected.
 */
public final class Logging {

    /** ~15 MB ceiling: 3 files × 5 MB each. */
    private static final int  LOG_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int  LOG_FILE_COUNT      = 3;
    private static final String LOG_FILE_PATTERN  = "jdiskmark-%u-%g.log";

    private static Path resolvedLogDir = null;

    private Logging() {}

    /**
     * Initialise file-based logging. Must be the first call in
     * {@link App#main}.
     *
     * @param mode the application mode resolved from the command-line args
     *             ({@link App.Mode#GUI} or {@link App.Mode#CLI})
     */
    public static void init(App.Mode mode) {
        Path logDir = resolveLogDir();
        resolvedLogDir = logDir;

        try {
            Files.createDirectories(logDir);
        } catch (IOException ex) {
            // Can't create log dir — fall back to stderr and continue.
            System.err.println("[Logging] Could not create log directory: " + logDir + " — " + ex.getMessage());
            return;
        }

        String pattern = logDir.resolve(LOG_FILE_PATTERN).toString();

        try {
            FileHandler fh = new FileHandler(
                    pattern,
                    LOG_FILE_SIZE_BYTES,
                    LOG_FILE_COUNT,
                    true /* append */);
            fh.setFormatter(new CompactFormatter());
            fh.setLevel(Level.ALL);

            Logger root = Logger.getLogger("");
            root.addHandler(fh);
            // Root stays at INFO: keeps third-party library noise (Hibernate, Derby, etc.)
            // out of the log file. The FileHandler is ALL so jdiskmark-specific loggers
            // can be lowered independently if finer diagnostics are ever needed.
            root.setLevel(Level.INFO);

            // In GUI mode there is no visible console — remove the default
            // ConsoleHandler so nothing is silently swallowed by a hidden stream.
            // In CLI mode we keep it so terminal users see live output.
            if (mode == App.Mode.GUI) {
                for (Handler h : root.getHandlers()) {
                    if (h instanceof ConsoleHandler) {
                        root.removeHandler(h);
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("[Logging] Could not open log file at: " + pattern + " — " + ex.getMessage());
        }
    }

    /**
     * Returns the log directory that was resolved during {@link #init}.
     * Returns {@code null} if {@code init} has not yet been called.
     */
    public static Path getLogDir() {
        return resolvedLogDir;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Determines the correct log directory for the current platform and
     * deployment mode (installer vs. portable/IDE).
     */
    static Path resolveLogDir() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (!isPackagedInstall()) {
            // Portable mode: ./logs relative to the working directory.
            // Keeps the log files next to the executable in the extracted zip.
            return Path.of(".", "logs");
        }

        if (os.contains("win")) {
            // Windows installer — %LOCALAPPDATA% avoids cloud sync (%APPDATA%\Roaming)
            // and is persistent (unlike %TEMP%).
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null || localAppData.isBlank()) {
                // Fallback: construct manually (should never happen on a real Windows install)
                localAppData = System.getProperty("user.home") + "\\AppData\\Local";
            }
            return Path.of(localAppData, "JDiskMark", "logs");

        } else if (os.contains("mac")) {
            // macOS convention: ~/Library/Logs/<app>
            return Path.of(System.getProperty("user.home"), "Library", "Logs", "jdiskmark");

        } else {
            // Linux — honour XDG Base Directory spec for state files.
            // We are not a system service, so /var/log is inappropriate.
            String xdgState = System.getenv("XDG_STATE_HOME");
            if (xdgState != null && !xdgState.isBlank()) {
                return Path.of(xdgState, "jdiskmark", "logs");
            }
            return Path.of(System.getProperty("user.home"), ".local", "state", "jdiskmark", "logs");
        }
    }

    /**
     * Detects whether the app was launched from a jpackage-produced installer.
     *
     * <p>Layout differences by platform:
     * <ul>
     *   <li><b>Windows / Linux jpackage</b> — CWD is the install root, so
     *       {@code ./app/} exists directly.</li>
     *   <li><b>macOS jpackage (.app bundle)</b> — the launcher lives in
     *       {@code Contents/MacOS/} and CWD is set to that directory, so
     *       {@code app/} is one level up at {@code ../app/} (i.e.
     *       {@code Contents/app/}).</li>
     * </ul>
     * In IDE / portable mode neither path exists.
     */
    private static boolean isPackagedInstall() {
        // Linux / Windows: CWD = install root, app/ is a direct child.
        if (java.nio.file.Files.isDirectory(Path.of(".", "app"))) {
            return true;
        }
        // macOS .app bundle: CWD = Contents/MacOS/, app/ is at ../app/.
        if (java.nio.file.Files.isDirectory(Path.of("..", "app"))) {
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Compact log formatter
    // -------------------------------------------------------------------------

    /**
     * Produces concise single-line log records:
     * {@code 2026-06-05T19:44:01 INFO  jdiskmark.App - message}
     */
    private static final class CompactFormatter extends Formatter {

        private static final java.time.format.DateTimeFormatter TS =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        @Override
        public String format(LogRecord r) {
            String ts = java.time.LocalDateTime
                    .ofInstant(r.getInstant(), java.time.ZoneId.systemDefault())
                    .format(TS);

            String level  = String.format("%-7s", r.getLevel().getName());
            String logger = r.getLoggerName();
            // Trim package prefix for readability in the log file
            if (logger != null && logger.startsWith("jdiskmark.")) {
                logger = logger.substring("jdiskmark.".length());
            }

            StringBuilder sb = new StringBuilder(128);
            sb.append(ts).append(' ')
                    .append(level).append(' ')
                    .append(logger).append(" - ")
                    .append(formatMessage(r))
                    .append(System.lineSeparator());

            if (r.getThrown() != null) {
                // Append the full stack trace for exceptions
                java.io.StringWriter sw = new java.io.StringWriter();
                r.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                sb.append(sw);
            }

            return sb.toString();
        }
    }
}
