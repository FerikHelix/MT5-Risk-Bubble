package com.ferik.mt5risk.ui.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.ferik.mt5risk.AppGraph
import com.ferik.mt5risk.balance.BalanceDisplay
import com.ferik.mt5risk.pass.MessageSeverity
import com.ferik.mt5risk.pass.OutcomeMessage
import com.ferik.mt5risk.ui.BubblePosition
import com.ferik.mt5risk.ui.PickerLogic
import com.ferik.mt5risk.ui.PickerStep
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the three overlay windows: the draggable bubble, the risk picker and the result toast. The
 * bubble only shows while MetaTrader 5 is the foreground app, so it never triggers a pass anywhere else.
 */
class OverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = OverlayLifecycleOwner()

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var pickerView: View? = null
    private var toastView: ComposeView? = null
    private var toastJob: Job? = null
    private var pollJob: Job? = null
    private var pickerTimeoutJob: Job? = null
    private var pickerDismissReceiver: BroadcastReceiver? = null

    private val bubbleLabel = mutableStateOf("%")
    private val pickerState = mutableStateOf<PickerUi?>(null)
    private val toastState = mutableStateOf<OutcomeMessage?>(null)

    fun start() {
        owner.resume()
        scope.launch {
            addBubble()
            launch { AppGraph.uiPrefs.lastRisk.collect { bubbleLabel.value = it?.let(PickerLogic::label)?.replace(" ", "") ?: "%" } }
            launch { AppGraph.messages.collect { showToast(it) } }
            pollJob = launch { followForeground() }
        }
    }

    fun stop() {
        pollJob?.cancel()
        toastJob?.cancel()
        closePicker()
        remove(bubbleView)
        remove(pickerView)
        remove(toastView)
        bubbleView = null
        pickerView = null
        toastView = null
        owner.destroy()
        scope.cancel()
    }

    // --- bubble -----------------------------------------------------------------------------

    private suspend fun addBubble() {
        val metrics = context.resources.displayMetrics
        val saved = AppGraph.uiPrefs.bubblePosition()
        // Default sits at the right edge, a third of the way down: over the chart, clear of the order buttons.
        val position = saved ?: BubblePosition(metrics.widthPixels - 170, metrics.heightPixels / 3)

        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            focusable = false,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
        val content = composeView {
            BubbleContent(label = bubbleLabel.value, onTap = ::openPicker)
        }
        var startX = position.x
        var startY = position.y
        val view = DraggableFrame(
            context = context,
            onDragStart = {
                startX = params.x
                startY = params.y
            },
            onDragBy = { dx, dy ->
                params.x = startX + dx
                params.y = startY + dy
                runCatching { windowManager.updateViewLayout(bubbleView, params) }
            },
            onDragEnd = { scope.launch { AppGraph.uiPrefs.setBubblePosition(BubblePosition(params.x, params.y)) } },
        ).apply {
            // Compose reads the lifecycle from the window's root view, which is this frame.
            owner.attachTo(this)
            addView(content)
        }
        runCatching { windowManager.addView(view, params) }
            .onFailure { AppGraph.notify(OutcomeMessage(MessageSeverity.ERROR, "Couldn't show the bubble - allow drawing over other apps")) }
            .onSuccess {
                bubbleView = view
                bubbleParams = params
            }
    }

    private suspend fun followForeground() {
        while (true) {
            val visible = pickerView != null || AppGraph.isMt5Foreground()
            bubbleView?.visibility = if (visible) View.VISIBLE else View.GONE
            delay(FOREGROUND_POLL_MS)
        }
    }

    // --- picker -----------------------------------------------------------------------------

    private fun openPicker() {
        if (pickerView != null) return
        scope.launch {
            val reading = AppGraph.readTicket()
            val rules = AppGraph.ruleStore.current()
            when (val step = PickerLogic.stepFor(reading, rules)) {
                is PickerStep.Unavailable -> showToast(OutcomeMessage(MessageSeverity.ERROR, step.message))
                is PickerStep.AskMinLot -> showPicker(PickerUi.AskMinLot(step.symbol))
                is PickerStep.ChooseRisk -> showPicker(chooseRisk(step.symbol))
            }
        }
    }

    private suspend fun chooseRisk(symbol: String): PickerUi.ChooseRisk {
        val cached = AppGraph.balanceStore.current()
        val label = cached?.let { BalanceDisplay.format(it, System.currentTimeMillis()) }
            ?: "No balance yet - open the Trade tab once, or set it manually"
        return PickerUi.ChooseRisk(
            symbol = symbol,
            balanceLabel = label,
            balanceIsManual = cached?.source == com.ferik.mt5risk.balance.BalanceSource.MANUAL,
            lastUsed = AppGraph.uiPrefs.lastRiskNow(),
        )
    }

    private fun showPicker(initial: PickerUi) {
        pickerState.value = initial
        val params = overlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            focusable = true,
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }

        val content = composeView {
            val state = pickerState.value
            if (state != null) {
                PickerContent(
                    state = state,
                    onMinLot = { min -> saveMinLot(state, min) },
                    onRisk = ::runRisk,
                    onManualBalance = { value -> scope.launch { setManualBalance(state, value) } },
                    onClearManualBalance = { scope.launch { clearManualBalance(state) } },
                    onDismiss = ::closePicker,
                )
            }
        }
        val view = PickerFrame(context, onBack = ::closePicker).apply {
            // Compose reads the lifecycle from the window's root view, which is this frame, not the ComposeView.
            owner.attachTo(this)
            addView(content)
        }
        runCatching { windowManager.addView(view, params) }.onSuccess {
            pickerView = view
            registerPickerDismissReceiver()
            restartPickerTimeout()
        }
    }

    /**
     * While the picker is up MetaTrader 5 is hidden from us, so we cannot see the user leave it. The
     * picker therefore closes itself on Home or Recents, on screen-off, on Back and after an idle spell,
     * so it can never linger over another app.
     */
    private fun registerPickerDismissReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = closePicker()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        pickerDismissReceiver = receiver
    }

    private fun restartPickerTimeout() {
        pickerTimeoutJob?.cancel()
        pickerTimeoutJob = scope.launch {
            delay(PICKER_IDLE_MS)
            closePicker()
        }
    }

    private fun saveMinLot(state: PickerUi, min: BigDecimal) {
        val symbol = (state as? PickerUi.AskMinLot)?.symbol ?: return
        scope.launch {
            AppGraph.ruleStore.saveMinLot(symbol, min)
            pickerState.value = chooseRisk(symbol)
            restartPickerTimeout()
        }
    }

    private suspend fun setManualBalance(state: PickerUi, value: BigDecimal) {
        val symbol = (state as? PickerUi.ChooseRisk)?.symbol ?: return
        AppGraph.balanceStore.setManual(value, AppGraph.lastCurrency)
        pickerState.value = chooseRisk(symbol)
        restartPickerTimeout()
    }

    private suspend fun clearManualBalance(state: PickerUi) {
        val symbol = (state as? PickerUi.ChooseRisk)?.symbol ?: return
        AppGraph.balanceStore.clearManual()
        pickerState.value = chooseRisk(symbol)
        restartPickerTimeout()
    }

    private fun runRisk(percent: BigDecimal) {
        closePicker()
        // Let the picker window leave first, so MetaTrader 5 is the window the pass reads.
        scope.launch {
            delay(PICKER_CLOSE_SETTLE_MS)
            AppGraph.runPass(percent)
        }
    }

    private fun closePicker() {
        pickerTimeoutJob?.cancel()
        pickerTimeoutJob = null
        pickerDismissReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        pickerDismissReceiver = null
        remove(pickerView)
        pickerView = null
        pickerState.value = null
    }

    // --- toast ------------------------------------------------------------------------------

    private fun showToast(message: OutcomeMessage) {
        toastJob?.cancel()
        remove(toastView)
        toastView = null
        toastState.value = message

        val interactive = message.offerResetRuleFor != null
        val params = overlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            focusable = false,
            touchable = interactive,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (TOAST_TOP_DP * context.resources.displayMetrics.density).toInt()
        }
        val view = composeView {
            toastState.value?.let { current ->
                ToastContent(current, onResetRule = ::resetRule)
            }
        }
        runCatching { windowManager.addView(view, params) }.onSuccess { toastView = view }

        toastJob = scope.launch {
            delay(if (message.severity == MessageSeverity.SUCCESS) TOAST_MS else TOAST_LONG_MS)
            remove(toastView)
            toastView = null
        }
    }

    private fun resetRule(symbol: String) {
        toastJob?.cancel()
        remove(toastView)
        toastView = null
        scope.launch {
            AppGraph.ruleStore.delete(symbol)
            showPicker(PickerUi.AskMinLot(symbol))
        }
    }

    // --- window plumbing --------------------------------------------------------------------

    private fun composeView(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView =
        ComposeView(context).apply {
            owner.attachTo(this)
            setContent(content)
        }

    private fun overlayParams(
        width: Int,
        height: Int,
        focusable: Boolean,
        touchable: Boolean = true,
    ): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun remove(view: View?) {
        if (view == null) return
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private companion object {
        const val FOREGROUND_POLL_MS = 800L
        const val PICKER_CLOSE_SETTLE_MS = 250L
        const val PICKER_IDLE_MS = 45_000L
        const val TOAST_MS = 4_500L
        const val TOAST_LONG_MS = 8_000L
        const val TOAST_TOP_DP = 72
    }
}

/** Holds the picker's ComposeView and turns the Back key into a dismiss, which a bare overlay never gets. */
private class PickerFrame(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Wraps the bubble so it can be dragged without losing taps: nothing is intercepted until the finger
 * moves past the touch slop, so a plain tap still reaches the button inside. Screen coordinates are
 * used because the window itself moves under the finger.
 */
private class DraggableFrame(
    context: Context,
    private val onDragStart: () -> Unit,
    private val onDragBy: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {

    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                onDragStart()
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!dragging && (kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop)) {
                    dragging = true
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_MOVE -> onDragBy((event.rawX - downX).toInt(), (event.rawY - downY).toInt())
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                dragging = false
                onDragEnd()
            }
        }
        return true
    }
}
