# Spec Delta

## Purpose

Defines how the app observes and drives the MetaTrader 5 order ticket on the phone through Android accessibility: reading and writing the ticket's fields while its sheet stays collapsed, expanding and collapsing the sheet only as a fallback, and the guarantee that order-submission controls are never actuated.

## ADDED Requirements

### Requirement: Scope limited to MetaTrader 5
The system SHALL read and act on UI content only from the MetaTrader 5 application (package `net.metaquotes.metatrader5`) and SHALL ignore all other applications.

#### Scenario: Other app in the foreground
- **WHEN** an application other than MetaTrader 5 is in the foreground
- **THEN** the system reads nothing and writes nothing

### Requirement: Order-submission controls are never actuated
The system SHALL NOT click, long-press, focus-and-activate or otherwise trigger the Buy, Sell or Place order controls, nor the one-click trading toggle, under any circumstance including error recovery.

#### Scenario: A pass completes
- **WHEN** a lot-sizing pass finishes, successfully or not
- **THEN** no order has been submitted and no order-submission control has received an action from the system

#### Scenario: Gesture targets
- **WHEN** the system dispatches a gesture to collapse the sheet in the fallback path
- **THEN** the gesture starts and ends outside the Buy, Sell and Place controls

### Requirement: Read the symbol without expanding
The system SHALL read the symbol name from the collapsed ticket strip, so the symbol can be identified and its volume rule resolved before any ticket field is read or written.

#### Scenario: Symbol on the collapsed strip
- **WHEN** only the collapsed strip is visible and it shows GBPJPY
- **THEN** the symbol is read as GBPJPY without expanding the sheet

#### Scenario: Symbol not readable
- **WHEN** the symbol name cannot be read from the strip or the ticket
- **THEN** the pass aborts with a message that the ticket could not be read and nothing is written

### Requirement: Only visible nodes identify the ticket state
The system SHALL decide whether the ticket is collapsed, expanded or absent from the nodes the user can see. A collapsed strip is recognised by the expand control being visible while the Volume field is not, and a Volume field that exists off screen SHALL NOT be taken as an open ticket.

#### Scenario: Hidden ticket behind the strip
- **WHEN** the expand control is visible and the Volume field exists but is not visible
- **THEN** the ticket is treated as collapsed, not as expanded

#### Scenario: Ticket not on screen
- **WHEN** neither the collapsed strip nor the expanded ticket is visible
- **THEN** the pass aborts with a message asking the user to open the order ticket

### Requirement: Work on the collapsed ticket without moving the sheet
When the ticket is collapsed, the system SHALL read the volume, stop-loss value, stop-loss loss, margin and free margin from the ticket fields that MetaTrader 5 keeps behind the strip, and SHALL write the Volume field there, without expanding the sheet, so that only the stop-loss and take-profit lines on the chart appear to change.

#### Scenario: Collapsed strip with a stop-loss set
- **WHEN** the strip is collapsed and a stop-loss is set
- **THEN** the volume, stop-loss value, loss and free margin are read as numbers and the sheet is not expanded

#### Scenario: Stop-loss dragged on the chart
- **WHEN** the user drags the stop-loss line on the chart while the sheet is collapsed
- **THEN** the next read shows the new stop-loss value and the loss MT5 computes for it

#### Scenario: Volume written while collapsed
- **WHEN** the system sets the volume to 0.38 on the collapsed ticket
- **THEN** re-reading shows 0.38 and an updated loss, and the sheet is still collapsed

### Requirement: Fall back to expanding the sheet only when needed
When the collapsed ticket's fields are missing or cannot be read, the system SHALL expand the sheet, run the pass, and collapse it again so that the sheet ends as it was found, including when the pass is refused, fails or is cancelled. If the sheet was already expanded by the user, the system SHALL read it in place and leave it expanded.

#### Scenario: Hidden fields missing
- **WHEN** the strip is collapsed but the ticket fields behind it cannot be read
- **THEN** the sheet is expanded, the pass runs, and the sheet is collapsed again

#### Scenario: Failure during the fallback
- **WHEN** the fallback pass fails after the sheet was expanded
- **THEN** the sheet is collapsed before the result is shown

#### Scenario: Already expanded by the user
- **WHEN** the user has the ticket expanded when the pass starts
- **THEN** the pass reads and writes it in place and leaves it expanded

### Requirement: Read the ticket fields
The system SHALL read the symbol, current volume, volume unit mode, stop-loss value, stop-loss loss, margin, free margin, bid and ask from the ticket, and SHALL only proceed when the volume unit mode is Lots.

#### Scenario: All fields present
- **WHEN** the ticket has a stop-loss set
- **THEN** the volume, stop-loss value, stop-loss loss and free margin are read as numbers

#### Scenario: Pips stop-loss mode
- **WHEN** the stop-loss is entered in Pips
- **THEN** the loss MT5 reports is still read and the stop-loss field is only used to tell whether a stop-loss is set

#### Scenario: Volume unit is not Lots
- **WHEN** the volume unit mode is something other than Lots
- **THEN** the pass aborts and tells the user to switch the ticket to Lots

#### Scenario: Pending-order ticket
- **WHEN** the ticket is on a pending-order type instead of Market Execution
- **THEN** the same fields are read and the same sizing is applied, using the loss MT5 reports

### Requirement: Write the volume field
The system SHALL set the Volume field of the ticket directly with the computed value without opening the on-screen keyboard, and SHALL confirm by re-reading that the field holds the intended value.

#### Scenario: Value accepted
- **WHEN** the system sets the volume to 0.33
- **THEN** re-reading the field returns 0.33 and the reported loss has updated

#### Scenario: Value not accepted
- **WHEN** re-reading shows the field holds a different value than written, for example because MT5 rounded it down to its own step
- **THEN** the pass reports failure, does not claim success, and leaves the sheet as it was found

### Requirement: Fail safe when the ticket is unreadable
When a required field is missing, empty or unparseable, for example after an MT5 update changes its UI, the system SHALL abort without writing and SHALL report that the ticket is unreadable, naming the missing field.

#### Scenario: Renamed field
- **WHEN** the loss field cannot be found in the ticket, collapsed or expanded
- **THEN** no volume is written and the user is told the ticket could not be read
