# auto-apply-sl-risk Specification

## Purpose

Automatically re-sizes the lot when the stop-loss moves on the chart, according to the saved risk setting, so the intended risk is preserved without manual action.

## Requirements

### Requirement: Auto-apply toggle
The system SHALL provide a toggle (default on) that enables automatic re-sizing when the stop-loss changes, and SHALL remember its value across app restarts.

#### Scenario: Default on
- **WHEN** the user has never changed the setting
- **THEN** auto-apply is enabled

#### Scenario: Toggled off
- **WHEN** the user disables auto-apply
- **THEN** moving the stop-loss does not trigger a pass

#### Scenario: Remembered
- **WHEN** the user enables or disables auto-apply and restarts the app
- **THEN** the setting is the same as before the restart

### Requirement: Auto-apply only with a saved risk
The system SHALL run an automatic pass only when a risk setting from an earlier picker choice exists, and SHALL otherwise wait without writing.

#### Scenario: No risk chosen yet
- **WHEN** auto-apply is on but the user has never chosen a risk
- **THEN** moving the stop-loss does not write a volume

#### Scenario: Risk chosen earlier
- **WHEN** the user chose 1 % earlier and then moves the stop-loss
- **THEN** a pass uses 1 %

### Requirement: Auto-apply on stop-loss move
When auto-apply is on and the stop-loss value shown by the ticket changes from the last observed value, the system SHALL run exactly one pass using the saved risk setting.

#### Scenario: SL dragged
- **WHEN** the user drags the stop-loss line and the ticket shows a new stop-loss value
- **THEN** one pass runs and the volume updates

#### Scenario: Baseline established
- **WHEN** the ticket first becomes readable or auto-apply is just enabled
- **THEN** the current stop-loss value is recorded as the baseline and no pass runs for it

#### Scenario: Debounced drag
- **WHEN** the stop-loss value changes several times within one second
- **THEN** at most one pass runs after the value settles

#### Scenario: Pass output does not re-trigger
- **WHEN** a pass changes only the volume and the stop-loss value stays the same
- **THEN** no further pass runs

### Requirement: Auto-apply pauses while busy
The system SHALL NOT start an automatic pass while another pass is running or while the risk picker is open.

#### Scenario: Picker open
- **WHEN** the picker is open and the stop-loss changes
- **THEN** no automatic pass starts

#### Scenario: Pass running
- **WHEN** a pass is running and the stop-loss changes again
- **THEN** no second pass starts

### Requirement: Manual Apply when disabled
When auto-apply is disabled, the system SHALL do nothing on a stop-loss move, and the picker SHALL run a pass only when Apply is tapped, using the risk shown in the field.

#### Scenario: Apply tapped
- **WHEN** the user moves the stop-loss, reopens the picker and taps Apply
- **THEN** a pass runs using the risk shown in the picker

#### Scenario: Apply with auto-apply on
- **WHEN** auto-apply is enabled and the user taps Apply
- **THEN** exactly one pass runs and auto-apply stays enabled
