# Tasks

## 1. Project scaffold

- [x] 1.1 Create the Android Studio project (Kotlin, Jetpack Compose, minSdk 26, targetSdk 35, package `com.ferik.mt5risk`) in this repository with the Gradle wrapper using Android Studio's bundled JDK; verify `gradlew assembleDebug` succeeds and produces a debug APK.
- [x] 1.2 Declare the manifest entries for an accessibility service scoped to `net.metaquotes.metatrader5` with `canRetrieveWindowContent` and `canPerformGestures`, the `SYSTEM_ALERT_WINDOW`, foreground-service and notification permissions, and a package-visibility query for MT5; verify `adb install` of the debug APK on the phone succeeds and the service appears under Accessibility settings.
- [x] 1.3 Add a `.gitignore` for Android build output and local files; verify `git status` (once initialised by the user) shows no build artifacts.

## 2. Device probe (de-risk before building the rest)

- [x] 2.1 Implement a throwaway debug-only probe in the accessibility service that logs the MT5 window tree (id, text, bounds, clickable, editable, actions) for the collapsed strip, expanded Market ticket, expanded pending ticket and Trade tab; verify the log from the phone matches the node ids recorded in design.md.
- [x] 2.2 Probe `ACTION_SET_TEXT` on `volume_lots_input` on the demo account with values such as `0.33`; verify the field reads back the value and `sl_calc_loss` and `margin_value` change, and record which variant (plain, focus, paste fallback) is needed; also write a value that is not a multiple of the symbol's step (for example 0.15 on a 0.1-step index) and record whether MT5 rejects or rounds it.
- [x] 2.3 Probe that a stop-loss dragged on the chart shows up in `sl_input` and `sl_calc_loss`, in the hidden fields of the collapsed sheet and in the expanded sheet; verify and record the result, with the precondition-message fallback noted if it does not.
- [x] 2.4 Probe `dispatchGesture` swipe-down collapse from coordinates derived from node bounds, checking the start and end points clear `sell_btn`, `buy_btn` and `place_btn`; verify `btn_submit` reappears and Volume and SL persist after reopening. This path is now the fallback; the normal path never moves the sheet.
- [x] 2.5 Probe whether `sl_calc_loss` exists when the SL entry mode is Pips or USD; verify and record the result so the Pips/USD behaviour in the reader is decided.
- [x] 2.6 Save the recorded trees as test fixtures under `app/src/test/resources/mt5/` (collapsed, expanded Market, expanded pending, Trade tab); verify the files load in a JVM test.
- [x] 2.7 Delete the throwaway probe (`DebugProbe` and its registration in the service) now that its findings are recorded in design.md (hidden live ticket nodes, plain `SET_TEXT`, dragged SL visible, Pips mode, non-step volumes rounded down); verify a release build contains no probe class or `Mt5Probe` logging, for example by searching the built APK's dex.

## 3. Core lot-sizing engine (pure Kotlin)

- [x] 3.1 Implement the amount parser for `5 000.00 USD`, `-54.51 USC` and non-breaking-space variants returning `BigDecimal` and currency; verify unit tests cover thousands separators, negative values, cent currency and garbage input.
- [x] 3.2 Implement the risk budget and scaling `new = current * budget / |loss|` using `BigDecimal`, taking the symbol's volume rule for rounding and clamping; verify unit tests reproduce the spike numbers (5 000 balance, 1 %, loss 1.49 at 0.01 gives 0.33; 0.3356 floors to 0.33; max clamp; min lot above budget produces the warning outcome).
- [x] 3.3 Implement the verify-and-correct decision logic with at most 3 writes, recomputing from the latest volume/loss pair; verify unit tests cover price drift over budget, convergence in one correction, exhaustion after 3 attempts and under-use from rounding.
- [x] 3.4 Implement preconditions (no SL, wrong-side loss, no balance, stale balance below free margin, no volume rule for the symbol, unreadable field, invalid risk percentage outside (0, 100]) as typed refusals with user-facing reasons; verify unit tests assert each refusal and that no write is requested.
- [x] 3.5 Implement `SymbolVolumeRules` (rule `{min, step = min, max = 100}`, exact-name lookup, unknown-symbol result, floor-to-step, clamp to min and max); verify unit tests cover forex 0.01 (0.3356 gives 0.33), an index with min 0.1 (3.37 gives 3.3, 0.06 gives 0.1 with the min-lot warning), the max clamp, exact-match name variants (`GBPJPY` vs `GBPJPY.pc`) and that an unknown symbol requests no write.

