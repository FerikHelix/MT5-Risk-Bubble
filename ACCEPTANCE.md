# Acceptance results

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
