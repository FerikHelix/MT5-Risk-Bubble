package com.ferik.mt5risk.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ferik.mt5risk.balance.BalanceDisplay
import com.ferik.mt5risk.core.PropFirm
import com.ferik.mt5risk.core.RiskBasisKind
import com.ferik.mt5risk.core.RiskMode
import com.ferik.mt5risk.core.RiskSetting
import com.ferik.mt5risk.core.SymbolVolumeRules
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

/** Which page of the picker card is showing. The symbol question is its own state in [PickerUi]. */
enum class PickerPage { RISK, SETUP }

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
        /** What the single risk field holds, in the unit [riskMode] names. */
        val riskText: String = "",
        val autoApply: Boolean = true,
        val riskMode: RiskMode = RiskMode.PERCENT,
        val basis: RiskBasisKind = RiskBasisKind.BALANCE,
        val propFirm: PropFirm? = null,
        val page: PickerPage = PickerPage.RISK,
    ) : PickerUi
}

@Composable
fun PickerContent(
    state: PickerUi,
    onMinLot: (BigDecimal) -> Unit,
    onApply: (RiskSetting) -> Unit,
    onRiskText: (String) -> Unit,
    onRiskMode: (RiskMode) -> Unit,
    onPage: (PickerPage) -> Unit,
    onBasis: (RiskBasisKind) -> Unit,
    onPropFirm: (PropFirm) -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onManualBalance: (BigDecimal) -> Unit,
    onClearManualBalance: () -> Unit,
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
                shape = Palette.cardShape,
                colors = Palette.overlayCard(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state) {
                        is PickerUi.AskMinLot -> SymbolPage(state, onMinLot, onDismiss)
                        is PickerUi.ChooseRisk -> when (state.page) {
                            PickerPage.RISK -> RiskPage(
                                state = state,
                                onApply = onApply,
                                onRiskText = onRiskText,
                                onRiskMode = onRiskMode,
                                onPage = onPage,
                                onDismiss = onDismiss,
                            )
                            PickerPage.SETUP -> SetupPage(
                                state = state,
                                onBasis = onBasis,
                                onPropFirm = onPropFirm,
                                onAutoApply = onAutoApply,
                                onManualBalance = onManualBalance,
                                onClearManualBalance = onClearManualBalance,
                                onEdit = onEdit,
                                onPage = onPage,
                                onDismiss = onDismiss,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Title row shared by every page: optional back, the title, and a close control. */
@Composable
private fun PageHeader(title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (onBack != null) {
            TextButton(onClick = onBack, colors = Palette.textButton()) {
                Text("Back", fontSize = Palette.bodySize)
            }
        }
        Text(
            title,
            fontSize = Palette.titleSize,
            fontWeight = FontWeight.Bold,
            color = Palette.color(Palette.TEXT),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onClose,
            modifier = Modifier.semantics { contentDescription = "Close" },
            colors = Palette.textButton(),
        ) { Text("\u00D7", fontSize = Palette.titleSize) }
    }
}

// --- risk page --------------------------------------------------------------------------------

@Composable
private fun RiskPage(
    state: PickerUi.ChooseRisk,
    onApply: (RiskSetting) -> Unit,
    onRiskText: (String) -> Unit,
    onRiskMode: (RiskMode) -> Unit,
    onPage: (PickerPage) -> Unit,
    onDismiss: () -> Unit,
) {
    val fixedMode = state.riskMode == RiskMode.FIXED
    val percentValue = if (fixedMode) null else PickerLogic.parseCustomRisk(state.riskText)
    val fixedValue = if (fixedMode) PickerLogic.parseFixedRisk(state.riskText) else null
    val risk = PickerLogic.riskSetting(percentValue, fixedValue, fixedMode)
    val base = PickerLogic.basePreview(state.balance, state.basis, state.propFirm)
    val explanation = PickerLogic.explanation(risk, base, state.currency, state.propFirm)
    val drawdownBase = state.propFirm?.let { PickerLogic.basePreview(state.balance, RiskBasisKind.DRAWDOWN, it) }
    val exhausted = base?.isExhausted == true
    val overBuffer = if (fixedMode && fixedValue != null && drawdownBase != null) {
        fixedValue > drawdownBase.value
    } else {
        false
    }

    PageHeader("Risk for ${state.symbol}", onBack = null, onClose = onDismiss)
    Text(state.balanceLabel, fontSize = Palette.secondarySize, color = Palette.color(Palette.TEXT_SECONDARY))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = state.riskText,
            onValueChange = onRiskText,
            label = { Text("Risk") },
            singleLine = true,
            isError = state.riskText.isNotBlank() && risk == null,
            shape = Palette.controlShape,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        SegmentedChoice(
            options = listOf(RiskMode.PERCENT, RiskMode.FIXED),
            selected = state.riskMode,
            label = { if (it == RiskMode.PERCENT) "%" else "USD" },
            onSelect = onRiskMode,
            modifier = Modifier.width(120.dp),
        )
    }

    Text(
        explanation?.line ?: "Enter the risk to size",
        fontSize = Palette.secondarySize,
        color = Palette.color(Palette.TEXT_SECONDARY),
    )
    explanation?.detail?.let {
        Text(it, fontSize = Palette.secondarySize, color = Palette.color(Palette.TEXT_SECONDARY))
    }
    if (exhausted) {
        Text(
            "Drawdown floor reached - no room left",
            fontSize = Palette.secondarySize,
            color = Palette.color(Palette.STATUS_MISSING),
        )
    } else if (overBuffer && fixedValue != null && drawdownBase != null) {
        Text(
            "${money(fixedValue, state.currency)} is more than the " +
                "${money(drawdownBase.value, state.currency)} left before the floor",
            fontSize = Palette.secondarySize,
            color = Palette.color(Palette.STATUS_CHECK),
        )
    }

    Button(
        onClick = { risk?.let(onApply) },
        enabled = risk != null && !exhausted,
        shape = Palette.controlShape,
        modifier = Modifier.fillMaxWidth(),
        colors = Palette.primaryButton(),
    ) { Text("Apply", fontSize = Palette.bodySize) }

    HorizontalDivider(color = Palette.color(Palette.OUTLINE, 0.35f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPage(PickerPage.SETUP) }
            .padding(vertical = 4.dp),
    ) {
        Text(
            PickerLogic.summary(base, state.currency, state.autoApply),
            fontSize = Palette.secondarySize,
            color = Palette.color(Palette.TEXT_SECONDARY),
            modifier = Modifier.weight(1f),
        )
        Text(">", fontSize = Palette.bodySize, color = Palette.color(Palette.TEXT_SECONDARY))
    }
}

// --- setup page -------------------------------------------------------------------------------

@Composable
private fun SetupPage(
    state: PickerUi.ChooseRisk,
    onBasis: (RiskBasisKind) -> Unit,
    onPropFirm: (PropFirm) -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onManualBalance: (BigDecimal) -> Unit,
    onClearManualBalance: () -> Unit,
    onEdit: () -> Unit,
    onPage: (PickerPage) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingBase by remember { mutableStateOf(false) }
    var editingBalance by remember { mutableStateOf(false) }
    var startText by remember {
        mutableStateOf(state.propFirm?.startBalance?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var drawdownText by remember {
        mutableStateOf(state.propFirm?.maxDrawdown?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var manualText by remember { mutableStateOf("") }

    val draft = PickerLogic.propFirm(startText, drawdownText)
    val pairInvalid = startText.isNotBlank() && drawdownText.isNotBlank() && draft == null
    val buffer = state.propFirm?.let { PickerLogic.basePreview(state.balance, RiskBasisKind.DRAWDOWN, it) }
    val manualValue = PickerLogic.parseManualBalance(manualText)
    val showBaseEditor = editingBase || (state.basis == RiskBasisKind.DRAWDOWN && state.propFirm == null)

    PageHeader("Setup", onBack = { onPage(PickerPage.RISK) }, onClose = onDismiss)

    SectionLabel("BASE")
    SegmentedChoice(
        options = listOf(RiskBasisKind.BALANCE, RiskBasisKind.DRAWDOWN),
        selected = state.basis,
        label = { if (it == RiskBasisKind.BALANCE) "Balance" else "Drawdown" },
        onSelect = onBasis,
        optionEnabled = { it == RiskBasisKind.BALANCE || state.balance != null },
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.propFirm != null && !showBaseEditor) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                PickerLogic.drawdownDetail(state.propFirm, buffer?.value),
                fontSize = Palette.secondarySize,
                color = Palette.color(Palette.TEXT_SECONDARY),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { editingBase = true }, colors = Palette.textButton()) {
                Text("Edit", fontSize = Palette.bodySize)
            }
        }
    }

    if (showBaseEditor) {
        OutlinedTextField(
            value = startText,
            onValueChange = { startText = it; onEdit() },
            label = { Text("Starting balance") },
            singleLine = true,
            isError = startText.isNotBlank() &&
                (PickerLogic.parsePropFirmStart(startText) == null || pairInvalid),
            shape = Palette.controlShape,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = drawdownText,
            onValueChange = { drawdownText = it; onEdit() },
            label = { Text("Maximum drawdown") },
            singleLine = true,
            isError = drawdownText.isNotBlank() && draft == null,
            shape = Palette.controlShape,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    draft?.let {
                        onPropFirm(it)
                        editingBase = false
                    }
                },
                enabled = draft != null,
                shape = Palette.controlShape,
                colors = Palette.primaryButton(),
            ) { Text("Save", fontSize = Palette.bodySize) }
            draft?.let {
                Text(
                    "floor ${money(it.floor, state.currency)}",
                    fontSize = Palette.secondarySize,
                    color = Palette.color(Palette.TEXT_SECONDARY),
                )
            }
        }
    }

    SectionLabel("BALANCE")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            state.balanceLabel,
            fontSize = Palette.secondarySize,
            color = Palette.color(Palette.TEXT_SECONDARY),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { editingBalance = !editingBalance }, colors = Palette.textButton()) {
            Text("Change", fontSize = Palette.bodySize)
        }
    }
    if (editingBalance) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it; onEdit() },
                label = { Text("Balance") },
                singleLine = true,
                shape = Palette.controlShape,
                colors = Palette.textField(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    manualValue?.let {
                        onManualBalance(it)
                        editingBalance = false
                    }
                },
                enabled = manualValue != null,
                shape = Palette.controlShape,
                colors = Palette.primaryButton(),
            ) { Text("Save", fontSize = Palette.bodySize) }
        }
        if (state.balanceIsManual) {
            OutlinedButton(
                onClick = {
                    onClearManualBalance()
                    editingBalance = false
                },
                shape = Palette.controlShape,
                colors = Palette.outlinedButton(),
            ) {
                Text("Use the Trade tab balance again", fontSize = Palette.bodySize)
            }
        }
    }

    SectionLabel("AUTO-APPLY")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Resize when the stop-loss moves",
            fontSize = Palette.bodySize,
            color = Palette.color(Palette.TEXT),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = state.autoApply, onCheckedChange = onAutoApply)
    }

    OutlinedButton(
        onClick = { onPage(PickerPage.RISK) },
        shape = Palette.controlShape,
        modifier = Modifier.fillMaxWidth(),
        colors = Palette.outlinedButton(),
    ) { Text("Back to risk", fontSize = Palette.bodySize) }
}

