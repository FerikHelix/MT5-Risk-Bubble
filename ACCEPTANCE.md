# Acceptance results

Two runs are recorded here. The **2026-11-24** run covers the current two-page picker and the drawdown basis; the **2026-10-22** run still holds the instrument matrix for the unchanged sizing core.

## Run 2026-11-24 - risk/setup picker and drawdown basis

Environment: Redmi Note 11 (2207117BPG), Android 13, MIUI; MetaTrader 5 build 500.6260 on a demo USD account with a 5 776.77 balance. Build: **release 0.1.0**, signed with the debug key and installed over the debug build, so saved data survived. 289 JVM unit tests pass (`gradlew testDebugUnitTest`).

The picker was rebuilt for this run: a **risk page** (field, unit, one sizing sentence, Apply, summary row), a **setup page** (base, balance, auto-apply; no Apply), and a separate **New symbol** page. The sizing core (scaling, rounding, verify loop, preconditions) is unchanged and remains covered by the 2026-10-22 run below and by the unit tests.

| Check | Result | Evidence |
|---|---|---|
| Risk page | Pass | Title and close, balance line, prefilled field, `[% | USD]` segmented control, `1 % of 5 776.77 USD = 57.77 USD at risk`, one-line summary, blue Apply |
| Apply runs exactly one pass | Pass | `0.10 lots - loss 54.37 USD (0.94 % of balance)`; MT5's own SL label moved from 11.36 to 54.18 and the volume from 0.01 to 0.10 |
| Setup page | Pass | BASE / BALANCE / AUTO-APPLY sections, segmented base with a check, Edit and Change inline, auto-apply switch, **no Apply anywhere**; Back returns to the risk page without writing |
| Summary row | Pass | Tapping it opens Setup; after a change it reads `Balance 5 776.77 - auto-apply on`; no basis, balance or auto-apply control on the risk page |
| Manual balance | Pass | Saved 5 776.77 and the label became `Balance 5 776.77 (manual)`; the drawdown option was disabled while no balance was known |
| New symbol page | Pass | First use on BTCUSDm showed `New symbol`, the symbol, 0.01 / 0.1 / 1 and custom; saving 0.01 went straight to the risk page |
| Amounts stay whole | Pass | Non-breaking thousands separator; no value split at the default font or at system font scale 1.3; the sentence and summary wrap only at word boundaries |
| Corner scale | Pass | 20dp card, 12dp field, buttons and segmented control with flat inner corners, no pills; 16dp toast |
| Drawdown basis | Pass | Verified the same day on the preceding build (math unchanged): start 6000 / drawdown 5000 gives floor 1 000.00 and `DD left 4 776.77`, 1 % = 47.77; the pass wrote `0.02 lots - loss 37.56 USD (0.79 % of drawdown left)`. A manual balance of 900 gave `DD left 0`, a disabled Apply and `Drawdown floor reached - no room left` |
| Auto-apply | Pass | Verified the same day on the preceding build (code unchanged): on, an SL drag ran exactly one pass; off, dragging did nothing until Apply |
| Largest system font | Pass | Font scale 1.3: the card fits without scrolling, Apply stays reachable, and no amount breaks across lines |

Two layout defects were found by looking at the rendered screen and fixed before the release: the auto-apply label ran into its switch (now 8dp apart), and the no-balance summary wrapped onto a second line (now `No balance - auto-apply on`).

Not covered by this run:

- The picker in **landscape**: the phone rotated during the session and MT5's landscape strips did not expose the symbol consistently, so the overlay was only exercised in portrait.
- The 45 s idle close was not re-timed on this build (the code path is unchanged).
- Xiaomi pop-up, Autostart and battery settings, unchanged from the 2026-10-22 run.

## Run 2026-10-22 - lot sizing, first build

Run on 2026-10-22 on the target phone (Redmi Note 10S, Android 13, MIUI 14) against a **MetaTrader 5 demo account** (build 500.6260, USD account, balance 5 000.00). The app was the final debug build, after the throwaway probe was removed. 235 JVM unit tests also pass (`gradlew testDebugUnitTest`).

