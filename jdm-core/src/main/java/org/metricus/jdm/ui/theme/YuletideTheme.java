package org.metricus.jdm.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.util.LinkedHashMap;
import java.util.Map;
import jdiskmark.App;
import org.metricus.jdm.ui.PaletteDefinition;
import org.metricus.jdm.ui.ThemeDefinition;

public final class YuletideTheme implements ThemeDefinition {

    public static final Color PINE          = new Color(0x1D5A3A);
    public static final Color HOLLY         = new Color(0x9F1F2E);
    public static final Color NOEL_BG       = new Color(0xF8FCF8);
    public static final Color NOEL_TEXT     = new Color(0x1B2E22);
    public static final Color HOLLY_FADE    = new Color(0x9F, 0x1F, 0x2E, 170);
    public static final Color HOLLY_LIGHT   = new Color(0xD15263);
    public static final Color HOLLY_DARK    = new Color(0x6F121E);
    public static final Color PINE_FADE     = new Color(0x1D, 0x5A, 0x3A, 170);
    public static final Color PINE_LIGHT    = new Color(0x5C8F70);
    public static final Color PINE_DARK     = new Color(0x103825);
    public static final Color BADGE_BG      = new Color(0xEAF4EC);

    private static final Color PINE_HOVER   = new Color(0x184B31);
    private static final Color PINE_PRESS   = new Color(0x123723);
    private static final Color TAB_HOVER    = new Color(0xEEF6EF);

    private static final String HEX_HOLLY = "#9F1F2E";
    private static final String HEX_BG = "#F8FCF8";
    private static final String HEX_TEXT = "#1B2E22";

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
        extras.put("@accentColor", HEX_HOLLY);
        extras.put("@background", HEX_BG);
        extras.put("@foreground", HEX_TEXT);
        extras.put("TitlePane.foreground", HEX_TEXT);
        return extras;
    }

    @Override
    public Map<String, Object> uiManagerOverrides() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Table.selectionBackground", uiColor(PINE));
        m.put("Table.selectionForeground", uiColor(Color.WHITE));
        m.put("List.selectionBackground", uiColor(PINE));
        m.put("List.selectionForeground", uiColor(Color.WHITE));
        m.put("Tree.selectionBackground", uiColor(PINE));
        m.put("Tree.selectionForeground", uiColor(Color.WHITE));
        m.put("TabbedPane.underlineColor", uiColor(HOLLY));
        m.put("TabbedPane.inactiveUnderlineColor", uiColor(HOLLY));
        m.put("TabbedPane.focusColor", uiColor(new Color(0xDDECDF)));
        m.put("TabbedPane.hoverColor", uiColor(TAB_HOVER));
        m.put("TabbedPane.hoverForeground", uiColor(PINE));
        m.put("ScrollBar.thumb", uiColor(PINE));
        m.put("ScrollBar.thumbHover", uiColor(PINE_HOVER));
        m.put("ScrollBar.thumbPressed", uiColor(PINE_PRESS));
        return m;
    }

    @Override public Color titleBarForeground()    { return NOEL_TEXT; }
    @Override public Color progressBarForeground() { return HOLLY; }

    @Override public Color badgeDefaultBg()   { return BADGE_BG; }
    @Override public Color badgeDefaultFg()   { return PINE; }
    @Override public Color badgeStaleBg()     { return HOLLY; }
    @Override public Color badgeBorderColor() { return PINE; }
    @Override public boolean cycleBadgeColors() { return true; }
    @Override public Color badgeEvenFg()      { return PINE; }
    @Override public Color badgeOddFg()       { return HOLLY; }

    @Override public boolean hasLinkedPalette() { return true; }
    @Override public PaletteDefinition linkedPalette() { return new YuletidePalette(); }

    @Override
    public String startButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#1D5A3A",
            "#184B31",
            "#123723",
            "#1D5A3A88",
            "#ffffff"
        );
    }

    @Override
    public String cancelButtonStyle() {
        return org.metricus.jdm.ui.ButtonStyles.buildStyle(
            "#9F1F2E",
            "#7F1824",
            "#62121B",
            "#9F1F2E88",
            "#ffffff"
        );
    }

    @Override public Color iconPrimaryTint()   { return HOLLY; }
    @Override public Color iconSecondaryTint() { return PINE_LIGHT; }

    public static final class YuletidePalette implements PaletteDefinition {

        private static final Stroke BOLD = new BasicStroke(1.5f);
        private static final Stroke DASH = new BasicStroke(
                1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10.0f, new float[]{2.0f, 6.0f}, 0.0f);

        @Override public Color chartBackground()  { return NOEL_BG; }
        @Override public Color plotBackground()   { return Color.WHITE; }
        @Override public Color plotOutline()      { return new Color(0xB8CCBB); }
        @Override public Color gridColor()        { return new Color(0xE4EEE6); }
        @Override public Color legendBackground() { return NOEL_BG; }
        @Override public Color legendBorderColor(){ return new Color(0xC9D8CC); }

        @Override public Color bwWriteSample()  { return HOLLY; }
        @Override public Color bwWriteTrend()   { return HOLLY_FADE; }
        @Override public Color bwWriteMax()     { return HOLLY_LIGHT; }
        @Override public Color bwWriteMin()     { return HOLLY_DARK; }
        @Override public Color bwReadSample()   { return PINE; }
        @Override public Color bwReadTrend()    { return PINE_FADE; }
        @Override public Color bwReadMax()      { return PINE_LIGHT; }
        @Override public Color bwReadMin()      { return PINE_DARK; }
        @Override public Color msWriteLatency() { return HOLLY; }
        @Override public Color msReadLatency()  { return PINE; }

        @Override public Stroke bwWriteSampleStroke() { return BOLD; }
        @Override public Stroke bwWriteTrendStroke()  { return DASH; }
        @Override public Stroke bwReadSampleStroke()  { return BOLD; }
        @Override public Stroke bwReadTrendStroke()   { return DASH; }

        @Override public Color textPaint() { return NOEL_TEXT; }
    }
}
