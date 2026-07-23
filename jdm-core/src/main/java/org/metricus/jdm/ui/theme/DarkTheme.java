package org.metricus.jdm.ui.theme;

import java.awt.Color;
import jdiskmark.App;
import org.metricus.jdm.ui.ThemeDefinition;

public final class DarkTheme implements ThemeDefinition {

    @Override
    public String lafClassName() {
        return App.isMacOs()
                ? "com.formdev.flatlaf.themes.FlatMacDarkLaf"
                : "com.formdev.flatlaf.FlatDarkLaf";
    }

    @Override public Color badgeDefaultBg() { return new Color(40, 40, 40, 180); }
    @Override public Color badgeDefaultFg() { return new Color(200, 200, 200); }
    @Override public Color badgeStaleBg()   { return new Color(0xC8, 0x78, 0x00); }
}
