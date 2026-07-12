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
}
