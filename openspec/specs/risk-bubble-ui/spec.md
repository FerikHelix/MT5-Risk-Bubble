# risk-bubble-ui Specification

## Purpose

Defines the user-facing surface of the companion app: the floating bubble over MetaTrader 5, the risk picker, the feedback shown after a pass, how the bubble stays alive in the background, and the setup the user must complete on the target phone before it works.

## Requirements

### Requirement: Floating bubble over MetaTrader 5
The system SHALL show a draggable floating bubble above other apps while the service is enabled, SHALL remember its position, and SHALL keep it clear of MetaTrader 5's Buy, Sell and Place controls by default.

#### Scenario: Bubble shown
- **WHEN** the service is enabled and MetaTrader 5 is in the foreground
- **THEN** the bubble is visible and can be dragged to a new position that is remembered next time

#### Scenario: Other apps in the foreground
- **WHEN** another app is in the foreground
- **THEN** the bubble is hidden or inert so it does not trigger passes outside MetaTrader 5

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

### Requirement: Manage saved symbol rules
The settings screen SHALL list every saved symbol rule with its minimum, step and maximum, and SHALL let the user edit the minimum and maximum or delete a rule. Deleting a rule SHALL make the picker ask for that symbol again.

#### Scenario: Edit a maximum
- **WHEN** the user changes the maximum of a saved rule from 100 to 50
- **THEN** later passes on that symbol clamp to 50

#### Scenario: Delete a rule
- **WHEN** the user deletes the rule for SP500
- **THEN** the next tap on the bubble while SP500 is shown asks for the minimum lot again

### Requirement: Result and error feedback
After every pass the system SHALL show a short message stating the outcome: the volume written, the risk percentage targeted, the loss MT5 reported and the account currency, or the specific reason nothing was written. The message SHALL be visible without opening the order ticket.

#### Scenario: Success message
- **WHEN** a pass writes 0.33 lots and MT5 reports a loss of 48.65 USD
- **THEN** the user sees a message such as "0.33 lots - loss 48.65 USD (0.97 % of balance)"

#### Scenario: Refusal message
- **WHEN** a pass is refused because no stop-loss is set
- **THEN** the user sees "Set a stop-loss first" and the volume is unchanged

#### Scenario: Rule mismatch message
- **WHEN** a pass fails because MT5 shows a different volume than the one written
- **THEN** the user sees the stored rule and the value MT5 shows, with an action to re-set that symbol's minimum lot

### Requirement: Single pass at a time
The system SHALL run at most one lot-sizing pass at a time and SHALL ignore bubble taps while a pass is running.

#### Scenario: Double tap
- **WHEN** the user taps the bubble again while a pass is in progress
- **THEN** no second pass starts

### Requirement: Stay alive in the background
The service SHALL run as a foreground service with a visible notification so the system does not stop it while MetaTrader 5 is in use, and SHALL give the user a way to stop it from that notification.

#### Scenario: Notification control
- **WHEN** the service is running
- **THEN** a persistent notification is shown with a stop action that removes the bubble

### Requirement: Guided setup on the target device
The app SHALL show a setup screen that reports the status of each required permission and setting, with a shortcut to open the relevant system screen for each: draw over other apps, the accessibility service including allowing restricted settings for a sideloaded app, notifications, background pop-up display, autostart and unrestricted battery use. It SHALL NOT start passes until the overlay and accessibility service are enabled.

#### Scenario: Missing accessibility permission
- **WHEN** the accessibility service is not enabled
- **THEN** the setup screen shows it as missing and offers a shortcut to the accessibility settings, and the bubble does not run passes

#### Scenario: All required permissions granted
- **WHEN** overlay and accessibility are enabled
- **THEN** the setup screen shows them as ready and the bubble can be used

### Requirement: Clear disclosure of what the app does
The setup screen SHALL state that the app reads only MetaTrader 5's screen, never places orders, and only changes the Volume field, before the user enables the accessibility service.

#### Scenario: First launch
- **WHEN** the user opens the app for the first time
- **THEN** the disclosure is shown before the accessibility shortcut

### Requirement: Readable text in the overlay
Every line of text in the bubble, the risk picker, the minimum-lot question and the result message SHALL have an explicit colour that contrasts with its background by at least 4.5 : 1, and the picker title SHALL be visibly larger than its body text. No text SHALL depend on a default or inherited colour.

#### Scenario: Picker title
- **WHEN** the risk picker opens for XAUUSD
- **THEN** the title "Risk for XAUUSD" is light text on the dark card with a contrast of at least 4.5 : 1

#### Scenario: Highlighted preset
- **WHEN** the picker highlights the last used percentage
- **THEN** its text still contrasts with its highlighted background by at least 4.5 : 1

#### Scenario: Bubble label
- **WHEN** the bubble is shown over MetaTrader 5's chart
- **THEN** its label is white on a blue background that contrasts by at least 4.5 : 1

### Requirement: Readable result messages when drawn see-through
Success, warning and error result messages SHALL stay readable at 4.5 : 1 or better against their background even if the system draws the overlay at 80 % opacity over MetaTrader 5's light chart text. The three severities SHALL differ by background colour as well as by wording.

#### Scenario: Success message over the chart
- **WHEN** a pass succeeds and the success message is drawn at 80 % opacity over white chart text
- **THEN** the message text remains readable at 4.5 : 1 or better

#### Scenario: Warning and error messages
- **WHEN** a pass ends with a warning or an error
- **THEN** each message uses its own distinct background colour and meets the same contrast

### Requirement: Dark, readable in-app screens
The app's own screens SHALL always use a dark colour scheme regardless of the phone's theme setting, and every status label, button and body text on them SHALL contrast with its background by at least 4.5 : 1.

#### Scenario: Phone set to light theme
- **WHEN** the phone's theme is light and the user opens the app
- **THEN** the setup and minimum-lot screens still show the dark colour scheme

#### Scenario: Status labels
- **WHEN** the setup screen shows a Ready, Missing or Check yourself status
- **THEN** each label is readable at 4.5 : 1 or better against the screen background

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

### Requirement: Fixed stop-loss risk mode
The risk picker SHALL allow entering a fixed amount in the account currency as an alternative to a percentage of the selected base.

#### Scenario: Fixed amount entered
- **WHEN** the user enters 60 with the USD unit selected
- **THEN** the pass targets a risk budget of 60 in the account currency

#### Scenario: Fixed mode remembered
- **WHEN** the user reopens the picker after using a fixed amount
- **THEN** the USD unit is still selected with the last amount

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
