package com.ferik.mt5risk.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ferik.mt5risk.balance.BalanceDisplay
import com.ferik.mt5risk.core.PropFirm
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskMode
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.core.SymbolVolumeRules
import com.ferik.mt5risk.pass.MessageSeverity
import com.ferik.mt5risk.pass.OutcomeMessage
import com.ferik.mt5risk.ui.Palette
import com.ferik.mt5risk.ui.PickerLogic
import java.math.BigDecimal

@Composable
fun OverlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette.colorScheme, content = content)
}

/** The floating button. Dragging and tapping are handled by the window controller around it. */
@Composable
fun BubbleContent(label: String, onTap: () -> Unit) {
    OverlayTheme {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 52.dp, minHeight = 52.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Palette.color(Palette.BUBBLE, Palette.BUBBLE_ALPHA))
                .clickable(onClick = onTap)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A long label (0.01 %) or a large system font grows the bubble sideways instead of clipping.
            Text(
                text = label,
                color = Palette.color(Palette.ON_BUBBLE),
                fontWeight = FontWeight.Bold,
                fontSize = Palette.bubbleSize,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** What the picker panel is currently showing. */
sealed interface PickerUi {
    data class AskMinLot(val symbol: String) : PickerUi
    data class ChooseRisk(
        val symbol: String,
        /** Cached balance in account currency, null until the Trade tab was read or set by hand. */
        val balance: BigDecimal?,
        val currency: String?,
        val balanceLabel: String,
        val balanceIsManual: Boolean,
        val lastUsed: BigDecimal?,
        val autoApply: Boolean = true,
        val riskMode: RiskMode = RiskMode.PERCENT,
        val fixedRisk: BigDecimal? = null,
        val basis: RiskBasisKind = RiskBasisKind.BALANCE,
        val propFirm: PropFirm? = null,
    ) : PickerUi
}

@Composable
fun PickerContent(
    state: PickerUi,
    onMinLot: (BigDecimal) -> Unit,
    onApply: (RiskSetting) -> Unit,
    onManualBalance: (BigDecimal) -> Unit,
    onClearManualBalance: () -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onRiskMode: (RiskMode) -> Unit,
    onBasis: (RiskBasisKind) -> Unit,
    onPropFirm: (BigDecimal, BigDecimal) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayTheme {
        // The scrim closes the picker; the card itself swallows taps so they do not fall through.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(onClick = onDismiss)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(20.dp),
                colors = Palette.overlayCard(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state) {
                        is PickerUi.AskMinLot -> MinLotQuestion(state, onMinLot)
                        is PickerUi.ChooseRisk -> RiskChoices(
                            state = state,
                            onApply = onApply,
                            onManualBalance = onManualBalance,
                            onClearManualBalance = onClearManualBalance,
                            onAutoApply = onAutoApply,
                            onRiskMode = onRiskMode,
                            onBasis = onBasis,
                            onPropFirm = onPropFirm,
                            onEdit = onEdit,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        colors = Palette.textButton(),
                    ) { Text("Close", fontSize = Palette.bodySize) }
                }
            }
        }
    }
}

@Composable
private fun MinLotQuestion(state: PickerUi.AskMinLot, onMinLot: (BigDecimal) -> Unit) {
    var custom by remember { mutableStateOf("") }
    val customValue = SymbolVolumeRules.parseMinLot(custom)

    Text(
        "Minimum lot for ${state.symbol}?",
        fontSize = Palette.titleSize,
        fontWeight = FontWeight.Bold,
        color = Palette.color(Palette.TEXT),
    )
    Text(
        "Asked once. Forex is usually 0.01, stocks and indices often 0.1. The lot then moves in steps of that size.",
        fontSize = Palette.secondarySize,
        color = Palette.color(Palette.TEXT_SECONDARY),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (choice in SymbolVolumeRules.MIN_LOT_CHOICES) {
            Button(onClick = { onMinLot(choice) }, modifier = Modifier.weight(1f), colors = Palette.chipButton()) {
                Text(choice.toPlainString(), fontSize = Palette.bodySize)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("Custom") },
            singleLine = true,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { customValue?.let(onMinLot) }, enabled = customValue != null, colors = Palette.primaryButton()) {
            Text("Save", fontSize = Palette.bodySize)
        }
    }
}

