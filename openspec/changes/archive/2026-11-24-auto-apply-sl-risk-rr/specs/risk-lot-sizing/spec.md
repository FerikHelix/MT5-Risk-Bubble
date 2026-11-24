# Spec Delta

## MODIFIED Requirements

### Requirement: Lot derived from MT5's reported loss
The system SHALL compute the target volume by scaling the ticket's current volume by the ratio of the risk budget to the absolute stop-loss loss that MetaTrader 5 currently reports, where the risk budget is the chosen risk percentage applied to the sizing base: the account balance, or the drawdown buffer when that basis is selected. The system SHALL NOT use its own symbol, tick-value or contract-size data.

#### Scenario: One percent of a 5 000.00 balance
- **WHEN** the balance is 5 000.00, the chosen risk is 1 %, the ticket volume is 0.01 and MT5 reports a stop-loss loss of 1.49
- **THEN** the risk budget is 50.00 and the target volume before rounding is about 0.3356

#### Scenario: Works for any instrument and account currency
- **WHEN** the ticket is for a JPY pair, a metal or an index, or the account currency is a cent currency
- **THEN** the same scaling is applied and no per-symbol data is consulted

## ADDED Requirements

### Requirement: Drawdown buffer as the sizing base
When the drawdown basis is selected, the system SHALL compute the sizing base as the account balance minus the drawdown floor, where the floor is the user's starting balance minus the user's maximum drawdown.

#### Scenario: Start of a challenge
- **WHEN** the starting balance is 10 000.00, the maximum drawdown is 500.00, the balance is 10 000.00 and the chosen risk is 1 %
- **THEN** the floor is 9 500.00, the buffer is 500.00 and the risk budget is 5.00

#### Scenario: After a loss
- **WHEN** the same account has a balance of 9 600.00 and the chosen risk is 1 %
- **THEN** the buffer is 100.00 and the risk budget is 1.00

#### Scenario: Fixed amount does not use the base
- **WHEN** the drawdown basis is selected and the user chooses a fixed amount of 50.00
- **THEN** the risk budget is 50.00 regardless of the buffer

### Requirement: Buffer capped at the maximum drawdown
The system SHALL cap the sizing base at the configured maximum drawdown, so profit above the starting balance does not raise the money at risk beyond the chosen percentage of the drawdown the firm gave.

#### Scenario: Balance above the starting balance
- **WHEN** the starting balance is 10 000.00, the maximum drawdown is 500.00, the balance is 11 000.00 and the chosen risk is 1 %
- **THEN** the buffer above the floor is 1 500.00, the base used is 500.00 and the risk budget is 5.00

### Requirement: Refuse sizing when the drawdown buffer is gone
When the balance is at or below the drawdown floor, the system SHALL NOT write a volume and SHALL report that the drawdown floor has been reached.

#### Scenario: Balance at the floor
- **WHEN** the starting balance is 10 000.00, the maximum drawdown is 500.00 and the balance is 9 500.00
- **THEN** no volume is written and the user is told the drawdown floor has been reached

#### Scenario: Automatic pass with no room left
- **WHEN** auto-apply is on, the buffer is zero and the stop-loss moves
- **THEN** no volume is written and the user is told the drawdown floor has been reached

### Requirement: Drawdown numbers come from the user
The system SHALL take the starting balance and the maximum drawdown from the user, SHALL store them on the device only, and SHALL NOT infer them from trade history, from the account balance or from the firm's rules.

#### Scenario: Numbers survive a restart
- **WHEN** the user saved a starting balance of 10 000.00 and a maximum drawdown of 500.00 and restarts the app
- **THEN** both numbers are still in use

#### Scenario: Balance moves, numbers do not
- **WHEN** a Trade tab read updates the cached balance from 10 000.00 to 9 800.00
- **THEN** the stored starting balance and maximum drawdown are unchanged and only the buffer is recomputed
