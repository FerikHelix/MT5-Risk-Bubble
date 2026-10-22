# Spec Delta

## Purpose

Defines the user-facing surface of the companion app: the floating bubble over MetaTrader 5, the risk picker, the feedback shown after a pass, how the bubble stays alive in the background, and the setup the user must complete on the target phone before it works.

## ADDED Requirements

### Requirement: Floating bubble over MetaTrader 5
The system SHALL show a draggable floating bubble above other apps while the service is enabled, SHALL remember its position, and SHALL keep it clear of MetaTrader 5's Buy, Sell and Place controls by default.

#### Scenario: Bubble shown
- **WHEN** the service is enabled and MetaTrader 5 is in the foreground
- **THEN** the bubble is visible and can be dragged to a new position that is remembered next time

#### Scenario: Other apps in the foreground
- **WHEN** another app is in the foreground
- **THEN** the bubble is hidden or inert so it does not trigger passes outside MetaTrader 5

### Requirement: Risk picker
Tapping the bubble SHALL open a picker with the presets 0.5 %, 1 % and 2 % and a custom percentage entry, together with the balance in use. Choosing a value SHALL start exactly one lot-sizing pass and close the picker.

#### Scenario: Preset chosen
- **WHEN** the user taps the bubble and chooses 1 %
- **THEN** one pass starts using 1 % and the picker closes

#### Scenario: Custom value
- **WHEN** the user enters 0.75 as a custom percentage and confirms
- **THEN** one pass starts using 0.75 %

#### Scenario: Remembered choice
- **WHEN** the user reopens the picker after an earlier pass
- **THEN** the last used percentage is highlighted

### Requirement: Ask the symbol's minimum lot when unknown
When the symbol currently shown in MetaTrader 5 has no saved volume rule, the risk picker SHALL first ask for that symbol's minimum lot, offering 0.01, 0.1, 1 and a custom value, and SHALL save the answer before any pass starts. It SHALL NOT ask again for a symbol that already has a rule.

#### Scenario: First time on a symbol
- **WHEN** the user taps the bubble while SP500 is shown and SP500 has no saved rule
- **THEN** the picker asks "Minimum lot for SP500?" with 0.01, 0.1, 1 and custom, and after the user answers 0.1 it shows the risk percentages

#### Scenario: Known symbol
- **WHEN** the user taps the bubble while GBPJPY is shown and GBPJPY has a saved rule
- **THEN** the picker goes straight to the risk percentages without asking

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
