# JDiskMark Theme Reference

Captures the architecture, color systems, LAF configuration, and chart palette
details for each implemented theme.  Use as a handoff document when starting a
new thread or onboarding a new contributor.

---

## Architecture

### File Layout

| File | Role |
|---|---|
| [`ThemeDefinition.java`](../src/main/java/org/metricus/jdm/ui/ThemeDefinition.java) | Interface — every theme implements this contract |
| [`Theme.java`](../src/main/java/org/metricus/jdm/ui/Theme.java) | Enum — one constant per theme, each holding a `ThemeDefinition` |
| [`theme/`](../src/main/java/org/metricus/jdm/ui/theme) | Package — standalone `ThemeDefinition` classes |
| [`Gui.java`](../src/main/java/jdiskmark/Gui.java) | Generic `configureLaf(ThemeDefinition)` + `applyTheme(Theme)` — no per-theme code |
| [`Palette.java`](../src/main/java/org/metricus/jdm/ui/Palette.java) | Enum for chart color palettes, independent of window themes |

### Data Flow

```
Theme enum
  └─ holds ThemeDefinition instance
  └─ apply() → Gui.applyTheme(Theme)
                  ├─ Gui.configureLaf(ThemeDefinition)
                  │    ├─ FlatLaf.setGlobalExtraDefaults(def.flatLafExtras())
                  │    ├─ UIManager.setLookAndFeel(def.lafClassName())
                  │    ├─ clearThemeOverrides()
                  │    └─ def.uiManagerOverrides() → UIManager.put(...)
                  ├─ FlatLaf.updateUI()
                  ├─ refreshAllWindows()
                  ├─ updateChartPanelStyle()
                  ├─ titleBarForeground → JRootPane client property
                  ├─ progressBarForeground → progressBar.setForeground()
                  └─ applyLinkedPalette() (Old Glory, Sakura only)
```

### ThemeDefinition Interface

| Method | Required | Purpose |
|---|---|---|
| `lafClassName()` | **Yes** | Fully-qualified FlatLaf class name string |
| `flatLafExtras()` | No | Map of FlatLaf `@variable` overrides, set before `setLookAndFeel()` |
| `uiManagerOverrides()` | No | Map of `UIManager.put()` overrides, set after `setLookAndFeel()` |
| `titleBarForeground()` | No | `JRootPane.titleBarForeground` client property |
| `progressBarForeground()` | No | Direct `setForeground()` on the progress bar |
| `badgeDefaultBg()` | **Yes** | Badge background (non-stale state) |
| `badgeDefaultFg()` | **Yes** | Badge foreground (non-stale, fallback) |
| `badgeStaleBg()` | **Yes** | Badge background when setting differs from current config |
| `badgeStaleFg()` | No | Badge foreground when stale (default: `Color.WHITE`) |
| `badgeBorderColor()` | No | Badge border outline (null = no custom border) |
| `cycleBadgeColors()` | No | If `true`, non-stale badges alternate fg between even/odd indices (default: `false`) |
| `badgeEvenFg()` | No | Even-index badge fg when cycling (default: `badgeDefaultFg()`) |
| `badgeOddFg()` | No | Odd-index badge fg when cycling (default: `badgeDefaultFg()`) |

### Enums (auto-populate menus)

**`Palette`** drives **Graph > Color Palette** menu (via `GraphPaletteMenu` iterating `values()`):

    CLASSIC, LAGOON, MARINE, EMBER, BETA

**`Theme`** drives **Graph > Window Theme** menu (via `GraphThemeMenu` iterating `values()`):

    DARK, LIGHT, DARCULA, OLD_GLORY, SAKURA, HARVEST

---

## Adding a New Theme — Checklist

