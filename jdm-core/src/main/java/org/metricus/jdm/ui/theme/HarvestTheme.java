package org.metricus.jdm.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.util.LinkedHashMap;
import java.util.Map;
import jdiskmark.App;
import org.metricus.jdm.ui.PaletteDefinition;
import org.metricus.jdm.ui.ThemeDefinition;

public final class HarvestTheme implements ThemeDefinition {

    public static final Color ORANGE        = new Color(0xC45C11);
    public static final Color AMBER         = new Color(0xD4920A);
    public static final Color UMBER         = new Color(0x3B1F0A);
    public static final Color PUMPKIN       = new Color(0xE07020);
    public static final Color PUMPKIN_FADE  = new Color(0xE0, 0x70, 0x20, 170);
    public static final Color PUMPKIN_LIGHT = new Color(0xF0A050);
    public static final Color PUMPKIN_DARK  = new Color(0x8B3A0A);
    public static final Color FOREST        = new Color(0x5A7A40);
    public static final Color FOREST_FADE   = new Color(0x5A, 0x7A, 0x40, 170);
    public static final Color FOREST_LIGHT  = new Color(0x8AAA68);
    public static final Color FOREST_DARK   = new Color(0x354D22);
    public static final Color BADGE_BG      = new Color(0xFFF0D0);

    private static final Color ORANGE_HOVER  = new Color(0xA34A0C);
    private static final Color ORANGE_PRESS  = new Color(0x7A3608);
    private static final Color TAB_HOVER     = new Color(0xFFF4E4);

    private static final String HEX_ORANGE = "#C45C11";
    private static final String HEX_AMBER  = "#D4920A";
    private static final String HEX_UMBER  = "#3B1F0A";

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
        extras.put("@accentColor", HEX_AMBER);
        extras.put("@background",  "#FFF8EE");
        extras.put("@foreground",  HEX_UMBER);
        extras.put("TitlePane.foreground", HEX_UMBER);
        return extras;
    }

    @Override
    public Map<String, Object> uiManagerOverrides() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Table.selectionBackground",     uiColor(ORANGE));
        m.put("Table.selectionForeground",     uiColor(Color.WHITE));
        m.put("List.selectionBackground",      uiColor(ORANGE));
        m.put("List.selectionForeground",      uiColor(Color.WHITE));
        m.put("Tree.selectionBackground",      uiColor(ORANGE));
        m.put("Tree.selectionForeground",      uiColor(Color.WHITE));
        // Selected tab: no fill — normal background + Umber text, amber underline only.
        // This keeps the text readable on both selected and hovered tabs,
        // matching the Sakura/Light tab style the user prefers.
        m.put("TabbedPane.underlineColor",          uiColor(AMBER));
        m.put("TabbedPane.inactiveUnderlineColor",  uiColor(AMBER));
        m.put("TabbedPane.focusColor",              uiColor(new Color(0xFFF0D8)));
        m.put("TabbedPane.hoverColor",              uiColor(TAB_HOVER));
        m.put("ScrollBar.thumb",               uiColor(ORANGE));
        m.put("ScrollBar.thumbHover",          uiColor(ORANGE_HOVER));
        m.put("ScrollBar.thumbPressed",        uiColor(ORANGE_PRESS));
        return m;
    }

    @Override public Color titleBarForeground()    { return UMBER; }
    @Override public Color progressBarForeground() { return ORANGE; }

    @Override public Color badgeDefaultBg()   { return BADGE_BG; }
    @Override public Color badgeDefaultFg()   { return ORANGE; }
    @Override public Color badgeStaleBg()     { return PUMPKIN_DARK; }
    @Override public Color badgeBorderColor() { return ORANGE; }
    @Override public boolean cycleBadgeColors() { return true; }
    @Override public Color badgeEvenFg()       { return ORANGE; }
    @Override public Color badgeOddFg()        { return AMBER; }

    @Override public boolean hasLinkedPalette() { return true; }
    @Override public PaletteDefinition linkedPalette() { return new HarvestPalette(); }

    @Override
    public String startButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#C45C11",   // ORANGE — normal background
            "#A34A0C",   // ORANGE_HOVER — darker on hover
            "#7A3608",   // ORANGE_PRESS — pressed
            "#C45C1188", // orange focus ring
            "#ffffff"
        );
    }

    @Override
    public String cancelButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#8B3A0A",   // PUMPKIN_DARK — deep rust for stop
            "#6B2C08",   // darker on hover
            "#4E2006",   // pressed
            "#8B3A0A88", // focus ring
            "#ffffff"
        );
    }

    @Override public Color iconPrimaryTint()   { return PUMPKIN; }
    @Override public Color iconSecondaryTint() { return AMBER; }

    public static final class HarvestPalette implements PaletteDefinition {

        private static final Stroke BOLD = new BasicStroke(1.5f);
        private static final Stroke DASH = new BasicStroke(
                1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{2.0f, 6.0f}, 0.0f);

        @Override public Color chartBackground()  { return new Color(0xFFF8EE); }
        @Override public Color plotBackground()    { return new Color(0xFFF8EE); }
        @Override public Color plotOutline()        { return new Color(0xBBBBBB); }
        @Override public Color gridColor()          { return new Color(0xE8D8C0); }
        @Override public Color legendBackground()  { return new Color(0xFFF8EE); }
        @Override public Color legendBorderColor() { return new Color(0xCCBBA0); }

        @Override public Color bwWriteSample()  { return PUMPKIN; }
        @Override public Color bwWriteTrend()   { return PUMPKIN_FADE; }
        @Override public Color bwWriteMax()     { return PUMPKIN_LIGHT; }
        @Override public Color bwWriteMin()     { return PUMPKIN_DARK; }
        @Override public Color bwReadSample()   { return FOREST; }
        @Override public Color bwReadTrend()    { return FOREST_FADE; }
        @Override public Color bwReadMax()      { return FOREST_LIGHT; }
        @Override public Color bwReadMin()      { return FOREST_DARK; }
        @Override public Color msWriteLatency() { return PUMPKIN; }
        @Override public Color msReadLatency()  { return FOREST; }

        @Override public Stroke bwWriteSampleStroke() { return BOLD; }
        @Override public Stroke bwWriteTrendStroke()  { return DASH; }
        @Override public Stroke bwReadSampleStroke()  { return BOLD; }
        @Override public Stroke bwReadTrendStroke()   { return DASH; }

        @Override public Color textPaint() { return UMBER; }
    }
}
