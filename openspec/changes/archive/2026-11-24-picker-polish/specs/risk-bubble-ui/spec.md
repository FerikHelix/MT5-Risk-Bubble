# Spec Delta

## MODIFIED Requirements

### Requirement: Ask the symbol's minimum lot when unknown
When the symbol currently shown in MetaTrader 5 has no saved volume rule, the picker SHALL show a symbol page that names the symbol, offers 0.01, 0.1, 1 and a custom value, saves the answer before any pass starts, and closes without saving when dismissed. It SHALL NOT ask again for a symbol that already has a rule.

#### Scenario: First time on a symbol
- **WHEN** the user taps the bubble while SP500 is shown and SP500 has no saved rule
- **THEN** the picker shows a page titled for SP500 with 0.01, 0.1, 1 and custom, and after the user answers 0.1 it shows the risk page

#### Scenario: Known symbol
- **WHEN** the user taps the bubble while GBPJPY is shown and GBPJPY has a saved rule
- **THEN** the picker goes straight to the risk page without asking

#### Scenario: Dismissed without answering
- **WHEN** the user closes the picker without answering the minimum-lot question
- **THEN** no rule is saved, no pass starts and no volume is written

### Requirement: Risk entry in the picker
Tapping the bubble SHALL open the picker on the risk page: one risk field prefilled with the last value used, a percent/currency unit control, one line stating the base and the money at risk, a summary row naming the base in use and the auto-apply state, a close control, and Apply. Tapping Apply SHALL start one lot-sizing pass and close the picker. The page SHALL NOT show preset buttons, a risk-reward control, or the basis, balance and auto-apply controls.

#### Scenario: Prefilled field
- **WHEN** the user reopens the picker after an earlier 1 % pass
- **THEN** the field shows 1 with the percent unit selected, and tapping Apply starts one pass at 1 %

#### Scenario: Custom value
- **WHEN** the user enters 0.75 in the risk field and taps Apply
- **THEN** one pass starts using 0.75 % of the selected base

#### Scenario: Currency unit
- **WHEN** the user switches the unit to USD, enters 60 and taps Apply
- **THEN** one pass starts with a risk budget of 60 in the account currency

#### Scenario: Remembered entry
- **WHEN** the user reopens the picker after using a fixed amount earlier
- **THEN** the field holds the last amount, the USD unit is still selected and the last basis is still selected

#### Scenario: No presets
- **WHEN** the picker opens
- **THEN** the field and its unit control are the only way to enter a risk, and no preset percentage button is shown

### Requirement: Auto-apply toggle in risk picker
The setup page SHALL show an auto-apply switch (default on) that enables re-sizing when the stop-loss moves, and the risk page's Apply control SHALL be visible whether auto-apply is on or off.

#### Scenario: Default on
- **WHEN** the picker opens for the first time
- **THEN** auto-apply is on in the setup page and Apply is visible on the risk page

#### Scenario: Toggled off
- **WHEN** the user switches auto-apply off
- **THEN** moving the stop-loss does nothing and Apply remains the way to run a pass

### Requirement: Prop-firm numbers in the picker
The setup page SHALL let the user enter a starting balance and a maximum drawdown inline, SHALL reject non-positive values and a drawdown not smaller than the starting balance, and SHALL keep the last accepted numbers.

#### Scenario: First use
- **WHEN** the user chooses the drawdown option before any numbers are saved
- **THEN** two fields for starting balance and maximum drawdown are shown on the setup page

#### Scenario: Saved
- **WHEN** the user saves a starting balance of 10 000.00 and a maximum drawdown of 500.00
- **THEN** the fields collapse, the drawdown detail line shows the buffer and the basis applies immediately

#### Scenario: Rejected numbers
- **WHEN** the user enters a maximum drawdown of 0 or one not smaller than the starting balance
- **THEN** both fields are flagged as invalid and nothing is saved

#### Scenario: Changed later
- **WHEN** the user taps Edit beside the saved floor
- **THEN** both fields appear with the saved numbers and saving replaces them

## ADDED Requirements

### Requirement: Amounts stay whole
Every amount the overlay shows SHALL use a non-breaking thousands separator so the value never splits across lines at any font size.

#### Scenario: Narrow card, large amount
- **WHEN** the balance is 1 234 567.89 and the card is narrow
- **THEN** the amount is drawn as one unbroken token rather than a number split over two lines

### Requirement: Sizing summary on the risk page
The risk page SHALL state the base in use, the amount still available on that base and the auto-apply state in one summary row, and SHALL state the risk percentage or fixed amount with the resulting money at risk in one line under the field.

#### Scenario: Balance basis
- **WHEN** the cached balance is 9 600.00, the chosen risk is 1 % and the balance base is selected
- **THEN** the line under the field reads 1 % of 9 600.00 = 96.00 at risk and the summary row names the balance

#### Scenario: Drawdown basis
- **WHEN** the starting balance is 10 000.00, the maximum drawdown is 500.00, the balance is 9 600.00 and the chosen risk is 1 %
- **THEN** the line reads 1 % of 100.00 = 1.00 at risk and the summary row states 100.00 of 500.00 left

#### Scenario: Fixed amount
- **WHEN** the unit is USD and the user enters 50.00
- **THEN** the line under the field reads fixed 50.00 at risk and names the base it was measured against

### Requirement: Setup page
The picker SHALL open the setup page from the risk page's summary row, SHALL return to the risk page on back, SHALL close the picker from either page, and SHALL NOT offer any control that starts a lot-sizing pass.

#### Scenario: Opened from the summary
- **WHEN** the user taps the summary row on the risk page
- **THEN** the setup page is shown with the current base selected and the saved numbers in place

#### Scenario: Back to risk
- **WHEN** the user taps back on the setup page
- **THEN** the risk page is shown again and nothing is written

#### Scenario: No pass from setup
- **WHEN** the setup page is open
- **THEN** no Apply or equivalent control is shown and no pass starts from that page

### Requirement: Risk basis selection
The setup page SHALL present the two sizing bases as one segmented control, SHALL remember the selected base, SHALL open the prop-firm fields when the drawdown option is chosen before numbers are saved, and SHALL disable that option only when no balance is available.

#### Scenario: Drawdown unavailable
- **WHEN** no balance has been read
- **THEN** the drawdown option is disabled with a short hint and the balance base stays selected

#### Scenario: Basis remembered
- **WHEN** the user selects the drawdown base and reopens the picker later
- **THEN** the drawdown base is still selected

## REMOVED Requirements

### Requirement: Risk basis chips
**Reason**: The chips duplicated amounts already stated in the floor line and split values across lines at default font size. The base is now a segmented control on the setup page, and the amounts live in the risk page's summary and explanation lines.
**Migration**: The base is chosen and remembered exactly as before; only the control, its page and the place the amounts appear changed.
