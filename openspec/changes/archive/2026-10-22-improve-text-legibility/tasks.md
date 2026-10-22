# Tasks

## 1. Palette and contrast guard

- [x] 1.1 Add a `Palette` object holding the colours from design.md D1/D2 and the text sizes from D4, and verify the project compiles with `gradlew assembleDebug`
- [x] 1.2 Add a JVM unit test that computes the WCAG contrast of every foreground/background pair in `Palette`, plus the 80 % blend over white for the three toasts and the 90 % bubble over white, and verify it passes at 4.5 : 1 (and fails if a pair is deliberately lowered)

## 2. Overlay recolour

- [x] 2.1 In `OverlayContent.kt` set an explicit colour on every `Text` (picker title, minimum-lot question, balance line, hints, chip labels, toast text, reset button) and size per D4, and verify no `Text` in the file is left without a colour by searching the file
- [x] 2.2 Replace the default lavender buttons with the neutral chip, selected-chip and primary-action colours, and the bubble with `#1456B8` at 90 % opacity, and verify the picker, custom-percentage and minimum-lot screens compile and the existing picker-logic tests still pass
- [x] 2.3 Recolour the three toast severities (success, warning with dark text, error) per D1 and verify the severity-to-colour mapping with a unit test

## 3. In-app screens

- [x] 3.1 Lock `MainActivity` to a dark colour scheme built from `Palette` and verify the app looks dark with the phone set to light theme (screenshot)
- [x] 3.2 Update the status labels and buttons in `MainScreens.kt` to the palette and verify the Setup and Minimum lots tabs against the palette test

## 4. Device check

- [x] 4.1 Run the full JVM suite with `gradlew testDebugUnitTest` and verify it passes with no regressions
- [x] 4.2 Install the debug build on the Redmi Note 10S, re-grant the overlay permission, and check screenshots of the bubble, the picker for XAUUSD, the minimum-lot question, each toast type over MT5's chart, and both in-app tabs; verify all text is clearly readable and nothing wraps or clips at default and large system font
- [x] 4.3 Update README.md/ACCEPTANCE.md if they describe colours or screens, and verify they match the final look (skip if they do not mention it)

## Workflow follow-up

- Archive the change after the on-device check, syncing the `risk-bubble-ui` delta into the main spec.
