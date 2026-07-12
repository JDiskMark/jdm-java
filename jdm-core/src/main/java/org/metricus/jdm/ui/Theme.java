package org.metricus.jdm.ui;

import jdiskmark.App;
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

    public String getLafClassName() {
        boolean isMac = App.isMacOs();

        return switch (this) {
            case DARK    -> isMac ? "com.formdev.flatlaf.themes.FlatMacDarkLaf"
                                 : "com.formdev.flatlaf.FlatDarkLaf";
            case LIGHT   -> isMac ? "com.formdev.flatlaf.themes.FlatMacLightLaf"
                                 : "com.formdev.flatlaf.FlatLightLaf";
            case DARCULA -> "com.formdev.flatlaf.FlatDarculaLaf";
            case OLD_GLORY -> isMac ? "com.formdev.flatlaf.themes.FlatMacDarkLaf"
                                 : "com.formdev.flatlaf.FlatDarkLaf";
            case SAKURA  -> isMac ? "com.formdev.flatlaf.themes.FlatMacLightLaf"
                                 : "com.formdev.flatlaf.FlatLightLaf";
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
