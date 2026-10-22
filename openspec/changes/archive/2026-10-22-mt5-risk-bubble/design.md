# Design

## Context

Greenfield: the repository holds only the MetaTrader 5 APK (`net.metaquotes.metatrader5`, 500.6260) and the OpenSpec scaffold. The app is built with Android Studio (Kotlin, Jetpack Compose) for one device: Redmi Note 10S, Android 13 (API 33), MIUI 14, sideloaded. See proposal.md for motivation.

Constraints established by a read-only spike on that device (demo account):

- MT5 draws the order ticket with native views carrying resource ids, so the accessibility tree exposes them. No `FLAG_SECURE` on MT5.
- Ticket (expanded) ids: `volume_lots_input` (EditText), `sl_input`, `tp_input`, `sl_calc_loss`, `tp_calc_profit`, `margin_value`, `free_margin_value`, `bid_price`, `ask_price`, `symbol_name`, `volume_mode_text`, `stops_mode_price|pips|money`, `sell_btn`, `buy_btn`, `place_btn`.
- Collapsed strip ids: `btn_sl`, `btn_tp`, `btn_submit` (expand arrow), `symbol_selector`, `symbol_name`. These are visible; the rest of the ticket is not.
- **MT5 keeps the whole ticket in the live accessibility tree while the sheet is collapsed**, off screen: `isVisibleToUser` is false and the bounds are empty or inverted (for example `top=2445, bottom=2400`). `uiautomator` hides these nodes, which is why an early spike wrongly concluded the values were unavailable.
- Those hidden fields are live. `sl_calc_loss` tracks the price, a stop-loss dragged on the chart updates the hidden `sl_input` and `sl_calc_loss`, and `margin_value`, `free_margin_value` and `volume_lots_input` are all present. `ACTION_SET_TEXT` on the hidden `volume_lots_input` succeeds and MT5 recomputes loss and margin, with the sheet never moving (verified: SL dragged to 209.853, lot written 0.38, MT5 loss 47.15 USD against a 50 budget).
- MT5 silently rounds a volume that is not a multiple of the symbol's step down (writing 0.715 left 0.71).
- `sl_calc_loss` also exists when the stop-loss is entered in Pips; `sl_input` then holds a pips value, so it is only used to tell whether a stop-loss is set.
- Trade tab ids: `balance`, `equity`, `margin_free`. The ticket has no balance.
- Typing a volume makes `sl_calc_loss` and `margin_value` recompute within about a second. The loss uses the live price, so it drifts between reads (e.g. -47.22, -45.13, -52.26 USD for the same 0.33 lots and SL within a minute).
- Fallback path: expanding = `ACTION_CLICK` on `btn_submit`; collapsing = a downward `dispatchGesture` swipe from just above the sheet header; Back only closes the keyboard. Volume and SL persist across collapse and reopen.
- `uiautomator dump` is unreliable on this device (MIUI) and never idles because of live ticks; this affects probing only, not an accessibility service.
- Amounts are formatted like `5 000.00 USD` / `-54.51 USC` (space thousands separator, currency suffix).

## Goals / Non-Goals

**Goals:**
- One tap on the bubble plus a percentage yields a lot whose MT5-reported SL loss is at or just under the risk budget, with the sheet never moving in the normal path.
- All sizing logic is pure Kotlin with no Android dependencies, covered by JVM unit tests, so correctness does not depend on the device.
- All MT5-specific knowledge (ids, layout assumptions) lives in one adapter, so an MT5 update means editing one file.
- A refused or failed pass can never leave a wrong lot silently in the ticket.

**Non-Goals:**
- Placing, modifying or closing orders; pressing Buy/Sell/Place or toggling one-click trading.
- Auto-follow (armed) mode, live risk readout, tick-by-tick adjustment. The design keeps the engine and adapter reusable for a later armed mode but implements none of it.
- Any server, broker API, MetaApi or EA bridge; any network access.
- Other brokers' apps, other devices, Play Store distribution.

## Decisions

### D1. Integrate through an AccessibilityService, not an API
MT5 on Android has no public order or account API (the manifest exposes only VIEW/SEND intents and internal components). An accessibility service is the only supported way for another app to read and set fields in MT5's UI.
- Alternatives: MetaApi/cloud bridge (needs account credentials in a third party, latency, cost); an EA plus HTTP (needs desktop MT5 and a VPS); pure calculator overlay (user retypes the lot; kept only as a conceptual fallback, not built in v1).

