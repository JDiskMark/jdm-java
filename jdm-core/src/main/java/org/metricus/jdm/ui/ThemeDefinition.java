package org.metricus.jdm.ui;

import java.awt.Color;
import java.util.Map;

/**
 * Contract for a window theme's colors and LAF configuration.
 * <p>
 * Each implementation supplies the FlatLaf class name, optional global extras
 * and UIManager overrides, and badge/progress-bar/title-bar colors.
 */
public interface ThemeDefinition {

    String lafClassName();

    default Map<String, String> flatLafExtras() { return null; }

    default Map<String, Object> uiManagerOverrides() { return null; }

    default Color titleBarForeground() { return null; }

    default Color progressBarForeground() { return null; }

    Color badgeDefaultBg();
    Color badgeDefaultFg();
    Color badgeStaleBg();
    default Color badgeStaleFg() { return Color.WHITE; }

    default Color badgeBorderColor() { return null; }

    default boolean cycleBadgeColors() { return false; }

    default Color badgeEvenFg() { return badgeDefaultFg(); }

    default Color badgeOddFg() { return badgeDefaultFg(); }

    default boolean hasLinkedPalette() { return false; }
    default PaletteDefinition linkedPalette() { return null; }

    /**
     * Returns a FlatLaf {@code FlatLaf.style} string to apply to the
     * Start button, or {@code null} to use the application default
     * (GitHub green via {@link org.metricus.jdm.ui.ButtonStyles#DEFAULT_START}).
     * <p>
     * Themed Start-button colours should harmonise with the theme's accent.
     * Override this in a {@link ThemeDefinition} implementation to supply a
     * theme-specific style.
     */
    default String startButtonStyle() { return null; }

    /**
     * Returns a FlatLaf {@code FlatLaf.style} string to apply to the
     * button when it is in the Cancel/running state, or {@code null} to use
     * the application default (amber via {@link org.metricus.jdm.ui.ButtonStyles#CANCEL}).
     * <p>
     * Override in themes where amber clashes with the overall palette
     * (e.g. a theme whose accent is already orange, or one with a strong
     * thematic red that better signals "stop").
     */
    default String cancelButtonStyle() { return null; }
}
