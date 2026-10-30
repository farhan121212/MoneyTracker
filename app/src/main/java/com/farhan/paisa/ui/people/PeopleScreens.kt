package com.farhan.paisa.ui.people

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.localDate
import com.farhan.paisa.domain.owedBy
import com.farhan.paisa.domain.totalOwedToYou
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.Avatar
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.ChipRow
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.GroupTextField
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.titleOf
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PeopleScreen(vm: MainViewModel, ledger: Ledger, nav: Nav) {
    var adding by remember { mutableStateOf<String?>(null) }
    FullScreen {
        NavHeader("People", { nav.back() })
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Text("Owed to you", style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp))
                Text(Money.whole(ledger.totalOwedToYou()), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(16.dp))
            }
            item {
                Group {
                    if (ledger.people.isEmpty()) {
                        EmptyState("No one here yet", "People show up when you split a bill with them.")
                    }
                    ledger.people
                        .sortedByDescending { ledger.owedBy(it.id) }
                        .forEachIndexed { index, person ->
                            if (index > 0) RowDivider(start = 68.dp)
                            val owed = ledger.owedBy(person.id)
                            GroupRow(
                                title = person.name,
                                subtitle = when {
                                    owed > 0 -> "Owes you"
                                    owed < 0 -> "Paid you extra"
                                    else -> "All settled"
                                },
                                leading = { Avatar(person.name, person.id) },
                                trailing = {
                                    if (owed != 0L) Text(Money.format(owed), style = MaterialTheme.typography.titleSmall)
                                },
                                onClick = { nav.person(person.id) },
                            )
                        }
                }
                Spacer(Modifier.height(12.dp))
                if (adding == null) {
                    PrimaryButton("Add a person", { adding = "" }, color = Pop.Surface, contentColor = Pop.Text)
                } else {
                    Group {
                        GroupTextField(
                            value = adding.orEmpty(),
                            onValueChange = { adding = it },
                            placeholder = "Name",
                            capitalization = KeyboardCapitalization.Words,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Save",
                        enabled = !adding.isNullOrBlank(),
                        onClick = {
                            val name = adding.orEmpty().trim()
                            adding = null
                            vm.write { it.addPerson(name) }
                        },
                    )
                }
            }
        }
    }
}

private sealed interface Entry {
    val date: LocalDate

    data class Share(val txn: Txn, val amount: Long) : Entry {
        override val date get() = txn.localDate()
    }

    data class Paid(val txn: Txn) : Entry {
        override val date get() = txn.localDate()
    }
}

@Composable
fun PersonScreen(vm: MainViewModel, ledger: Ledger, nav: Nav, id: Long) {
    val person = ledger.personById[id] ?: return
    val context = LocalContext.current
    var settling by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val owed = ledger.owedBy(id)
    val entries: List<Entry> = (
        ledger.shares.filter { it.personId == id }.mapNotNull { share ->
            ledger.txnById[share.txnId]?.let { Entry.Share(it, share.amount) }
        } + ledger.txns.filter { it.type == TxType.SETTLEMENT && it.personId == id }.map { Entry.Paid(it) }
        ).sortedByDescending { it.date }

    FullScreen {
        NavHeader(
            title = person.name,
            onBack = { nav.back() },
            trailing = { CircleButton(Icons.Rounded.Delete, "Delete", { confirmDelete = true }) },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(person.name, person.id, size = 72.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (owed > 0) "Owes you" else if (owed < 0) "Paid you extra" else "All settled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Pop.TextSecondary,
                    )
                    Text(Money.format(if (owed < 0) -owed else owed), style = MaterialTheme.typography.displayMedium)
                }
                Spacer(Modifier.height(20.dp))
                if (owed > 0) {
                    PrimaryButton("Record a payment", { settling = true })
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Send a reminder",
                        color = Pop.Surface,
                        contentColor = Pop.Text,
                        onClick = {
                            val message = "Hey ${person.name}, you owe me ${Money.format(owed)}. Could you send it over when you get a chance?"
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                            context.startActivity(Intent.createChooser(send, "Send reminder"))
                        },
                    )
                }
                Spacer(Modifier.height(24.dp))
                SectionLabel("History")
                Group {
                    if (entries.isEmpty()) {
                        EmptyState("No splits yet", "Bills you split with ${person.name} show up here.")
                    }
                    val format = DateTimeFormatter.ofPattern("d MMM yyyy")
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) RowDivider()
                        when (entry) {
                            is Entry.Share -> GroupRow(
                                title = ledger.titleOf(entry.txn),
                                subtitle = "Their share · ${entry.date.format(format)}",
                                trailing = { Text(Money.format(entry.amount), style = MaterialTheme.typography.titleSmall) },
                                onClick = { nav.txn(entry.txn.id) },
                            )
                            is Entry.Paid -> GroupRow(
                                title = "Paid you back",
                                subtitle = entry.date.format(format),
                                trailing = {
                                    Text(Money.format(-entry.txn.amount), style = MaterialTheme.typography.titleSmall, color = Pop.Good)
                                },
                                onClick = { nav.txn(entry.txn.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (settling) {
        SettleSheet(ledger, person.name, owed, onDismiss = { settling = false }) { amount, accountId ->
            val txn = Txn(
                type = TxType.SETTLEMENT,
                amount = amount,
                accountId = accountId,
                personId = id,
                date = LocalDate.now().atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            vm.write { it.saveTxn(txn, emptyList(), emptyList()) }
            settling = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${person.name}?") },
            text = { Text("Their shares become your own spending and any payments from them are removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    nav.back()
                    vm.write { it.deletePerson(person) }
                }) { Text("Delete", color = Pop.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleSheet(
    ledger: Ledger,
    name: String,
    owed: Long,
    onDismiss: () -> Unit,
    onSave: (amount: Long, accountId: Long) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accounts = ledger.activeAccounts.filter { it.kind != AccountKind.CREDIT_CARD }
    var amount by remember { mutableStateOf(Money.editable(owed)) }
    var accountId by remember {
        mutableStateOf((accounts.firstOrNull { it.role == AccountRole.SPENDING } ?: accounts.firstOrNull())?.id)
    }
    val parsed = Money.parse(amount) ?: 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Pop.Ink) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Gutter)) {
            Text("$name paid you", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "This goes into your account but doesn't count as income, because it's your own money coming back.",
                style = MaterialTheme.typography.bodyMedium,
                color = Pop.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Group {
                GroupTextField(amount, { amount = it }, "₹0", label = "Amount", keyboardType = KeyboardType.Decimal)
            }
            Text(
                "Into",
                style = MaterialTheme.typography.labelMedium,
                color = Pop.TextSecondary,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
            )
            ChipRow(accounts, selected = accounts.indexOfFirst { it.id == accountId }) { account ->
                Chip(
                    label = account.name,
                    icon = iconFor(account),
                    selected = account.id == accountId,
                    selectedColor = Pop.accent(account.accent),
                    onClick = { accountId = account.id },
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = "Save",
                enabled = parsed > 0 && accountId != null,
                onClick = { accountId?.let { onSave(parsed, it) } },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
