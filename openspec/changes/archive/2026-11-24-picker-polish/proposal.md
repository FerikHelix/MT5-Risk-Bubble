# Proposal

## Why

The picker mixes two tempos in one card: the risk value you set on every trade, and the setup you touch once per challenge. A screenshot at default font size shows the result — `Balance 5` / `776.77` and `of 5` / `000.00` break inside numbers, amounts are repeated on chips and again in the floor line, three footer rows stack up, bright green selection pills compete with the blue Apply, and the corner radii disagree (pill buttons around a nearly square field).

## What Changes

- **Split the picker by tempo** into two pages of one card. The **risk page** keeps only what a trade needs: the prefilled field, the `% | USD` unit, one sentence stating the base and the money at risk, Apply, and one summary row (`Drawdown 4 776.77 left · auto-apply on >`). The **setup page** holds the base segmented control, the prop-firm numbers with Edit, the balance override with Change, and the auto-apply switch. Setup never offers a way to start a pass.
- Give the **minimum-lot question its own page** with the same shell ("New symbol", the symbol in the title, a close control), instead of pretending to be the risk page.
- **One corner scale**: card 20dp, controls (buttons, field, segmented control) 12dp, toast 16dp. Material's defaults (pill buttons, 4dp fields) are replaced by one shared control shape.
- **Non-breaking thousands separator**, so an amount is always one token (`5 776.77` never splits).
- **Quieter hierarchy**: the selected segment is a muted blue tint, Apply stays the only saturated action, and green returns to meaning Ready/success.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `risk-bubble-ui`: the picker becomes a risk page plus a setup page reached from a summary row; the symbol question becomes its own page; basis, balance and auto-apply controls move off the risk page; amounts must not break inside a value.

## Impact

`OverlayContent` (page shell and both pages), `OverlayController` (page state, back and close), `PickerLogic` (sentence and summary text), `BalanceDisplay` (separator), `Palette`/shape constants, and their tests, plus the README. No dependency, data or sizing-math changes; `risk-lot-sizing` and `auto-apply-sl-risk` are untouched.
