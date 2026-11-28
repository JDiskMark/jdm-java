package org.metricus.jdm.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jdiskmark.App;

/**
 * Tracks test data that Java keeps locked on Windows FAT32/exFAT drives after
 * a Direct I/O run (JDK handle leak, #257 #260 #268).
 *
 * Further runs on such a drive use a fresh {@code jdm-data/run-N} folder, the
 * space still held is reported so the user can be offered a restart, and
 * folders left behind by an earlier session are removed at startup.
 * Locations are keyed by path and volume serial, so a different drive given
 * the same letter is not treated as holding locked data.
 */
public final class LockedTestData {

    public static final String RUN_DIR_PREFIX = "run-";
    private static final String PROBE_PREFIX = ".jdm-dio-probe-";
    private static final File PENDING_FILE = new File(App.APP_CACHE_DIR, "pending-removal.txt");
    private static final Logger LOG = Logger.getLogger(LockedTestData.class.getName());

    private record Key(String location, Object volumeSerial) {}

    private static final class Held {
        final List<File> dirs = new ArrayList<>();
        long bytes;
    }

    private static final Map<Key, Held> HELD = new ConcurrentHashMap<>();
    private static final Set<Key> KEEP_ACCEPTED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger RUN_NUMBER = new AtomicInteger(1);
    private static volatile CompletableFuture<Void> startupCleanup = CompletableFuture.completedFuture(null);

    private LockedTestData() {}

    private static Key key(File location) {
        Path path = location.toPath().toAbsolutePath();
        Object serial;
        try {
            serial = Files.getFileStore(path).getAttribute("volume:vsn");
        } catch (IOException | RuntimeException e) {
            serial = null;
        }
        return new Key(path.toString(), serial);
    }

    /**
     * Records a test data folder that could not be deleted because Direct I/O
     * was kept. The caller registers it for removal after exit.
     *
     * @return true for the first locked folder at this location this session
     */
    public static boolean register(File location, File dir, long bytes) {
        Held held = HELD.computeIfAbsent(key(location), k -> new Held());
        synchronized (held) {
            boolean first = held.dirs.isEmpty();
            held.dirs.add(dir);
            held.bytes += bytes;
            return first;
        }
    }

    /** True when an earlier run this session left locked test data at {@code location}. */
    public static boolean hasLocked(File location) {
        return location != null && HELD.containsKey(key(location));
    }

    /** Bytes held by locked test data at {@code location}; 0 when none. */
    public static long lockedBytes(File location) {
        Held held = location == null ? null : HELD.get(key(location));
        if (held == null) return 0;
        synchronized (held) { return held.bytes; }
    }

    /** Number of runs that left locked test data at {@code location}. */
    public static int lockedRuns(File location) {
        Held held = location == null ? null : HELD.get(key(location));
        if (held == null) return 0;
        synchronized (held) { return held.dirs.size(); }
    }

    /** e.g. "3 runs, 12.4 GB", or null when nothing is held at {@code location}. */
    public static String describe(File location) {
        int runs = lockedRuns(location);
        if (runs == 0) return null;
        return runs + (runs == 1 ? " run, " : " runs, ") + formatBytes(lockedBytes(location));
    }

    /** A new, not yet existing {@code jdm-data/run-N} folder at {@code location}. */
    public static File nextRunDir(File location) {
        File dataDir = new File(location, App.DATADIRNAME);
        File dir;
        do {
            dir = new File(dataDir, RUN_DIR_PREFIX + RUN_NUMBER.incrementAndGet());
        } while (dir.exists());
        return dir;
    }

    /**
     * The benchmark location for a test folder: the parent of {@code jdm-data},
     * also when the test folder is a {@code jdm-data/run-N} subfolder.
     */
    public static Path locationOf(Path testDir) {
        Path parent = testDir.getParent();
        if (parent == null) return testDir;
        Path name = parent.getFileName();
        if (name != null && name.toString().equalsIgnoreCase(App.DATADIRNAME)
                && parent.getParent() != null) {
            return parent.getParent();
        }
        return parent;
    }

    /** True when the user already chose to keep Direct I/O at {@code location} this session. */
    public static boolean isKeepAccepted(String location) {
        return KEEP_ACCEPTED.contains(key(new File(location)));
    }

