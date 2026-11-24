# Design

## Context

See proposal.md for motivation. Relevant current state: the picker was rebuilt in this change with preset chips, a fixed-USD mode and risk-reward; `RiskReward` and `TakeProfitPlanner` exist only in the working tree. `PassRunner` derives the budget as `risk → budget(balance)` and refuses a pass when the cached balance is below free margin (`Preconditions.StaleBalance`). The balance comes from `BalanceStore`, captured off the Trade tab or set manually. `RiskEngine` only needs a budget and MT5's reported loss, so where the budget comes from is a caller concern.

## Goals / Non-Goals

**Goals:** shrink the picker to one field plus a basis choice; let a prop-firm trader size from the drawdown buffer without arithmetic; keep the balance as a trust check; remove risk-reward from every layer and from the specs.

**Non-Goals:** no daily-loss-limit tracking, no trailing-drawdown tracking, no equity reads, no prop-firm settings screen in the app (the numbers live in the picker), no cloud or account sync, no change to auto-apply behaviour or to the Volume-only write surface beyond dropping the TP write.

## Decisions

### D1. SL detection rides the existing 800 ms foreground poll
Unchanged from the earlier revision: `followForeground()` extends to read the ticket only while auto-apply is on, a saved risk exists, the picker is closed and no pass runs. Keep `slPrice` and symbol, debounce 700 ms, re-seed after a pass and after the picker closes. Alternative (a second 2 s timer) rejected: it doubles accessibility reads and can run outside MT5.

### D2. A pure `RiskBasis` resolves the budget base
Add `core/RiskBasis.kt` with no Android imports:

```
PropFirm(startBalance, maxDrawdown)
floor   = startBalance - maxDrawdown
buffer  = balance - floor                  (>= 0, never negative)
base    = min(buffer, maxDrawdown)         (capped, see D3)
kind    = BALANCE | DRAWDOWN
```

`RiskSetting.budget(base)` keeps its signature, so `RiskEngine` and `VerifyLoop` are untouched. Alternatives: a branch inside `PassRunner` (harder to unit-test, mixes presentation and sizing) and a new `RiskEngine` parameter (changes a well-tested pure function for no gain). Rejected both.

### D3. Cap the base at the maximum drawdown
The user's stated model is "a fraction of the drawdown the firm gave me". Uncapped, profit above the starting balance inflates the buffer (1 500 above the floor on a 500 drawdown) and turns 1 % into 3 % of the firm's actual limit. Capping keeps the dollar risk identical to the start of the challenge while the account is in profit, and shrinks it on the way to the floor. Alternative: no cap, rejected as an unintended leverage increase.

### D4. The balance stays a trust check, not the budget source
`Preconditions.afterRead` keeps receiving the cached balance and free margin, and `StaleBalance` still refuses when the cached balance is below free margin — a wrong balance would make the buffer look bigger than it is. Only the budget comes from the base. `PassOutcome.Success` replaces `balance` with a base value plus a `BaseKind`, and `OutcomeMessage` prints "of balance" or "of drawdown left" from that kind. Alternative: overwrite the balance with the drawdown figure, rejected because it poisons the free-margin check and the stored Trade tab value.

### D5. Remove risk-reward in all four places it lives
1. Model: delete `RiskReward`, `TakeProfitPlanner`, `TpPlan`, `Refusal.RiskRewardUnavailable`.
2. Driver: drop `setTakeProfit` from the interface and the accessibility implementation; leave `Mt5Ids.TP_INPUT` unused but present.
3. Reading: drop `tpPrice`/`tpProfit` from `TicketSnapshot` and their parse from `TicketReader`; the spec again claims the app only changes Volume, so keeping dead reads would be dishonest.
4. Callers and UI: drop the `takeProfit` parameter through `AppGraph.runPass`, `PassRunner`, `OverlayController`; delete the RR row, the `savedRiskReward` mirror and the `riskReward` preference.

Delete the `mt5-ticket-automation` delta rather than writing a REMOVED requirement, because the requirement never reached the main spec.

### D6. One field, one unit control, two basis chips
The field is prefilled from the last risk, so the common path is bubble → Apply. The unit is an inline `% | USD` toggle inside the field row rather than a second row of mode buttons: fixed USD is still useful (a flat amount unrelated to the buffer) and costs no vertical space. The basis chips carry the amount each would use (`Balance 9 600`, `DD left 100`), which removes a dropdown and any hidden state. In USD mode the chips stay visible but inert, so the buffer number is still readable as a ceiling.

Apply becomes always visible and the auto-apply checkbox moves to a compact switch on the last row, with "Change balance" beside it. This deletes `PickerLogic.showApply`, `RISK_PRESETS` and `isLastUsed`; the bubble label still shows the last percentage, so the preset highlight has no remaining job.

### D7. Prop-firm numbers live inline in the overlay
Tapping the drawdown chip without saved numbers expands two fields (starting balance, maximum drawdown) inside the card. The picker already has a 45 s idle close, Back/Home dismiss and IME resize, so a short entry fits; a separate app screen would send the user out of MT5 mid-trade. Validation is `> 0` for both and `maxDrawdown < startBalance`; failures flag the fields and save nothing. Alternative: a "Prop firm" tab in the app, rejected by the user's choice to keep it in the overlay.

### D8. Warn-only guardrail, refuse only at zero
A fixed amount larger than the buffer gets an inline amber line (`50.00 USD is more than the 20.00 left before the floor`) and still applies; a percentage cannot exceed the base. At a zero buffer the drawdown chip reads `DD left 0`, Apply is disabled and `Preconditions` refuses, so an automatic pass cannot fire either. Alternative: block whenever the budget exceeds the buffer, rejected because it leaves no way to act on a bad day and the user asked for warn.

## Risks / Trade-offs

- **Balance lags equity** → buffer ignores floating loss on open trades, so the shown room is optimistic while a position is live; the field label shows the balance age and the Trade tab capture refreshes it. Documented, not fixed in this change.
- **Static drawdown only** → on trailing-DD accounts the floor rises with the peak and the stored numbers go stale; the user updates them by hand. A peak tracker is a separate capability.
- **Cached balance can be stale** → `StaleBalance` still refuses; the basis never bypasses it.
- **Removing TP reads could break fixtures** → `TicketReaderTest` and `FakeMt5` currently expose TP values; prune them and run the full unit suite in the same step.
- **Delete-before-add ordering** → removing `RiskReward` first leaves the picker temporarily referring to a missing type; do the model/driver/reader sweep, then the callers, then the UI, compiling after each group.
- **Basis default when numbers are missing** → `BALANCE`, so an existing user sees no change until they configure drawdown.

## Migration Plan

Stored prefs: `risk_reward` becomes unread and is left in place (harmless), `risk_mode`, `last_risk`, `fixed_risk` and `auto_apply` keep their meaning; new keys are `prop_firm_start_balance`, `prop_firm_max_drawdown`, `risk_basis`. Nothing to migrate: with no prop-firm numbers the app behaves exactly as before. Rollback is a code revert; the new keys are ignored by the old build.

## Open Questions

None.
