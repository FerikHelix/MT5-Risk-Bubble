package com.ferik.mt5risk.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.ferik.mt5risk.AppGraph
import com.ferik.mt5risk.core.SymbolVolumeRules
import com.ferik.mt5risk.core.VolumeRule
import com.ferik.mt5risk.service.BubbleForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab { SETUP, RULES }

@Composable
fun MainScreens() {
    var tab by remember { mutableStateOf(Tab.SETUP) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("MT5 Risk Bubble", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.color(Palette.TEXT))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            TabButton("Setup", selected = tab == Tab.SETUP) { tab = Tab.SETUP }
            TabButton("Minimum lots", selected = tab == Tab.RULES) { tab = Tab.RULES }
        }
        when (tab) {
            Tab.SETUP -> SetupScreen()
            Tab.RULES -> RulesScreen()
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = {}, colors = Palette.primaryButton()) { Text(label, fontSize = Palette.bodySize) }
    } else {
        OutlinedButton(onClick = onClick, colors = Palette.outlinedButton()) { Text(label, fontSize = Palette.bodySize) }
    }
}

// --- setup ----------------------------------------------------------------------------------

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val disclosureAccepted by AppGraph.uiPrefs.disclosureAccepted.collectAsState(initial = false)
    var statuses by remember { mutableStateOf(readStatuses(context)) }
    var running by remember { mutableStateOf(BubbleForegroundService.running) }

    // Settings screens are left and come back, so poll instead of waiting for a callback.
    LaunchedEffect(Unit) {
        while (true) {
            statuses = readStatuses(context)
            running = BubbleForegroundService.running
            delay(1_000)
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        statuses = readStatuses(context)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!disclosureAccepted) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = Palette.screenCard()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Before you turn anything on",
                            fontWeight = FontWeight.Bold,
                            fontSize = Palette.bodySize,
                            color = Palette.color(Palette.TEXT),
                        )
                        Text(SetupLogic.DISCLOSURE, fontSize = Palette.secondarySize, color = Palette.color(Palette.TEXT_SECONDARY))
                        Button(
                            onClick = { scope.launch { AppGraph.uiPrefs.acceptDisclosure() } },
                            colors = Palette.primaryButton(),
                        ) { Text("I understand", fontSize = Palette.bodySize) }
                    }
                }
            }
        }

        items(SetupItem.entries.toList()) { item ->
            val status = statuses[item] ?: SetupStatus.MISSING
            Card(modifier = Modifier.fillMaxWidth(), colors = Palette.screenCard()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = Palette.bodySize,
                            color = Palette.color(Palette.TEXT),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            statusLabel(status),
                            color = statusColor(status),
                            fontWeight = FontWeight.Bold,
                            fontSize = Palette.bodySize,
                        )
                    }
                    Text(item.why, fontSize = Palette.secondarySize, color = Palette.color(Palette.TEXT_SECONDARY))
                    val locked = item == SetupItem.ACCESSIBILITY && !disclosureAccepted
                    if (status != SetupStatus.READY) {
                        OutlinedButton(
                            enabled = !locked,
                            colors = Palette.outlinedButton(),
                            onClick = {
                                if (item == SetupItem.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openSettingsFor(context, item)
                                }
                            },
                        ) { Text(if (locked) "Read the notice above first" else "Open settings", fontSize = Palette.bodySize) }
                    }
                    if (item == SetupItem.ACCESSIBILITY && status != SetupStatus.READY) {
                        TextButton(onClick = { openAppInfo(context) }, colors = Palette.textButton()) {
                            Text("Open App info (Allow restricted settings)", fontSize = Palette.secondarySize)
                        }
                    }
                }
            }
        }

        item {
            val canStart = SetupLogic.canStart(statuses)
            Button(
                enabled = canStart || running,
                modifier = Modifier.fillMaxWidth(),
                colors = Palette.primaryButton(),
                onClick = {
                    if (running) BubbleForegroundService.stop(context) else BubbleForegroundService.start(context)
                },
            ) { Text(SetupLogic.startButtonLabel(statuses, running), fontSize = Palette.bodySize) }
        }
    }
}

private fun readStatuses(context: Context): Map<SetupItem, SetupStatus> {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return mapOf(
        SetupItem.OVERLAY to ready(Settings.canDrawOverlays(context)),
        SetupItem.ACCESSIBILITY to ready(AppGraph.isAccessibilityConnected),
        SetupItem.NOTIFICATIONS to ready(NotificationManagerCompat.from(context).areNotificationsEnabled()),
        SetupItem.POPUPS to SetupStatus.CHECK_MANUALLY,
        SetupItem.AUTOSTART to SetupStatus.CHECK_MANUALLY,
        SetupItem.BATTERY to ready(power.isIgnoringBatteryOptimizations(context.packageName)),
    )
}

