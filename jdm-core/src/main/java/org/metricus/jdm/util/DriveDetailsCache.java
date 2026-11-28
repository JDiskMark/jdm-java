package org.metricus.jdm.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caches the slow, per-drive OS lookups (model, bus type, USB version, file
 * system, sector size, partition) used by the drive UI.
 *
 * Entries are keyed by mount path but only reused while the volume
 * fingerprint still matches, so a mount point or drive letter that is handed
 * to a different device is looked up again. Usage and free space are not
 * cached. Safe to call from any thread; call from a background thread, as a
 * miss runs external processes.
 */
public final class DriveDetailsCache {

    public record Details(String model, String busType, String usbVersion,
                          String filesystem, String sectorSize, String partitionId) {

        /** Bus type with the USB version appended for USB drives, e.g. "USB 3.2 Gen 2". */
        public String busDisplay() {
            if ("USB".equalsIgnoreCase(busType) && usbVersion != null) {
                return busType + " " + usbVersion;
            }
            return busType;
        }
    }

    private record Slot(String fingerprint, CompletableFuture<Details> details) {}

    private static final Logger LOG = Logger.getLogger(DriveDetailsCache.class.getName());
    private static final Map<String, Slot> CACHE = new ConcurrentHashMap<>();

    private DriveDetailsCache() {}

    /**
     * Returns the details for the drive containing {@code location}, looking
     * them up only when there is no entry for the current volume.
     */
    public static Details get(File location) {
        String key = location.getAbsolutePath();
        String fingerprint = fingerprint(location);
        if (fingerprint == null) {
            CACHE.remove(key);
            return load(location);
        }
        boolean[] owner = {false};
        Slot slot = CACHE.compute(key, (k, old) -> {
            if (old != null && old.fingerprint().equals(fingerprint)) {
                return old;
            }
            owner[0] = true;
            return new Slot(fingerprint, new CompletableFuture<>());
        });
        if (owner[0]) {
            try {
                slot.details().complete(load(location));
            } catch (RuntimeException ex) {
                slot.details().completeExceptionally(ex);
                CACHE.remove(key, slot);
                throw ex;
            }
        }
        return slot.details().join();
    }

    /** Drops every cached entry so the next lookups read the drives again. */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Identifies the volume currently mounted at {@code location}, or null
     * when it cannot be read (e.g. the drive was removed).
     */
    private static String fingerprint(File location) {
        try {
            FileStore store = Files.getFileStore(location.toPath());
            String serial = null;
            try {
                serial = String.valueOf(store.getAttribute("volume:vsn"));
            } catch (UnsupportedOperationException | IllegalArgumentException ignore) {
                // volume serial is only available on Windows
            }
            return store.name() + '|' + store.type() + '|' + store.getTotalSpace() + '|' + serial;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static Details load(File location) {
        Path path = location.toPath();
        String model = safe(() -> Util.getDriveModel(location), location);
        String busType = safe(() -> Util.getBusType(path), location);
        String usbVersion = "USB".equalsIgnoreCase(busType)
                ? safe(() -> Util.getUsbVersion(path), location) : null;
        String filesystem = safe(() -> Util.getFilesystem(path), location);
        String sectorSize = safe(() -> Util.getSectorSize(path), location);
        String partitionId = safe(() -> Util.getPartitionId(path), location);
        return new Details(model, busType, usbVersion, filesystem, sectorSize, partitionId);
    }

    private static <T> T safe(Supplier<T> lookup, File location) {
        try {
            return lookup.get();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "drive lookup failed for " + location, ex);
            return null;
        }
    }
}
