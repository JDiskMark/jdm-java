package org.metricus.jdm.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.util.LinkedHashMap;
import java.util.Map;
import jdiskmark.App;
import org.metricus.jdm.ui.PaletteDefinition;
import org.metricus.jdm.ui.ThemeDefinition;

public final class SakuraTheme implements ThemeDefinition {

    public static final Color ROSE        = new Color(0xD4607C);
    public static final Color PINK        = new Color(0xE8849A);
    public static final Color BARK        = new Color(0x2D1B22);
    public static final Color FADE        = new Color(0xE8, 0x84, 0x9A, 170);
    public static final Color LIGHT       = new Color(0xF5C2CE);
    public static final Color DARK        = new Color(0xA83060);
    public static final Color SAGE        = new Color(0x7A9E7E);
    public static final Color SAGE_FADE   = new Color(0x7A, 0x9E, 0x7E, 170);
    public static final Color SAGE_LIGHT  = new Color(0xB0CCAA);
    public static final Color SAGE_DARK   = new Color(0x4A6B4D);
    public static final Color BADGE_BG    = new Color(0xFCEEF2);

    private static final Color SCROLL_HOVER  = new Color(0xC55878);
    private static final Color SCROLL_PRESS  = new Color(0xA84062);
    private static final Color TAB_HOVER     = new Color(0xFDF0F4);

    private static final String HEX_ROSE = "#D4607C";
    private static final String HEX_BARK = "#2D1B22";

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
        extras.put("@accentColor", HEX_ROSE);
        extras.put("@background",  "#FFFBFC");
        extras.put("@foreground",  HEX_BARK);
        extras.put("TitlePane.foreground", HEX_BARK);
        return extras;
    }

    @Override
    public Map<String, Object> uiManagerOverrides() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Table.selectionBackground",     uiColor(ROSE));
        m.put("Table.selectionForeground",     uiColor(Color.WHITE));
        m.put("List.selectionBackground",      uiColor(ROSE));
        m.put("List.selectionForeground",      uiColor(Color.WHITE));
        m.put("Tree.selectionBackground",      uiColor(ROSE));
        m.put("Tree.selectionForeground",      uiColor(Color.WHITE));
        m.put("TabbedPane.underlineColor",         uiColor(ROSE));
        m.put("TabbedPane.inactiveUnderlineColor", uiColor(ROSE));
        m.put("TabbedPane.hoverColor",             uiColor(TAB_HOVER));
        m.put("ScrollBar.thumb",               uiColor(PINK));
        m.put("ScrollBar.thumbHover",          uiColor(SCROLL_HOVER));
        m.put("ScrollBar.thumbPressed",        uiColor(SCROLL_PRESS));
        return m;
    }

    @Override public Color titleBarForeground()    { return BARK; }
    @Override public Color progressBarForeground() { return ROSE; }

    @Override public Color badgeDefaultBg()   { return BADGE_BG; }
    @Override public Color badgeDefaultFg()   { return ROSE; }
    @Override public Color badgeStaleBg()     { return DARK; }
    @Override public Color badgeBorderColor() { return ROSE; }
    @Override public boolean cycleBadgeColors() { return true; }
    @Override public Color badgeEvenFg()       { return ROSE; }
    @Override public Color badgeOddFg()        { return DARK; }

    @Override public boolean hasLinkedPalette() { return true; }
    @Override public PaletteDefinition linkedPalette() { return new SakuraPalette(); }

    @Override
    public String startButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#D4607C",   // ROSE — normal background
            "#C55878",   // darker rose on hover
            "#A84062",   // pressed
            "#D4607C88", // rose focus ring
            "#ffffff"
        );
    }

    @Override
    public String cancelButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#A83060",   // DARK — Sakura's stale/warning rose-purple
            "#8C2450",   // darker on hover
            "#70183E",   // pressed
            "#A8306088", // focus ring
            "#ffffff"
        );
    }

    public static final class SakuraPalette implements PaletteDefinition {

        private static final Stroke BOLD = new BasicStroke(1.5f);
        private static final Stroke DASH = new BasicStroke(
                1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{2.0f, 6.0f}, 0.0f);

        @Override public Color chartBackground()  { return Color.WHITE; }
        @Override public Color plotBackground()    { return Color.WHITE; }
        @Override public Color plotOutline()        { return new Color(0xCCCCCC); }
        @Override public Color gridColor()          { return new Color(0xEEEEEE); }
        @Override public Color legendBackground()  { return Color.WHITE; }
        @Override public Color legendBorderColor() { return new Color(0xDDDDDD); }

        @Override public Color bwWriteSample()  { return PINK; }
        @Override public Color bwWriteTrend()   { return FADE; }
        @Override public Color bwWriteMax()     { return LIGHT; }
        @Override public Color bwWriteMin()     { return DARK; }
        @Override public Color bwReadSample()   { return SAGE; }
        @Override public Color bwReadTrend()    { return SAGE_FADE; }
        @Override public Color bwReadMax()      { return SAGE_LIGHT; }
        @Override public Color bwReadMin()      { return SAGE_DARK; }
        @Override public Color msWriteLatency() { return PINK; }
        @Override public Color msReadLatency()  { return SAGE; }

        @Override public Stroke bwWriteSampleStroke() { return BOLD; }
        @Override public Stroke bwWriteTrendStroke()  { return DASH; }
        @Override public Stroke bwReadSampleStroke()  { return BOLD; }
        @Override public Stroke bwReadTrendStroke()   { return DASH; }

        @Override public Color textPaint() { return BARK; }
    }
}
