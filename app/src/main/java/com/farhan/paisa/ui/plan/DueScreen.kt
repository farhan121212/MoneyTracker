package com.farhan.paisa.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.DueItem
import com.farhan.paisa.domain.DueKind
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.cycles
import com.farhan.paisa.domain.duePlan
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.IconBadge
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.components.rememberToday
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val RailDay = DateTimeFormatter.ofPattern("d")
private val RailMonth = DateTimeFormatter.ofPattern("MMM")
private val ShortDate = DateTimeFormatter.ofPattern("d MMM")

/** Card bills and regular bills in the order they fall due, and whether today's money reaches them. */
@Composable
fun DueScreen(ledger: Ledger, settings: Settings, nav: Nav) {
    val today = rememberToday()
    val cycles = remember(ledger, settings) { ledger.cycles(settings) }
    val period = cycles.around(today)
    val plan = remember(ledger, period, today) { ledger.duePlan(today, period, cycles.after(period)) }

    FullScreen {
        NavHeader("Coming up", { nav.back() })
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Text("Due by ${period.end.format(ShortDate)}", style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp))
                Text(Money.whole(plan.total), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(8.dp))
                Cover(plan.balance, plan.total, plan.shortBy)
            }
            if (plan.beforeSalary.isEmpty() && plan.afterSalary.isEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Group { EmptyState("Nothing due", "Bills and card payments appear here as they come up.") }
                }
            }
            if (plan.beforeSalary.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Before your next salary")
                    Group {
                        plan.beforeSalary.forEachIndexed { index, step ->
                            if (index > 0) RowDivider(start = 68.dp)
                            DueRow(step.item, today, step.left, onOpen(step.item, nav))
                        }
                    }
                }
            }
            if (plan.afterSalary.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("After your next salary")
                    Group {
                        plan.afterSalary.forEachIndexed { index, item ->
                            if (index > 0) RowDivider(start = 68.dp)
                            DueRow(item, today, null, onOpen(item, nav))
                        }
                    }
                }
            }
            if (plan.undated.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Add dates to place these")
                    Group {
                        plan.undated.forEachIndexed { index, card ->
                            if (index > 0) RowDivider(start = 68.dp)
                            GroupRow(
                                title = card.name,
                                subtitle = "Needs a statement day and a due day",
                                leading = { IconBadge(iconFor(card), Pop.accent(card.accent)) },
                                onClick = { nav.editAccount(card.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun onOpen(item: DueItem, nav: Nav): () -> Unit = {
    if (item.kind == DueKind.BILL) nav.add(bill = item.ownerId) else nav.account(item.ownerId)
}

@Composable
private fun Cover(balance: Long, total: Long, shortBy: Long) {
    val short = shortBy > 0
    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (short) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
            null,
            tint = if (short) Pop.Warning else Pop.Good,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (short) {
                "${Money.whole(shortBy)} short: everyday accounts hold ${Money.whole(balance)}"
            } else {
                "Covered: ${Money.whole(balance - total)} of ${Money.whole(balance)} stays in everyday accounts"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Pop.TextSecondary,
        )
    }
}

@Composable
private fun DueRow(item: DueItem, today: LocalDate, left: Long?, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, item.date).toInt()
    val tint = when (item.kind) {
        DueKind.BILL -> Pop.Sky
        DueKind.CARD_BILL -> Pop.Pink
        DueKind.CARD_NEXT -> Pop.TextMuted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .press(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.date.format(RailDay), style = MaterialTheme.typography.titleMedium, color = if (days < 0) Pop.Danger else Pop.Text)
            Text(item.date.format(RailMonth), style = MaterialTheme.typography.labelSmall, color = Pop.TextMuted)
        }
        Box(Modifier.width(4.dp).height(36.dp).clip(CircleShape).background(tint))
        Column(Modifier.weight(1f)) {
            Text(
                when (item.kind) {
                    DueKind.BILL -> item.name
                    DueKind.CARD_BILL -> "${item.name} bill"
                    DueKind.CARD_NEXT -> "${item.name} next bill so far"
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail(item, days, left),
                style = MaterialTheme.typography.bodySmall,
                color = if (left != null && left < 0) Pop.Danger else Pop.TextSecondary,
            )
        }
        Text(Money.whole(item.amount), style = MaterialTheme.typography.titleSmall)
    }
}

private fun detail(item: DueItem, days: Int, left: Long?): String {
    val timing = when {
        days < 0 -> "Overdue"
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        else -> "In $days days"
    }
    return when {
        item.onCard -> "$timing · goes on your card"
        left == null -> timing
        left < 0 -> "$timing · ${Money.whole(-left)} short"
        else -> "$timing · ${Money.whole(left)} left after"
    }
}
