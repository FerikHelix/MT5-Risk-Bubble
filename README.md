# MT5 Risk Bubble

An Android app that puts a floating bubble over the **MetaTrader 5** mobile app. You set a stop-loss, tap the bubble and enter a risk, such as `1 %` or `60 USD`. The app then writes the lot size into the order ticket's Volume field so that MT5's own stop-loss loss is at or just under that risk.

It is for traders who size every trade by risk on a phone. You still press Buy or Sell yourself.

> **Status:** version `0.1.0`. Built and tested on Xiaomi phones (Android 13, MIUI) with MT5 build 500.6260 on a demo account. Other phones and MT5 builds are untested.

---

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.2.21, JVM target 17 |
| UI | Jetpack Compose (BOM 2025.09.01), Material 3, drawn in a system overlay window |
| Android | `compileSdk` / `targetSdk` 35, `minSdk` 26 (Android 8.0) |
| Build | Gradle 8.14.3 (wrapper), Android Gradle Plugin 8.13.2, version catalog in `gradle/libs.versions.toml` |
| Async | kotlinx-coroutines 1.10.2 |
| Storage | Jetpack DataStore Preferences 1.1.7 |
| MT5 access | Android `AccessibilityService`, limited to the `net.metaquotes.metatrader5` package |
| Tests | JUnit 4.13.2, kotlinx-coroutines-test |

---

## Prerequisites

- Android Studio, or JDK 17 plus the Android SDK (platform 35).
- An Android phone with MetaTrader 5 installed, and USB debugging for `adb install`.
- An MT5 account. Use a demo account first.

---

## Getting started

```bash
# 1. Build the debug APK (output: app/build/outputs/apk/debug/app-debug.apk)
gradlew.bat assembleDebug

# 2. Install it on the phone
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Run the unit tests
gradlew.bat testDebugUnitTest
```

On macOS or Linux use `./gradlew` instead of `gradlew.bat`.

### Phone setup

Open the app and work down its Setup list. Each row opens the right settings screen.

| Permission | What to do |
|---|---|
| Draw over other apps | Turn it on |
| Accessibility service | Android blocks this for sideloaded apps. Open App info, tap the menu, choose **Allow restricted settings**, then turn on **MT5 Risk Bubble** under Accessibility |
| Notifications | Allow. The notification keeps the bubble alive and has a **Stop** button |
| Background pop-ups (Xiaomi) | Allow it in the app's permissions. The app cannot check this |
| Autostart (Xiaomi) | Turn it on. The app cannot check this |
| Battery | Set to **No restrictions** |

When overlay and accessibility are ready, tap **Start the bubble**. The bubble shows only while MT5 is the top app.

---

## Configuration

There are no build-time settings, env vars or API keys. The app has no network permission. Everything the user sets is stored on the phone:

| Setting | Where it is set | Used for |
|---|---|---|
| Risk unit (% or fixed amount) | `%` / `USD` switch in the picker | Percent of the base, or a fixed amount in account currency |
| Last risk percent, last fixed amount | Picker field | Prefills the picker next time |
| Risk base: Balance or Drawdown | Setup page | What a percentage is taken from |
| Prop-firm start balance and max drawdown | Setup page, Drawdown base | Sets the floor (`start - max drawdown`) |
| Manual balance | Setup page | Overrides the balance read from MT5 until the next Trade tab read |
| Auto-apply (default on) | Setup page | Re-size the lot when the stop-loss moves |
| Minimum lot per symbol | "New symbol" page on first use, then the **Minimum lots** tab | Lot step and minimum. Choices: 0.01, 0.1, 1 or custom. Max defaults to 100 |
| Bubble position | Drag the bubble | Restored on next start |

---

## Commands

| Command | What it does |
|---|---|
| `gradlew.bat assembleDebug` | Build the debug APK |
| `gradlew.bat assembleRelease` | Build the release APK. No signing config is set, so sign it yourself |
| `gradlew.bat testDebugUnitTest` | Run the JVM unit tests |
| `adb install -r <apk>` | Install or update the APK on a connected phone |

---

## Project structure

```text
app/src/main/java/com/ferik/mt5risk/
├── MainActivity.kt, Mt5RiskApp.kt  # Launcher activity and Application class
├── AppGraph.kt                     # Wires stores, services and the pass runner together
├── core/       # Risk math, drawdown base, verify loop, preconditions, volume rules, amount parsing
├── mt5/        # MT5 view IDs, ticket reader, order-control deny-list, collapse gesture planner
├── pass/       # One sizing pass (PassRunner) and its result messages
├── balance/    # Balance read from the Trade tab, manual override, staleness
├── rules/      # Saved minimum lot per symbol
├── service/    # Accessibility service, ticket driver, foreground service for the bubble
└── ui/         # Setup screens, picker logic, SL change detector, prefs, palette
    └── overlay/  # The bubble and picker drawn over MT5
app/src/test/   # Unit tests and MT5 accessibility-tree XML fixtures
openspec/       # Specs and archived change proposals
ACCEPTANCE.md   # On-device acceptance runs
```

---

## How it works

### One sizing pass

