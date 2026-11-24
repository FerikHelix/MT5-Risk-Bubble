# Proposal

## Why

The risk picker grew to about fourteen controls, and risk-reward taught the app to write a field the user prefers to place by hand. At the same time, a percentage of account balance means little on a prop-firm account, where failure is a fixed drawdown line rather than the account going to zero. Both are worth fixing before this change is archived: removing a feature that was only ever added in this working tree is a delete today, and a second change after archive.

## What Changes

- **Remove risk-reward entirely**: the picker's RR row, the take-profit write it needed, and the take-profit reads that fed its verification. The app writes the Volume field and nothing else again.
- Replace the 0.5 % / 1 % / 2 % preset buttons and the RR row with **one risk field** that opens prefilled with the last value, plus a small %/USD unit control and a live amount-at-risk line.
- Add a **risk basis** choice shown as two chips under the field: the account balance, or the **drawdown buffer** still left above a prop-firm floor.
- Let the user enter **prop-firm numbers** (starting balance and maximum drawdown) inline in the picker. The floor is `starting balance − maximum drawdown`; the buffer is the balance above that floor, capped at the maximum drawdown; the risk budget is the chosen percentage of that buffer.
- **Refuse** a pass when the buffer is gone, and **warn** (never block) when a fixed amount would risk more than the buffer.
- Show **Apply always**, so changing a risk is one predictable action; auto-apply on stop-loss move is unchanged.
- **BREAKING** (internal): the sizing base replaces the balance as the base of the percentage, and result messages must name which base was used.

## Capabilities

### New Capabilities
- `auto-apply-sl-risk`: detect stop-loss movement and re-size the lot according to the saved risk setting. Already specified in this change; unchanged by this revision.

### Modified Capabilities
- `risk-bubble-ui`: the risk picker becomes one prefilled field with a unit control and a basis choice; presets and risk-reward disappear; prop-firm numbers are entered in the picker; result messages name the basis.
- `risk-lot-sizing`: the risk percentage applies to a sizing base (balance or drawdown buffer) instead of always to the balance, and a pass is refused when the buffer is exhausted.

## Impact

Affected: `core/RiskSetting.kt` (drop `RiskReward`), `mt5/TakeProfitPlanner.kt` (delete), `TicketDriver`/`AccessibilityTicketDriver` (drop `setTakeProfit`), `TicketReader`/`TicketSnapshot` (drop take-profit reads), `PassRunner`/`PassOutcome`/`OutcomeMessage` (drop the TP step, carry the base kind), `core/RiskBasis.kt` (new), `Preconditions` (refuse an exhausted buffer), `PickerLogic`/`OverlayContent`/`OverlayController` (picker rewrite), `UiPrefs` (prop-firm numbers and basis; drop RR). The `mt5-ticket-automation` spec delta is deleted, since the ticket surface returns to Volume-only. No new dependencies.
