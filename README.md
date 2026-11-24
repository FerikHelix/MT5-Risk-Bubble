# MT5 Risk Bubble

A floating bubble for **MetaTrader 5 on Android** that sets your lot size from a risk percentage or a fixed amount, and can re-size automatically when you move the stop-loss.

You put the stop-loss where you want it on the chart, tap the bubble, and enter a risk: a percentage of your balance, a percentage of the drawdown a prop firm allows, or a fixed amount such as 60 USD. The app works out the lot so that MetaTrader 5's **own** stop-loss loss figure is at or just under that risk, and types it into the order ticket's Volume field. You still press Buy or Sell yourself.

Built for a sideloaded APK on one phone (Redmi Note 10S, Android 13, MIUI 14) and checked against MetaTrader 5 build **500.6260**. Try it on a demo account first.

## What it does

1. You drag the stop-loss line on the chart (or type it) with the order strip showing.
2. The first time, you tap the bubble and enter a risk. From then on, with **auto-apply** on, moving the stop-loss runs the pass by itself; with it off, **Apply** in the picker runs it.
3. The app reads the symbol, volume and **stop-loss loss** that MT5 shows, scales the volume (`new = current x budget / loss`), rounds it **down** to the symbol's lot step, writes it, then reads MT5's recomputed loss and corrects downward if the price moved (at most 3 writes).
4. A message appears over the chart, for example `0.33 lots - loss 49.17 USD (0.98 % of balance)`.

The order ticket's sheet does not move: MetaTrader 5 keeps its fields live behind the collapsed strip, and the app reads and writes them there. Only if those fields cannot be read does it open the sheet, use it, and close it again.

Because MT5 computes the loss, there is no symbol table to maintain: JPY pairs, gold, indices and cent accounts all work the same way, and the loss is already in your account currency.

## What it never does

- It never presses **Buy, Sell or Place**, never toggles one-click trading, and never opens, changes or closes an order. The only field it changes is **Volume**.
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
3. Tap the bubble, enter a risk and tap **Apply**. The field opens prefilled with the last value and the small **% / USD** control changes the unit. The line under it says exactly what the entry means (`1 % of 4 776.77 = 47.77 USD at risk`), and the summary row at the bottom names the base, what is left and whether auto-apply is on. Tapping that row opens **Setup**.

The picker has two pages, so nothing you set once competes with the number you use every trade.

**Setup** holds what a challenge changes rarely: the **base** (Balance or Drawdown, with the prop-firm numbers and their **Edit**), the **balance** override, and the **auto-apply** switch. It never has an Apply button, so a settings change cannot place a trade; **Back to risk** returns to the field.

**Auto-apply.** While it is on and a risk is already saved, dragging the stop-loss re-sizes the lot after the value settles (about 0.7 s of quiet). Moving the stop-loss several times quickly still produces one pass. It pauses while the picker is open or another pass is running. Turn it off if you only want a pass when you tap **Apply**. The setting is remembered across restarts.

**Fixed risk.** Switch to **USD** and type an amount (for example `60`) to risk that many units of the account currency instead of a percentage. The result message shows both the amount and what it worked out to as a percentage, so a fixed risk is never reported as a percentage you did not choose.

**Prop-firm drawdown.** Open **Setup** and choose **Drawdown**, then enter your **starting balance** and **maximum drawdown** once; **Edit** beside the saved floor changes them. The floor is `starting balance - maximum drawdown`, and the percentage then applies to what is still above that floor, capped at the maximum drawdown so profit never raises the money at risk. At the start of a 10 000 challenge with a 500 drawdown, 1 % risks 5.00; after losing 400 it risks 1.00, because only 100 is left before the floor. The result message says `% of drawdown left`. A fixed amount above the buffer is allowed but flagged, and when the floor is reached **Apply** is disabled and any automatic pass is refused.

**The first time you use a symbol** the picker opens a **New symbol** page asking for its **minimum lot** (0.01, 0.1, 1 or your own). Forex is usually 0.01; stocks and indices often 0.1. The lot then moves in steps of that size, and the answer is remembered per symbol (names match exactly, so `GBPJPY` and `GBPJPY.pc` each ask once). Change or delete these under **Minimum lots**. The maximum lot defaults to 100.

**Balance.** MT5 shows your balance only on the **Trade** tab, so the app remembers it each time you open that tab, and the picker shows it with its age (`Balance 5 000.00 USD, seen 3 min ago`). Open the Trade tab again after your balance changes. You can also type a balance under **Setup -> Change**. If the remembered balance is lower than your free margin it is treated as stale or from another account, and nothing is written.

## Limits worth knowing

- The loss uses the live price, so it moves while you read this sentence. The app reads and writes within about a second and corrects once or twice if needed, but on a tight stop in a fast market it can still report "still over budget after 3 tries". It then tells you the last loss it saw and does not claim success.
- The lot is sized for the price at the moment you tap. Tap close to when you trade.
- If MetaTrader 5 stops keeping its ticket in the screen tree (a future update), the app falls back to opening the sheet, and if that fails too it refuses instead of guessing. The MT5 ids live in one file, `Mt5Ids.kt`.
- The drawdown buffer uses the remembered **balance**, not equity. Floating losses on open trades are not included, so the room shown is optimistic while a position is live.
- The drawdown numbers are **static**. A trailing drawdown moves the floor up with your peak, and the app does not track peaks, so update the two numbers by hand on those accounts. Daily loss limits are not tracked at all.
- Only the Price and Pips stop-loss modes have been checked.

## Code map

```
core/       pure Kotlin: amount parsing, risk scaling, drawdown basis, verify loop, preconditions, per-symbol volume rules
mt5/        reading the MT5 screen, the order-control deny-list, the sheet fallback
pass/       one lot-sizing pass and the messages shown afterwards
balance/    remembered balance and its staleness check
rules/      saved per-symbol minimum lots
service/    accessibility service and the foreground service that owns the bubble
ui/         bubble, picker, toast, setup and settings screens
```

The design and requirements are in `openspec/`; the current change is `openspec/changes/auto-apply-sl-risk-rr/`.
