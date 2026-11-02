package com.farhan.paisa.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.cycles
import com.farhan.paisa.domain.salaryPlan
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.bills.dueText
import com.farhan.paisa.ui.components.CardShape
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.IconBadge
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.drawCardArt
import com.farhan.paisa.ui.insights.StackedBar
import com.farhan.paisa.ui.theme.Pop
import com.farhan.paisa.ui.components.LegendDot
import com.farhan.paisa.ui.components.rememberToday
import java.time.format.DateTimeFormatter

/** The first thing to do with a salary: see what's spoken for and what's really left. */
@Composable
fun SalaryPlanScreen(ledger: Ledger, settings: Settings, nav: Nav) {
    val today = rememberToday()
    val period = remember(ledger, settings, today) { ledger.cycles(settings).around(today) }
    val plan = remember(ledger, period, today, settings.savingsTarget) { ledger.salaryPlan(today, period, settings.savingsTarget) }
    val spending = ledger.activeAccounts.firstOrNull { it.kind == AccountKind.BANK && it.role == AccountRole.SPENDING }
    val savings = ledger.activeAccounts.firstOrNull { it.role == AccountRole.SAVINGS }

    FullScreen {
        NavHeader("Salary plan", { nav.back() })
        LazyColumn(contentPadding = PushedPadding) {
            if (plan == null) {
                item {
                    Group { EmptyState("No salary yet", "Log money in under the Salary category and your plan appears here.") }
                }
                return@LazyColumn
            }
            item {
                val short = plan.everyday < 0
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(if (short) Pop.Tangerine else Pop.Lime)
                        .drawBehind { drawCardArt(1) }
                        .padding(20.dp),
                ) {
                    Text(
                        if (short) "Salary falls short by" else "Left for everyday spending",
                        style = MaterialTheme.typography.labelLarge,
                        color = Pop.OnPop,
                    )
                    Text(
                        Money.whole(if (short) -plan.everyday else plan.everyday),
                        style = MaterialTheme.typography.displayLarge,
                        color = Pop.OnPop,
                    )
                    Text(
                        if (short) "Trim the savings goal or a bill to close the gap" else "About ${Money.whole(plan.perDay)} a day until ${period.end.format(DateTimeFormatter.ofPattern("d MMM"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Pop.OnPopSoft,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Group {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (plan.estimated) {
                                "${Money.whole(plan.salary)}, your last salary. This updates when the next one lands."
                            } else {
                                "${Money.whole(plan.salary)} landed on ${plan.landed.format(DateTimeFormatter.ofPattern("d MMM"))}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        val parts = listOf(
                            Triple("Bills", plan.billsTotal, Pop.Chart[4]),
                            Triple("Cards", plan.cardsTotal, Pop.Chart[3]),
                            Triple("Saving", plan.savingsGoal, Pop.Chart[1]),
                            Triple("Everyday", plan.everyday, Pop.Chart[0]),
                        ).filter { it.second > 0 }
                        StackedBar(parts.map { (_, amount, color) -> amount to color })
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            parts.forEach { (name, _, color) -> LegendDot(color, name) }
                        }
                    }
                }
            }
            if (plan.savingsGoal > 0) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Save first")
                    Group {
                        val remaining = (plan.savingsGoal - plan.saved).coerceAtLeast(0)
                        GroupRow(
                            title = "Savings goal",
                            subtitle = "${Money.whole(plan.saved)} of ${Money.whole(plan.savingsGoal)} moved",
                            leading = { IconBadge(Icons.Rounded.Savings, Pop.Lavender) },
                            trailing = {
                                Step(remaining == 0L, "Move ${Money.compact(remaining)}") {
                                    nav.add(TxType.TRANSFER, from = spending?.id ?: 0, to = savings?.id ?: 0, amount = remaining)
                                }
                            },
                        )
                    }
                }
            }
            if (plan.cards.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Card bills")
                    Group {
                        plan.cards.forEachIndexed { index, line ->
                            if (index > 0) RowDivider(start = 68.dp)
                            GroupRow(
                                title = line.card.name,
                                subtitle = if (line.due > 0) "${Money.whole(line.due)} to pay" else "${Money.whole(line.paid)} paid",
                                leading = { IconBadge(Icons.Rounded.CreditCard, Pop.accent(line.card.accent)) },
                                trailing = {
                                    Step(line.due == 0L, "Pay") {
                                        nav.add(TxType.TRANSFER, from = spending?.id ?: 0, to = line.card.id, amount = line.due)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (plan.bills.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Bills")
                    Group {
                        plan.bills.forEachIndexed { index, status ->
                            if (index > 0) RowDivider(start = 68.dp)
                            GroupRow(
                                title = status.bill.name,
                                subtitle = "${Money.whole(status.bill.amount)} · ${dueText(status)}",
                                leading = { IconBadge(Icons.Rounded.EventRepeat, Pop.Sky) },
                                trailing = { Step(status.paid, "Pay") { nav.add(bill = status.bill.id) } },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Paying here only logs it. Move the money in your bank app as usual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pop.TextMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Step(done: Boolean, label: String, onClick: () -> Unit) {
    if (done) {
        Box(Modifier.padding(end = 8.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CheckCircle, "Done", tint = Pop.Good)
        }
    } else {
        TextButton(onClick = onClick) { Text(label, color = Pop.Lime) }
    }
}
