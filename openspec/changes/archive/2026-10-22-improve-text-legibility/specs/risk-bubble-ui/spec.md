# Spec Delta

## ADDED Requirements

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
