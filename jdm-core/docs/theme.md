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
                  └─ applyLinkedPalette() (Old Glory, Sakura, Harvest, Trick or Treat, Yuletide)
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

    BETA_DARK, BETA_LIGHT, LAGOON, MARINE, EMBER, CLASSIC

**`Theme`** drives **Graph > Window Theme** menu (via `GraphThemeMenu` iterating `values()`):

      DARK, LIGHT, DARCULA, OLD_GLORY, SAKURA, HARVEST, TRICK_OR_TREAT, YULETIDE

---

## Unlinked Palette Design Constraint

**Unlinked palettes** (every `Palette` constant that is *not* returned by
`Theme.hasLinkedPalette()`) may only set colors and strokes **inside the graph
canvas**.  The surrounding UI chrome — axis labels, chart title, legend
background and border, outer chart background — is owned by the active LAF and
must not be overridden.

### What an unlinked palette MAY override

| `PaletteDefinition` method | Scope |
|---|---|
| `plotBackground()` | Fill color of the XY plot canvas (inside the axes) |
| `plotOutline()` | Border of the plot canvas |
| `gridColor()` | Domain and range gridlines |
| `bwWrite*()` / `bwRead*()` | Bandwidth renderer series paints (8 series) |
| `msWriteLatency()` / `msReadLatency()` | Latency renderer series paints |
| `bwWriteSampleStroke()` etc. | Per-series `BasicStroke` overrides |

### What an unlinked palette MUST NOT override

| `PaletteDefinition` method | Why |
|---|---|
| `chartBackground()` | Outer chart paint — set by LAF |
| `textPaint()` | Axis labels, chart title, legend item text — set by LAF |
| `legendBackground()` | Legend fill — set by LAF |
| `legendBorderColor()` | Legend border — set by LAF |

### Why this matters

Unlinked palettes are LAF-agnostic and can be applied to any window theme
(Dark, Light, Darcula, etc.).  Overriding text or legend colors forces values
that may be unreadable on the current LAF — for example, forcing dark text
while the Dark LAF renders a dark background.

Linked palettes (Old Glory, Sakura, Harvest, Trick or Treat, Yuletide) are exempt because they are
always applied together with a specific LAF that they control end-to-end.

### Adding a new unlinked palette — checklist

1. Create a class in `org.metricus.jdm.ui.palette` implementing `PaletteDefinition`.
2. Only override the **MAY** methods listed above.
3. Do **not** override `chartBackground()`, `textPaint()`, `legendBackground()`,
   or `legendBorderColor()`.
4. Add one line to the `Palette` enum (declaration order = menu order).
5. No changes to `Gui.java`, `GraphPaletteMenu`, or any theme class are needed.

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

## Trick or Treat Theme

**Identity:** Halloween night - pumpkin orange + candy violet on deep twilight.