1. **Create** a new class in `org.metricus.jdm.ui.theme` implementing `ThemeDefinition`
   - Return the FlatLaf class name from `lafClassName()`
   - Override `flatLafExtras()` and `uiManagerOverrides()` for custom LAF styling
   - Provide badge colors (`badgeDefaultBg`, `badgeDefaultFg`, `badgeStaleBg`)
   - Set `cycleBadgeColors() → true` if badge fg should alternate between even/odd
   - Put chart palette color constants as `public static final Color` if the theme has a linked palette
2. **Add one line** to the `Theme` enum:
   ```java
   MY_THEME("My Theme", new MyTheme()),
   ```
3. **If the theme has a linked chart palette:**
   - Add a `setMyThemeColorScheme()` method to `ChartPalette`
   - Add a `MY_THEME` constant to the `Palette` enum
   - Add a `case` to `Theme.applyLinkedPalette()`
   - Add `this == MY_THEME` to `Theme.hasLinkedPalette()`

**That's it.** No changes needed to `Gui.java`, `MainFrame.java`, `GraphThemeMenu`, or badge logic.

> **Timing note:** `chartBadgeList` is assigned after `refreshChartBadges()` in
> `createChartPanel()`. The theme-specific alternation in `setBadgeStaleReturn()` guards
> on `chartBadgeList != null`; the list is populated before any user-triggered refresh.

> **UIManager note:** Override values must be `ColorUIResource` instances (not plain
> `Color`) to be properly overridden on subsequent `updateUI()` calls. The theme
> classes handle this internally via a `uiColor()` helper.

---

## Old Glory Theme

**Identity:** US flag patriotic — navy + crimson on pure white.

