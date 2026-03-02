package jdiskmark;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

/**
 * Detects JVM garbage collection events during sample measurements.
 * Used to identify and retry samples that were affected by GC pauses.
 */
public class GcDetector {

    static final int MAX_GC_RETRIES = 3;
    
    private static final Logger logger = Logger.getLogger(GcDetector.class.getName());

    public static boolean gcRetryEnabled = false;
    public static boolean gcHintsEnabled = false;
    
    private final AtomicBoolean gcDetected = new AtomicBoolean(false);
    private final List<NotificationEmitter> emitters = new ArrayList<>();
    private final NotificationListener listener = (Notification notification, Object handback) -> {
        if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            gcDetected.set(true);
        }
    };
    
    public static void printActive() {
        String gcNames = ManagementFactory.getGarbageCollectorMXBeans().stream()
            .map(bean -> bean.getName().split(" ")[0]) // Get the first word (e.g., "ZGC", "G1", "PS")
            .distinct()
            .collect(Collectors.joining(", "));
        App.msg("Active GC: " + gcNames);
    }

    /** Start listening for GC events and reset the detected flag. */
    public void start() {
        stop(); // Remove any previously registered listeners before re-registering
        gcDetected.set(false);
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener(listener, null, null);
                emitters.add(emitter);
            }
        }
    }

    /**
     * @return  true if a GC event was detected since the last {@code start()} or {@code reset()}.
     */
    public boolean isGcDetected() {
        return gcDetected.get();
    }

    /** Reset the detected flag without re-registering listeners. */
    public void reset() {
        gcDetected.set(false);
    }

    /** Stop listening for GC events and remove all registered listeners. */
    public void stop() {
        for (NotificationEmitter emitter : emitters) {
            try {
                emitter.removeNotificationListener(listener);
            } catch (ListenerNotFoundException e) {
                logger.log(Level.FINE, "Failed to remove GC listener: {0}", e.getMessage());
            } catch (RuntimeException re) {
                logger.log(Level.FINE, "Unexpected error: {0}", re.getMessage());
            }
        }
        emitters.clear();
    }
    
    public static void triggerAndWait() {
        
        App.msg("triggering gc w 2s timeout");
        long countBefore = getGlobalGcCount();
        System.gc();

        long startTime = System.currentTimeMillis();
        // Wait for the GC count to increment OR timeout after 2 seconds
        while (getGlobalGcCount() <= countBefore && (System.currentTimeMillis() - startTime) < 2000) {
            try {
                Thread.sleep(50); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Final brief settle time for background cleanup
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    }

    private static long getGlobalGcCount() {
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();

        // Prefer ZGC-style "Cycles" beans when present; otherwise count all collectors
        boolean hasCyclesBeans = beans.stream()
                .anyMatch(bean -> bean.getName().contains("Cycles"));

        return beans.stream()
                .filter(bean -> !hasCyclesBeans || bean.getName().contains("Cycles"))
                .mapToLong(bean -> {
                    long count = bean.getCollectionCount();
                    return count < 0 ? 0 : count;
                })
                .sum();
    }
}
