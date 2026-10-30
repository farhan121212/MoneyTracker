package com.farhan.paisa.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Backup
import com.farhan.paisa.data.Exports
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.Money
import com.farhan.paisa.sms.SmsSync
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.IconBadge
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.theme.Pop
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: MainViewModel, ledger: Ledger, settings: Settings, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(settings.name) }
    var budget by remember { mutableStateOf(settings.monthlyBudget.takeIf { it > 0 }?.let(Money::editable).orEmpty()) }
    var target by remember { mutableStateOf(settings.savingsTarget.takeIf { it > 0 }?.let(Money::editable).orEmpty()) }
    var pendingRestore by remember { mutableStateOf<Backup?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    var senders by remember { mutableStateOf(settings.extraSenders) }
    val enableSms = rememberSmsEnabler(vm)

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    LaunchedEffect(name, budget, target, senders) {
        vm.repo.settings.update {
            it.copy(
                name = name.trim(),
                monthlyBudget = Money.parse(budget) ?: 0,
                savingsTarget = Money.parse(target) ?: 0,
                extraSenders = senders.trim(),
            )
        }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { Exports.writeBackup(context, uri, vm.repo) }
                    .onSuccess { toast("Backup saved") }
                    .onFailure { toast("Couldn't save the backup") }
            }
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { Exports.writeCsv(context, uri, ledger) }
                    .onSuccess { toast("Spreadsheet saved") }
                    .onFailure { toast("Couldn't save the spreadsheet") }
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { Exports.readBackup(context, uri) }
                    .onSuccess { pendingRestore = it }
                    .onFailure { toast("That isn't a Paisa backup file") }
            }
        }
    }

    FullScreen {
        NavHeader("Settings", { nav.back() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Gutter),
        ) {
            Group {
                GroupTextField(name, { name = it }, "Your name", label = "Name", capitalization = KeyboardCapitalization.Words)
            }
            Caption("Monthly goals")
            Group {
                GroupTextField(budget, { budget = it }, "₹0", label = "Spending limit", keyboardType = KeyboardType.Decimal)
                RowDivider()
                GroupTextField(target, { target = it }, "₹0", label = "Savings goal", keyboardType = KeyboardType.Decimal)
            }
            Caption("Organise")
            Group {
                GroupRow(
                    title = "Categories",
                    subtitle = "Icons, colours and limits",
                    leading = { IconBadge(Icons.Rounded.Category, Pop.Lime) },
                    onClick = nav::categories,
                )
                RowDivider(start = 68.dp)
                GroupRow(
                    title = "Tags",
                    subtitle = "Rename or remove",
                    leading = { IconBadge(Icons.Rounded.Tag, Pop.Lavender) },
                    onClick = nav::tags,
                )
            }
            Caption("Bank SMS")
            Group {
                Toggle("Find payments in SMS", "You review each one. Messages stay on this phone.", settings.smsEnabled) { on ->
                    if (on) {
                        enableSms()
                    } else {
                        vm.write { it.settings.update { current -> current.copy(smsEnabled = false) } }
                    }
                }
                if (settings.smsEnabled) {
                    RowDivider()
                    GroupRow(
                        title = "Bring in past payments",
                        subtitle = "Skip any made before you set your balances, or they count twice",
                        trailing = {
                            Row {
                                listOf(7, 30).forEach { days ->
                                    TextButton(onClick = {
                                        scope.launch {
                                            val found = SmsSync.importPast(context, days)
                                            toast(if (found == 1) "Found 1 payment" else "Found $found payments")
                                        }
                                    }) { Text("$days days") }
                                }
                            }
                        },
                    )
                    RowDivider()
                    GroupTextField(senders, { senders = it.uppercase() }, "e.g. NEWBNK", label = "Other senders", capitalization = KeyboardCapitalization.Characters)
                }
            }
            Caption("Reminders")
            Group {
                Toggle("Daily nudge", "At 9 PM if nothing is logged that day", settings.dailyReminder) { on ->
                    if (on) askForNotifications()
                    vm.write { it.settings.update { current -> current.copy(dailyReminder = on) } }
                }
                RowDivider()
                Toggle("Bills and cards", "Before each due date", settings.dueReminder) { on ->
                    if (on) askForNotifications()
                    vm.write { it.settings.update { current -> current.copy(dueReminder = on) } }
                }
            }
            Caption("Privacy")
            Group {
                Toggle("App lock", "Fingerprint or screen lock to open", settings.lockEnabled) { on ->
                    val available = BiometricManager.from(context)
                        .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
                    if (on && !available) {
                        toast("Set a screen lock on your phone first")
                    } else {
                        vm.write { it.settings.update { current -> current.copy(lockEnabled = on) } }
                    }
                }
            }
            Caption("Your data stays on this phone. Back it up so that a lost phone doesn't lose it.")
            Group {
                GroupRow(title = "Save a backup", onClick = { saveBackup.launch("paisa-backup-${LocalDate.now()}.json") })
                RowDivider()
                GroupRow(title = "Restore a backup", onClick = { openBackup.launch(arrayOf("application/json", "application/octet-stream")) })
                RowDivider()
                GroupRow(title = "Export to spreadsheet", subtitle = "CSV of all entries", onClick = { saveCsv.launch("paisa-${LocalDate.now()}.csv") })
                RowDivider()
                GroupRow(
                    title = "Erase everything",
                    trailing = { Text("Erase", style = MaterialTheme.typography.labelLarge, color = Pop.Danger) },
                    onClick = { confirmErase = true },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all data?") },
            text = { Text("This swaps everything in the app for the ${backup.txns.size} entries in the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    // Leaving the screen drops its edit fields, which still hold the old values.
                    nav.back()
                    vm.write { it.restore(backup) }
                    toast("Backup restored")
                }) { Text("Replace", color = Pop.Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything?") },
            text = { Text("All accounts and entries are deleted from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    vm.write { it.eraseEverything() }
                }) { Text("Erase", color = Pop.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Pop.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    GroupRow(
        title = title,
        subtitle = subtitle,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Pop.Lime, checkedThumbColor = Pop.OnPop),
            )
        },
    )
}