**Class:** [`OldGloryTheme.java`](file:///c:/Users/james/git/jdm-java/jdm-core/src/main/java/org/metricus/jdm/ui/theme/OldGloryTheme.java)

### Color Constants

| Constant | Hex | Role |
|---|---|---|
| `BLUE` | `#3C3B6E` | Primary accent: UI text, selections, scrollbar, tab selected bg |
| `RED` | `#B22234` | Secondary accent: `@accentColor` (checkboxes/focus), tab underline, progress bar |
| `CRIMSON` | `#DC143C` | Chart write-sample, write-latency |
| `CRIMSON_FADE` | `#DC143C` a=170 | Chart write-trend dashed line |
| `CRIMSON_LIGHT` | `#FF6B6B` | Chart write-max |
| `CRIMSON_DARK` | `#8B0000` | Chart write-min |
| `BLUE_FADE` | `#3C3B6E` a=170 | Chart read-trend dashed line |
| `BLUE_LIGHT` | `#7878B4` | Chart read-max (periwinkle) |
| `BLUE_DARK` | `#1A1940` | Chart read-min (deep navy) |
| `BADGE_BG` | `#EEF2FF` | Badge background (pale lavender-blue) |

### LAF Configuration

**Base LAF:** `FlatLightLaf` / `FlatMacLightLaf`

**Global extra defaults (before setLookAndFeel):**
```
@accentColor  = #B22234   (RED)
@background   = #FFFFFF
@foreground   = #3C3B6E   (BLUE)
TitlePane.foreground = #3C3B6E
```

**UIManager overrides (after setLookAndFeel):**
```
Table/List/Tree.selectionBackground = BLUE  + WHITE fg
TabbedPane.selectedBackground       = BLUE
TabbedPane.selectedForeground       = Color.WHITE
TabbedPane.underlineColor           = RED
TabbedPane.inactiveUnderlineColor   = RED
TabbedPane.focusColor               = BLUE
TabbedPane.hoverColor               = #ECEDF8 (very light blue)
ScrollBar.thumb                     = BLUE
ScrollBar.thumbHover                = #2B2A52
ScrollBar.thumbPressed              = #1A1A38
```

**Runtime extras (via `applyTheme`):**
- `JRootPane.titleBarForeground` → `BLUE`
- `progressBar.setForeground(RED)`
- Auto-applies `Palette.OLD_GLORY` chart palette

### Badge Colors

```
badgeStaleBg   = RED
badgeStaleFg   = Color.WHITE
badgeDefaultBg = BADGE_BG      (#EEF2FF pale lavender-blue)
badgeDefaultFg = BLUE
badgeBorder    = 1px BLUE outline + 2/5/2/5 padding
cycleBadgeColors = true
  even index → RED
  odd index  → BLUE
```

### Chart Palette

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
| bw[4] | Read sample | `BLUE` + bold | `#3C3B6E` |
| bw[5] | Read trend | `BLUE_FADE` + dash | `#3C3B6E` a170 |
| bw[6] | Read max | `BLUE_LIGHT` | `#7878B4` |
| bw[7] | Read min | `BLUE_DARK` | `#1A1940` |
| ms[0] | Write latency | `CRIMSON` | `#DC143C` |
| ms[1] | Read latency | `BLUE` | `#3C3B6E` |

**Axes + title + legend text:** `BLUE` (#3C3B6E)

---

## Sakura Theme

**Identity:** Cherry blossom spring — rose pink + sage green on near-white.

**Class:** [`SakuraTheme.java`](file:///c:/Users/james/git/jdm-java/jdm-core/src/main/java/org/metricus/jdm/ui/theme/SakuraTheme.java)

### Color Constants

| Constant | Hex | Role |
|---|---|---|
| `ROSE` | `#D4607C` | Primary accent: `@accentColor`, selections, tab underline, progress bar |
| `PINK` | `#E8849A` | Chart write-sample, write-latency, scrollbar thumb |
| `FADE` | `#E8849A` a=170 | Chart write-trend dashed line |
| `LIGHT` | `#F5C2CE` | Chart write-max |
| `DARK` | `#A83060` | Chart write-min |
| `BARK` | `#2D1B22` | Cherry bark dark: chart axis/title text, title bar |
| `SAGE` | `#7A9E7E` | Chart read-sample, read-latency |
| `SAGE_FADE` | `#7A9E7E` a=170 | Chart read-trend dashed line |
| `SAGE_LIGHT` | `#B0CCAA` | Chart read-max |
| `SAGE_DARK` | `#4A6B4D` | Chart read-min |
| `BADGE_BG` | `#FCEEF2` | Badge background (pale pink blush) |

### LAF Configuration

**Base LAF:** `FlatLightLaf` / `FlatMacLightLaf`

**Global extra defaults (before setLookAndFeel):**
```
@accentColor  = #D4607C   (ROSE)
@background   = #FFFBFC   (near-white with faint pink tint)
@foreground   = #2D1B22   (BARK)
TitlePane.foreground = #2D1B22
```

**UIManager overrides (after setLookAndFeel):**
```
Table/List/Tree.selectionBackground = ROSE          + WHITE fg
TabbedPane.underlineColor           = ROSE
TabbedPane.inactiveUnderlineColor   = ROSE
TabbedPane.hoverColor               = #FDF0F4 (very light pink)
ScrollBar.thumb                     = PINK          (#E8849A)
ScrollBar.thumbHover                = #C55878
ScrollBar.thumbPressed              = #A84062
```

**Runtime extras (via `applyTheme`):**
- `JRootPane.titleBarForeground` → `BARK`
- `progressBar.setForeground(ROSE)`
- Auto-applies `Palette.SAKURA` chart palette

### Badge Colors

```
badgeStaleBg   = DARK            (#A83060 deep rose)
badgeStaleFg   = Color.WHITE
badgeDefaultBg = BADGE_BG        (#FCEEF2 pale pink blush)
badgeDefaultFg = ROSE
badgeBorder    = 1px ROSE outline + 2/5/2/5 padding
cycleBadgeColors = true
  even index → ROSE   (#D4607C)
  odd index  → DARK   (#A83060)
```

### Chart Palette

**Canvas:** pure white — outer chart + plot background
**Grid:** very light #EEEEEE
**Legend:** white bg, #DDDDDD border
**Strokes:** bold 1.5f for sample series; short-dash 2.0/6.0 1.2f for trend lines

| Series | Role | Color | Hex |
|---|---|---|---|
| bw[0] | Write sample | `PINK` + bold | `#E8849A` |
| bw[1] | Write trend | `FADE` + dash | `#E8849A` a170 |
| bw[2] | Write max | `LIGHT` | `#F5C2CE` |
| bw[3] | Write min | `DARK` | `#A83060` |
| bw[4] | Read sample | `SAGE` + bold | `#7A9E7E` |
| bw[5] | Read trend | `SAGE_FADE` + dash | `#7A9E7E` a170 |
| bw[6] | Read max | `SAGE_LIGHT` | `#B0CCAA` |
| bw[7] | Read min | `SAGE_DARK` | `#4A6B4D` |
| ms[0] | Write latency | `PINK` | `#E8849A` |
| ms[1] | Read latency | `SAGE` | `#7A9E7E` |

**Axes + title + legend text:** `BARK` (#2D1B22)

---

## Standard Themes (Dark / Light / Darcula)

These themes use their FlatLaf defaults without custom extras or UIManager overrides.

| Theme | Class | LAF Class |
|---|---|---|
| Dark | [`DarkTheme.java`](file:///c:/Users/james/git/jdm-java/jdm-core/src/main/java/org/metricus/jdm/ui/theme/DarkTheme.java) | `FlatDarkLaf` / `FlatMacDarkLaf` |
| Light | [`LightTheme.java`](file:///c:/Users/james/git/jdm-java/jdm-core/src/main/java/org/metricus/jdm/ui/theme/LightTheme.java) | `FlatLightLaf` / `FlatMacLightLaf` |
| Darcula | [`DarculaTheme.java`](file:///c:/Users/james/git/jdm-java/jdm-core/src/main/java/org/metricus/jdm/ui/theme/DarculaTheme.java) | `FlatDarculaLaf` |

Badge colors follow the same `ThemeDefinition` interface but with simpler values:

| Property | Dark / Darcula | Light |
|---|---|---|
| `badgeDefaultBg` | `rgba(40,40,40,180)` | `rgba(220,220,220,200)` |
| `badgeDefaultFg` | `rgb(200,200,200)` | `rgb(50,50,50)` |
| `badgeStaleBg` | `#C87800` (amber) | `#E6A01E` (gold) |
| `cycleBadgeColors` | `false` | `false` |

---

## Shared Infrastructure

### clearThemeOverrides()

Called by `configureLaf()` after every `setLookAndFeel()`. Nulls all custom UIManager keys
and resets `FlatLaf.setGlobalExtraDefaults(null)` so previous theme colors don't bleed through.

### restoreDefaultPlotBackground()

Called by Classic, Blue-Green, Bard Cool/Warm. Not called by Beta, Old Glory, or Sakura.

```
plot.setBackgroundPaint(Color.DARK_GRAY.darker())
plot.setOutlinePaint(Color.WHITE)
grid: new Color(80, 80, 80)
Clears per-series strokes: bw[0], bw[1], bw[4], bw[5] → null
```

### Tab Behavior Summary

| Theme | Selected bg | Selected fg | Hover | Underline |
|---|---|---|---|---|
| Old Glory | `#3C3B6E` navy | White | `#ECEDF8` very light blue | `#B22234` red |
| Sakura | LAF default | LAF default | `#FDF0F4` very light pink | `#D4607C` rose |
| Dark / Darcula | LAF default | LAF default | LAF default | LAF default |
| Light | LAF default | LAF default | LAF default | LAF default |

---

## Planned Themes (not yet implemented)

- **Halloween** — orange + dark purple
- **Christmas** — forest green + crimson
- **Turtle, Rabbit, Polar Bear, Cat** — animal-themed seasonal palettes