1. The accessibility service reads the MT5 window: symbol, Volume, the Volume unit, stop-loss price, MT5's stop-loss loss label and free margin. It reads the ticket where it is, even behind the collapsed strip.
2. It checks the preconditions below. If one fails, it writes nothing and shows the reason.
3. It works out the budget: a percent of the base, or the fixed amount.
4. It scales the lot: `new = current x budget / |loss|`, rounded **down** to the symbol's step and clamped to min and max.
5. It writes the new value into the Volume field, then reads MT5's new loss.
6. If the loss is still over budget (the price moved), it rescales from the new reading and writes again. It writes at most **3** times in one pass.
7. It shows the result, for example `0.33 lots - loss 49.17 USD (0.98 % of balance)`.

The loss always comes from MT5, so there is no symbol or tick-value table. JPY pairs, gold, indices and cent accounts all work the same way, and the loss is already in account currency.

If the hidden fields cannot be read, the app taps the expand arrow, does the pass and then collapses the ticket again. One pass can run at a time, with a 20 second timeout.

### Risk base

| Base | Budget for `X %` |
|---|---|
| Balance | `balance x X / 100` |
| Drawdown | `min(balance - floor, max drawdown) x X / 100`, where `floor = start balance - max drawdown` |

When the balance is at or below the floor, sizing stops with "The drawdown floor has been reached". A fixed amount is used as it is on either base.

### Preconditions (refusals)

| Refusal | Cause |
|---|---|
| `Open the order ticket first` | No ticket or strip on screen |
| `Risk must be above 0 % and at most 100 %` | Bad percent |
| `Fixed risk must be above 0` | Bad fixed amount |
| `Set the minimum lot for <symbol> first` | First use of a symbol |
| `Switch the ticket volume to Lots` | Volume unit is not Lots |
| `No balance yet - open the Trade tab so it can be read` | No saved balance |
| `Saved balance ... is below free margin ...` | Saved balance looks stale |
| `Set a stop-loss first` | SL is empty or 0 |
| `Stop-loss is on the wrong side of the entry` | MT5 shows a profit, not a loss, at the SL |
| `Couldn't read the order ticket (<field>)` | A needed field is missing or cannot be parsed |

After a write, if MT5 shows a different volume than the app wrote, the symbol's minimum lot is wrong. The app reports it and offers to fix the rule.

### Bubble and picker

| Behaviour | Detail |
|---|---|
| Visibility | Bubble shows only while MT5 is the top app window |
| Picker pages | Risk page (field, unit, Apply), Setup page (base, balance, auto-apply), New symbol page |
| Auto-apply | When the SL value has not changed for 0.7 s after a move, one pass runs |
| Idle close | The picker closes on Back, Home, or after 45 s idle |
| Balance capture | Read from the Trade tab's Balance row whenever it is on screen (at most every 0.7 s) |

### Safety rules

- The only controls the app acts on are the expand arrow, the Volume field and one downward swipe to collapse the ticket.
- `OrderControlGuard` vetoes any click or gesture on, or across, Buy, Sell, Place and the one-click trading controls. Gesture paths keep a 24 px margin around them.
- All MT5 view IDs are in `mt5/Mt5Ids.kt`. If MT5 renames one, the app refuses instead of guessing.

---

## Data

Three DataStore Preferences files on the phone. `android:allowBackup="false"`, so they are not backed up.

| Store | Keys | Content |
|---|---|---|
| `balance` | `balance_auto_*`, `balance_manual_*` | Last Trade tab balance and optional manual balance, each with time and currency |
| `symbol_rules` | `rule:<symbol>` | Per-symbol rule as `min\|max`. The step equals the minimum lot |
| `ui` | `last_risk_percent`, `fixed_risk`, `risk_mode`, `risk_basis`, `prop_firm_start_balance`, `prop_firm_max_drawdown`, `auto_apply`, `disclosure_accepted`, `bubble_x`, `bubble_y` | Picker and setup choices |

---

## Testing

```bash
gradlew.bat testDebugUnitTest
```

There are 289 JVM unit tests. They cover the risk math, drawdown base, rounding and clamping, the verify loop, preconditions, amount parsing (space and non-breaking thousands separators, `USD`, `USC`), the ticket reader against recorded MT5 accessibility-tree XML in `app/src/test/resources/mt5/`, the order-control guard, the collapse gesture, the stores, and the picker and setup logic.

`pass/FakeMt5.kt` simulates the MT5 ticket, including price drift between reads and the collapsed and expanded states. The tests use it to prove a pass only expands, writes and collapses.

On-device acceptance results are in `ACCEPTANCE.md`. Instrument coverage there includes JPY pairs, gold and an index with a whole-lot minimum.

---

## Known issues / gotchas

- In a fast market with a tight stop, the loss can drift between reads. The pass may end with "Still over budget after 3 tries" instead of reporting success.
- Rounding down means the final loss is often a little under the budget.
- The drawdown base uses balance, not equity, so open losses are not counted. Trailing drawdown and daily loss limits are not tracked.
- MT5 shows the balance only on the Trade tab. Open that tab after deposits, withdrawals or closed trades, or the saved balance goes stale.
- Only the Price and Pips stop-loss modes have been tested.
- The picker was only tested in portrait. In landscape, MT5 does not show the symbol consistently.
- A future MT5 update can rename view IDs. Update `mt5/Mt5Ids.kt` if the app starts refusing with "Couldn't read the order ticket".
- On Android 13+, the accessibility toggle stays grey for a sideloaded APK until you choose **Allow restricted settings** in App info.
- Xiaomi pop-up and Autostart permissions cannot be checked by the app. Without them the bubble may not appear.
- The release build type has no signing config.