### D2. Use MT5's own SL loss as the oracle
`new = current * budget / |loss|` is exact because loss is linear in volume at a fixed price. MT5 has already done symbol, JPY, metal, index and account-currency conversion, so there is no symbol table, tick value or contract size to maintain.
- Alternative: compute from price distance, tick size/value and cross rates. Rejected: large surface for per-symbol and per-account errors, and the answer would still be checked against MT5's label anyway.
- Consequence: the loss label is mandatory. Its absence (no SL) is a refusal, not a guess.

### D3. Closed-loop verify, anchored on fresh readings
Because the price drifts, one pass is: read (volume V0, loss L0) -> compute V1 -> write -> read (V1, L1). If `|L1|` is over budget, recompute from the latest pair `V2 = V1 * budget / |L1|` rounded down, write, read again, at most 3 writes. The reported loss, not our estimate, decides success. If the budget is under-used because rounding went down by a step, that is accepted; the user's risk is never above the budget at the moment of the final read.
- Drift observed on the device: about ±5 % within seconds on a 10-pip stop-loss and about 16 % over a minute on a 20-pip one. In direct mode (D7) a read and a write each take well under a second, so the drift between two attempts of one pass is small; the loop exists to catch the cases where the price still moved.
- Alternative: write once and trust the arithmetic. Rejected: drift of several percent was observed within seconds.

### D4. Module layout
```
app/
  core/            (pure Kotlin, JVM-tested)
    RiskEngine         budget, scaling, verify-loop decisions
    SymbolVolumeRules  per-symbol {min, step = min, max}: floor-to-step, clamp, unknown-symbol result
    AmountParser       "5 000.00 USD" / "-54.51 USC" -> (BigDecimal, currency)
    Preconditions      no SL, wrong side, stale balance, no symbol rule, unreadable -> typed refusals
  mt5/             (Android, MT5-specific)
    Mt5Ids             the only place resource ids live
    UiNode             read-only node view incl. isVisible (live adapter + XML test adapter)
    TicketReader       tree -> Collapsed(symbol, hidden snapshot) | Expanded(snapshot) | Unreadable(field)
                       decides state from visible nodes only; reads the hidden fields behind the strip
    TicketDriver       read, setVolume (direct, no sheet movement); expand and collapse (fallback only)
    CollapseGesturePlanner   swipe geometry, fallback only
  balance/
    BalanceStore       DataStore-backed {value, currency?, seenAt, source}
    BalanceWatcher     listens for Trade tab nodes, updates the store
  rules/
    SymbolRuleStore    DataStore-backed map: exact symbol name -> {min, step, max}
  service/
    RiskAccessibilityService   events, owns TicketDriver, runs passes (mutex)
    BubbleForegroundService    notification, stop action, owns overlay lifetime
  ui/
    BubbleOverlay, RiskPicker, ResultToast (Compose in a ComposeView on WindowManager)
    SetupScreen        permission/status checklist + disclosure
    SettingsScreen     saved symbol volume rules: list, edit, delete
```
Money uses `BigDecimal`, not `Double`, to avoid floor-to-step errors (0.33 vs 0.3299999).

### D5. Node access
Find nodes by `viewIdResourceName` (full `net.metaquotes.metatrader5:id/...`), restricted to the MT5 window. Read text from `AccessibilityNodeInfo.text`. The service config is limited to package `net.metaquotes.metatrader5`, event types needed for window/content changes, `canRetrieveWindowContent`, and `canPerformGestures`.
- Alternative: match by visible label text. Rejected: fragile (language, wording); ids were stable and descriptive in the spike. If an id is missing the reader returns `Unreadable(field)`.

### D6. Write path: `ACTION_SET_TEXT` on `volume_lots_input`
No keyboard, no cursor handling, atomic string. After the action, re-read the node and require equality with the intended string. The device probe showed the plain action is enough: it is accepted, MT5 recomputes loss and margin, and it works on the hidden field of the collapsed sheet. Focus-then-set and clipboard paste also worked but add nothing, so they are not implemented.
- Alternative: tap and type via gestures. Rejected as the primary path: slower, keyboard pops, easy to mistype (adb key injection landed `0.03` for `0.33` in the spike).

