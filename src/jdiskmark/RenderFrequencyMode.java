package jdiskmark;

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
    
    public long getIntervalMillis() {
        return switch (this) {
            case PER_100MS -> 100;
            case PER_500MS -> 500;
            case PER_1000MS -> 1000;
            default -> 0;
        };
    }
}