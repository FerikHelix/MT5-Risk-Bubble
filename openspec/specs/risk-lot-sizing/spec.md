# risk-lot-sizing Specification

## Purpose

Defines how a trade volume (lots) is derived from a chosen risk percentage, the account balance and the stop-loss money loss that MetaTrader 5 reports, so that the loss at the stop-loss stays within the chosen risk and nothing is written when the inputs cannot be trusted.

## Requirements

### Requirement: Lot derived from MT5's reported loss
The system SHALL compute the target volume by scaling the ticket's current volume by the ratio of the risk budget to the absolute stop-loss loss that MetaTrader 5 currently reports, where the risk budget is the chosen risk percentage of the account balance. The system SHALL NOT use its own symbol, tick-value or contract-size data.

#### Scenario: One percent of a 5 000.00 balance
- **WHEN** the balance is 5 000.00, the chosen risk is 1 %, the ticket volume is 0.01 and MT5 reports a stop-loss loss of 1.49
- **THEN** the risk budget is 50.00 and the target volume before rounding is about 0.3356

#### Scenario: Works for any instrument and account currency
- **WHEN** the ticket is for a JPY pair, a metal or an index, or the account currency is a cent currency
- **THEN** the same scaling is applied and no per-symbol data is consulted

### Requirement: Rounding and volume limits
The system SHALL round the target volume DOWN to a multiple of the symbol's volume step and SHALL clamp it between the symbol's minimum and maximum volume, as given by that symbol's volume rule. If the minimum volume would still exceed the risk budget, the system SHALL warn the user that the minimum lot exceeds the chosen risk before writing it.

#### Scenario: Forex rounds down, never up
- **WHEN** the symbol's rule is min 0.01 and step 0.01, and the unrounded target volume is 0.3356
- **THEN** the volume written is 0.33

#### Scenario: Index steps in 0.1
- **WHEN** the symbol's rule is min 0.1 and step 0.1, and the unrounded target volume is 3.37
- **THEN** the volume written is 3.3, and 0.1, 0.2, 0.3 are the only possible values below 0.4

#### Scenario: Minimum lot exceeds the risk
- **WHEN** the risk budget is smaller than the loss at the symbol's minimum volume
- **THEN** the system warns that even the minimum lot exceeds the chosen risk and reports the resulting loss, and does not silently present it as within risk

#### Scenario: Target below the minimum
- **WHEN** the symbol's rule is min 0.1 and the unrounded target volume is 0.06
- **THEN** the volume written is the minimum 0.1 together with the minimum-lot warning

#### Scenario: Maximum volume clamp
- **WHEN** the unrounded target volume exceeds the symbol's maximum volume
- **THEN** the volume written is the maximum volume and the user is told it was clamped

### Requirement: Per-symbol volume rule learned once
The system SHALL keep a volume rule for each symbol, matched by the exact symbol name, consisting of a minimum volume, a step equal to that minimum, and a maximum volume that defaults to 100. When a symbol has no rule, the system SHALL ask the user once for the symbol's minimum lot, store the rule, and SHALL NOT write any volume until the rule exists.

#### Scenario: Forex symbol with minimum 0.01
- **WHEN** the user answers 0.01 for GBPJPY
- **THEN** the rule is min 0.01, step 0.01, max 100 and is used for every later pass on GBPJPY without asking again

#### Scenario: Index symbol with minimum 0.1
- **WHEN** the user answers 0.1 for SP500
- **THEN** the rule is min 0.1, step 0.1, max 100 and lots are written as 0.1, 0.2, 0.3 and so on

#### Scenario: Unknown symbol asks before writing
- **WHEN** a pass is requested on a symbol that has no rule
- **THEN** the user is asked for the minimum lot first, no volume is written until it is answered, and the pass continues with the answer

#### Scenario: Read-back mismatch
- **WHEN** the volume field does not hold the written value after the write, for example because the symbol's real step differs from the stored rule
- **THEN** the pass reports failure with the stored rule and the value MT5 shows, and offers to re-set that symbol's rule

#### Scenario: Name variants are separate symbols
- **WHEN** the same instrument appears as GBPJPY and as GBPJPY.pc
- **THEN** each name has its own rule and each is asked for once

### Requirement: Verify and correct against MT5's recomputed loss
After writing a volume, the system SHALL re-read the loss that MetaTrader 5 reports for the new volume and SHALL adjust the volume, using the freshest volume and loss pair, until the loss does not exceed the risk budget. The system SHALL stop after at most 3 attempts.

#### Scenario: Price drift pushes the loss over budget
- **WHEN** after writing the volume MT5 reports a loss above the risk budget because the price moved
- **THEN** the system lowers the volume by recomputing from the latest pair and re-reads the loss again

#### Scenario: Attempts exhausted
- **WHEN** the loss still exceeds the risk budget after 3 attempts
- **THEN** the system leaves the lowest volume tried that is within budget if one exists, otherwise reports failure and the last observed loss to the user

### Requirement: Preconditions that block any write
The system SHALL NOT change the volume, and SHALL tell the user the reason, when any of these hold: no stop-loss is set, the stop-loss is on the wrong side of the entry so the reported loss is not a loss, no usable balance is available, the balance is stale, the symbol's volume rule has not been set, or the ticket values cannot be read.

#### Scenario: No stop-loss
- **WHEN** the stop-loss value is 0 or the loss label is absent
- **THEN** the volume is left unchanged and the user is told to set a stop-loss first

#### Scenario: Wrong-side stop-loss
- **WHEN** MT5 reports a positive or zero stop-loss value instead of a loss
- **THEN** the volume is left unchanged and the user is told the stop-loss is on the wrong side

### Requirement: Locale-tolerant number and currency parsing
The system SHALL parse amounts that use a space or non-breaking space as the thousands separator, a point as the decimal separator, and any alphabetic currency suffix (for example USD or USC), and SHALL use the currency only for display and for consistency checks.

#### Scenario: Thousands separator
- **WHEN** MT5 shows a free margin of "5 000.00 USD"
- **THEN** it is parsed as 5000.00 in currency USD

#### Scenario: Cent account
- **WHEN** MT5 shows a loss of "-54.51 USC"
- **THEN** it is parsed as a loss of 54.51 in currency USC and the risk budget is computed in the same currency

### Requirement: Risk percentage input
The system SHALL accept a risk percentage greater than 0 and not greater than 100, and SHALL reject any other value without writing.

#### Scenario: Invalid custom risk
- **WHEN** the user enters 0 or a value above 100
- **THEN** no volume is written and the user is told the value is invalid
