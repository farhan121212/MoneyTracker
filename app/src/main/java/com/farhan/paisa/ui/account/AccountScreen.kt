package com.farhan.paisa.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.CardStatus
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.accountFlow
import com.farhan.paisa.domain.cardStatus
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.AccountCard
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PrimaryButton
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.StatTile
import com.farhan.paisa.ui.components.TxnRow
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun AccountScreen(ledger: Ledger, nav: Nav, id: Long) {
    val account = ledger.accountById[id] ?: return
    val today = remember { LocalDate.now() }
    val card = account.kind == AccountKind.CREDIT_CARD
    val flow = ledger.accountFlow(account, Period.of(YearMonth.from(today)))
    val txns = ledger.txns.filter { it.accountId == id || it.toAccountId == id }

    FullScreen {
        NavHeader(
            title = account.name,
            onBack = { nav.back() },
            trailing = { CircleButton(Icons.Rounded.Edit, "Edit", { nav.editAccount(id) }) },
        )
        LazyColumn(contentPadding = PushedPadding) {
            item {
                AccountCard(ledger, account, today, Modifier.fillMaxWidth().height(180.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(if (card) "Paid this month" else "In this month", Money.whole(flow.moneyIn), Modifier.weight(1f))
                    StatTile(if (card) "Spent this month" else "Out this month", Money.whole(flow.moneyOut), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                if (card) {
                    val status = ledger.cardStatus(account, today)
                    CardDetails(account, status)
                    Spacer(Modifier.height(12.dp))
                    if (status.owed > 0) {
                        val spending = ledger.activeAccounts.firstOrNull { it.kind == AccountKind.BANK && it.role == AccountRole.SPENDING }
                        PrimaryButton(
                            text = "Pay ${Money.format(status.billed.takeIf { it > 0 } ?: status.owed)}",
                            onClick = {
                                nav.add(
                                    type = TxType.TRANSFER,
                                    from = spending?.id ?: 0,
                                    to = id,
                                    amount = status.billed.takeIf { it > 0 } ?: status.owed,
                                )
                            },
                        )
                    }
                } else {
                    val other = ledger.activeAccounts.firstOrNull { it.id != id && it.kind != AccountKind.CREDIT_CARD }
                    PrimaryButton(
                        text = if (account.role == AccountRole.SAVINGS) "Add to savings" else "Move money",
                        onClick = {
                            if (account.role == AccountRole.SAVINGS) {
                                nav.add(TxType.TRANSFER, from = other?.id ?: 0, to = id)
                            } else {
                                nav.add(TxType.TRANSFER, from = id, to = other?.id ?: 0)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(24.dp))
                SectionLabel(
                    text = "History",
                    action = if (txns.size > 20) "See all" else null,
                    onAction = { nav.filtered(account = id) },
                )
            }
            item {
                Group {
                    if (txns.isEmpty()) {
                        EmptyState("No activity yet", "Payments from this account show up here.")
                    }
                    txns.take(20).forEachIndexed { index, txn ->
                        if (index > 0) RowDivider(start = 68.dp)
                        TxnRow(ledger, txn) { nav.txn(txn.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardDetails(card: Account, status: CardStatus) {
    val format = DateTimeFormatter.ofPattern("d MMM")
    val rows = buildList {
        add("On latest statement" to Money.format(status.billed))
        add("Since then" to Money.format(status.unbilled))
        status.dueDate?.let { add("Due date" to it.format(format)) }
        if (card.creditLimit > 0) add("Available credit" to Money.format(status.available))
    }
    Group {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) RowDivider()
            GroupRow(
                title = label,
                trailing = {
                    Text(value, style = MaterialTheme.typography.titleSmall, color = Pop.TextSecondary)
                },
                modifier = Modifier.padding(0.dp),
            )
        }
    }
}
