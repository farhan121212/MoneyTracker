package com.farhan.paisa.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.balanceOf
import com.farhan.paisa.domain.cardStatus
import com.farhan.paisa.domain.cashOnHand
import com.farhan.paisa.domain.totalCardDebt
import com.farhan.paisa.domain.totalOwedToYou
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.IconBadge
import com.farhan.paisa.ui.components.LargeTitle
import com.farhan.paisa.ui.components.Meter
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.ScreenPadding
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.StatTile
import com.farhan.paisa.ui.components.dueLabel
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate

@Composable
fun WalletScreen(ledger: Ledger, nav: Nav) {
    val today = remember { LocalDate.now() }
    val accounts = ledger.activeAccounts
    val cards = accounts.filter { it.kind == AccountKind.CREDIT_CARD }
    val savings = accounts.filter { it.kind != AccountKind.CREDIT_CARD && it.role == AccountRole.SAVINGS }
    val everyday = accounts.filter { it.kind != AccountKind.CREDIT_CARD && it.role == AccountRole.SPENDING }

    Column(Modifier.fillMaxSize().background(Pop.Ink)) {
        LargeTitle("Wallet") {
            CircleButton(Icons.Rounded.Add, "Add account", { nav.editAccount() })
        }
        LazyColumn(contentPadding = ScreenPadding) {
            item {
                Text("Money you have", style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                Text(Money.whole(ledger.cashOnHand()), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Owed on cards", Money.whole(ledger.totalCardDebt()), Modifier.weight(1f))
                    StatTile("Owed to you", Money.whole(ledger.totalOwedToYou()), Modifier.weight(1f))
                }
            }
            accountSection("Savings", savings, ledger, nav)
            accountSection("Everyday", everyday, ledger, nav)
            if (cards.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Credit cards")
                    Group {
                        cards.forEachIndexed { index, card ->
                            if (index > 0) RowDivider(start = 68.dp)
                            CardRow(ledger, card, today) { nav.account(card.id) }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Group {
                    GroupRow(
                        title = "Goals",
                        subtitle = "What you're saving for",
                        leading = { IconBadge(Icons.Rounded.Flag, Pop.Lavender) },
                        onClick = nav::goals,
                    )
                    RowDivider(start = 68.dp)
                    GroupRow(
                        title = "Recurring bills",
                        subtitle = "Rent, subscriptions and regular payments",
                        leading = { IconBadge(Icons.Rounded.EventRepeat, Pop.Sky) },
                        onClick = nav::bills,
                    )
                    RowDivider(start = 68.dp)
                    GroupRow(
                        title = "People",
                        subtitle = "Splits and who owes you",
                        leading = { IconBadge(Icons.Rounded.Group, Pop.Tangerine) },
                        onClick = nav::people,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountSection(
    title: String,
    accounts: List<Account>,
    ledger: Ledger,
    nav: Nav,
) {
    if (accounts.isEmpty()) return
    item {
        Spacer(Modifier.height(24.dp))
        SectionLabel(title)
        Group {
            accounts.forEachIndexed { index, account ->
                if (index > 0) RowDivider(start = 68.dp)
                GroupRow(
                    title = account.name,
                    subtitle = if (account.last4.isNotBlank()) "•••• ${account.last4}" else null,
                    leading = { IconBadge(iconFor(account), Pop.accent(account.accent)) },
                    trailing = { Text(Money.whole(ledger.balanceOf(account)), style = MaterialTheme.typography.titleSmall) },
                    onClick = { nav.account(account.id) },
                )
            }
        }
    }
}

@Composable
private fun CardRow(ledger: Ledger, card: Account, today: LocalDate, onClick: () -> Unit) {
    val status = ledger.cardStatus(card, today)
    Column {
        GroupRow(
            title = card.name,
            subtitle = dueLabel(status.overdue, status.daysToDue, status.billed) ?: "No bill due",
            leading = { IconBadge(iconFor(card), Pop.accent(card.accent)) },
            trailing = { Text(Money.whole(status.owed), style = MaterialTheme.typography.titleSmall) },
            onClick = onClick,
        )
        if (card.creditLimit > 0) {
            Row(
                Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Meter(
                    value = status.owed.toFloat() / card.creditLimit,
                    color = Pop.accent(card.accent),
                    height = 4.dp,
                )
            }
        }
    }
}
