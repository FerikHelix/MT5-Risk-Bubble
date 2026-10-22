# MT5 Risk Bubble

A floating bubble for **MetaTrader 5 on Android** that sets your lot size from a risk percentage.

You put the stop-loss where you want it on the chart, tap the bubble, and pick a risk (0.5 %, 1 %, 2 % or your own). The app works out the lot so that MetaTrader 5's **own** stop-loss loss figure is at or just under that percentage of your balance, and types it into the order ticket's Volume field. You still press Buy or Sell yourself.

Built for a sideloaded APK on one phone (Redmi Note 10S, Android 13, MIUI 14) and checked against MetaTrader 5 build **500.6260**. Try it on a demo account first.

## What it does

1. You drag the stop-loss line on the chart (or type it) with the order strip showing.
2. You tap the bubble and choose a risk.
3. The app reads the symbol, volume and **stop-loss loss** that MT5 shows, scales the volume (`new = current x budget / loss`), rounds it **down** to the symbol's lot step, writes it, then reads MT5's recomputed loss and corrects downward if the price moved (at most 3 writes).
4. A message appears over the chart, for example `0.33 lots - loss 49.17 USD (0.98 % of balance)`.

The order ticket's sheet does not move: MetaTrader 5 keeps its fields live behind the collapsed strip, and the app reads and writes them there. Only if those fields cannot be read does it open the sheet, use it, and close it again.

Because MT5 computes the loss, there is no symbol table to maintain: JPY pairs, gold, indices and cent accounts all work the same way, and the loss is already in your account currency.

## What it never does

- It never presses **Buy, Sell or Place**, never toggles one-click trading, and never opens, changes or closes an order. The one field it changes is **Volume**.
- It reads only the MetaTrader 5 window. It does not use the internet and declares no network permission.
- It writes nothing, and says why, when there is no stop-loss, the stop-loss is on the wrong side, no balance is known, the balance looks stale, the ticket is not in Lots, or the ticket cannot be read.

## Build and install

You need Android Studio (the bundled JDK is enough) and the Android SDK.

```
gradlew.bat assembleDebug          # builds app/build/outputs/apk/debug/app-debug.apk
gradlew.bat testDebugUnitTest      # the JVM unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run with the phone connected (USB debugging on).

## First-time setup on the phone

Open **MT5 Risk Bubble** and go down the Setup list. The first card explains exactly what the app can and cannot do; read it, then tap **I understand**.

| Row | What to do |
|---|---|
| Draw over other apps | Open settings and switch it on. |
| Accessibility service | On a sideloaded app Android first blocks this. Tap **Open App info**, open the menu (three dots, top right) and choose **Allow restricted settings**. Then open Accessibility settings, find **MT5 Risk Bubble** under Downloaded apps, and switch it on. |
| Notifications | Allow. Android keeps the bubble alive only with a visible notification, which also holds the **Stop** button. |
| Pop-up windows while running in the background (Xiaomi) | In the app's permissions, allow it. The app cannot detect this one, so it shows "Check yourself". |
| Autostart (Xiaomi) | Switch it on for MT5 Risk Bubble. Also shown as "Check yourself". |
| Battery: no restrictions | Set battery saver to **No restrictions** for the app. |

When **Draw over other apps** and **Accessibility service** are both Ready, **Start the bubble** becomes available. The bubble appears only while MetaTrader 5 is the app on screen. Drag it anywhere; it remembers its place. **Stop** is in the notification.

## Using it

1. Open the MT5 order ticket on the chart so the strip with **SL / TP** shows at the bottom.
2. Set the stop-loss.
3. Tap the bubble, choose a risk.

**The first time you use a symbol** the picker asks for its **minimum lot** (0.01, 0.1, 1 or your own). Forex is usually 0.01; stocks and indices often 0.1. The lot then moves in steps of that size, and the answer is remembered per symbol (names match exactly, so `GBPJPY` and `GBPJPY.pc` each ask once). Change or delete these under **Minimum lots**. The maximum lot defaults to 100.

**Balance.** MT5 shows your balance only on the **Trade** tab, so the app remembers it each time you open that tab, and the picker shows it with its age (`Balance 5 000.00 USD, seen 3 min ago`). Open the Trade tab again after your balance changes. You can also type a balance in the picker. If the remembered balance is lower than your free margin it is treated as stale or from another account, and nothing is written.

## Limits worth knowing

- The loss uses the live price, so it moves while you read this sentence. The app reads and writes within about a second and corrects once or twice if needed, but on a tight stop in a fast market it can still report "still over budget after 3 tries". It then tells you the last loss it saw and does not claim success.
- The lot is sized for the price at the moment you tap. Tap close to when you trade.
- If MetaTrader 5 stops keeping its ticket in the screen tree (a future update), the app falls back to opening the sheet, and if that fails too it refuses instead of guessing. The MT5 ids live in one file, `Mt5Ids.kt`.
- Only the Price and Pips stop-loss modes have been checked.

## Code map

```
core/       pure Kotlin: amount parsing, risk scaling, verify loop, preconditions, per-symbol volume rules
mt5/        reading the MT5 screen, the order-control deny-list, the sheet fallback
pass/       one lot-sizing pass and the messages shown afterwards
balance/    remembered balance and its staleness check
rules/      saved per-symbol minimum lots
service/    accessibility service and the foreground service that owns the bubble
ui/         bubble, picker, toast, setup and settings screens
```

The design and requirements are in `openspec/changes/mt5-risk-bubble/`.
