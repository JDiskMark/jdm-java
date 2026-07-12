# JDiskMark Theme Reference

Captures the full color systems, LAF configuration, and chart palette details for
each implemented theme. Use as a handoff document when starting a new thread.

---

## Architecture

| File | Role |
|---|---|
| `jdm-core/.../ThemeColors.java` | Single source of all named `Color` constants and hex-string variants |
| `jdm-core/.../ChartPalette.java` | Static factory — one `setXxxColorScheme()` per chart palette |
| `jdm-core/.../Gui.java` | Hosts `Palette` + `Theme` enums, `configureXxxLaf()`, `goXxxTheme()`, and badge helpers |

### Enums (auto-populate menus)

**`Gui.Palette`** drives **Graph > Color Palette** menu (via `GraphPaletteMenu` iterating `values()`):
```
CLASSIC, BLUE_GREEN, BARD_COOL, BARD_WARM, BETA, OLD_GLORY, SAKURA
```

**`Gui.Theme`** drives **Graph > Theme** menu (via `GraphThemeMenu` iterating `values()`):
```
DARK, LIGHT, DARCULA, OLD_GLORY, SAKURA
```

### Adding a new theme — checklist

1. Add constant to `Gui.Palette` + `case` in `Palette.apply()` -> `ChartPalette.setXxxColorScheme()`
2. Add constant to `Gui.Theme` + `case` in `Theme.apply()` -> `goXxxTheme()` + `getLafClassName()`
3. Add color constants to `ThemeColors.java`
4. Implement `configureXxxLaf()` in `Gui.java` (FlatLaf global extras + UIManager overrides)
5. Implement `goXxxTheme()` in `Gui.java` (calls configure, `FlatLaf.updateUI()`, auto-applies palette)
6. Implement `ChartPalette.setXxxColorScheme()` (renderers, axes, background)
7. Add branches to: `clearBadgeHighlights()`, `updateBadgeThemeColors()`, `setBadgeStaleReturn()`

> **Timing note:** `chartBadgeList` is assigned after `refreshChartBadges()` in
> `createChartPanel()`. The theme-specific alternation in `setBadgeStaleReturn()` guards
> on `chartBadgeList != null`; the list is populated before any user-triggered refresh.

---

## Old Glory Theme

**Identity:** US flag patriotic — navy + crimson on pure white.

### ThemeColors constants

| Constant | Hex | Role |
|---|---|---|
| `OLD_GLORY_BLUE` | `#3C3B6E` | Primary accent: UI text, selections, scrollbar, tab selected bg |
| `OLD_GLORY_RED` | `#B22234` | Secondary accent: `@accentColor` (checkboxes/focus), tab underline, progress bar |
| `CRIMSON` | `#DC143C` | Chart write-sample, write-latency |
| `CRIMSON_FADE` | `#DC143C` a=170 | Chart write-trend dashed line |
| `CRIMSON_LIGHT` | `#FF6B6B` | Chart write-max |
| `CRIMSON_DARK` | `#8B0000` | Chart write-min |
| `OLD_GLORY_BLUE_FADE` | `#3C3B6E` a=170 | Chart read-trend dashed line |
| `OLD_GLORY_BLUE_LIGHT` | `#7878B4` | Chart read-max (periwinkle) |
| `OLD_GLORY_BLUE_DARK` | `#1A1940` | Chart read-min (deep navy) |
| `OLD_GLORY_BADGE_BG` | `#EEF2FF` | Badge background (pale lavender-blue) |
| `OLD_GLORY_BLUE_HOVER` | `#2B2A52` | Scrollbar thumb hover |
| `OLD_GLORY_BLUE_PRESS` | `#1A1A38` | Scrollbar thumb pressed |
| `OLD_GLORY_TAB_HOVER` | `#ECEDF8` | Tab hover tint (very light blue) |
| `HEX_OLD_GLORY_BLUE` | `"#3C3B6E"` | String form for `setGlobalExtraDefaults()` |
| `HEX_OLD_GLORY_RED` | `"#B22234"` | String form for `setGlobalExtraDefaults()` |

### LAF Configuration (configureOldGloryLaf)

**Base LAF:** `FlatLightLaf` / `FlatMacLightLaf`

**Global extra defaults (before setLookAndFeel):**
```
@accentColor  = #B22234   (OLD_GLORY_RED)
@background   = #FFFFFF
@foreground   = #3C3B6E   (OLD_GLORY_BLUE)
TitlePane.foreground = #3C3B6E
```

**UIManager overrides (after setLookAndFeel):**
```
Table/List/Tree.selectionBackground = OLD_GLORY_BLUE  + WHITE fg
TabbedPane.selectedBackground       = OLD_GLORY_BLUE
TabbedPane.selectedForeground       = Color.WHITE
TabbedPane.underlineColor           = OLD_GLORY_RED
TabbedPane.inactiveUnderlineColor   = OLD_GLORY_RED
TabbedPane.focusColor               = OLD_GLORY_BLUE
TabbedPane.hoverColor               = OLD_GLORY_TAB_HOVER  (#ECEDF8, very light blue)
ScrollBar.thumb                     = OLD_GLORY_BLUE
ScrollBar.thumbHover                = OLD_GLORY_BLUE_HOVER  (#2B2A52)
ScrollBar.thumbPressed              = OLD_GLORY_BLUE_PRESS  (#1A1A38)
TitlePane.foreground                = OLD_GLORY_BLUE
```

