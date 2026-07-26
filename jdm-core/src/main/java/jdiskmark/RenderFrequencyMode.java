package jdiskmark;

/**
 * Controls how often benchmark samples are published to the UI during a run.
 * <p>
 * PER_SAMPLE   – publish after every sample (default; lowest latency, highest CPU)
 * PER_OPERATION – buffer all samples and publish once the operation completes
 * PER_100MS / PER_500MS / PER_1000MS – batch samples and flush on a fixed interval
 */
public enum RenderFrequencyMode {
    PER_SAMPLE("Per Sample"),
    PER_OPERATION("Per Operation"),
    PER_100MS("Per 100ms"),
    PER_500MS("Per 500ms"),
    PER_1000MS("Per 1000ms");

    private final String label;

    RenderFrequencyMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Returns the flush interval in milliseconds for timed modes; 0 for non-timed modes. */
    public long getIntervalMillis() {
        return switch (this) {
            case PER_100MS  -> 100;
            case PER_500MS  -> 500;
            case PER_1000MS -> 1000;
            default         -> 0;
        };
    }
}
