# MT5 Risk Bubble

An Android floating bubble that sets your lot size in **MetaTrader 5** from a risk percentage or a fixed amount.

You place the stop-loss on the chart, tap the bubble and enter a risk, such as `1 %` or `60 USD`. The app adjusts the lot until MT5's own stop-loss loss figure is at or just under that amount, then types the lot into the order ticket's Volume field. You still press Buy or Sell yourself.

Because MT5 does the loss calculation, the app needs no symbol table. JPY pairs, gold, indices and cent accounts all work the same way, and the loss is already in your account currency.

It was built and tested on one phone (Redmi Note 10S, Android 13, MIUI 14) with MT5 build 500.6260. Try it on a demo account first.

## What it never does

- It never presses Buy, Sell or Place, never toggles one-click trading, and never opens, changes or closes an order. The only field it touches is Volume.
- It reads only the MT5 window. It has no network permission.
- If something is missing or wrong, it writes nothing and tells you why: no stop-loss, a stop-loss on the wrong side, an unknown or stale balance, a ticket that is not in lots, or a ticket it cannot read.

## How a sizing pass works

1. It reads the symbol, the volume and the stop-loss loss that MT5 shows.
2. It scales the volume (`new = current x budget / loss`) and rounds it down to the lot step.
3. It writes the volume, reads MT5's new loss and corrects downward if the price moved. It writes at most 3 times.
4. It shows the result, for example `0.33 lots - loss 49.17 USD (0.98 % of balance)`.

## Build

You need Android Studio (its bundled JDK is enough) and the Android SDK.

```
gradlew.bat assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
gradlew.bat testDebugUnitTest      # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Phone setup

Open the app and work down the Setup list.

| Permission | What to do |
|---|---|
| Draw over other apps | Turn it on. |
| Accessibility service | Android blocks this for sideloaded apps. Go to App info, open the ⋮ menu, choose **Allow restricted settings**, then turn on **MT5 Risk Bubble** under Accessibility -> Downloaded apps. |
| Notifications | Allow. The notification keeps the bubble alive and holds the **Stop** button. |
| Background pop-ups (Xiaomi) | Allow it in the app's permissions. The app can't check this one. |
| Autostart (Xiaomi) | Turn it on. The app can't check this one either. |
| Battery | Set it to **No restrictions**. |

When the overlay and accessibility permissions are ready, tap **Start the bubble**. The bubble shows only while MT5 is on screen.

## Using it

Open the order ticket so the SL/TP strip shows, set the stop-loss, tap the bubble, enter a risk and tap **Apply**.

- **Percent or fixed amount:** the % / USD switch changes the unit. A fixed amount is also reported as a percentage.
- **Auto-apply:** when it is on, moving the stop-loss re-sizes the lot after about 0.7 s without further changes. It is set in **Setup**.
- **Prop-firm drawdown:** in Setup, choose **Drawdown** and enter the starting balance and the maximum drawdown. The percentage is then taken from what is left above the floor (`start - max drawdown`). When the floor is reached, sizing stops.
- **New symbol:** the first time you use a symbol, the app asks for its minimum lot (0.01, 0.1, 1 or a custom value) and remembers it. You can edit these under **Minimum lots**.
- **Balance:** MT5 shows the balance only on the Trade tab, so the app saves it whenever you open that tab and shows how old it is. You can also type it in Setup.

## Limits

- The loss uses the live price. In a fast market with a tight stop, the app may report "still over budget after 3 tries" instead of claiming success.
- The drawdown uses balance, not equity, so open losses are not counted. The drawdown limits are static, so trailing drawdown and daily loss limits are not tracked.
- Only the Price and Pips stop-loss modes have been tested.
- A future MT5 update could change the screen IDs, which are all in `Mt5Ids.kt`. If the app can't read the ticket, it refuses instead of guessing.

## Code layout

```
core/      risk math, drawdown, verify loop, preconditions, lot rules
mt5/       reading the MT5 screen and the order-control deny-list
pass/      one sizing pass and its result messages
balance/   remembered balance and staleness check
rules/     saved minimum lot per symbol
service/   accessibility and foreground services
ui/        bubble, picker, toast and setup screens
```

The specs are in `openspec/`.
