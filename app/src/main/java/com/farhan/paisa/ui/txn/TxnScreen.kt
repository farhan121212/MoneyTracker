package com.farhan.paisa.ui.txn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.localDate
import com.farhan.paisa.domain.myShare
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.Avatar
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.TxnBadge
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.components.titleOf
import com.farhan.paisa.ui.theme.Pop
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxnScreen(vm: MainViewModel, ledger: Ledger, nav: Nav, id: Long) {
    val txn = ledger.txnById[id] ?: return
    var confirmDelete by remember { mutableStateOf(false) }
    val shares = ledger.sharesByTxn[id].orEmpty()
    val tags = ledger.tagIdsByTxn[id].orEmpty().mapNotNull { ledger.tagById[it] }

    FullScreen {
        NavHeader(
            title = "",
            onBack = { nav.back() },
            trailing = { CircleButton(Icons.Rounded.Edit, "Edit", { nav.add(edit = id) }) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TxnBadge(ledger, txn, size = 64.dp)
                Spacer(Modifier.height(12.dp))
                Text(ledger.titleOf(txn), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(Money.format(txn.amount), style = MaterialTheme.typography.displayMedium)
                Text(
                    txn.localDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Pop.TextSecondary,
                )
            }
            Spacer(Modifier.height(24.dp))
            Group {
                val rows = buildList {
                    add((if (txn.type == TxType.EXPENSE) "Paid from" else if (txn.type == TxType.TRANSFER) "From" else "Into") to ledger.accountById[txn.accountId]?.name.orEmpty())
                    if (txn.type == TxType.TRANSFER) add("To" to ledger.accountById[txn.toAccountId]?.name.orEmpty())
                    if (txn.type == TxType.EXPENSE || txn.type == TxType.TRANSFER) add("Paid by" to txn.method.label)
                    ledger.categoryById[txn.categoryId]?.let { add("Category" to it.name) }
                    if (txn.note.isNotBlank()) add("Note" to txn.note)
                }
                rows.forEachIndexed { index, (label, value) ->
                    if (index > 0) RowDivider()
                    GroupRow(
                        title = label,
                        trailing = { Text(value, style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary, textAlign = TextAlign.End) },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        Text(
                            "#${tag.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Pop.OnPop,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Pop.Lavender)
                                .press { nav.filtered(tag = tag.id) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            if (shares.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("Split")
                Group {
                    GroupRow(
                        title = "Your share",
                        trailing = { Text(Money.format(ledger.myShare(txn)), style = MaterialTheme.typography.titleSmall) },
                    )
                    shares.forEach { share ->
                        val person = ledger.personById[share.personId] ?: return@forEach
                        RowDivider()
                        GroupRow(
                            title = person.name,
                            leading = { Avatar(person.name, person.id, size = 32.dp) },
                            trailing = { Text(Money.format(share.amount), style = MaterialTheme.typography.titleSmall) },
                            onClick = { nav.person(person.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Delete",
                style = MaterialTheme.typography.titleSmall,
                color = Pop.Danger,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .press { confirmDelete = true }
                    .padding(vertical = 16.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("Balances update right away. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    nav.back()
                    vm.write { it.deleteTxn(id) }
                }) { Text("Delete", color = Pop.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