**Class:** [`TrickOrTreatTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/TrickOrTreatTheme.java)

### Color Constants

| Constant | Hex | Role |
|---|---|---|
| `NIGHT` | `#12091F` | Base chart + legend background (midnight backdrop) |
| `VIOLET` | `#3A1C57` | UI secondary accent (scrollbar + focus) |
| `ORANGE` | `#F47B20` | Primary accent, write sample, progress bar |
| `ORANGE_FADE` | `#F47B20` a=170 | Write trend dashed line |
| `ORANGE_LIGHT` | `#FFB15E` | Write max |
| `ORANGE_DARK` | `#B84E06` | Write min + stale badge bg |
| `PURPLE` | `#B28BFF` | Read sample + badge fg |
| `PURPLE_FADE` | `#B28BFF` a=170 | Read trend dashed line |
| `PURPLE_LIGHT` | `#D5C0FF` | Read max + icon secondary tint |
| `PURPLE_DARK` | `#7A55C8` | Read min + cancel accent |
| `BADGE_BG` | `#261139` | Badge default background |

### LAF Configuration

**Base LAF:** `FlatDarkLaf` / `FlatMacDarkLaf`

**Global extra defaults (before setLookAndFeel):**
```
@accentColor  = #F47B20   (ORANGE)
@background   = #12091F   (NIGHT)
@foreground   = #EBDCFD   (moonlit lavender)
TitlePane.foreground = #EBDCFD
```

**UIManager overrides (after setLookAndFeel):**
```
Table/List/Tree.selectionBackground = ORANGE + BLACK fg
TabbedPane.underlineColor           = ORANGE
TabbedPane.inactiveUnderlineColor   = ORANGE
TabbedPane.focusColor               = VIOLET
TabbedPane.hoverColor               = #2B153F
TabbedPane.hoverForeground          = PURPLE
ScrollBar.thumb                     = VIOLET
ScrollBar.thumbHover                = #542D77
ScrollBar.thumbPressed              = #3A1C57
```

**Runtime extras (via `applyTheme`):**
- `JRootPane.titleBarForeground` -> `#EBDCFD`
- `progressBar.setForeground(ORANGE)`
- Auto-applies linked `TrickOrTreatPalette`

### Badge Colors

```
badgeStaleBg   = ORANGE_DARK
badgeStaleFg   = Color.WHITE
badgeDefaultBg = BADGE_BG      (#261139)
badgeDefaultFg = PURPLE
badgeBorder    = 1px ORANGE outline + 2/5/2/5 padding
cycleBadgeColors = true
   even index -> ORANGE
   odd index  -> PURPLE
```

### Chart Palette

**Canvas:** `NIGHT` outer chart, `#1B0E2B` plot background  
**Grid:** `#3C2757` (subtle violet)  
**Legend:** `NIGHT` bg, `#5A3B7E` border  
**Strokes:** bold 1.5f for sample series; short-dash 2.0/6.0 1.2f for trend lines

| Series | Role | Color | Hex |
|---|---|---|---|
| bw[0] | Write sample | `ORANGE` + bold | `#F47B20` |
| bw[1] | Write trend | `ORANGE_FADE` + dash | `#F47B20` a170 |
| bw[2] | Write max | `ORANGE_LIGHT` | `#FFB15E` |
| bw[3] | Write min | `ORANGE_DARK` | `#B84E06` |
| bw[4] | Read sample | `PURPLE` + bold | `#B28BFF` |
| bw[5] | Read trend | `PURPLE_FADE` + dash | `#B28BFF` a170 |
| bw[6] | Read max | `PURPLE_LIGHT` | `#D5C0FF` |
| bw[7] | Read min | `PURPLE_DARK` | `#7A55C8` |
| ms[0] | Write latency | `ORANGE` | `#F47B20` |
| ms[1] | Read latency | `PURPLE` | `#B28BFF` |

**Axes + title + legend text:** `#EBDCFD`

---

## Yuletide Theme

**Identity:** Christmas holiday - holly crimson + pine green on winter white.

**Class:** [`YuletideTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/YuletideTheme.java)

### Color Constants

| Constant | Hex | Role |
|---|---|---|
| `PINE` | `#1D5A3A` | Primary selection + read sample |
| `HOLLY` | `#9F1F2E` | Accent, underline, write sample, progress bar |
| `NOEL_BG` | `#F8FCF8` | Theme + chart background |
| `NOEL_TEXT` | `#1B2E22` | Foreground text + title bar |
| `HOLLY_FADE` | `#9F1F2E` a=170 | Write trend dashed line |
| `HOLLY_LIGHT` | `#D15263` | Write max |
| `HOLLY_DARK` | `#6F121E` | Write min |
| `PINE_FADE` | `#1D5A3A` a=170 | Read trend dashed line |
| `PINE_LIGHT` | `#5C8F70` | Read max + icon secondary tint |
| `PINE_DARK` | `#103825` | Read min |
| `BADGE_BG` | `#EAF4EC` | Badge default background |

### LAF Configuration

**Base LAF:** `FlatLightLaf` / `FlatMacLightLaf`

**Global extra defaults (before setLookAndFeel):**
```
@accentColor  = #9F1F2E   (HOLLY)
@background   = #F8FCF8   (NOEL_BG)
@foreground   = #1B2E22   (NOEL_TEXT)
TitlePane.foreground = #1B2E22
```

**UIManager overrides (after setLookAndFeel):**
```
Table/List/Tree.selectionBackground = PINE + WHITE fg
TabbedPane.underlineColor           = HOLLY
TabbedPane.inactiveUnderlineColor   = HOLLY
TabbedPane.focusColor               = #DDECDF
TabbedPane.hoverColor               = #EEF6EF
TabbedPane.hoverForeground          = PINE
ScrollBar.thumb                     = PINE
ScrollBar.thumbHover                = #184B31
ScrollBar.thumbPressed              = #123723
```

**Runtime extras (via `applyTheme`):**
- `JRootPane.titleBarForeground` -> `NOEL_TEXT`
- `progressBar.setForeground(HOLLY)`
- Auto-applies linked `YuletidePalette`

### Badge Colors

```
badgeStaleBg   = HOLLY
badgeStaleFg   = Color.WHITE
badgeDefaultBg = BADGE_BG      (#EAF4EC)
badgeDefaultFg = PINE
badgeBorder    = 1px PINE outline + 2/5/2/5 padding
cycleBadgeColors = true
   even index -> PINE
   odd index  -> HOLLY
```

### Chart Palette

**Canvas:** `NOEL_BG` outer chart, white plot background  
**Grid:** `#E4EEE6` (soft winter mint)  
**Legend:** `NOEL_BG` bg, `#C9D8CC` border  
**Strokes:** bold 1.5f for sample series; short-dash 2.0/6.0 1.2f for trend lines

| Series | Role | Color | Hex |
|---|---|---|---|
| bw[0] | Write sample | `HOLLY` + bold | `#9F1F2E` |
| bw[1] | Write trend | `HOLLY_FADE` + dash | `#9F1F2E` a170 |
| bw[2] | Write max | `HOLLY_LIGHT` | `#D15263` |
| bw[3] | Write min | `HOLLY_DARK` | `#6F121E` |
| bw[4] | Read sample | `PINE` + bold | `#1D5A3A` |
| bw[5] | Read trend | `PINE_FADE` + dash | `#1D5A3A` a170 |
| bw[6] | Read max | `PINE_LIGHT` | `#5C8F70` |
| bw[7] | Read min | `PINE_DARK` | `#103825` |
| ms[0] | Write latency | `HOLLY` | `#9F1F2E` |
| ms[1] | Read latency | `PINE` | `#1D5A3A` |

**Axes + title + legend text:** `NOEL_TEXT` (#1B2E22)

---

## Standard Themes (Dark / Light / Darcula)

These themes use their FlatLaf defaults without custom extras or UIManager overrides.

| Theme | Class | LAF Class |
|---|---|---|
| Dark | [`DarkTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/DarkTheme.java) | `FlatDarkLaf` / `FlatMacDarkLaf` |
| Light | [`LightTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/LightTheme.java) | `FlatLightLaf` / `FlatMacLightLaf` |
| Darcula | [`DarculaTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/DarculaTheme.java) | `FlatDarculaLaf` |
| Harvest | [`HarvestTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/HarvestTheme.java) | `FlatDarkLaf` with autumn overrides — linked palette |
| Trick or Treat | [`TrickOrTreatTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/TrickOrTreatTheme.java) | `FlatDarkLaf` with Halloween orange/violet overrides — linked palette |
| Yuletide | [`YuletideTheme.java`](../src/main/java/org/metricus/jdm/ui/theme/YuletideTheme.java) | `FlatLightLaf` with Christmas holly/pine overrides — linked palette |

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

Called at the start of every `Palette.apply(PaletteDefinition)` invocation to
reset any previously applied custom canvas colors and strokes before the new
palette's values are written.  Palette implementations that supply their own
`plotBackground()` will immediately overwrite this reset.

```
plot.setBackgroundPaint(Color.DARK_GRAY.darker())
plot.setOutlinePaint(Color.WHITE)
grid: new Color(80, 80, 80)
Clears per-series strokes: bw[0], bw[1], bw[4], bw[5] → null
```

### Volatility Band Renderer

The volatility band is an optional overlay (toggled via **Options > Volatility Band**)
that renders ±2σ standard deviation bands around the trend lines to help detect
thermal throttling.  It uses two additional datasets and renderers, separate from
the main bandwidth series:

| Dataset | Renderer | Series | Mapped Axis |
|---|---|---|---|
| 2 (write band) | `wBandRenderer` (`XYDifferenceRenderer`) | 0: Write Upper Band, 1: Write Lower Band | Axis 0 (MB/s) |
| 3 (read band) | `rBandRenderer` (`XYDifferenceRenderer`) | 0: Read Upper Band, 1: Read Lower Band | Axis 0 (MB/s) |

**Color derivation rule** — band colors are **not** defined in `PaletteDefinition`.
They are derived at runtime from the active palette's trend line colors via
`Gui.updateBandColors()`, which is called at the end of `Palette.apply()`:

| Element | Source | Alpha |
|---|---|---|
| Band fill (positive + negative) | `bwRenderer` series 1 / 5 paint (write / read trend) | 50 (~20%) |
| Band edge (series 0 + 1 stroke) | same source color | 100 (~40%) |
| Stroke width | hardcoded | `0.5f` |

**No legend entries** — both band renderers have `setSeriesVisibleInLegend(false)`
on all series.  Visibility is controlled by `setDefaultSeriesVisible()` in the
three `updateLegendAndAxis()` overloads.

### Tab Behavior Summary

| Theme | Selected bg | Selected fg | Hover | Underline |
|---|---|---|---|---|
| Old Glory | `#3C3B6E` navy | White | `#ECEDF8` very light blue | `#B22234` red |
| Sakura | LAF default | LAF default | `#FDF0F4` very light pink | `#D4607C` rose |
| Dark / Darcula | LAF default | LAF default | LAF default | LAF default |
| Light | LAF default | LAF default | LAF default | LAF default |

---

## Planned Themes (not yet implemented)

- **Turtle, Rabbit, Polar Bear, Cat** — animal-themed seasonal palettes