### D7. Direct mode first; expand and collapse only as a fallback
Normal path ("direct mode"): the strip is collapsed, so the pass reads the symbol from the visible strip, then reads and writes the ticket fields MT5 keeps behind it (`volume_lots_input`, `sl_input`, `sl_calc_loss`, `free_margin_value`, ...). The sheet never moves, so the user sees only the stop-loss and take-profit lines on the chart change. State is decided from visible nodes only: a collapsed strip has `btn_submit` visible while `volume_lots_input` is not; an off-screen field is never taken as an open ticket.

Fallback path: only when the hidden fields are missing or unreadable (for example an MT5 update stops keeping them), expand with `ACTION_CLICK` on `btn_submit` and wait (bounded, about 2 s) until the volume field is visible, run the pass, then collapse with a `dispatchGesture` swipe at the horizontal centre from just above the sheet header (`symbol_selector` top minus 60 px) down to above the first order control, and wait until `btn_submit` is visible again. Collapse is attempted in a `finally` path for every pass that expanded the sheet. The gesture path is validated against `OrderControlGuard` (24 px margin around `sell_btn`, `buy_btn`, `place_btn` and the one-click toggle). If the user already has the ticket expanded, it is read in place and left expanded.
- Alternative: always expand, run, collapse (the original plan). Rejected after the probe: it makes the sheet slide for about a second on every use and adds a gesture and waits that can fail, for no benefit while the hidden fields are live.
- Alternative: leave the sheet expanded after the pass. Rejected by requirement: the user wants to see only the chart lines move.
- Alternative: GLOBAL_ACTION_BACK to collapse. Rejected: observed to close the keyboard only.

### D8. Balance capture and staleness
`BalanceWatcher` runs on content changes of the MT5 window: when a node with id `balance` is present and parses, it stores `{value, seenAt}`. Currency of the balance is taken from the next ticket read. Persistence in DataStore. Staleness rule: `cachedBalance < freeMargin` (both in account currency) blocks the pass, since free margin cannot exceed balance for a correct account. Manual override is stored with `source=manual` and has the same free-margin check.
- Alternative: use free margin as the base. Rejected by the user: it shrinks with open positions, making risk drift.
- Alternative: read balance from the ticket by opening the Trade tab automatically. Rejected for v1: navigating tabs changes what the user is looking at; revisit later.

### D9. Per-symbol volume rules, learned once
The ticket does not expose a symbol's volume min, max or step, and they differ by instrument class (forex 0.01, stocks and indices such as SP500 0.1). A rule is `{min, step, max}` with `step = min` (forex moves 0.01, 0.02, 0.03; SP500 moves 0.1, 0.2, 0.3) and `max` defaulting to 100, keyed by the exact `symbol_name` text. Names are matched exactly, so `GBPJPY` and `GBPJPY.pc` are separate rules.

The symbol is readable on the collapsed strip, so the rule is resolved before the sheet is expanded. When the symbol has no stored rule, the picker asks once for its minimum lot (0.01 / 0.1 / 1 / custom), saves it in `SymbolRuleStore`, and only then lets the pass continue. No volume is written while a rule is missing. A settings screen lists saved rules with edit (min, max) and delete.

The read-back after writing catches MT5 normalising or rejecting the value (field shows a different number). That fails the pass and offers to re-ask the symbol's rule, instead of assuming the write worked.
- Alternative: one global rule (0.01 / 0.01 / 100). Rejected: it floors an index lot to an invalid step (0.33 on a 0.1-step symbol).
- Alternative: pre-seeded symbol table. Rejected: the user cannot list the symbols and names differ per broker.
- Alternative: infer min from the ticket's starting volume. Rejected: it reflects the user's last lot, not the symbol's limit.
- Alternative: probe by stepping the volume arrows to discover limits. Rejected: it moves the user's field repeatedly; revisit only if asking proves annoying.

### D10. Overlay and lifetime
The bubble is a Compose `ComposeView` added with `WindowManager` as `TYPE_APPLICATION_OVERLAY` (needs "draw over other apps"), owned by a foreground service with a stop action. The accessibility service reports the foreground package so the bubble can hide when MT5 is not on screen. One pass at a time via a mutex; taps during a pass are ignored.
- Alternative: `TYPE_ACCESSIBILITY_OVERLAY` from the accessibility service itself, which removes the overlay permission. Not chosen for v1 to keep the bubble's lifecycle and the stop control independent of the accessibility service; may be revisited if MIUI's overlay permission proves troublesome.

