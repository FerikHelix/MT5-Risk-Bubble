# Proposal

## Why

Sizing a MetaTrader 5 order by risk percentage on the phone is manual: the trader works out the stop-loss distance, tick value and account-currency conversion by hand (or in a separate calculator) and types the lot back into the ticket. This is slow and error-prone for JPY pairs, metals, indices and cent accounts. MT5's own order ticket already shows the money loss at the stop-loss for the current volume, so the missing piece is a small companion that uses that number to set the lot for a chosen risk percentage.

A spike on the target device (Redmi Note 10S, Android 13, MIUI 14, MT5 500.6260) confirmed that the ticket is built from native views with readable resource ids, that the Volume field is a writable `EditText`, that the loss label recomputes live when the volume changes, and that the ticket sheet can be expanded and collapsed programmatically. A later probe found that MetaTrader 5 also keeps the whole ticket, with live values, in the accessibility tree while the sheet is collapsed, so its fields can be read and written without opening the sheet at all.

## What Changes

- New sideloaded Android app (Kotlin, Jetpack Compose) that shows a floating bubble over MetaTrader 5.
- Tapping the bubble opens a small risk picker (presets 0.5 / 1 / 2 % plus a custom value). Choosing a value runs one lot-sizing pass.
- A lot-sizing pass reads the current volume and MT5's own stop-loss money loss from the ticket fields while the sheet stays collapsed, scales the volume so the loss matches the chosen percentage of the account balance, rounds the lot down to the volume step, writes it into the ticket, and verifies it against MT5's recomputed loss (correcting downward, at most 3 attempts). The sheet never moves, so only the stop-loss and take-profit lines on the chart appear to change. The app expands and collapses the sheet only as a fallback when the hidden fields cannot be read, and always leaves it as it found it.
- Volume limits are kept per symbol (minimum lot, with the step equal to the minimum, and a maximum defaulting to 100). The app asks once for a new symbol's minimum lot (forex 0.01, indices such as SP500 0.1) and remembers it; saved rules can be edited or deleted in settings.
- The app never presses Buy, Sell or Place. It refuses and explains when preconditions fail (no SL, no or stale balance, wrong-side SL, unreadable ticket).
- The account balance is read from the Trade tab whenever it is visible and cached; a cached balance that is lower than the ticket's free margin is treated as stale or from another account.
- Currency suffixes (`USD`, `USC`, others) and space thousands separators are parsed generically.
- Setup guidance for the Android/MIUI permissions the app depends on (overlay, accessibility with restricted settings, background pop-ups, autostart, battery).

Out of scope for this change: armed/auto-follow mode that re-fits the lot when the SL changes (planned follow-up phase), tick-by-tick chasing (dropped), a live risk readout, any order execution.

## Capabilities

### New Capabilities
- `risk-lot-sizing`: how a lot is derived from a risk percentage, the account balance and MT5's reported SL loss, including rounding to per-symbol volume rules learned once, the verify-and-correct loop, and the conditions under which nothing is written.
- `mt5-ticket-automation`: how the app drives the MetaTrader 5 order ticket through the accessibility service: reading and writing ticket fields while the sheet stays collapsed, expanding and collapsing it only as a fallback, and the guarantee that order-submission controls are never actuated.
- `balance-cache`: how the account balance is captured from the MT5 Trade tab, stored with its age and currency context, checked for staleness against free margin, and overridden manually.
- `risk-bubble-ui`: the floating bubble, risk picker including the one-time minimum-lot question and the saved-rules settings, result and error feedback, foreground-service lifetime, and the permission/setup flow on the target device.

### Modified Capabilities
<!-- None: this is a new project with no existing specs. -->

## Impact

- New Android project (Gradle/Kotlin, Compose) in this repository; no existing app code is affected.
- Requires Android permissions and services: `SYSTEM_ALERT_WINDOW` (overlay), an `AccessibilityService` scoped to package `net.metaquotes.metatrader5` with gesture dispatch, and a foreground service.
- Depends on MetaTrader 5 (`net.metaquotes.metatrader5`, build 500.6260) UI resource ids staying stable; an MT5 update that renames them breaks reading and must degrade to a clear "unreadable ticket" refusal, not a wrong lot.
- Distributed as a sideloaded APK only; no Play Store policy review. Intended for personal use, first validated on a demo account.
- The user places every order themselves; trading risk remains with the user.