// --- symbol page ------------------------------------------------------------------------------

@Composable
private fun SymbolPage(state: PickerUi.AskMinLot, onMinLot: (BigDecimal) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    val customValue = SymbolVolumeRules.parseMinLot(custom)

    PageHeader("New symbol", onBack = null, onClose = onDismiss)
    Text(
        state.symbol,
        fontSize = Palette.bodySize,
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
            Button(
                onClick = { onMinLot(choice) },
                shape = Palette.controlShape,
                modifier = Modifier.weight(1f),
                colors = Palette.chipButton(),
            ) { Text(choice.toPlainString(), fontSize = Palette.bodySize) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("Custom") },
            singleLine = true,
            shape = Palette.controlShape,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { customValue?.let(onMinLot) },
            enabled = customValue != null,
            shape = Palette.controlShape,
            colors = Palette.primaryButton(),
        ) { Text("Save", fontSize = Palette.bodySize) }
    }
}

// --- shared pieces ----------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = Palette.secondarySize,
        fontWeight = FontWeight.Bold,
        color = Palette.color(Palette.TEXT_SECONDARY),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionEnabled: (T) -> Boolean = { true },
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = optionEnabled(option),
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                    baseShape = Palette.controlShape,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Palette.color(Palette.SELECTED_SOFT),
                    activeContentColor = Palette.color(Palette.ON_SELECTED_SOFT),
                    inactiveContainerColor = Palette.color(Palette.CHIP),
                    inactiveContentColor = Palette.color(Palette.TEXT),
                ),
                label = { Text(label(option), fontSize = Palette.secondarySize) },
            )
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
            shape = Palette.toastShape,
            colors = CardDefaults.cardColors(containerColor = Palette.color(backgroundRgb), contentColor = text),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(message.text, color = text, fontSize = Palette.bodySize)
                message.offerResetRuleFor?.let { symbol ->
                    OutlinedButton(
                        onClick = { onResetRule(symbol) },
                        shape = Palette.controlShape,
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