### D11. Testing strategy
- JVM unit tests for `RiskEngine`, `SymbolVolumeRules`, `AmountParser`, `Preconditions` using the numbers observed in the spike (e.g. 5 000 balance, 1 %, loss 1.49 at 0.01 -> 0.33; a 0.1-step symbol floors 3.37 to 3.3; USC suffix; non-breaking space).
- Fixture tests for `TicketReader` using accessibility trees recorded from the device (collapsed strip, expanded Market, expanded pending, Trade tab), plus a collapsed strip with the hidden ticket nodes marked not visible, as observed live.
- Manual device checklist on the demo account for the end-to-end pass, never touching order buttons.

## Risks / Trade-offs

- **MT5 update renames ids** -> `Mt5Ids` is the single point of change; missing ids produce `Unreadable(field)` and no write; a startup self-check can log which ids are missing.
- **Price drift between read and write** -> the verify loop (D3), report the final observed loss, accept a slightly under-used budget. On a tight stop-loss in a fast market a pass can still end over budget after 3 writes; it then reports the last observed loss rather than claiming success.
- **Lot changes under the user's finger** (they tap Buy while a pass is running) -> one-shot only in v1, the pass takes about a second and is user-initiated; the result message shows the final lot. Armed mode (later) must add stricter guards before shipping.
- **An MT5 update stops keeping the ticket in the tree while collapsed** -> the reader sees no readable hidden fields, so the pass takes the expand-and-collapse fallback (D7), and if that cannot read the ticket either it refuses with `Unreadable(field)` and writes nothing. The fallback is tested separately so it is known to work before it is needed.
- **Fallback collapse gesture fails or hits the wrong thing** -> bounds-checked start and end against `OrderControlGuard`, a post-check that `btn_submit` is visible again, otherwise the result says the ticket could not be closed.
- **Balance cache from another account** -> free-margin consistency check and visible balance/age in the picker; wrong but plausible cache (e.g. a larger old balance) remains possible, hence the age display.
- **Wrong symbol rule entered by the user** -> the read-back catches MT5 changing or rejecting the value and offers to re-ask; a rule that is too coarse only under-uses the budget; a rule that is too fine for the symbol risks rejection, which the read-back reports. Rules are editable in settings.
- **Broker symbol name variants** (`GBPJPY` vs `GBPJPY.pc`) -> exact-match keys mean one extra prompt per variant, accepted for simplicity over fuzzy matching.
- **Cent contract differences** -> the loss oracle handles cent accounts without code changes.
- **MIUI kills the service or blocks the overlay** -> setup screen checklist (restricted settings, pop-ups in background, autostart, battery); foreground service with notification.
- **Accessibility service trust and abuse surface** -> package-scoped to MT5, explicit disclosure screen, never actuates order controls (enforced in `TicketDriver` by a deny-list of ids and bounds checks, and covered by tests).
- **Trading risk remains the user's** -> demo-first validation; the app computes size, it does not guarantee outcome.

## Migration Plan

1. Build a debug APK in Android Studio; install on the demo-account phone with `adb install`.
2. Follow the setup screen: allow restricted settings (App info), enable the accessibility service, grant overlay, notifications, pop-ups in background, autostart, unrestricted battery.
3. Run the manual checklist on the demo account; keep it to demo until the checklist passes on several symbols (a JPY pair, gold, an index such as SP500, and the cent account if available). Expect one minimum-lot prompt per new symbol.
4. Rollback: stop the service from the notification, disable the accessibility service, uninstall the app. MT5 and its data are untouched.

## Open Questions

Resolved by the device probe: plain `ACTION_SET_TEXT` is accepted and MT5 recomputes loss and margin (D6); a stop-loss dragged on the chart appears in `sl_input` and `sl_calc_loss` (D7); `sl_calc_loss` exists in Pips mode; MT5 rounds a non-step volume down (the read-back reports it).

- Whether `sl_calc_loss` stays present when the stop-loss is entered in USD (money) mode. The probe could only reach Price and Pips on the Market tab. If it is absent, the pass refuses through the existing unreadable-field path with a message to switch to Price or Pips mode.
- Whether an MT5 build other than 500.6260 keeps the collapsed ticket in the tree. Only this build was probed; the fallback in D7 covers a build that does not.
