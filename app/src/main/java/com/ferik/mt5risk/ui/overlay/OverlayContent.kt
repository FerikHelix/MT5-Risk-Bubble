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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
        val balanceLabel: String,
        val balanceIsManual: Boolean,
        val lastUsed: BigDecimal?,
    ) : PickerUi
}

@Composable
fun PickerContent(
    state: PickerUi,
    onMinLot: (BigDecimal) -> Unit,
    onRisk: (BigDecimal) -> Unit,
    onManualBalance: (BigDecimal) -> Unit,
    onClearManualBalance: () -> Unit,
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
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state) {
                        is PickerUi.AskMinLot -> MinLotQuestion(state, onMinLot)
                        is PickerUi.ChooseRisk -> RiskChoices(state, onRisk, onManualBalance, onClearManualBalance)
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
    onRisk: (BigDecimal) -> Unit,
    onManualBalance: (BigDecimal) -> Unit,
    onClearManualBalance: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    val customValue = PickerLogic.parseCustomRisk(custom)
    val manualValue = PickerLogic.parseManualBalance(manual)

    Text(
        "Risk for ${state.symbol}",
        fontSize = Palette.titleSize,
        fontWeight = FontWeight.Bold,
        color = Palette.color(Palette.TEXT),
    )
    Text(state.balanceLabel, fontSize = Palette.secondarySize, color = Palette.color(Palette.TEXT_SECONDARY))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (preset in PickerLogic.RISK_PRESETS) {
            val highlighted = PickerLogic.isLastUsed(preset, state.lastUsed)
            Button(
                onClick = { onRisk(preset) },
                modifier = Modifier.weight(1f),
                colors = if (highlighted) Palette.selectedChipButton() else Palette.chipButton(),
            ) { Text(PickerLogic.label(preset), fontSize = Palette.bodySize) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("Custom %") },
            singleLine = true,
            isError = custom.isNotBlank() && customValue == null,
            colors = Palette.textField(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { customValue?.let(onRisk) }, enabled = customValue != null, colors = Palette.primaryButton()) {
            Text("Go", fontSize = Palette.bodySize)
        }
    }

    if (!showManual) {
        TextButton(onClick = { showManual = true }, colors = Palette.textButton()) {
            Text("Set balance manually", fontSize = Palette.bodySize)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
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
