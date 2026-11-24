# Spec Delta

## ADDED Requirements

### Requirement: Risk entry in the picker
Tapping the bubble SHALL open a picker with one risk field prefilled with the last value used, a percent/currency unit control, a live amount-at-risk line, a choice between the account balance and the drawdown buffer as the sizing base, and the balance in use with its age. Tapping Apply SHALL start exactly one lot-sizing pass and close the picker. The picker SHALL NOT show preset percentage buttons or any risk-reward control.

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
The risk picker SHALL show an auto-apply checkbox (default on) that enables re-sizing when the stop-loss moves, and the Apply control SHALL be visible whether auto-apply is on or off.

#### Scenario: Default on
- **WHEN** the picker opens for the first time
- **THEN** auto-apply is checked and Apply is visible

#### Scenario: Toggled off
- **WHEN** the user unchecks auto-apply
- **THEN** moving the stop-loss does nothing and Apply remains the way to run a pass

### Requirement: Fixed stop-loss risk mode
The risk picker SHALL allow entering a fixed amount in the account currency as an alternative to a percentage of the selected base.

#### Scenario: Fixed amount entered
- **WHEN** the user enters 60 with the USD unit selected
- **THEN** the pass targets a risk budget of 60 in the account currency

#### Scenario: Fixed mode remembered
- **WHEN** the user reopens the picker after using a fixed amount
- **THEN** the USD unit is still selected with the last amount

### Requirement: Risk basis chips
The picker SHALL show the two sizing bases as chips labelled with the amount each would use, SHALL remember the selected basis, SHALL open the prop-firm fields when the drawdown chip is tapped before any numbers are saved, and SHALL disable that chip only when no balance is available.

#### Scenario: Balance basis
- **WHEN** the cached balance is 9 600.00 and the balance chip is selected
- **THEN** the risk percentage applies to 9 600.00

#### Scenario: Drawdown basis
- **WHEN** the starting balance is 10 000.00, the maximum drawdown is 500.00 and the balance is 9 600.00
- **THEN** the drawdown chip reads 100.00 and the risk percentage applies to 100.00

#### Scenario: Drawdown unavailable
- **WHEN** no balance has been read
- **THEN** the drawdown chip is disabled with a short hint and the balance basis stays selected

#### Scenario: Basis remembered
- **WHEN** the user selects the drawdown basis and reopens the picker later
- **THEN** the drawdown basis is still selected

### Requirement: Prop-firm numbers in the picker
The picker SHALL let the user enter a starting balance and a maximum drawdown inline, SHALL reject non-positive values and a drawdown not smaller than the starting balance, and SHALL keep the last accepted numbers.

#### Scenario: First use
- **WHEN** the user taps the drawdown chip before any numbers are saved
- **THEN** two fields for starting balance and maximum drawdown are shown in the picker

#### Scenario: Saved
- **WHEN** the user saves a starting balance of 10 000.00 and a maximum drawdown of 500.00
- **THEN** the fields collapse, the drawdown chip shows the buffer and the basis applies immediately

#### Scenario: Rejected numbers
- **WHEN** the user enters a maximum drawdown of 0 or one not smaller than the starting balance
- **THEN** both fields are flagged as invalid and nothing is saved

#### Scenario: Changed later
- **WHEN** the user taps Edit beside the saved floor
- **THEN** both fields appear with the saved numbers and saving replaces them

### Requirement: Basis named in result messages
Result messages SHALL name the base the risk was measured against and SHALL NOT describe a drawdown-based risk as a percentage of balance.

#### Scenario: Balance basis message
- **WHEN** a pass on the balance basis writes 0.33 lots at a 48.65 USD loss on a 5 000.00 balance
- **THEN** the message reads "0.33 lots - loss 48.65 USD (0.97 % of balance)"

#### Scenario: Drawdown basis message
- **WHEN** a pass on the drawdown basis writes 0.10 lots at a 0.98 USD loss with a 100.00 buffer
- **THEN** the message states the loss and a percentage of the drawdown buffer, not of the balance

### Requirement: Fixed risk in result messages
Result messages SHALL state a fixed amount as the amount and as the achieved percentage of the selected base, and SHALL NOT claim a percentage the user never chose.

#### Scenario: Fixed risk success
- **WHEN** a pass with a 60.00 budget writes 0.33 lots at a 58.00 loss
- **THEN** the user sees a message stating 60.00 as the risk and the achieved percentage of the base

## REMOVED Requirements

### Requirement: Risk picker
**Reason**: The preset buttons 0.5 %, 1 % and 2 % duplicated the remembered value already shown on the bubble, and the risk-reward controls wrote a take-profit field the user wants to place by hand. Together they made the picker about fourteen controls to answer one question.
**Migration**: Enter the risk in the single prefilled field; the last used percentage is restored from the stored risk, not from a preset. Fixed amounts and the account-balance basis behave as before, and the drawdown basis is new.
