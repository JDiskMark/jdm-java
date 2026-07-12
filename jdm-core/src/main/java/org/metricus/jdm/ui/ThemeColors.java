package org.metricus.jdm.ui;

import java.awt.Color;

/**
 * Named color constants shared across application themes and chart palettes.
 * <p>
 * Using named constants prevents color drift when the same logical color is
 * referenced in multiple methods or files, and makes the intent of each color
 * self-documenting without needing inline comments everywhere.
 * </p>
 * <p>
 * Hex-string variants (prefixed with {@code HEX_}) are provided for FlatLaf's
 * {@code setGlobalExtraDefaults()} which requires strings, not Color objects.
 * </p>
 */
public final class ThemeColors {

    // -----------------------------------------------------------------------
    // Old Glory (US Flag) palette
    // -----------------------------------------------------------------------

    /** Old Glory Blue (Pantone 282) - UI text, read series, selections, scrollbar. */
    public static final Color OLD_GLORY_BLUE       = new Color(0x3C3B6E);

    /** Old Glory Red (Pantone 193) - progress bar, tab underline, accent (checkboxes, focus). */
    public static final Color OLD_GLORY_RED        = new Color(0xB22234);

    /** Crimson - vivid red for write-sample and write-latency series. */
    public static final Color CRIMSON              = new Color(0xDC143C);

    /** Crimson at 170/255 alpha - write-trend dashed line. */
    public static final Color CRIMSON_FADE         = new Color(0xDC, 0x14, 0x3C, 170);

    /** Old Glory Blue at 170/255 alpha - read-trend dashed line. */
    public static final Color OLD_GLORY_BLUE_FADE  = new Color(0x3C, 0x3B, 0x6E, 170);

    /** Light crimson (#FF6B6B) - write-max series. */
    public static final Color CRIMSON_LIGHT        = new Color(0xFF6B6B);

    /** Dark red (#8B0000) - write-min series. */
    public static final Color CRIMSON_DARK         = new Color(0x8B0000);

    /** Periwinkle blue (#7878B4) - read-max series. */
    public static final Color OLD_GLORY_BLUE_LIGHT = new Color(0x7878B4);

    /** Deep navy (#1A1940) - read-min series. */
    public static final Color OLD_GLORY_BLUE_DARK  = new Color(0x1A1940);

    /** Badge background - pale lavender-blue (#EEF2FF). */
    public static final Color OLD_GLORY_BADGE_BG   = new Color(0xEEF2FF);

    /** Scrollbar thumb hover - slightly darker navy (#2B2A52). */
    public static final Color OLD_GLORY_BLUE_HOVER = new Color(0x2B2A52);

    /** Scrollbar thumb pressed - darkest navy (#1A1A38). */
    public static final Color OLD_GLORY_BLUE_PRESS = new Color(0x1A1A38);

    /** Tab hover highlight - very light blue (#ECEDF8); subtle tint over white background. */
    public static final Color OLD_GLORY_TAB_HOVER  = new Color(0xECEDF8);

    // ---- Hex-string variants for FlatLaf setGlobalExtraDefaults() ----------

    /** Hex string form of {@link #OLD_GLORY_BLUE} for FlatLaf key values. */
    public static final String HEX_OLD_GLORY_BLUE  = "#3C3B6E";

    /** Hex string form of {@link #OLD_GLORY_RED} for FlatLaf key values. */
    public static final String HEX_OLD_GLORY_RED   = "#B22234";

    // -----------------------------------------------------------------------
    // Sakura (Cherry Blossom) palette
    // -----------------------------------------------------------------------

    /** Sakura rose (#D4607C) - selection bg, tab selected, accent, progress bar. */
    public static final Color SAKURA_ROSE        = new Color(0xD4607C);

    /** Medium petal pink (#E8849A) - chart write sample, scrollbar thumb. */
    public static final Color SAKURA_PINK        = new Color(0xE8849A);

    /** Petal pink at 170/255 alpha - chart write trend dashed line. */
    public static final Color SAKURA_FADE        = new Color(0xE8, 0x84, 0x9A, 170);

    /** Pale petal (#F5C2CE) - chart write max. */
    public static final Color SAKURA_LIGHT       = new Color(0xF5C2CE);

    /** Deep rose (#A83060) - chart write min. */
    public static final Color SAKURA_DARK        = new Color(0xA83060);

    /** Cherry bark (#2D1B22) - primary text and odd-index badge foreground. */
    public static final Color SAKURA_BARK        = new Color(0x2D1B22);

    /** Spring sage green (#7A9E7E) - chart read sample. */
    public static final Color SAKURA_SAGE        = new Color(0x7A9E7E);

    /** Spring sage at 170/255 alpha - chart read trend dashed line. */
    public static final Color SAKURA_SAGE_FADE   = new Color(0x7A, 0x9E, 0x7E, 170);

    /** Light sage (#B0CCAA) - chart read max. */
    public static final Color SAKURA_SAGE_LIGHT  = new Color(0xB0CCAA);

    /** Forest green (#4A6B4D) - chart read min. */
    public static final Color SAKURA_SAGE_DARK   = new Color(0x4A6B4D);

    /** Badge background - pale pink blush (#FCEEF2). */
    public static final Color SAKURA_BADGE_BG    = new Color(0xFCEEF2);

    /** Scrollbar thumb hover - deeper rose (#C55878). */
    public static final Color SAKURA_SCROLL_HOVER = new Color(0xC55878);

    /** Scrollbar thumb pressed - darkest rose (#A84062). */
    public static final Color SAKURA_SCROLL_PRESS = new Color(0xA84062);

    /** Tab hover tint - very light pink (#FDF0F4) over white background. */
    public static final Color SAKURA_TAB_HOVER   = new Color(0xFDF0F4);

    // ---- Hex-string variants for Sakura setGlobalExtraDefaults() -----------

    /** Hex string form of {@link #SAKURA_ROSE} for FlatLaf key values. */
    public static final String HEX_SAKURA_ROSE   = "#D4607C";

    /** Hex string form of {@link #SAKURA_BARK} for FlatLaf key values. */
    public static final String HEX_SAKURA_BARK   = "#2D1B22";

    // -----------------------------------------------------------------------

    private ThemeColors() { /* static constants only */ }
}
