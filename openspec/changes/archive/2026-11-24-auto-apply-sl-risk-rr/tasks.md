# Tasks

## 1. Remove risk-reward

- [x] 1.1 Delete `RiskReward`, `TakeProfitPlanner`, `TpPlan` and `Refusal.RiskRewardUnavailable`; verify `./gradlew testDebugUnitTest` compiles after the model sweep
- [x] 1.2 Drop `setTakeProfit` from `TicketDriver`/`AccessibilityTicketDriver` and `tpPrice`/`tpProfit` from `TicketSnapshot`/`TicketReader`; verify `TicketReaderTest` passes with the take-profit assertions removed
- [x] 1.3 Remove the `takeProfit` parameter through `PassRunner`/`AppGraph`/`OverlayController` and the TP cases in `PassRunnerTest`/`FakeMt5`; verify `PassRunnerTest` covers a volume-only pass
- [x] 1.4 Drop the `riskReward` preference, the `savedRiskReward` mirror, the RR row and its handler; verify `rg "RiskReward|TakeProfit|setTakeProfit"` finds nothing and the unit suite passes

## 2. Sizing base

- [x] 2.1 Add `core/RiskBasis.kt` (floor, buffer, cap at the maximum drawdown, exhausted) and verify `RiskBasisTest` covers start of challenge, after a loss, above the starting balance and at the floor
- [x] 2.2 Resolve base plus kind in `PassRunner` while `Preconditions` still receives the real balance and free margin; verify `PassRunnerTest` covers a balance pass, a drawdown pass and an exhausted-buffer refusal
- [x] 2.3 Carry the base kind into `PassOutcome.Success` and `OutcomeMessage`; verify `OutcomeMessageTest` prints "of balance", "of drawdown left" and the fixed-amount wording

## 3. Preferences

- [x] 3.1 Add `propFirmStartBalance`, `propFirmMaxDrawdown` and `riskBasis` (defaults null, null, `BALANCE`) to `UiPrefs`; verify a new `UiPrefsTest` on `InMemoryPreferencesDataStore` covers defaults and round-trip

## 4. Picker

- [x] 4.1 Replace the preset buttons and RR row with one prefilled risk field, the inline `% | USD` unit control and the live amount-at-risk line; verify `PickerLogicTest` covers parsing, unit mapping and the amount label
- [x] 4.2 Add the basis chips with their amounts and the inline prop-firm block; verify `PickerLogicTest` covers chip availability, rejected numbers and the chip labels
- [x] 4.3 Keep Apply always visible and move auto-apply to a switch; remove `RISK_PRESETS`, `isLastUsed` and `showApply`; verify `PickerLogicTest` covers the staged-then-Apply path with auto-apply off
- [x] 4.4 Wire `OverlayController` to save the numbers and basis, restart the idle timeout and show the over-buffer warning; verify by reading the picker flow end to end in code review

## 5. Documentation

- [x] 5.1 Rewrite the README risk section for one field plus basis, the drawdown cap and the static-drawdown/balance-not-equity caveats, and remove every risk-reward mention; verify the described steps match the built picker

## 6. Auto-apply (completed earlier in this change)

- [x] 6.1 Add a pure `SlChangeDetector` (baseline, symbol change, epsilon diff, debounce) with unit tests for seed, same value, move, symbol change and rapid changes
- [x] 6.2 Wire the detector into `OverlayController.followForeground()`, gated on auto-apply, a saved risk, the picker closed and no pass running
- [x] 6.3 Verify on device: auto-apply on, drag the stop-loss → one pass; off → none until Apply; none while the picker is open; exhausted buffer → refusal message and no write

## Workflow follow-up

- Run `./gradlew testDebugUnitTest` and a device smoke test before archive.
- Archive the change after review.