## 4. MT5 ticket adapter

- [x] 4.1 Create the single `Mt5Ids` mapping and a `TicketReader` that turns the accessibility tree into a snapshot or an `Unreadable(field)` result, restricted to the MT5 package; verify fixture tests for collapsed, expanded Market, expanded pending and missing-field cases pass.
- [x] 4.2 Implement volume unit mode detection and refuse when it is not Lots; verify a fixture test with a non-Lots mode produces the refusal.
- [x] 4.3 Implement direct read and write of the hidden ticket fields: `TicketReader` returns the collapsed strip together with a snapshot of the fields behind it, deciding state from visible nodes only, and the driver reads and writes them without moving the sheet; keep expand (click `btn_submit`, bounded wait) and collapse (gesture, bounded wait, always attempted when the driver expanded the sheet) as the fallback path. Verify with JVM tests (collapsed fixture with hidden nodes yields a snapshot, an unreadable hidden field yields the fallback signal) and on the demo-account device that a collapsed read returns live values with the sheet unmoved, and that the fallback expands then collapses back to the strip.
- [x] 4.4 Implement `setVolume` with plain `ACTION_SET_TEXT` (the variant task 2.2 chose) on the Volume field, working on the hidden field while collapsed, with read-back confirmation; verify on device that a written 0.33 reads back as 0.33 with the sheet unmoved and a mismatch (for example 0.715 rounded to 0.71 by MT5) is reported as failure.
- [x] 4.5 Read the symbol name from the collapsed strip without expanding the sheet; verify the collapsed-strip fixture test returns `GBPJPY` and that an unreadable symbol produces the unreadable-ticket result.
- [x] 4.6 Add the deny-list guard that rejects any action or gesture targeting or crossing `sell_btn`, `buy_btn`, `place_btn` or the one-click toggle; verify unit tests assert the guard blocks clicks on those ids and gestures whose path intersects their bounds.

## 5. Persistent stores: balance cache and symbol rules

- [x] 5.1 Implement `BalanceStore` (value, optional currency, seen time, source auto/manual) persisted with DataStore; verify unit tests cover save, load after restart and manual override replacement by a newer Trade tab value.
- [x] 5.2 Implement `BalanceWatcher` that captures the `balance` node whenever the Trade tab is visible and ignores unreadable rows; verify with the Trade tab fixture and on device that opening the Trade tab updates the cache.
- [x] 5.3 Implement the free-margin staleness check and the balance/age display text; verify unit tests assert `4 000 < 5 000` is rejected and `5 000 >= 4 439.63` is accepted.

- [x] 5.4 Implement `SymbolRuleStore` persisted with DataStore (save, load after restart, edit min and max, delete); verify unit tests cover persistence across a restart, editing a maximum, deleting a rule so the symbol becomes unknown again, and exact-name keys.

## 6. Pass orchestration