## The five acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | With the order strip showing and an SL set, tapping the bubble and choosing a risk leaves MT5's own loss at or under that percentage of the balance, within one volume step, and the sheet never opened | Pass | GBPJPY 1 % gave `0.38 lots - loss 48.82 USD (0.98 %)`, MT5's chart label agreed (-48.82). USDJPY `0.55 lots, 49.38 USD (0.99 %)`. XAUUSD `0.09 lots, 47.43 USD (0.95 %)` and `0.08 lots, 47.52 USD (0.95 %)`. Custom 0.75 % gave `0.36 lots, 36.46 USD (0.73 %)`. The sheet stayed collapsed in every normal run |
| 2 | Never presses Buy, Sell or Place | Pass | After all runs the Trade tab has no positions and History shows only the 5 000 deposit (profit 0.00). The fake-MT5 tests assert that a pass only expands, writes and collapses, and the order-control guard has its own tests |
| 3 | Writes nothing and explains when preconditions fail | Pass | No ticket: "Open the order ticket first". No stop-loss: "Set a stop-loss first". Manual balance 4 000 below free margin 5 000: "Saved balance 4000 is below free margin 5000.00 - open the Trade tab to refresh it" (lot unchanged). Unknown symbol: asks for the minimum lot before anything is written |
| 4 | Lots round down to the symbol's step, clamp to the maximum, and warn when the minimum lot already exceeds the risk | Pass | Whole-lot index (Nikkei225): `2 lots, 34.39 USD (0.69 %)` from 2.9 allowed. Cap: `100.0 lots ... Capped at the maximum volume` (amber). Minimum lot: `1 lots, 17.48 USD (0.35 %). Minimum lot already risks more than your 0.01 %` (amber) |
| 5 | Re-reads after writing, corrects down, stops after 3 attempts, and the sheet always ends as found | Pass | The correction loop and the 3-attempt stop are covered by the verify-loop and pass-runner tests with drifting prices. On the device the loss drifted several percent between reads and every pass finished under budget. The fallback (hidden fields forced unreadable) expanded the sheet, wrote 2.00 -> 0.40 lots and collapsed it again (`0.40 lots, 48.84 USD`) |

## Instruments covered

| Instrument | Minimum lot / step | Notes |
|---|---|---|
| GBPJPY, USDJPY (JPY pairs) | 0.01 | Loss converted to USD by MT5 |
| XAUUSD (gold) | 0.01 | Labels with a thousands separator, `-1 264.00 USD`, parsed correctly |
| Nikkei225 (index CFD in JPY) | **1** | MT5 rounded a 0.1-step lot (1.2) down to 1, and the read-back reported it; after correcting the rule to 1 the app wrote whole lots |

## Edge cases

| Case | Result |
|---|---|
| Currency suffix and thousands separator | Pass: `USD`, space thousands separator (`5 000.00`, `-1 264.00 USD`) on the device; `USC` and non-breaking spaces in unit tests |
| Stale balance | Pass (see criterion 3) |
| SL = 0 | Pass: "Set a stop-loss first" |
| SL on the wrong side | Unit-tested only (could not be produced on the demo ticket) |
| Tiny lot and 2-decimal label rounding | Pass: Nikkei225 at 0.01 % wrote the minimum lot with the warning |
| Pending ticket vs Market ticket | Pass: Sell Limit ticket gave `2 lots, 34.94 USD (0.70 %)`; Market tickets throughout |
| Price drift between reads | Pass: loss moved 5 to 16 % within a minute on tight stops; passes still ended under budget |
| Unknown symbol asks before any write | Pass for 5 symbols; deleting a rule makes it ask again |
| Wrong symbol rule caught by the read-back | Pass: `MT5 shows 0.44 instead of 0.443` (USDJPY, rule 0.001) and `MT5 shows 1 instead of 1.2` (Nikkei225, rule 0.1), each with a re-set button |
| Stop-loss in Pips mode | Pass: SL `3000` pips, loss label read, `13 lots, 2.48 USD (0.05 %)` |
| Stop-loss dragged on the chart | Pass: the hidden `sl_input` and loss followed the drag |
| Ticket already expanded by the user | Pass: read in place and left expanded |

## Other behaviour checked on the device

- The picker closes on Back, Home and after 45 s idle, and never lingers over another app. The bubble is hidden outside MetaTrader 5.
- The bubble can be dragged and returns to its place after the service is stopped and restarted. A plain tap still opens the picker.
- The notification shows "MT5 Risk Bubble is on" with a Stop action; Start is blocked until overlay and accessibility are ready.
- Setup shortcuts open the right Xiaomi screens (app permissions, Autostart, battery list).
- The release APK declares no network, location or storage permissions and contains no probe code.

## Not verified

- Stop-loss entered in **USD** (money) mode: only Price and Pips were reachable on the Market ticket.
- A **fresh sideload** through the "Allow restricted settings" step: during testing the accessibility service was enabled with `adb`, which bypasses that Android prompt.
- The Xiaomi **pop-up** and **Autostart** switches, and battery "no restrictions", which the app cannot detect (it shows "Check yourself"). Long-running behaviour with the screen off was not tested.
- Other MetaTrader 5 builds. Only 500.6260 was probed.

## Things to know

- Saved minimum lots and the cached balance are wiped if the app is uninstalled. Reinstalling with `adb install -r` keeps them.
- MIUI resets the "draw over other apps" permission when the app is reinstalled; set it again from the Setup screen.
