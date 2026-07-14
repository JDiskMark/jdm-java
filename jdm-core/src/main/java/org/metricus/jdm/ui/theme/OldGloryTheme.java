package org.metricus.jdm.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.util.LinkedHashMap;
import java.util.Map;
import jdiskmark.App;
import org.metricus.jdm.ui.PaletteDefinition;
import org.metricus.jdm.ui.ThemeDefinition;

public final class OldGloryTheme implements ThemeDefinition {

    public static final Color BLUE         = new Color(0x3C3B6E);
    public static final Color RED          = new Color(0xB22234);
    public static final Color CRIMSON       = new Color(0xDC143C);
    public static final Color CRIMSON_FADE  = new Color(0xDC, 0x14, 0x3C, 170);
    public static final Color CRIMSON_LIGHT = new Color(0xFF6B6B);
    public static final Color CRIMSON_DARK  = new Color(0x8B0000);
    public static final Color BLUE_FADE     = new Color(0x3C, 0x3B, 0x6E, 170);
    public static final Color BLUE_LIGHT    = new Color(0x7878B4);
    public static final Color BLUE_DARK     = new Color(0x1A1940);
    public static final Color BADGE_BG      = new Color(0xEEF2FF);

    private static final Color BLUE_HOVER   = new Color(0x2B2A52);
    private static final Color BLUE_PRESS   = new Color(0x1A1A38);
    private static final Color TAB_HOVER    = new Color(0xECEDF8);

    private static final String HEX_BLUE = "#3C3B6E";
    private static final String HEX_RED  = "#B22234";

    private static javax.swing.plaf.ColorUIResource uiColor(Color c) {
        return new javax.swing.plaf.ColorUIResource(c);
    }

    @Override
    public String lafClassName() {
        return App.isMacOs()
                ? "com.formdev.flatlaf.themes.FlatMacLightLaf"
                : "com.formdev.flatlaf.FlatLightLaf";
    }

    @Override
    public Map<String, String> flatLafExtras() {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("@accentColor", HEX_RED);
        extras.put("@background",  "#FFFFFF");
        extras.put("@foreground",  HEX_BLUE);
        extras.put("TitlePane.foreground", HEX_BLUE);
        return extras;
    }

    @Override
    public Map<String, Object> uiManagerOverrides() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Table.selectionBackground",     uiColor(BLUE));
        m.put("Table.selectionForeground",     uiColor(Color.WHITE));
        m.put("List.selectionBackground",      uiColor(BLUE));
        m.put("List.selectionForeground",      uiColor(Color.WHITE));
        m.put("Tree.selectionBackground",      uiColor(BLUE));
        m.put("Tree.selectionForeground",      uiColor(Color.WHITE));
        // Selected tab: no fill — normal background + Blue foreground, red underline only.
        // Keeps tab text readable on both selected and hovered tabs.
        m.put("TabbedPane.underlineColor",          uiColor(RED));
        m.put("TabbedPane.inactiveUnderlineColor",  uiColor(RED));
        m.put("TabbedPane.focusColor",              uiColor(new Color(0xEEF0FF)));
        m.put("TabbedPane.hoverColor",              uiColor(TAB_HOVER));
        // Dark red text on hover for patriotic flair.
        m.put("TabbedPane.hoverForeground",         uiColor(CRIMSON_DARK));
        m.put("ScrollBar.thumb",               uiColor(BLUE));
        m.put("ScrollBar.thumbHover",          uiColor(BLUE_HOVER));
        m.put("ScrollBar.thumbPressed",        uiColor(BLUE_PRESS));
        // Default (accent-colored) buttons use a red background via @accentColor;
        // force white text so it is readable against that red fill.
        m.put("Button.default.foreground",     uiColor(Color.WHITE));
        return m;
    }

    @Override public Color titleBarForeground()    { return BLUE; }
    @Override public Color progressBarForeground() { return RED; }

    @Override public Color badgeDefaultBg()   { return BADGE_BG; }
    @Override public Color badgeDefaultFg()   { return BLUE; }
    @Override public Color badgeStaleBg()     { return RED; }
    @Override public Color badgeBorderColor() { return BLUE; }
    @Override public boolean cycleBadgeColors() { return true; }
    @Override public Color badgeEvenFg()       { return RED; }
    @Override public Color badgeOddFg()        { return BLUE; }

    @Override public boolean hasLinkedPalette() { return true; }
    @Override public PaletteDefinition linkedPalette() { return new OldGloryPalette(); }

    @Override
    public String startButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#3C3B6E",   // BLUE — normal background
            "#2B2A52",   // BLUE_HOVER — darker navy on hover
            "#1A1A38",   // BLUE_PRESS — pressed
            "#3C3B6E88", // navy focus ring
            "#ffffff"
        );
    }

    @Override
    public String cancelButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#B22234",   // RED — thematic crimson flag red
            "#8B0000",   // CRIMSON_DARK — darker on hover
            "#6B0000",   // deeper press state
            "#B2223488", // crimson focus ring
            "#ffffff"
        );
    }

    @Override public Color iconPrimaryTint()   { return CRIMSON; }
    @Override public Color iconSecondaryTint() { return Color.WHITE; }

    public static final class OldGloryPalette implements PaletteDefinition {

        private static final Stroke BOLD = new BasicStroke(1.5f);
        private static final Stroke DASH = new BasicStroke(
                1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{2.0f, 6.0f}, 0.0f);

        @Override public Color chartBackground()  { return Color.WHITE; }
        @Override public Color plotBackground()    { return Color.WHITE; }
        @Override public Color plotOutline()        { return new Color(0x999999); }
        @Override public Color gridColor()          { return new Color(0xE0E0E0); }
        @Override public Color legendBackground()  { return Color.WHITE; }
        @Override public Color legendBorderColor() { return new Color(0xCCCCCC); }

        @Override public Color bwWriteSample()  { return CRIMSON; }
        @Override public Color bwWriteTrend()   { return CRIMSON_FADE; }
        @Override public Color bwWriteMax()     { return CRIMSON_LIGHT; }
        @Override public Color bwWriteMin()     { return CRIMSON_DARK; }
        @Override public Color bwReadSample()   { return BLUE; }
        @Override public Color bwReadTrend()    { return BLUE_FADE; }
        @Override public Color bwReadMax()      { return BLUE_LIGHT; }
        @Override public Color bwReadMin()      { return BLUE_DARK; }
        @Override public Color msWriteLatency() { return CRIMSON; }
        @Override public Color msReadLatency()  { return BLUE; }

        @Override public Stroke bwWriteSampleStroke() { return BOLD; }
        @Override public Stroke bwWriteTrendStroke()  { return DASH; }
        @Override public Stroke bwReadSampleStroke()  { return BOLD; }
        @Override public Stroke bwReadTrendStroke()   { return DASH; }

        @Override public Color textPaint() { return BLUE; }
    }
}
