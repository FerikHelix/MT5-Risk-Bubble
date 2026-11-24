# Tasks

## 1. Amounts

- [x] 1.1 Use a non-breaking thousands separator in `BalanceDisplay.formatAmount`; verify a test asserts that a formatted amount contains no regular space and that `1 234.56` is one token, and update the amount expectations in `OutcomeMessageTest`

## 2. Page shell and risk page

- [x] 2.1 Add a `PickerPage` state (`RISK`, `SETUP`, `SYMBOL`) and a page shell whose title row carries back and close, with `openPicker` always starting on `RISK` or `SYMBOL`; verify on the phone that close dismisses from every page and back returns to the risk page without writing
- [x] 2.2 Trim the risk page to field, unit, sentence, Apply and summary row, and add pure `PickerLogic.explanation` and `PickerLogic.summary`; verify `PickerLogicTest` covers the balance, drawdown and fixed-amount sentences plus the summary text
- [x] 2.3 Give the minimum-lot question the page shell with "New symbol" and the symbol name; verify a first run on an unknown symbol shows that page and closing it saves no rule

## 3. Setup page

- [x] 3.1 Move base selection, prop-firm numbers, balance override and auto-apply into labelled sections of the setup page; verify no pass can start from it and the risk page's summary reflects a change after back
- [x] 3.2 Keep Edit and Change inline in their sections; verify on the phone that saving drawdown numbers and clearing a manual balance still work

## 4. Corners and colour

- [x] 4.1 Add one 12dp control shape and apply it to buttons, the text field and the segmented control with flat inner corners, keeping the card at 20dp and the toast at 16dp; verify on the phone that no control is a pill and the segmented row reads as one unit
- [x] 4.2 Add a muted selection colour pair used only by the segmented control; verify `PaletteTest` proves 4.5 : 1 and that the Apply colours are unchanged

## 5. Documentation

- [x] 5.1 Update the README for the two pages, the summary row and the symbol page; verify the described controls match the built picker

## 6. Device verification

- [x] 6.1 On the phone at the default and the largest system font: no amount breaks across lines, both pages fit without scrolling, the summary row opens Setup, and Apply still runs exactly one pass

## Workflow follow-up

- Run `./gradlew testDebugUnitTest` and a device smoke test before archive.
- Archive the change after review.
