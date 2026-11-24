package com.ferik.mt5risk

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.ferik.mt5risk.balance.BalanceStore
import com.ferik.mt5risk.balance.BalanceWatcher
import com.ferik.mt5risk.core.BasisSelection
import com.ferik.mt5risk.core.Refusal
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.mt5.TicketDriver
import com.ferik.mt5risk.pass.MessageSeverity
import com.ferik.mt5risk.pass.OutcomeMessage
import com.ferik.mt5risk.pass.PassOutcome
import com.ferik.mt5risk.pass.PassResult
import com.ferik.mt5risk.pass.PassRunner
import com.ferik.mt5risk.rules.SymbolRuleStore
import com.ferik.mt5risk.ui.UiPrefs
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private val Context.balanceDataStore by preferencesDataStore(name = "balance")
private val Context.symbolRuleDataStore by preferencesDataStore(name = "symbol_rules")
private val Context.uiDataStore by preferencesDataStore(name = "ui")

/**
 * Process-wide holder for the stores and for the running accessibility driver. The overlay, the
 * activity and the accessibility service all live in the same process and reach each other here.
 */
object AppGraph {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val balanceStore: BalanceStore by lazy { BalanceStore(appContext.balanceDataStore) }
    val ruleStore: SymbolRuleStore by lazy { SymbolRuleStore(appContext.symbolRuleDataStore) }
    val uiPrefs: UiPrefs by lazy { UiPrefs(appContext.uiDataStore) }
    val balanceWatcher: BalanceWatcher by lazy { BalanceWatcher(balanceStore) }

    /** Account currency learned from the last ticket read, used to label the Trade tab balance. */
    @Volatile
    var lastCurrency: String? = null

    private val _messages = MutableSharedFlow<OutcomeMessage>(extraBufferCapacity = 8)

    /** What the user should be shown after each pass, over the chart. */
    val messages: SharedFlow<OutcomeMessage> = _messages.asSharedFlow()

    @Volatile
    private var runner: PassRunner? = null

    @Volatile
    private var accessibility: AccessibilityHandle? = null

    /** What the accessibility service lends to the rest of the app while it is connected. */
    interface AccessibilityHandle {
        val driver: TicketDriver
        fun isMt5Foreground(): Boolean
    }

    val isAccessibilityConnected: Boolean get() = accessibility != null

    fun attach(handle: AccessibilityHandle) {
        android.util.Log.i("Mt5Risk", "AppGraph.attach $handle")
        accessibility = handle
        runner = PassRunner(
            driver = handle.driver,
            balance = { balanceStore.current()?.value },
            ruleFor = { symbol -> ruleStore.current().ruleFor(symbol) },
            basis = {
                BasisSelection(
                    kind = uiPrefs.riskBasisNow(),
                    propFirm = uiPrefs.propFirmNow(),
                )
            },
        )
    }

    fun detach(handle: AccessibilityHandle) {
        if (accessibility === handle) {
            accessibility = null
            runner = null
        }
    }

    fun isMt5Foreground(): Boolean = accessibility?.isMt5Foreground() == true

    /** True while a pass is running, so auto-apply can stand down. */
    val isPassRunning: Boolean get() = runner?.isRunning == true

    /** The driver's current reading, for the picker to learn the symbol before it asks anything. */
    suspend fun readTicket() = accessibility?.driver?.read()

    /**
     * Runs one pass and publishes its message. Returns null when a pass is already running (the tap
     * is ignored) or the accessibility service is not connected (the user is told).
     */
    suspend fun runPass(risk: RiskSetting): PassResult? {
        val current = runner
        if (current == null) {
            _messages.emit(
                OutcomeMessage(
                    MessageSeverity.ERROR,
                    "Turn on the accessibility service for MT5 Risk Bubble first",
                ),
            )
            return null
        }
        when (risk) {
            is RiskSetting.Percent -> uiPrefs.setLastRisk(risk.value)
            is RiskSetting.Fixed -> uiPrefs.setFixedRisk(risk.amount)
        }
        val result = current.runIfIdle(risk) ?: return null
        (result.outcome as? PassOutcome.Success)?.currency?.let { lastCurrency = it }
        _messages.emit(OutcomeMessage.of(result))
        return result
    }

    /** Shown when the picker is opened without anything to size against. */
    fun notify(message: OutcomeMessage) {
        _messages.tryEmit(message)
    }

    fun refusal(refusal: Refusal) = notify(OutcomeMessage(MessageSeverity.ERROR, refusal.message))
}
