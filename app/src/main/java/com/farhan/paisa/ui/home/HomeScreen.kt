package com.farhan.paisa.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.BudgetStatus
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.billsAhead
import com.farhan.paisa.domain.billsFor
import com.farhan.paisa.domain.budgetStatus
import com.farhan.paisa.domain.cardStatus
import com.farhan.paisa.domain.owedBy
import com.farhan.paisa.domain.savedIn
import com.farhan.paisa.domain.spentIn
import com.farhan.paisa.domain.totalOwedToYou
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.bills.dueText
import com.farhan.paisa.ui.components.AccountCard
import com.farhan.paisa.ui.components.AddAccountCard
import com.farhan.paisa.ui.components.CardShape
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.Meter
import com.farhan.paisa.ui.components.Ring
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.TxnRow
import com.farhan.paisa.ui.components.dueLabel
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.settings.rememberSmsEnabler
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel, ledger: Ledger, settings: Settings, nav: Nav) {
    val today = remember { LocalDate.now() }
    val month = YearMonth.from(today)
    val period = Period.of(month)
    val spent = ledger.spentIn(period)
    val billsAhead = ledger.billsAhead(today)
    val status = budgetStatus(settings.monthlyBudget, spent, today, billsAhead)
    val accounts = ledger.activeAccounts.sortedBy { if (it.kind == AccountKind.CREDIT_CARD) 1 else 0 }
    val enableSms = rememberSmsEnabler(vm)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Pop.Ink),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item { Header(settings.name, onSettings = nav::settings) }
        item {
            Hero(
                monthName = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                status = status,
                billsAhead = billsAhead,
                onSetLimit = nav::settings,
            )
        }
        item { ReviewBanner(ledger.toReview.size, nav::review) }
        if (!settings.smsEnabled && !settings.smsPromptDismissed) {
            item {
                SmsPrompt(
                    onEnable = enableSms,
                    onDismiss = { vm.write { it.settings.update { current -> current.copy(smsPromptDismissed = true) } } },
                )
            }
        }
        item { QuickActions(ledger, today, nav) }
        item {
            Spacer(Modifier.height(24.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(accounts, key = { it.id }) { account ->
                    AccountCard(
                        ledger = ledger,
                        account = account,
                        today = today,
                        modifier = Modifier.width(264.dp).height(160.dp),
                        onClick = { nav.account(account.id) },
                    )
                }
                item {
                    AddAccountCard(Modifier.width(96.dp).height(160.dp)) { nav.editAccount() }
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Tiles(ledger, settings, period, nav)
        }
        item { CardDueNotices(ledger, today, nav) }
        item { BillNotices(ledger, today, nav) }
        item {
            Spacer(Modifier.height(24.dp))
            SectionLabel(
                text = "Latest",
                action = if (ledger.txns.isEmpty()) null else "See all",
                onAction = { nav.tab("activity") },
                modifier = Modifier.padding(horizontal = Gutter),
            )
            Group(Modifier.padding(horizontal = Gutter)) {
                val recent = ledger.txns.take(5)
                if (recent.isEmpty()) {
                    EmptyState("Nothing logged yet", "Tap the plus button after your next payment.")
                }
                recent.forEachIndexed { index, txn ->
                    if (index > 0) RowDivider(start = 68.dp)
                    TxnRow(ledger, txn) { nav.txn(txn.id) }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, onSettings: () -> Unit) {
    val hour = remember { LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = Pop.TextSecondary)
            Text(
                name.ifBlank { "there" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleButton(Icons.Rounded.Settings, "Settings", onSettings)
    }
}

@Composable
private fun Hero(monthName: String, status: BudgetStatus, billsAhead: Long, onSetLimit: () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter)) {
        if (status.budget == 0L) {
            Text("Spent in $monthName", style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary)
            Text(Money.whole(status.spent), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Set a monthly limit",
                style = MaterialTheme.typography.labelLarge,
                color = Pop.Lime,
                modifier = Modifier.press(onClick = onSetLimit).padding(vertical = 6.dp),
            )
            return@Column
        }
        val over = status.left < 0
        Text(
            if (over) "Over your limit for $monthName" else "Left to spend in $monthName",
            style = MaterialTheme.typography.bodyLarge,
            color = Pop.TextSecondary,
        )
        Text(Money.whole(if (over) -status.left else status.left), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Meter(
            value = status.used,
            color = if (over) Pop.Danger else Pop.Lime,
            marker = status.monthGone,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint, text) = paceMessage(status)
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary, modifier = Modifier.weight(1f))
            Text(
                "${Money.whole(status.spent)} of ${Money.whole(status.budget)}",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.TextMuted,
            )
        }
        if (billsAhead > 0) {
            Text(
                "After ${Money.whole(billsAhead)} of bills still to come",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    if (count == 0) return
    Row(
        Modifier
            .padding(start = Gutter, end = Gutter, top = 16.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Pop.Lime)
            .press(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count == 1) "1 payment to review" else "$count payments to review",
            style = MaterialTheme.typography.titleSmall,
            color = Pop.OnPop,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Pop.OnPop, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SmsPrompt(onEnable: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(start = Gutter, end = Gutter, top = 16.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Pop.Surface)
            .padding(16.dp),
    ) {
        Text("Find payments automatically", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "Paisa can spot payments in your bank SMS for you to confirm. Messages stay on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Pop.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Turn on", selected = true, onClick = onEnable)
            Chip("Not now", selected = false, onClick = onDismiss)
        }
    }
}

/** Bills coming up in the next week, with a quick way to log them as paid. */
@Composable
private fun BillNotices(ledger: Ledger, today: LocalDate, nav: Nav) {
    val soon = ledger.billsFor(today).filter { !it.paid && it.daysToDue <= 7 }.take(3)
    soon.forEach { status ->
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .padding(horizontal = Gutter)
                .fillMaxWidth()
                .clip(CardShape)
                .background(Pop.Surface)
                .press { nav.add(bill = status.bill.id) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Pop.Sky),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.EventRepeat, null, tint = Pop.OnPop, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(status.bill.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dueText(status), style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary)
            }
            Text(Money.whole(status.bill.amount), style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun paceMessage(status: BudgetStatus): Triple<ImageVector, Color, String> = when {
    status.left < 0 -> Triple(Icons.Rounded.WarningAmber, Pop.Danger, "Limit used up")
    status.aheadOfPace > 0 -> Triple(
        Icons.Rounded.WarningAmber,
        Pop.Warning,
        "${Money.whole(status.perDay)} a day to stay on limit",
    )
    else -> Triple(Icons.Rounded.CheckCircle, Pop.Good, "${Money.whole(status.perDay)} a day is safe")
}

@Composable
private fun QuickActions(ledger: Ledger, today: LocalDate, nav: Nav) {
    Row(
        Modifier.padding(start = Gutter, end = Gutter, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionPill("Save", Icons.Rounded.Savings, Modifier.weight(1f)) {
            val spending = ledger.activeAccounts.firstOrNull { it.kind == AccountKind.BANK && it.role == AccountRole.SPENDING }
            val savings = ledger.activeAccounts.firstOrNull { it.role == AccountRole.SAVINGS }
            nav.add(TxType.TRANSFER, from = spending?.id ?: 0, to = savings?.id ?: 0)
        }
        ActionPill("Pay card", Icons.Rounded.CreditCard, Modifier.weight(1f)) {
            val cards = ledger.activeAccounts.filter { it.kind == AccountKind.CREDIT_CARD }
            val card = cards.maxByOrNull { ledger.cardStatus(it, today).owed }
            if (card == null) {
                nav.editAccount()
            } else {
                val spending = ledger.activeAccounts.firstOrNull { it.kind == AccountKind.BANK && it.role == AccountRole.SPENDING }
                nav.add(TxType.TRANSFER, from = spending?.id ?: 0, to = card.id, amount = ledger.cardStatus(card, today).owed)
            }
        }
        ActionPill("Splits", Icons.Rounded.Group, Modifier.weight(1f)) { nav.people() }
    }
}

@Composable
private fun ActionPill(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Pop.Surface)
            .press(onClick = onClick)
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Pop.Text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Tiles(ledger: Ledger, settings: Settings, period: Period, nav: Nav) {
    val saved = ledger.savedIn(period)
    val owed = ledger.totalOwedToYou()
    Row(
        Modifier.padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .height(132.dp)
                .clip(CardShape)
                .background(Pop.Lavender)
                .press(onClick = nav::goals)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "Saved this month",
                    style = MaterialTheme.typography.labelLarge,
                    color = Pop.OnPop,
                    modifier = Modifier.weight(1f),
                )
                if (settings.savingsTarget > 0) {
                    Ring(
                        value = saved.toFloat() / settings.savingsTarget,
                        color = Pop.OnPop,
                        track = Pop.OnPop.copy(alpha = 0.14f),
                        modifier = Modifier.size(34.dp),
                        stroke = 6.dp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(Money.whole(saved), style = MaterialTheme.typography.titleLarge, color = Pop.OnPop, maxLines = 1)
            Text(
                if (settings.savingsTarget > 0) "of ${Money.whole(settings.savingsTarget)} goal" else "No goal set",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.OnPopSoft,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .height(132.dp)
                .clip(CardShape)
                .background(Pop.Tangerine)
                .press(onClick = nav::people)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Owed to you",
                    style = MaterialTheme.typography.labelLarge,
                    color = Pop.OnPop,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Pop.OnPop, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(Money.whole(owed), style = MaterialTheme.typography.titleLarge, color = Pop.OnPop, maxLines = 1)
            val waiting = ledger.people.count { ledger.owedBy(it.id) > 0 }
            Text(
                when (waiting) {
                    0 -> "All settled"
                    1 -> "From 1 person"
                    else -> "From $waiting people"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Pop.OnPopSoft,
            )
        }
    }
}

/** Card bills coming up in the next week, so a due date is never a surprise. */
@Composable
private fun CardDueNotices(ledger: Ledger, today: LocalDate, nav: Nav) {
    val dueSoon = ledger.activeAccounts
        .filter { it.kind == AccountKind.CREDIT_CARD }
        .map { it to ledger.cardStatus(it, today) }
        .filter { (_, status) -> status.billed > 0 && (status.daysToDue ?: 99) <= 7 }
    dueSoon.forEach { (card, status) ->
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .padding(horizontal = Gutter)
                .fillMaxWidth()
                .clip(CardShape)
                .background(Pop.Surface)
                .press { nav.account(card.id) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Pop.Pink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CreditCard, null, tint = Pop.OnPop, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${card.name} bill", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    dueLabel(status.overdue, status.daysToDue, status.billed).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Pop.TextSecondary,
                )
            }
            Text(Money.whole(status.billed), style = MaterialTheme.typography.titleSmall)
        }
    }
}
