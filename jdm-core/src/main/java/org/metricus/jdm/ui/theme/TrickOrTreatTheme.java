package org.metricus.jdm.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.util.LinkedHashMap;
import java.util.Map;
import jdiskmark.App;
import org.metricus.jdm.ui.PaletteDefinition;
import org.metricus.jdm.ui.ThemeDefinition;

public final class TrickOrTreatTheme implements ThemeDefinition {

    public static final Color NIGHT         = new Color(0x12091F);
    public static final Color VIOLET        = new Color(0x3A1C57);
    public static final Color ORANGE        = new Color(0xF47B20);
    public static final Color ORANGE_FADE   = new Color(0xF4, 0x7B, 0x20, 170);
    public static final Color ORANGE_LIGHT  = new Color(0xFFB15E);
    public static final Color ORANGE_DARK   = new Color(0xB84E06);
    public static final Color PURPLE        = new Color(0xB28BFF);
    public static final Color PURPLE_FADE   = new Color(0xB2, 0x8B, 0xFF, 170);
    public static final Color PURPLE_LIGHT  = new Color(0xD5C0FF);
    public static final Color PURPLE_DARK   = new Color(0x7A55C8);
    public static final Color BADGE_BG      = new Color(0x261139);

    private static final Color ORANGE_HOVER = new Color(0xD86413);
    private static final Color ORANGE_PRESS = new Color(0xA44606);
    private static final Color TAB_HOVER    = new Color(0x2B153F);

    private static final String HEX_ORANGE = "#F47B20";
    private static final String HEX_BG     = "#12091F";
    private static final String HEX_FG     = "#EBDCFD";

    private static javax.swing.plaf.ColorUIResource uiColor(Color c) {
        return new javax.swing.plaf.ColorUIResource(c);
    }

    @Override
    public String lafClassName() {
        return App.isMacOs()
                ? "com.formdev.flatlaf.themes.FlatMacDarkLaf"
                : "com.formdev.flatlaf.FlatDarkLaf";
    }

    @Override
    public Map<String, String> flatLafExtras() {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("@accentColor", HEX_ORANGE);
        extras.put("@background", HEX_BG);
        extras.put("@foreground", HEX_FG);
        extras.put("TitlePane.foreground", "#EBDCFD");
        return extras;
    }

    @Override
    public Map<String, Object> uiManagerOverrides() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Table.selectionBackground", uiColor(ORANGE));
        m.put("Table.selectionForeground", uiColor(Color.BLACK));
        m.put("List.selectionBackground", uiColor(ORANGE));
        m.put("List.selectionForeground", uiColor(Color.BLACK));
        m.put("Tree.selectionBackground", uiColor(ORANGE));
        m.put("Tree.selectionForeground", uiColor(Color.BLACK));
        m.put("TabbedPane.underlineColor", uiColor(ORANGE));
        m.put("TabbedPane.inactiveUnderlineColor", uiColor(ORANGE));
        m.put("TabbedPane.focusColor", uiColor(VIOLET));
        m.put("TabbedPane.hoverColor", uiColor(TAB_HOVER));
        m.put("TabbedPane.hoverForeground", uiColor(PURPLE));
        m.put("ScrollBar.thumb", uiColor(VIOLET));
        m.put("ScrollBar.thumbHover", uiColor(new Color(0x542D77)));
        m.put("ScrollBar.thumbPressed", uiColor(new Color(0x3A1C57)));
        return m;
    }

    @Override public Color titleBarForeground()    { return new Color(0xEBDCFD); }
    @Override public Color progressBarForeground() { return ORANGE; }

    @Override public Color badgeDefaultBg()   { return BADGE_BG; }
    @Override public Color badgeDefaultFg()   { return PURPLE; }
    @Override public Color badgeStaleBg()     { return ORANGE_DARK; }
    @Override public Color badgeBorderColor() { return ORANGE; }
    @Override public boolean cycleBadgeColors() { return true; }
    @Override public Color badgeEvenFg()      { return ORANGE; }
    @Override public Color badgeOddFg()       { return PURPLE; }

    @Override public boolean hasLinkedPalette() { return true; }
    @Override public PaletteDefinition linkedPalette() { return new TrickOrTreatPalette(); }

    @Override
    public String startButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#F47B20",
            "#D86413",
            "#A44606",
            "#F47B2088",
            "#ffffff"
        );
    }

    @Override
    public String cancelButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#7A55C8",
            "#6242A8",
            "#4D3386",
            "#7A55C888",
            "#ffffff"
        );
    }

    @Override public Color iconPrimaryTint()   { return ORANGE; }
    @Override public Color iconSecondaryTint() { return PURPLE_LIGHT; }

    public static final class TrickOrTreatPalette implements PaletteDefinition {

        private static final Stroke BOLD = new BasicStroke(1.5f);
        private static final Stroke DASH = new BasicStroke(
                1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{2.0f, 6.0f}, 0.0f);

        @Override public Color chartBackground()  { return NIGHT; }
        @Override public Color plotBackground()   { return new Color(0x1B0E2B); }
        @Override public Color plotOutline()      { return new Color(0x5A3B7E); }
        @Override public Color gridColor()        { return new Color(0x3C2757); }
        @Override public Color legendBackground() { return NIGHT; }
        @Override public Color legendBorderColor(){ return new Color(0x5A3B7E); }

        @Override public Color bwWriteSample()  { return ORANGE; }
        @Override public Color bwWriteTrend()   { return ORANGE_FADE; }
        @Override public Color bwWriteMax()     { return ORANGE_LIGHT; }
        @Override public Color bwWriteMin()     { return ORANGE_DARK; }
        @Override public Color bwReadSample()   { return PURPLE; }
        @Override public Color bwReadTrend()    { return PURPLE_FADE; }
        @Override public Color bwReadMax()      { return PURPLE_LIGHT; }
        @Override public Color bwReadMin()      { return PURPLE_DARK; }
        @Override public Color msWriteLatency() { return ORANGE; }
        @Override public Color msReadLatency()  { return PURPLE; }

        @Override public Stroke bwWriteSampleStroke() { return BOLD; }
        @Override public Stroke bwWriteTrendStroke()  { return DASH; }
        @Override public Stroke bwReadSampleStroke()  { return BOLD; }
        @Override public Stroke bwReadTrendStroke()   { return DASH; }

        @Override public Color textPaint() { return new Color(0xEBDCFD); }
    }
}
