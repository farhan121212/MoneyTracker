package com.farhan.paisa.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.NONE
import com.farhan.paisa.domain.OTHER
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.SAVED
import com.farhan.paisa.domain.byCategory
import com.farhan.paisa.domain.byMethod
import com.farhan.paisa.domain.byPayee
import com.farhan.paisa.domain.byTag
import com.farhan.paisa.domain.dailySpend
import com.farhan.paisa.domain.incomeIn
import com.farhan.paisa.domain.moneyFlow
import com.farhan.paisa.domain.runningTotal
import com.farhan.paisa.domain.savedIn
import com.farhan.paisa.domain.spentIn
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.IconBadge
import com.farhan.paisa.ui.components.LargeTitle
import com.farhan.paisa.ui.components.LegendDot
import com.farhan.paisa.ui.components.Meter
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.ScreenPadding
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.StatTile
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val TOP_CATEGORIES = 7

@Composable
fun InsightsScreen(ledger: Ledger, settings: Settings, nav: Nav) {
    val today = remember { LocalDate.now() }
    val thisMonth = YearMonth.from(today)
    var monthText by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val month = YearMonth.parse(monthText)
    val period = Period.of(month)
    val spent = ledger.spentIn(period)

    Column(Modifier.fillMaxSize().background(Pop.Ink)) {
        LargeTitle("Insights")
        LazyColumn(contentPadding = ScreenPadding) {
            item {
                MonthSwitcher(
                    month = month,
                    canGoForward = month < thisMonth,
                    onChange = { monthText = it.toString() },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Spent", Money.compact(spent), Modifier.weight(1f))
                    StatTile("Earned", Money.compact(ledger.incomeIn(period)), Modifier.weight(1f))
                    StatTile("Saved", Money.compact(ledger.savedIn(period)), Modifier.weight(1f))
                }
            }
            if (spent == 0L) {
                item { EmptyState("Nothing to show", "Log some spending this month to see it here.") }
            } else {
                item { CurveSection(ledger, settings, month, thisMonth, today) }
                item { FlowSection(ledger, period) }
                item { CategorySection(ledger, period, nav, month) }
                item { TagSection(ledger, period, nav, month) }
                item { PayeeSection(ledger, period, nav, month) }
                item { MethodSection(ledger, period) }
                item { CalendarSection(ledger, month) }
            }
            item { SavedSection(ledger, settings, month) }
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoForward: Boolean, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", { onChange(month.minusMonths(1)) })
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (canGoForward) {
            CircleButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", { onChange(month.plusMonths(1)) })
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun Panel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp))
    }
    Spacer(Modifier.height(10.dp))
    Group { Box(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun CurveSection(ledger: Ledger, settings: Settings, month: YearMonth, thisMonth: YearMonth, today: LocalDate) {
    val current = ledger.dailySpend(month).runningTotal()
    val previous = ledger.dailySpend(month.minusMonths(1)).runningTotal()
    val elapsed = if (month == thisMonth) today.dayOfMonth else current.size
    Panel("How the month is going", "Total spent so far, day by day") {
        SpendCurve(current, previous, settings.monthlyBudget, elapsed)
    }
}

@Composable
private fun FlowSection(ledger: Ledger, period: Period) {
    val links = ledger.moneyFlow(period)
    if (links.isEmpty()) return

    val perTarget = links.groupBy { it.target }.mapValues { (_, items) -> items.sumOf { it.amount } }
    val ranked = perTarget.entries.sortedWith(compareBy({ it.key == SAVED }, { -it.value })).map { it.key }
    val shown = ranked.filter { it != SAVED }.take(5).toSet() + setOfNotNull(SAVED.takeIf { it in perTarget })
    val folded = { key: Long -> if (key in shown) key else OTHER }

    val edges = links
        .groupBy { it.accountId to folded(it.target) }
        .map { (key, items) -> FlowEdge(key.first, key.second + TARGET_OFFSET, items.sumOf { it.amount }) }
    val targets = edges.groupBy { it.to }
        .map { (key, items) -> targetNode(ledger, key - TARGET_OFFSET, key, items.sumOf { it.amount }) }
        .sortedWith(compareBy({ it.key == SAVED + TARGET_OFFSET }, { it.key == OTHER + TARGET_OFFSET }, { -it.amount }))
    val sources = edges.groupBy { it.from }
        .map { (id, items) ->
            val account = ledger.accountById[id]
            FlowNode(id, account?.name ?: "Account", items.sumOf { it.amount }, Pop.accent(account?.accent ?: 0))
        }
        .sortedByDescending { it.amount }

    Panel("Money flow", "Which account paid for what. Tap a name to focus on it") {
        MoneyFlowChart(sources, targets, edges)
    }
}

/** Keeps category ids apart from account ids inside one chart. */
private const val TARGET_OFFSET = 1_000_000_000L

private fun targetNode(ledger: Ledger, target: Long, key: Long, amount: Long): FlowNode = when (target) {
    SAVED -> FlowNode(key, "Saved", amount, Pop.Chart[1])
    OTHER, NONE -> FlowNode(key, "Other", amount, Pop.ChartNeutral)
    else -> {
        val category = ledger.categoryById[target]
        FlowNode(key, category?.name ?: "Other", amount, Pop.categoryChart(category?.color ?: -1))
    }
}

@Composable
private fun CategorySection(ledger: Ledger, period: Period, nav: Nav, month: YearMonth) {
    val slices = ledger.byCategory(period)
    if (slices.isEmpty()) return
    val total = slices.sumOf { it.amount }
    val top = slices.take(TOP_CATEGORIES)
    val rest = slices.drop(TOP_CATEGORIES).sumOf { it.amount }
    val donut = top.map { slice ->
        val category = ledger.categoryById[slice.key]
        DonutSlice(slice.key, category?.name ?: "Uncategorised", slice.amount, Pop.categoryChart(category?.color ?: -1))
    } + listOfNotNull(if (rest > 0) DonutSlice(OTHER, "Everything else", rest, Pop.ChartNeutral) else null)

    Spacer(Modifier.height(24.dp))
    SectionLabel("Where it went")
    Group {
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            SpendDonut(donut, Modifier.width(220.dp))
        }
        slices.forEachIndexed { index, slice ->
            val category = ledger.categoryById[slice.key]
            val limit = category?.monthlyLimit ?: 0
            RowDivider(start = if (index == 0) 0.dp else 68.dp)
            Column {
                GroupRow(
                    title = category?.name ?: "Uncategorised",
                    subtitle = if (limit > 0) {
                        "${Money.whole(slice.amount)} of ${Money.whole(limit)}"
                    } else {
                        "${slice.count} ${if (slice.count == 1) "entry" else "entries"} · ${slice.amount * 100 / total}%"
                    },
                    leading = { IconBadge(iconFor(category), Pop.categoryPop(category?.color ?: -1)) },
                    trailing = { Text(Money.whole(slice.amount), style = MaterialTheme.typography.titleSmall) },
                    onClick = if (category != null) {
                        { nav.filtered(category = category.id, month = month.toString()) }
                    } else {
                        null
                    },
                )
                if (limit > 0) {
                    Box(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp)) {
                        Meter(
                            value = slice.amount.toFloat() / limit,
                            color = if (slice.amount > limit) Pop.Danger else Pop.Lime,
                            height = 4.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagSection(ledger: Ledger, period: Period, nav: Nav, month: YearMonth) {
    val tags = ledger.byTag(period)
    if (tags.isEmpty()) return
    val peak = tags.first().amount
    Spacer(Modifier.height(24.dp))
    SectionLabel("Tags")
    Group {
        tags.take(8).forEachIndexed { index, slice ->
            if (index > 0) RowDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .press { nav.filtered(tag = slice.key, month = month.toString()) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${ledger.tagById[slice.key]?.name.orEmpty()}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(Money.whole(slice.amount), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Meter(value = slice.amount.toFloat() / peak, color = Pop.Lavender, height = 4.dp, track = Color.Transparent)
                Text(
                    "${slice.count} ${if (slice.count == 1) "time" else "times"} · about ${Money.whole(slice.amount / slice.count)} each",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pop.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PayeeSection(ledger: Ledger, period: Period, nav: Nav, month: YearMonth) {
    val payees = ledger.byPayee(period).take(5)
    if (payees.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    SectionLabel("Paid the most")
    Group {
        payees.forEachIndexed { index, payee ->
            if (index > 0) RowDivider()
            GroupRow(
                title = payee.payee,
                subtitle = "${payee.count} ${if (payee.count == 1) "payment" else "payments"}",
                trailing = { Text(Money.whole(payee.amount), style = MaterialTheme.typography.titleSmall) },
                onClick = { nav.filtered(query = payee.payee, month = month.toString()) },
            )
        }
    }
}

private val MethodColors = mapOf(
    PayMethod.UPI to Pop.Chart[0],
    PayMethod.CARD to Pop.Chart[1],
    PayMethod.NET_BANKING to Pop.Chart[2],
    PayMethod.AUTO_DEBIT to Pop.Chart[3],
    PayMethod.CASH to Pop.Chart[4],
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodSection(ledger: Ledger, period: Period) {
    val methods = ledger.byMethod(period)
    if (methods.isEmpty()) return
    val total = methods.sumOf { it.second }
    Panel("How you paid") {
        Column {
            StackedBar(methods.map { (method, amount) -> amount to (MethodColors[method] ?: Pop.ChartNeutral) })
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                methods.forEach { (method, amount) ->
                    LegendDot(MethodColors[method] ?: Pop.ChartNeutral, "${method.label} ${amount * 100 / total}%")
                }
            }
        }
    }
}

@Composable
private fun CalendarSection(ledger: Ledger, month: YearMonth) {
    val blanks = month.atDay(1).dayOfWeek.value - 1
    Panel("Spending by day") {
        SpendCalendar(ledger.dailySpend(month), blanks)
    }
}

@Composable
private fun SavedSection(ledger: Ledger, settings: Settings, month: YearMonth) {
    val months = (5 downTo 0).map { back ->
        val each = month.minusMonths(back.toLong())
        each.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to ledger.savedIn(Period.of(each))
    }
    Panel("Saved each month", if (settings.savingsTarget > 0) "Orange line is your ${Money.whole(settings.savingsTarget)} goal" else null) {
        SavedBars(months, settings.savingsTarget)
    }
}
