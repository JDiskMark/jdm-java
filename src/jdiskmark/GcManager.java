package jdiskmark;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.logging.Logger;

/**
 * Utility class for JVM garbage collection management to minimize the effects
 * of GC events on benchmark sample measurements. Addresses issue #134.
 *
 * <p>Strategies implemented:
 * <ol>
 *   <li>Trigger a GC cycle before each benchmark to reduce the likelihood of
 *       a GC event occurring during sample measurements.</li>
 *   <li>Detect GC events during a sample measurement and retake the sample
 *       if GC is detected, up to {@link #MAX_GC_RETRIES} times.</li>
 * </ol>
 */
public class GcManager {

    private static final Logger logger = Logger.getLogger(GcManager.class.getName());

    /** Maximum number of times a sample will be retaken due to a GC event. */
    static final int MAX_GC_RETRIES = 3;

    private GcManager() {}

    /**
     * Triggers a GC cycle before a benchmark starts to reduce the likelihood
     * of a GC event occurring during sample measurements (issue #134, recommendation #3).
     */
    public static void triggerPreBenchmarkGc() {
        logger.fine("Triggering pre-benchmark GC to minimize GC interference during benchmark");
        System.gc();
    }

    /**
     * Returns the total number of GC collections across all GC collectors.
     * Uses the standard {@link GarbageCollectorMXBean} API.
     *
     * @return total GC collection count, or 0 if unavailable
     */
    public static long getTotalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count > 0) {
                total += count;
            }
        }
        return total;
    }

    /**
     * Returns {@code true} if at least one GC collection has occurred since
     * the given snapshot count was taken.
     *
     * @param gcCountSnapshot the GC count taken before the operation to check
     * @return true if GC occurred since the snapshot
     */
    public static boolean gcOccurredSince(long gcCountSnapshot) {
        return getTotalGcCount() > gcCountSnapshot;
    }
}
