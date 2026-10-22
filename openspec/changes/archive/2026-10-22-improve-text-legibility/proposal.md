# Proposal

## Why

On the phone, the text and colours of the companion are hard to read. The picker title ("Risk for XAUUSD", "Minimum lot for ...") has no explicit colour and falls back to black on a dark card (about 1.4 : 1). The highlighted last-used preset is dark purple on green (3.9 : 1). The toasts are borderline, and Android appears to draw overlay windows partly see-through, which pulls the success and warning toasts down to roughly 3.2 to 3.6 : 1 over MetaTrader 5's white chart text. The user always runs the phone in dark mode and wants the text easier to see.

## What Changes

- Give every line of text in the bubble, picker, minimum-lot question and result toast an explicit light colour on a dark card, and enlarge the picker title and toast text slightly.
- Replace the default lavender buttons and the dark-purple-on-green highlight with neutral dark chips, a clearly readable green selected state, and a blue primary action.
- Recolour the three toast severities (success, warning, error) so each stays readable even when the system draws the overlay at about 80 % opacity.
- Recolour the in-app screens (Setup, Minimum lots) to match, lighten the status labels so "Missing" is readable, and lock the app to the dark theme.
- Make the bubble background slightly more opaque so its label stays readable over the chart's price numbers.
- No layout, behaviour, permission or lot-sizing changes.

## Capabilities

### New Capabilities

### Modified Capabilities
- `risk-bubble-ui`: add a requirement that all text in the bubble, picker, result messages and in-app screens meets a minimum contrast on a dark theme, including when the overlay is drawn partly transparent.

## Impact

- Code: `ui/overlay/OverlayContent.kt` (colours and text sizes for bubble, picker, minimum-lot question, toast), `ui/MainScreens.kt` (status colours and buttons), `MainActivity.kt` (force the dark colour scheme).
- Specs: one added requirement in `risk-bubble-ui`; no existing requirement changes.
- Tests: the JVM logic tests are unaffected; a small colour-contrast unit test guards the palette, and the result is checked on the device with screenshots.
- No new dependencies, permissions or data.