private fun ready(ok: Boolean) = if (ok) SetupStatus.READY else SetupStatus.MISSING

private fun statusLabel(status: SetupStatus) = when (status) {
    SetupStatus.READY -> "Ready"
    SetupStatus.MISSING -> "Missing"
    SetupStatus.CHECK_MANUALLY -> "Check yourself"
}

private fun statusColor(status: SetupStatus) = when (status) {
    SetupStatus.READY -> Palette.color(Palette.STATUS_READY)
    SetupStatus.MISSING -> Palette.color(Palette.STATUS_MISSING)
    SetupStatus.CHECK_MANUALLY -> Palette.color(Palette.STATUS_CHECK)
}

private fun openSettingsFor(context: Context, item: SetupItem) {
    val pkg = context.packageName
    val intent = when (item) {
        SetupItem.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))
        SetupItem.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        SetupItem.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        SetupItem.POPUPS -> Intent("miui.intent.action.APP_PERM_EDITOR")
            .setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"))
            .putExtra("extra_pkgname", pkg)
        SetupItem.AUTOSTART -> Intent()
            .setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        SetupItem.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
    startOrFallBack(context, intent)
}

private fun openAppInfo(context: Context) {
    startOrFallBack(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

/** MIUI screens differ between versions, so any shortcut may be missing: fall back to App info. */
private fun startOrFallBack(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: SecurityException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// --- saved symbol rules ----------------------------------------------------------------------

@Composable
private fun RulesScreen() {
    val scope = rememberCoroutineScope()
    val rules by AppGraph.ruleStore.rules.collectAsState(initial = SymbolVolumeRules())
    var editing by remember { mutableStateOf<Pair<String, VolumeRule>?>(null) }

    if (rules.all().isEmpty()) {
        Text(
            "No symbols saved yet. The first time you tap the bubble on a symbol it asks for its minimum lot, " +
                "and the answer is listed here.",
            fontSize = Palette.bodySize,
            color = Palette.color(Palette.TEXT_SECONDARY),
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rules.all()) { (symbol, rule) ->
            Card(modifier = Modifier.fillMaxWidth(), colors = Palette.screenCard()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(symbol, fontWeight = FontWeight.Bold, fontSize = Palette.bodySize, color = Palette.color(Palette.TEXT))
                    Text(
                        RuleEditLogic.summary(rule.min, rule.step, rule.max),
                        fontSize = Palette.secondarySize,
                        color = Palette.color(Palette.TEXT_SECONDARY),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = symbol to rule }, colors = Palette.outlinedButton()) {
                            Text("Edit", fontSize = Palette.bodySize)
                        }
                        OutlinedButton(
                            onClick = { scope.launch { AppGraph.ruleStore.delete(symbol) } },
                            colors = Palette.outlinedButton(),
                        ) { Text("Delete", fontSize = Palette.bodySize) }
                    }
                }
            }
        }
    }

    editing?.let { (symbol, rule) ->
        EditRuleDialog(
            symbol = symbol,
            rule = rule,
            onSave = { edit ->
                scope.launch { AppGraph.ruleStore.edit(symbol, edit.min, edit.max) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EditRuleDialog(
    symbol: String,
    rule: VolumeRule,
    onSave: (RuleEditLogic.Edit) -> Unit,
    onDismiss: () -> Unit,
) {
    var minText by remember { mutableStateOf(rule.min.stripTrailingZeros().toPlainString()) }
    var maxText by remember { mutableStateOf(rule.max.stripTrailingZeros().toPlainString()) }
    val edit = RuleEditLogic.parse(minText, maxText)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.color(Palette.SCREEN_CARD),
        titleContentColor = Palette.color(Palette.TEXT),
        textContentColor = Palette.color(Palette.TEXT),
        title = { Text(symbol, fontSize = Palette.titleSize, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text("Minimum lot (also the step)") },
                    singleLine = true,
                    colors = Palette.textField(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text("Maximum lot") },
                    singleLine = true,
                    isError = edit == null,
                    colors = Palette.textField(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = edit != null, onClick = { edit?.let(onSave) }, colors = Palette.textButton()) {
                Text("Save", fontSize = Palette.bodySize)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = Palette.textButton()) { Text("Cancel", fontSize = Palette.bodySize) }
        },
    )
}

@Suppress("unused")
private val keepMaterialTheme = MaterialTheme