**goOldGloryTheme() extras:**
- `JRootPane.titleBarForeground` client property -> `OLD_GLORY_BLUE`
- `progressBar.setForeground(OLD_GLORY_RED)`
- Auto-applies `Palette.OLD_GLORY` chart palette

### Badge Colors (Old Glory)

```
BADGE_AMBER_BG   = OLD_GLORY_RED          // stale background
BADGE_STALE_FG   = Color.WHITE
BADGE_DEFAULT_BG = OLD_GLORY_BADGE_BG     // #EEF2FF pale lavender-blue
BADGE_DEFAULT_FG = OLD_GLORY_BLUE         // fallback

Badge border: 1px OLD_GLORY_BLUE outline + 2/5/2/5 padding
Alternating fg: even index -> OLD_GLORY_RED, odd index -> OLD_GLORY_BLUE
```

### Chart Palette (Old Glory)

**Canvas:** pure white — outer chart + plot background
**Grid:** subtle light gray #E0E0E0
**Legend:** white bg, #CCCCCC border
**Strokes:** bold 1.5f for sample series; short-dash 2.0/6.0 1.2f for trend lines

| Series | Role | Color | Hex |
|---|---|---|---|
| bw[0] | Write sample | `CRIMSON` + bold | `#DC143C` |
| bw[1] | Write trend | `CRIMSON_FADE` + dash | `#DC143C` a170 |
| bw[2] | Write max | `CRIMSON_LIGHT` | `#FF6B6B` |
| bw[3] | Write min | `CRIMSON_DARK` | `#8B0000` |
| bw[4] | Read sample | `OLD_GLORY_BLUE` + bold | `#3C3B6E` |
| bw[5] | Read trend | `OLD_GLORY_BLUE_FADE` + dash | `#3C3B6E` a170 |
| bw[6] | Read max | `OLD_GLORY_BLUE_LIGHT` | `#7878B4` |
| bw[7] | Read min | `OLD_GLORY_BLUE_DARK` | `#1A1940` |
| ms[0] | Write latency | `CRIMSON` | `#DC143C` |
| ms[1] | Read latency | `OLD_GLORY_BLUE` | `#3C3B6E` |