@Composable
private fun RiskChoices(
    state: PickerUi.ChooseRisk,
    onApply: (RiskSetting) -> Unit,
    onManualBalance: (BigDecimal) -> Unit,
    onClearManualBalance: () -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onRiskMode: (RiskMode) -> Unit,
    onBasis: (RiskBasisKind) -> Unit,
    onPropFirm: (BigDecimal, BigDecimal) -> Unit,
    onEdit: () -> Unit,
) {
    var percent by remember { mutableStateOf(state.lastUsed?.stripTrailingZeros()?.toPlainString() ?: "") }
    var fixed by remember { mutableStateOf(state.fixedRisk?.stripTrailingZeros()?.toPlainString() ?: "") }
    var startBalance by remember {
        mutableStateOf(state.propFirm?.startBalance?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var maxDrawdown by remember {
        mutableStateOf(state.propFirm?.maxDrawdown?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var setupOpen by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    val fixedMode = state.riskMode == RiskMode.FIXED
    val percentValue = PickerLogic.parseCustomRisk(percent)
    val fixedValue = PickerLogic.parseFixedRisk(fixed)
    val risk = PickerLogic.riskSetting(percentValue, fixedValue, fixedMode)
    val base = PickerLogic.basePreview(state.balance, state.basis, state.propFirm)
    val drawdownBase = state.propFirm?.let { PickerLogic.basePreview(state.balance, RiskBasisKind.DRAWDOWN, it) }
    val preview = PickerLogic.previewRisk(percentValue, fixedValue, fixedMode, base?.value)
    val manualValue = PickerLogic.parseManualBalance(manual)
    val draftPropFirm = PickerLogic.propFirm(startBalance, maxDrawdown)
    val propFirmPairInvalid = startBalance.isNotBlank() && maxDrawdown.isNotBlank() && draftPropFirm == null
    val exhausted = base?.isExhausted == true
    val drawdownReady = state.propFirm != null && state.balance != null
    val overBufferWarning = if (
        fixedMode && fixedValue != null && drawdownBase != null && fixedValue > drawdownBase.value
    ) {
        "${money(fixedValue, state.currency)} is more than the " +
            "${money(drawdownBase.value, state.currency)} left before the floor"
    } else {
        null
    }

    Text(
        "Risk for ${state.symbol}",
        fontSize = Palette.titleSize,
        fontWeight = FontWeight.Bold,
        color = Palette.color(Palette.TEXT),
    )
    Text(state.balanceLabel, fontSize = Palette.secondarySize, color = Palette.color(Palette.TEXT_SECONDARY))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (fixedMode) fixed else percent,
            onValueChange = {
                if (fixedMode) fixed = it else percent = it
                onEdit()
            },
            label = { Text("Risk") },
            singleLine = true,
            isError = (if (fixedMode) fixed else percent).isNotBlank() && risk == null,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onRiskMode(RiskMode.PERCENT) },
            colors = if (!fixedMode) Palette.selectedChipButton() else Palette.chipButton(),
        ) { Text("%", fontSize = Palette.bodySize) }
        Button(
            onClick = { onRiskMode(RiskMode.FIXED) },
            colors = if (fixedMode) Palette.selectedChipButton() else Palette.chipButton(),
        ) { Text("USD", fontSize = Palette.bodySize) }
    }
    Text(
        text = preview?.let { "= ${money(it, state.currency)} at risk" } ?: "Enter the risk to size",
        fontSize = Palette.secondarySize,
        color = Palette.color(Palette.TEXT_SECONDARY),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onBasis(RiskBasisKind.BALANCE) },
            modifier = Modifier.weight(1f),
            colors = if (state.basis == RiskBasisKind.BALANCE) Palette.selectedChipButton() else Palette.chipButton(),
        ) {
            val amount = state.balance?.let(BalanceDisplay::formatAmount) ?: "-"
            Text("Balance $amount", fontSize = Palette.secondarySize)
        }
        Button(
            onClick = { if (drawdownReady) onBasis(RiskBasisKind.DRAWDOWN) else setupOpen = true },
            enabled = state.balance != null,
            modifier = Modifier.weight(1f),
            colors = if (state.basis == RiskBasisKind.DRAWDOWN) Palette.selectedChipButton() else Palette.chipButton(),
        ) {
            val amount = drawdownBase?.let { BalanceDisplay.formatAmount(it.value) } ?: "-"
            Text("DD left $amount", fontSize = Palette.secondarySize)
        }
    }

    if (setupOpen || (state.basis == RiskBasisKind.DRAWDOWN && state.propFirm == null)) {
        Text(
            "Prop-firm numbers",
            fontSize = Palette.secondarySize,
            color = Palette.color(Palette.TEXT_SECONDARY),
        )
        OutlinedTextField(
            value = startBalance,
            onValueChange = { startBalance = it; onEdit() },
            label = { Text("Starting balance") },
            singleLine = true,
            isError = startBalance.isNotBlank() &&
                (PickerLogic.parsePropFirmStart(startBalance) == null || propFirmPairInvalid),
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = maxDrawdown,
            onValueChange = { maxDrawdown = it; onEdit() },
            label = { Text("Maximum drawdown") },
            singleLine = true,
            isError = maxDrawdown.isNotBlank() && draftPropFirm == null,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    draftPropFirm?.let {
                        onPropFirm(it.startBalance, it.maxDrawdown)
                        setupOpen = false
                    }
                },
                enabled = draftPropFirm != null,
                colors = Palette.primaryButton(),
            ) { Text("Save", fontSize = Palette.bodySize) }
            draftPropFirm?.let {
                Text(
                    "Floor ${BalanceDisplay.formatAmount(it.floor)}",
                    fontSize = Palette.secondarySize,
                    color = Palette.color(Palette.TEXT_SECONDARY),
                )
            }
        }
    }

    state.propFirm?.let { propFirm ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Floor ${BalanceDisplay.formatAmount(propFirm.floor)} - DD left " +
                    "${drawdownBase?.let { BalanceDisplay.formatAmount(it.value) } ?: "-"} " +
                    "of ${BalanceDisplay.formatAmount(propFirm.maxDrawdown)}",
                fontSize = Palette.secondarySize,
                color = Palette.color(Palette.TEXT_SECONDARY),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { setupOpen = !setupOpen }, colors = Palette.textButton()) {
                Text(if (setupOpen) "Hide" else "Edit", fontSize = Palette.bodySize)
            }
        }
    }

    if (exhausted) {
        Text(
            "Drawdown floor reached - no room left",
            fontSize = Palette.secondarySize,
            color = Palette.color(Palette.STATUS_MISSING),
        )
    } else if (overBufferWarning != null) {
        Text(
            overBufferWarning,
            fontSize = Palette.secondarySize,
            color = Palette.color(Palette.STATUS_CHECK),
        )
    }

    Button(
        onClick = { risk?.let(onApply) },
        enabled = risk != null && !exhausted,
        modifier = Modifier.fillMaxWidth(),
        colors = Palette.primaryButton(),
    ) { Text("Apply", fontSize = Palette.bodySize) }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(onClick = { showManual = !showManual }, colors = Palette.textButton()) {
            Text("Change balance", fontSize = Palette.bodySize)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-apply", fontSize = Palette.secondarySize, color = Palette.color(Palette.TEXT))
            Switch(checked = state.autoApply, onCheckedChange = onAutoApply)
        }
    }

    if (showManual) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it; onEdit() },
                label = { Text("Balance") },
                singleLine = true,
                colors = Palette.textField(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { manualValue?.let(onManualBalance) },
                enabled = manualValue != null,
                colors = Palette.primaryButton(),
            ) { Text("Save", fontSize = Palette.bodySize) }
        }
        if (state.balanceIsManual) {
            OutlinedButton(onClick = onClearManualBalance, colors = Palette.outlinedButton()) {
                Text("Use the Trade tab balance again", fontSize = Palette.bodySize)
            }
        }
    }
}

/** `102.41 USD`, or the amount alone when the account currency is unknown. */
private fun money(value: BigDecimal, currency: String?): String {
    val amount = BalanceDisplay.formatAmount(value)
    return if (currency != null) "$amount $currency" else amount
}

/** The message shown over the chart after a pass. Errors offer to re-ask a symbol's minimum lot. */
@Composable
fun ToastContent(message: OutcomeMessage, onResetRule: (String) -> Unit) {
    val (backgroundRgb, textRgb) = Palette.toast(message.severity)
    val text = Palette.color(textRgb)
    OverlayTheme {
        Card(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Palette.color(backgroundRgb), contentColor = text),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(message.text, color = text, fontSize = Palette.bodySize)
                message.offerResetRuleFor?.let { symbol ->
                    OutlinedButton(
                        onClick = { onResetRule(symbol) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = text),
                        border = BorderStroke(1.dp, text),
                    ) {
                        Text("Re-set minimum lot for $symbol", color = text, fontSize = Palette.secondarySize)
                    }
                }
            }
        }
    }
}
