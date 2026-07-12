package org.metricus.jdm.ui;

import jdiskmark.Gui;

/**
 * Available window themes (look-and-feel configurations).
 * <p>
 * Each constant maps to a {@code Gui.goXxxTheme()} method that configures
 * FlatLaf and updates the UI.  The {@link jdiskmark.GraphThemeMenu} iterates
 * {@code values()} to build the menu automatically.
 * </p>
 */
public enum Theme {
    DARK("Dark"),
    LIGHT("Light"),
    DARCULA("Darcula"),
    OLD_GLORY("Old Glory"),
    SAKURA("Sakura");

    private final String displayName;

    Theme(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

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
        switch (this) {
            case DARK      -> Gui.configureDarkLaf();
            case LIGHT     -> Gui.configureLightLaf();
            case DARCULA   -> Gui.configureDarculaLaf();
            case OLD_GLORY -> Gui.configureOldGloryLaf();
            case SAKURA    -> Gui.configureSakuraLaf();
        }
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
        switch (this) {
            case DARK      -> Gui.goDarkTheme();
            case LIGHT     -> Gui.goLightTheme();
            case DARCULA   -> Gui.goDarculaTheme();
            case OLD_GLORY -> Gui.goOldGloryTheme();
            case SAKURA    -> Gui.goSakuraTheme();
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
