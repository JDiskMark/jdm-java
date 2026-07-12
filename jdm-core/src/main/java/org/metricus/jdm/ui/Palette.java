package org.metricus.jdm.ui;

/**
 * Available chart colour palettes.
 * <p>
 * Each constant maps to a {@link ChartPalette} method that configures the
 * JFreeChart renderers and axes.  The {@link jdiskmark.GraphPaletteMenu}
 * iterates {@code values()} to build the menu automatically.
 * </p>
 */
public enum Palette {
    CLASSIC("Classic"),
    BLUE_GREEN("Blue Green"),
    BARD_COOL("Bard Cool"),
    BARD_WARM("Bard Warm"),
    BETA("Beta");

    private final String displayName;

    Palette(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    /** Applies this palette's colour scheme to the chart renderers. */
    public void apply() {
        switch (this) {
            case CLASSIC    -> ChartPalette.setClassicColorScheme();
            case BLUE_GREEN -> ChartPalette.setBlueGreenScheme();
            case BARD_COOL  -> ChartPalette.setCoolColorScheme();
            case BARD_WARM  -> ChartPalette.setWarmColorScheme();
            case BETA       -> ChartPalette.setBetaColorScheme();
        }
    }
}
