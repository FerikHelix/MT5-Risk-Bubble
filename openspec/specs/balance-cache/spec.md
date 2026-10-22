# balance-cache Specification

## Purpose

Defines how the account balance, which MetaTrader 5 shows only on the Trade tab and not on the order ticket, is captured, remembered with its age, checked against the ticket's free margin for staleness, and manually overridden, so the risk budget is always based on a balance the user can see and trust.

## Requirements

### Requirement: Capture the balance from the Trade tab
Whenever the MetaTrader 5 Trade tab is visible with a readable Balance row, the system SHALL record the balance value and the time it was seen, replacing any earlier cached value.

#### Scenario: Trade tab opened
- **WHEN** the user opens the Trade tab and the Balance row shows 5 000.00
- **THEN** the cached balance becomes 5000.00 with the current time

#### Scenario: Balance row unreadable
- **WHEN** the Trade tab is visible but the Balance row cannot be read
- **THEN** the previously cached balance is kept unchanged

### Requirement: Persist the cache across restarts
The system SHALL keep the cached balance and its timestamp when the app or service is restarted, so a recent value survives process death.

#### Scenario: Service restarted
- **WHEN** the service is killed and restarted after a balance was cached
- **THEN** the cached balance and its original timestamp are still available

### Requirement: Staleness check against free margin
The system SHALL treat the cached balance as stale or belonging to a different account, and SHALL NOT use it, when it is lower than the free margin shown on the ticket, and SHALL tell the user to open the Trade tab to refresh it.

#### Scenario: Cached balance below free margin
- **WHEN** the cached balance is 4 000.00 and the ticket's free margin is 5 000.00
- **THEN** no lot is written and the user is asked to open the Trade tab

#### Scenario: Consistent balance
- **WHEN** the cached balance is 5 000.00 and the free margin is 4 439.63
- **THEN** the cached balance is accepted for sizing

### Requirement: Show the balance and its age before use
The risk picker SHALL display the balance that will be used and how long ago it was seen, so the user can see what the percentage is applied to.

#### Scenario: Picker shows the base
- **WHEN** the user opens the risk picker
- **THEN** it shows the cached balance, its currency when known, and its age, for example "Balance 5 000.00, seen 3 min ago"

### Requirement: Manual balance override
The system SHALL let the user enter a balance manually, SHALL use it in place of the cached value until it is cleared or a newer value is captured from the Trade tab, and SHALL label it as manual.

#### Scenario: No cached balance
- **WHEN** no balance has ever been captured and the user enters 5000 manually
- **THEN** sizing uses 5000 and the picker labels the balance as manual

#### Scenario: Newer Trade tab value
- **WHEN** a manual balance is active and the user later opens the Trade tab
- **THEN** the captured balance replaces the manual one
