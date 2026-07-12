package org.metricus.jdm.ui;

import jdiskmark.Gui;
import org.metricus.jdm.ui.theme.DarculaTheme;
import org.metricus.jdm.ui.theme.DarkTheme;
import org.metricus.jdm.ui.theme.LightTheme;
import org.metricus.jdm.ui.theme.OldGloryTheme;
import org.metricus.jdm.ui.theme.SakuraTheme;

/**
 * Available window themes (look-and-feel configurations).
 * <p>
 * Each constant holds a {@link ThemeDefinition} that encapsulates colors,
 * FlatLaf extras, UIManager overrides, and badge styling.  Adding a new
 * theme requires a new {@code ThemeDefinition} class and one line here.
 * </p>
 */
public enum Theme {
    DARK("Dark", new DarkTheme()),
    LIGHT("Light", new LightTheme()),
    DARCULA("Darcula", new DarculaTheme()),
    OLD_GLORY("Old Glory", new OldGloryTheme()),
    SAKURA("Sakura", new SakuraTheme());

    private final String displayName;
    private final ThemeDefinition definition;

    Theme(String displayName, ThemeDefinition definition) {
        this.displayName = displayName;
        this.definition = definition;
    }

    public String displayName() { return displayName; }

    public ThemeDefinition definition() { return definition; }

    /**
     * Returns {@code true} if this theme bundles its own chart palette
     * that must not be overridden by the user's saved palette preference.
     */
    public boolean hasLinkedPalette() {
        return this == OLD_GLORY || this == SAKURA;
    }

    /**
     * Configures the FlatLaf look-and-feel for this theme.
     * Called once at startup <em>before</em> the main frame is created.
     * Unlike {@link #apply()}, this does <strong>not</strong> call
     * {@code FlatLaf.updateUI()} or repaint existing components.
     */
    public void configureLaf() {
        Gui.configureLaf(definition);
    }

    /**
     * If this theme owns a hard-linked chart palette, applies it now.
     * No-op for themes that rely on the user's saved palette preference.
     */
    public void applyLinkedPalette() {
        switch (this) {
            case OLD_GLORY -> ChartPalette.setOldGloryColorScheme();
            case SAKURA    -> ChartPalette.setSakuraColorScheme();
            default -> { /* user-selected palette loaded separately */ }
        }
    }

    /** Applies this theme's look-and-feel and updates the UI. */
    public void apply() {
        Gui.applyTheme(this);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
