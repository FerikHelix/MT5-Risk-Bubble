# Design

## Context

All overlay colours are literals in `ui/overlay/OverlayContent.kt` on top of the default Material 3 `darkColorScheme()`. Text that sets no colour (the picker title and the minimum-lot question) falls back to black, which is why it is almost invisible on the dark card. The in-app screens in `ui/MainScreens.kt` use the default scheme and follow the phone's light or dark setting (`MainActivity.kt`). See proposal.md for motivation.

Measured contrast (WCAG ratios, computed offline):

- Picker title: black on `#22262E`, 1.38 : 1.
- Highlighted preset: `#381E72` on `#2E9E5B`, 3.86 : 1.
- Toasts, white text at full opacity: success 5.33, warning 4.68, error 7.53. At 80 % opacity over white chart text these drop to 3.60, 3.25 and 4.90.
- Bubble, white on `#1E6FD9`: 4.85 at full opacity, 3.43 at 80 % over white.
- In-app status labels on the dark surface: Ready 5.0, Check 5.8, Missing 3.9.

The toast looks see-through in device screenshots. The cause is not confirmed (Android may cap overlay opacity), so the palette is chosen to pass even at 80 %.

## Goals / Non-Goals

**Goals:**
- Every text line meets 4.5 : 1, with margin for the 80 % case on toasts and the bubble.
- One small palette shared by overlay and in-app screens.
- A unit test that fails if a palette pair drops below its threshold.

**Non-Goals:**
- No layout, gesture, or behaviour changes; no fan-out chips, bottom sheet, or result chip.
- No light theme support. The user always runs dark, so the app is locked to dark.
- No change to MetaTrader 5's own colours.

## Decisions

**D1. One palette object, explicit colours everywhere.** A single `Palette` (Kotlin object) holds the colours below, and every `Text` and `Button` takes its colour from it. This removes the "inherited black" failure instead of patching the two titles. Alternative: only set the title colour. Rejected, because the same default would bite the next text added.

| Role | Foreground | Background | Contrast |
|---|---|---|---|
| Card text and title | `#F4F6FA` | card `#1A1E26` | 15.4 |
| Secondary text | `#C3C9D4` | card | 10.0 |
| Preset chip | `#F4F6FA` | `#2C3342` | 11.7 |
| Selected (last used) chip | `#04210F` | `#3DDC84` | 9.6 |
| Primary action | `#FFFFFF` | `#1456B8` | 6.9 |
| Success toast | `#FFFFFF` | `#0B5A32` | 8.3 (5.0 at 80 %) |
| Warning toast | `#1A1200` | `#FFC53D` | 11.8 (12.9 at 80 %) |
| Error toast | `#FFFFFF` | `#8E1B1B` | 9.0 (5.8 at 80 %) |
| In-app Ready / Missing / Check | `#4ADE80` / `#FF7A7A` / `#FFC53D` | `#1C1B1F` | 9.8 / 6.8 / 10.9 |

**D2. Bubble: darker blue, 90 % opacity.** The current `#1E6FD9` at 90 % still fails over white (4.08). `#1456B8` at 90 % gives 5.53 over white, 5.94 over light grey and 7.94 over black, all above 4.5. Alternative: keep the blue and go fully opaque (4.85). Rejected as less margin; the darker blue also matches the primary action colour. The bubble stays slightly translucent so the chart is not fully hidden.

**D3. Warning uses dark text on amber.** White on amber cannot pass without turning the amber brown and muddy; dark text on `#FFC53D` gives 11.8 and reads as a warning at a glance. The three toasts also differ in hue, so severity does not rely on wording alone.

**D4. Sizes.** Picker title 18 sp to 20 sp, toast and chip text 15 sp to 16 sp, secondary text 13 sp to 14 sp. Text size is a legibility aid only; the layout is otherwise unchanged and the picker width is checked on the device.

**D5. In-app screens locked to dark.** `MainActivity` always uses a dark colour scheme tuned to the palette (primary `#1456B8`, status colours above), ignoring `isSystemInDarkTheme()`. Alternative: keep following the system. Rejected: the user is always in dark mode, and a locked scheme means one tested palette instead of two.

**D6. Contrast guard as a JVM test.** A pure Kotlin test computes the WCAG ratio for each foreground/background pair in `Palette` (and the 80 % blend over white for toasts and bubble) and asserts the thresholds in D1/D2. Cheap, runs with the existing unit tests, and stops a later colour tweak from silently breaking readability. Alternative: screenshot tests. Rejected: heavy for a colour check, and the earlier instrumented-test run was already declined.

## Risks / Trade-offs

- [The real on-screen opacity is not known] → palette passes at 80 %; verify on the device with screenshots over a white-text chart and a light chart.
- [Larger text widens the picker or wraps a toast] → check on the device at default and large system font; shorten nothing unless it wraps.
- [MIUI forces its own dark-mode recolouring of app content] → check screenshots of the in-app screens; the explicit palette should win over any force-dark.
- [Contrast ratios say nothing about glare outdoors] → only the 4.5 : 1 floor is claimed, not "perfect"; colours are tunable in one object.

## Open Questions

- None that change the specs or tasks. The cause of the see-through toast can stay unconfirmed, since the palette does not depend on it.
