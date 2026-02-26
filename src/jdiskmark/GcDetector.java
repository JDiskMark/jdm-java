package jdiskmark;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

/**
 * Detects JVM garbage collection events during sample measurements.
 * Used to identify and retry samples that were affected by GC pauses.
 */
public class GcDetector {

    private static final Logger logger = Logger.getLogger(GcDetector.class.getName());

    private final AtomicBoolean gcDetected = new AtomicBoolean(false);
    private final List<NotificationEmitter> emitters = new ArrayList<>();
    private final NotificationListener listener = (Notification notification, Object handback) -> {
        if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            gcDetected.set(true);
        }
    };

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

    /** Returns true if a GC event was detected since the last {@code start()} or {@code reset()}. */
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
            } catch (Exception e) {
                logger.fine("Failed to remove GC listener: " + e.getMessage());
            }
        }
        emitters.clear();
    }
}