- [x] 6.1 Rework the single-pass state machine so it uses the hidden fields first (read the symbol and resolve its volume rule, read the collapsed ticket's fields, check preconditions, compute, write, verify loop) and falls back to expand, run, collapse only when the hidden fields are unreadable, with a mutex and timeouts; the existing runner and fake-MT5 tests are the starting point. Verify a JVM test with a fake driver walks success with no expand or collapse call, refusal, unknown-symbol (no read, no write), failure, and the fallback path, which always collapses when it expanded, including on timeout and cancellation.
- [x] 6.2 Wire the result into a user-visible outcome (volume, risk %, MT5 loss, currency, % of balance, or refusal reason, plus min-lot and clamp warnings); verify unit tests assert the message text for each outcome.
- [x] 6.3 Run end-to-end on the demo account for a JPY pair, gold and SP500 (or another index) with the collapsed strip showing and an SL set; verify MT5's reported loss ends at or just under 1 % of balance, the written index lot is a multiple of 0.1, the sheet never moved, and no order was placed.

## 7. Bubble and risk picker UI

- [x] 7.1 Implement the foreground service with notification and stop action that owns the overlay lifetime; verify on device that the notification shows and Stop removes the bubble.
- [x] 7.2 Implement the draggable Compose bubble overlay with remembered position, shown only while MT5 is the foreground app; verify on device that it hides in other apps and keeps its position after a restart.
- [x] 7.3 Implement the risk picker (0.5 / 1 / 2 %, custom entry validated to (0, 100], last-used highlight, balance and age line, manual balance entry) and ignore taps during a running pass; verify the JVM tests cover the preset list, custom-value validation and the one-pass-at-a-time rule, and on device that a preset and a custom percentage each start a pass and an invalid custom value cannot be submitted (automated Compose UI tests were deliberately skipped).
- [x] 7.4 Implement the result and error toast shown without opening the ticket, including the rule-mismatch message with its re-set action; verify on device that success, refusal and mismatch messages appear over the chart.
- [x] 7.5 Implement the first-time minimum-lot question in the picker (0.01 / 0.1 / 1 / custom, saved before any pass, never asked again for a known symbol, nothing saved if dismissed); verify the JVM tests cover the first-time, known-symbol and dismissed cases, and on device that a new symbol (an index such as SP500, or another) asks once and a known symbol does not ask again (automated Compose UI tests were deliberately skipped).
- [x] 7.6 Implement the settings screen listing saved symbol rules with edit (min, max) and delete; verify the JVM tests cover rule edit validation, and on device that the list shows the saved rules, an edit changes a rule and a deleted symbol is asked for again (automated Compose UI tests were deliberately skipped).

## 8. Setup screen and documentation

- [x] 8.1 Implement the setup screen with the disclosure text and a status checklist with shortcuts for overlay, accessibility (including allowing restricted settings), notifications, background pop-ups, autostart and battery; verify on the phone that each shortcut opens the right system screen and statuses update when granted.
- [x] 8.2 Block passes until overlay and accessibility are enabled; verify the JVM tests cover the start gating and the refusal when the service is off, and on device that Start is blocked until overlay and accessibility are ready (an automated UI test was deliberately skipped).
- [x] 8.3 Write `README.md` covering build with Android Studio, `adb install`, the MIUI setup steps and the safety guarantees; verify a fresh run through the README on the phone reaches a working bubble.

## 9. Integration checks

- [x] 9.1 Run the manual acceptance checklist on the demo account covering the five acceptance criteria (loss at or under 1 % with the sheet never opened in the normal path; never presses Buy/Sell/Place; refusals with reasons; floor, clamp and min-lot warning; verify loop with at most 3 attempts), plus the fallback path tested separately, forcing the hidden fields to be unreadable and checking the sheet ends collapsed, on both a forex symbol (min 0.01) and an index such as SP500 (min 0.1, stepping 0.1, 0.2, 0.3); record the results in the repository.
- [x] 9.2 Run the edge-case checks: currency suffix and thousands separator, stale balance, SL 0 and wrong side, tiny lot with 2-decimal label rounding, pending versus market ticket, price drift between reads, unknown symbol asks before any write, wrong symbol rule caught by the read-back, stop-loss in Pips mode, a stop-loss dragged on the chart, the ticket already expanded by the user (read in place and left expanded); record results and fix any failure before closing.
- [x] 9.3 Confirm that no network permission is declared and no data leaves the device; verify with `aapt2 dump permissions` on the release APK.

## Workflow follow-up

- Archive the change after the checklist in 9.1 passes on the demo account.
- Plan the armed (auto-follow) mode as a separate change after v1 is stable.