    /** Remembers that the user chose to keep Direct I/O at {@code location} this session. */
    public static void acceptKeep(String location) {
        KEEP_ACCEPTED.add(key(new File(location)));
    }

    /** Total size of the files under {@code dir}; 0 when it can't be read. */
    public static long sizeOf(File dir) {
        if (dir == null || !dir.exists()) return 0;
        try (Stream<Path> files = Files.walk(dir.toPath())) {
            return files.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------------
    // Leftovers from earlier sessions
    // ---------------------------------------------------------------------

    /**
     * Persists a path registered for removal after exit, so it can be removed
     * at the next startup if the exit cleanup did not run or finish.
     */
    public static synchronized void rememberPending(Path path) {
        if (!isTestDataPath(path)) return;
        Set<String> lines = readPending();
        if (lines.add(path.toAbsolutePath().toString())) {
            writePending(lines);
        }
    }

    /**
     * Starts removing test data left by an earlier session in the background.
     * Only paths recorded by {@link #rememberPending} are touched; paths on
     * drives that are not connected are kept for next time.
     */
    public static void cleanupLeftoversAsync() {
        if (!App.isWindows()) return;
        startupCleanup = CompletableFuture.runAsync(LockedTestData::cleanupLeftovers);
    }

    /** Waits (up to 10 s) for the startup cleanup so a new run doesn't race it. */
    public static void awaitStartupCleanup() {
        try {
            startupCleanup.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.log(Level.FINE, "startup cleanup not finished", e);
        }
    }

    private static void cleanupLeftovers() {
        Set<String> snapshot;
        synchronized (LockedTestData.class) {
            snapshot = readPending();
        }
        Set<String> done = new LinkedHashSet<>();
        for (String line : snapshot) {
            Path path = Path.of(line);
            Path root = path.getRoot();
            if (!isTestDataPath(path)) {
                done.add(line);
            } else if (root == null || !Files.exists(root)) {
                // drive not connected: keep for next time
            } else if (!Files.exists(path)) {
                done.add(line);
            } else {
                String failure = path.toFile().isDirectory()
                        ? Util.deleteDirectoryReason(path.toFile())
                        : deleteFileReason(path);
                if (failure == null) {
                    done.add(line);
                    App.msg("Removed test data left by an earlier session: " + path);
                } else {
                    LOG.log(Level.WARNING, "Could not remove leftover test data: {0}", failure);
                }
            }
        }
        if (!done.isEmpty()) {
            synchronized (LockedTestData.class) {
                Set<String> lines = readPending();
                lines.removeAll(done);
                writePending(lines);
            }
        }
    }

    private static String deleteFileReason(Path file) {
        try {
            Files.deleteIfExists(file);
            return null;
        } catch (IOException e) {
            return file + ": " + Util.describeIoError(e);
        }
    }

    /** Only JDiskMark test folders and probe files are ever removed. */
    private static boolean isTestDataPath(Path path) {
        Path name = path.getFileName();
        if (name == null) return false;
        String n = name.toString();
        if (n.equalsIgnoreCase(App.DATADIRNAME)) return true;
        if (n.startsWith(PROBE_PREFIX) && n.endsWith(".tmp")) return true;
        Path parent = path.getParent();
        return n.startsWith(RUN_DIR_PREFIX) && parent != null && parent.getFileName() != null
                && parent.getFileName().toString().equalsIgnoreCase(App.DATADIRNAME);
    }

    private static Set<String> readPending() {
        Set<String> lines = new LinkedHashSet<>();
        if (!PENDING_FILE.exists()) return lines;
        try {
            for (String line : Files.readAllLines(PENDING_FILE.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank()) lines.add(line.trim());
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not read " + PENDING_FILE, e);
        }
        return lines;
    }

    private static void writePending(Set<String> lines) {
        try {
            if (lines.isEmpty()) {
                Files.deleteIfExists(PENDING_FILE.toPath());
            } else {
                Files.createDirectories(PENDING_FILE.toPath().getParent());
                Files.write(PENDING_FILE.toPath(), lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not write " + PENDING_FILE, e);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes >= App.GIGABYTE) return String.format("%.1f GB", bytes / (double) App.GIGABYTE);
        if (bytes >= App.MEGABYTE) return String.format("%.1f MB", bytes / (double) App.MEGABYTE);
        return String.format("%.1f KB", bytes / (double) App.KILOBYTE);
    }
}