**Axes + title + legend text:** `OLD_GLORY_BLUE` (#3C3B6E)

---

## Sakura Theme

**Identity:** Cherry blossom spring — rose pink + sage green on near-white.

### ThemeColors constants

| Constant | Hex | Role |
|---|---|---|
| `SAKURA_ROSE` | `#D4607C` | Primary accent: `@accentColor`, selections, tab underline, progress bar |
| `SAKURA_PINK` | `#E8849A` | Chart write-sample, write-latency, scrollbar thumb |
| `SAKURA_FADE` | `#E8849A` a=170 | Chart write-trend dashed line |
| `SAKURA_LIGHT` | `#F5C2CE` | Chart write-max; **tab selected background** |
| `SAKURA_DARK` | `#A83060` | Chart write-min |
| `SAKURA_BARK` | `#2D1B22` | Cherry bark dark: chart axis/title text, title bar |
| `SAKURA_SAGE` | `#7A9E7E` | Chart read-sample, read-latency |
| `SAKURA_SAGE_FADE` | `#7A9E7E` a=170 | Chart read-trend dashed line |
| `SAKURA_SAGE_LIGHT` | `#B0CCAA` | Chart read-max |
| `SAKURA_SAGE_DARK` | `#4A6B4D` | Chart read-min |
| `SAKURA_BADGE_BG` | `#FCEEF2` | Badge background (pale pink blush) |
| `SAKURA_SCROLL_HOVER` | `#C55878` | Scrollbar thumb hover |
| `SAKURA_SCROLL_PRESS` | `#A84062` | Scrollbar thumb pressed |
| `SAKURA_TAB_HOVER` | `#FDF0F4` | Reserved (very light pink, not currently used for tabs) |
| `HEX_SAKURA_ROSE` | `"#D4607C"` | String form for `setGlobalExtraDefaults()` |
| `HEX_SAKURA_BARK` | `"#2D1B22"` | String form for `setGlobalExtraDefaults()` |

### LAF Configuration (configureSakuraLaf)

**Base LAF:** `FlatLightLaf` / `FlatMacLightLaf`

**Global extra defaults (before setLookAndFeel):**
```
@accentColor  = #D4607C   (SAKURA_ROSE)
@background   = #FFFBFC   (near-white with faint pink tint)
@foreground   = #2D1B22   (SAKURA_BARK)
TitlePane.foreground = #2D1B22
```

**UIManager overrides (after setLookAndFeel):**
```
Table/List/Tree.selectionBackground = SAKURA_ROSE       + WHITE fg
TabbedPane.selectedBackground       = SAKURA_LIGHT      (#F5C2CE, pale petal)
TabbedPane.selectedForeground       = Color.BLACK        WARNING: sensitive, do not change without testing
TabbedPane.underlineColor           = SAKURA_ROSE
TabbedPane.inactiveUnderlineColor   = SAKURA_ROSE
TabbedPane.focusColor               = SAKURA_LIGHT
TabbedPane.hoverColor               = SAKURA_ROSE       (deeper than selected = gets darker on hover)
ScrollBar.thumb                     = SAKURA_PINK        (#E8849A)
ScrollBar.thumbHover                = SAKURA_SCROLL_HOVER (#C55878)
ScrollBar.thumbPressed              = SAKURA_SCROLL_PRESS (#A84062)
TitlePane.foreground                = SAKURA_BARK
```

**Tab behavior:** Selected = soft pale petal (#F5C2CE) with black text. Hovering shows
deeper rose (#D4607C) — selected tab gets *darker* on hover. `selectedForeground = Color.BLACK`
is LAF-sensitive; changing it to a custom Color can cause incorrect rendering in some FlatLaf states.

**goSakuraTheme() extras:**
- `JRootPane.titleBarForeground` client property -> `SAKURA_BARK`
- `progressBar.setForeground(SAKURA_ROSE)`
- Auto-applies `Palette.SAKURA` chart palette

### Badge Colors (Sakura)

```
BADGE_AMBER_BG   = SAKURA_DARK            // stale background (#A83060 deep rose)
BADGE_STALE_FG   = Color.WHITE
BADGE_DEFAULT_BG = SAKURA_BADGE_BG        // #FCEEF2 pale pink blush
BADGE_DEFAULT_FG = SAKURA_ROSE            // fallback for newly-created badges

Badge border: 1px SAKURA_ROSE outline + 2/5/2/5 padding
Alternating fg: even index -> SAKURA_ROSE (#D4607C), odd index -> SAKURA_BARK (#2D1B22)
```

**Note on odd badge color:** `SAKURA_BARK (#2D1B22)` on `#FCEEF2` can appear slightly
greenish to some viewers due to simultaneous color contrast. If this is a problem, change
odd index to `SAKURA_DARK (#A83060)` to keep both alternating colors in the warm rose family.

### Chart Palette (Sakura)

**Canvas:** pure white — outer chart + plot background
**Grid:** very light #EEEEEE
**Legend:** white bg, #DDDDDD border
**Strokes:** bold 1.5f for sample series; short-dash 2.0/6.0 1.2f for trend lines

| Series | Role | Color | Hex |
|---|---|---|---|
| bw[0] | Write sample | `SAKURA_PINK` + bold | `#E8849A` |
| bw[1] | Write trend | `SAKURA_FADE` + dash | `#E8849A` a170 |
| bw[2] | Write max | `SAKURA_LIGHT` | `#F5C2CE` |
| bw[3] | Write min | `SAKURA_DARK` | `#A83060` |
| bw[4] | Read sample | `SAKURA_SAGE` + bold | `#7A9E7E` |
| bw[5] | Read trend | `SAKURA_SAGE_FADE` + dash | `#7A9E7E` a170 |
| bw[6] | Read max | `SAKURA_SAGE_LIGHT` | `#B0CCAA` |
| bw[7] | Read min | `SAKURA_SAGE_DARK` | `#4A6B4D` |
| ms[0] | Write latency | `SAKURA_PINK` | `#E8849A` |
| ms[1] | Read latency | `SAKURA_SAGE` | `#7A9E7E` |

**Axes + title + legend text:** `SAKURA_BARK` (#2D1B22)

---

## Shared Infrastructure

### restoreDefaultPlotBackground()

Called by Classic, Blue-Green, Bard Cool/Warm. Not called by Beta, Old Glory, or Sakura.

```
plot.setBackgroundPaint(Color.DARK_GRAY.darker())
plot.setOutlinePaint(Color.WHITE)
grid: new Color(80, 80, 80)
Clears per-series strokes: bw[0], bw[1], bw[4], bw[5] -> null
```

### clearOldGloryOverrides()

Called by light/dark/darcula configurators to reset FlatLaf global extra defaults to null,
preventing Old Glory or Sakura colors from bleeding into non-themed LAFs.

### Tab Behavior Summary

| Theme | Selected bg | Selected fg | Hover | Underline |
|---|---|---|---|---|
| Old Glory | `#3C3B6E` navy | White | `#ECEDF8` very light blue | `#B22234` red |
| Sakura | `#F5C2CE` pale petal | `Color.BLACK` | `#D4607C` rose (darker) | `#D4607C` rose |
| Dark / Darcula | LAF default | LAF default | LAF default | LAF default |
| Light | LAF default | LAF default | LAF default | LAF default |

---

## Planned Themes (not yet implemented)

- **Halloween** — orange + dark purple
- **Christmas** — forest green + crimson
- **Turtle, Rabbit, Polar Bear, Cat** — animal-themed seasonal palettes
