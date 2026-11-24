# Design

## Context

See proposal.md for motivation. The picker is an overlay window over MetaTrader 5 with a 45 s idle close, and the pass it triggers reads the ticket through the accessibility service. Everything it shows today lives in one column: risk field, unit pills, preview, two amount chips, floor line, Apply, Change balance, auto-apply, Close. The window is already a single `PickerUi.ChooseRisk` state rebuilt by `OverlayController.chooseRisk`.

## Goals / Non-Goals

**Goals:** split the card by tempo (trade vs setup) without a second window; make the trade path two taps; give the symbol question the same shell; one corner scale; amounts that cannot break; a selection colour that does not compete with Apply.

**Non-Goals:** no new settings for the app screen, no symbol-rule editing in the overlay, no change to sizing, preconditions, auto-apply detection or stored preferences, no tabs, no third window.

## Decisions

### D1. Two pages in one window, not tabs or a second overlay
Add a `PickerPage` state (`RISK`, `SETUP`, `SYMBOL`) to the picker window. `openPicker` always starts at `RISK` (or `SYMBOL` when the rule is missing); the setup page is reached from the summary row and left with back. Alternatives: Material tabs promote a rarely used page to equal weight; a second overlay window would double the lifecycle, dismissal and focus handling that `OverlayController` already carries; a multi-step wizard would make the trade path depend on state left behind by a previous visit.

### D2. The risk page holds only what a trade needs
Field, `% | USD` unit, one sentence, Apply, summary row, close. Apply exists only on this page, so no setup interaction can start a pass. The sentence is a single line built by a pure function (`PickerLogic.explanation`) so the arithmetic shown can be unit-tested without Compose.

### D3. The summary row is the setup entry point and the state readout
`Drawdown 4 776.77 left · auto-apply on >` — one row, tappable, carrying the two facts that can surprise a user who forgot them: which base the percentage applies to and whether the pass can fire on its own. Keeping it on the risk page is what makes hiding the controls acceptable; without it the split would hide state.

### D4. Setup page is sectioned, and has no primary action
Three labelled sections: Base (segmented control, floor/detail line, Edit), Balance (value, age, Change), Auto-apply (switch). `Back to risk` is a quiet button; the title row carries back and close. Alternative: keep Apply on both pages, rejected because a settings page must not place a trade and because two primary actions invite the wrong one.

### D5. The symbol question reuses the page shell
`New symbol`, the symbol name, one sentence of why, the four choices, custom field, Save. Same title row, same close. Saving moves to `RISK`; closing saves nothing. The page exists in the layout but is entered only from `PickerLogic.stepFor`, so no navigation control is needed.

### D6. One corner scale
```
card                20dp
controls            12dp   buttons, text field, segmented outer edge
segmented inner      0dp
toast               16dp
bubble              circle (unchanged)
```
Material defaults are the problem: `Button` is a full pill, `OutlinedTextField` is 4dp, `SegmentedButton` rounds every segment. Define one `controlShape` constant and pass it explicitly at each call site (`SegmentedButtonDefaults.itemShape(..., baseShape = controlShape)`), rather than restyling components globally. Alternatives: pills everywhere (recreates the clash with the field), 8dp everywhere (flatter, but the card then looks under-rounded at 20dp).

### D7. Selection is quieter than action
The selected segment uses a muted blue container with the normal text colour; Apply keeps the single saturated primary. Green stays reserved for Ready/success. The pair goes into `Palette` with its own contrast test at 4.5 : 1, matching how the rest of the palette is guarded.

### D8. Non-breaking separator belongs in formatting, not layout
`BalanceDisplay.formatAmount` switches its thousands separator to `\u00A0`. One change fixes every amount in the picker, the toasts and the app screens, and no composable needs `maxLines` tricks. Tests that assert amounts must use the same character.

## Risks / Trade-offs

- **Hidden state after the split** → the summary row states base, remaining amount and auto-apply on the risk page; the sentence names the base in the arithmetic.
- **An extra tap for auto-apply or balance** → accepted; both are rare compared with Apply. The summary row keeps them one tap closer than a settings screen would.
- **Page state leaking between visits** → `openPicker`/`closePicker` reset to `RISK`; the setup edit block is local state that starts collapsed.
- **Wrapping at the largest system font** → non-breaking amounts plus shorter labels; verify at the largest font on the device, not just default.
- **Segmented control does not scale past two options** → acceptable today; a third base would become a list, not a segment.
- **Muted selection failing contrast** → `PaletteTest` covers the pair before any device check.

## Migration Plan

None. No preference keys change, no stored value is read differently; a user who never opens the setup page sees the same behaviour as before, with a smaller card.

## Open Questions

None.
