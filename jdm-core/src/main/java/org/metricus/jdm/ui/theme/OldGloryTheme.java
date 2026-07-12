package org.metricus.jdm.ui.theme;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import jdiskmark.App;
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
        m.put("TabbedPane.selectedBackground",      uiColor(BLUE));
        m.put("TabbedPane.selectedForeground",      uiColor(Color.WHITE));
        m.put("TabbedPane.underlineColor",          uiColor(RED));
        m.put("TabbedPane.inactiveUnderlineColor",  uiColor(RED));
        m.put("TabbedPane.focusColor",              uiColor(BLUE));
        m.put("TabbedPane.hoverColor",              uiColor(TAB_HOVER));
        m.put("ScrollBar.thumb",               uiColor(BLUE));
        m.put("ScrollBar.thumbHover",          uiColor(BLUE_HOVER));
        m.put("ScrollBar.thumbPressed",        uiColor(BLUE_PRESS));
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
}
