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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Weekend
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.Cycles
import com.farhan.paisa.domain.Highlight
import com.farhan.paisa.domain.HighlightKind
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.NONE
import com.farhan.paisa.domain.OTHER
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.SAVED
import com.farhan.paisa.domain.byCategory
import com.farhan.paisa.domain.byMethod
import com.farhan.paisa.domain.byPayee
import com.farhan.paisa.domain.cycles
import com.farhan.paisa.domain.dailySpend
import com.farhan.paisa.domain.elapsed
import com.farhan.paisa.domain.highlights
import com.farhan.paisa.domain.incomeIn
import com.farhan.paisa.domain.moneyFlow
import com.farhan.paisa.domain.refundsIn
import com.farhan.paisa.domain.runningTotal
import com.farhan.paisa.domain.savedIn
import com.farhan.paisa.domain.spendMix
import com.farhan.paisa.domain.spentIn
import com.farhan.paisa.domain.tagStats
import com.farhan.paisa.domain.tagsIn
import com.farhan.paisa.domain.untaggedSpend
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
import com.farhan.paisa.ui.components.Segmented
import com.farhan.paisa.ui.components.StatTile
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.press
import com.farhan.paisa.ui.components.rememberToday
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val TOP_CATEGORIES = 7

private enum class Tab(val label: String) { OVERVIEW("Overview"), CALENDAR("Calendar"), TAGS("Tags") }

@Composable
fun InsightsScreen(ledger: Ledger, settings: Settings, nav: Nav) {
    val today = rememberToday()
    val cycles = remember(ledger, settings) { ledger.cycles(settings) }
    val current = cycles.around(today)
    var chosenStart by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    val period = chosenStart?.let { cycles.around(LocalDate.parse(it)) } ?: current
    val previous = cycles.before(period)
    val spent = remember(ledger, period) { ledger.spentIn(period) }

    Column(Modifier.fillMaxSize().background(Pop.Ink)) {
        LargeTitle("Insights")
        LazyColumn(contentPadding = ScreenPadding) {
            item {
                PeriodSwitcher(
                    period = period,
                    canGoForward = period.end.isBefore(current.start),
                    onBack = { chosenStart = previous.start.toString() },
                    onForward = { chosenStart = cycles.after(period).start.toString() },
                )
                Spacer(Modifier.height(10.dp))
                Segmented(Tab.entries.map { it to it.label }, tab, { tab = it })
            }
            when (tab) {
                Tab.OVERVIEW -> {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Spent", Money.compact(spent), Modifier.weight(1f))
                            StatTile("Earned", Money.compact(ledger.incomeIn(period)), Modifier.weight(1f))
                            StatTile("Saved", Money.compact(ledger.savedIn(period)), Modifier.weight(1f))
                        }
                        val refunded = ledger.refundsIn(period).sumOf { it.txn.amount }
                        if (refunded > 0) {
                            Text(
                                "Spent is after ${Money.whole(refunded)} of refunds",
                                style = MaterialTheme.typography.bodySmall,
                                color = Pop.TextSecondary,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            )
                        }
                    }
                    if (spent == 0L) {
                        item { EmptyState("Nothing to show", "Log some spending to see it here.") }
                    } else {
                        if (period.end.isBefore(today)) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Group {
                                    GroupRow(
                                        title = "See this month's recap",
                                        leading = { IconBadge(Icons.Rounded.AutoAwesome, Pop.Lavender) },
                                        onClick = { nav.recap(period.token) },
                                    )
                                }
                            }
                        }
                        item { HighlightsSection(ledger, period, previous, today) }
                        item { MixSection(ledger, period, today) }
                        item { CurveSection(ledger, settings, period, previous, today) }
                        item { FlowSection(ledger, period) }
                        item { CategorySection(ledger, period, nav) }
                        item { PayeeSection(ledger, period, nav) }
                        item { MethodSection(ledger, period) }
                    }
                    item { MonthsSection(ledger, settings, cycles, period) }
                }
                Tab.CALENDAR -> item { CalendarPanel(ledger, settings, period, today, nav) }
                Tab.TAGS -> item { TagsTab(ledger, period, previous, nav) }
            }
        }
    }
}

@Composable
private fun PeriodSwitcher(period: Period, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", onBack)
        Text(
            period.label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (canGoForward) {
            CircleButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", onForward)
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

@Composable
internal fun Panel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp))
    }
    Spacer(Modifier.height(10.dp))
    Group { Box(Modifier.padding(16.dp)) { content() } }
}

private fun iconOf(kind: HighlightKind): Pair<ImageVector, Color> = when (kind) {
    HighlightKind.PACE -> Icons.Rounded.Timeline to Pop.Lime
    HighlightKind.FIXED -> Icons.Rounded.Bolt to Pop.Sky
    HighlightKind.FREQUENT -> Icons.Rounded.Repeat to Pop.Tangerine
    HighlightKind.PEAK -> Icons.Rounded.CalendarMonth to Pop.Pink
    HighlightKind.WEEKEND -> Icons.Rounded.Weekend to Pop.Lavender
    HighlightKind.SPLIT -> Icons.Rounded.Group to Pop.Mint
}

@Composable
private fun HighlightsSection(ledger: Ledger, period: Period, previous: Period, today: LocalDate) {
    val found: List<Highlight> = remember(ledger, period, today) { ledger.highlights(period, previous, today) }
    if (found.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    SectionLabel("What stands out")
    Group {
        found.forEachIndexed { index, highlight ->
            if (index > 0) RowDivider(start = 68.dp)
            val (icon, tint) = iconOf(highlight.kind)
            GroupRow(
                title = highlight.title,
                subtitle = highlight.detail,
                leading = { IconBadge(icon, tint) },
            )
        }
    }
}

/** Rent dwarfs everything else, so it's split out for the rest of the month to be readable. */
@Composable
private fun MixSection(ledger: Ledger, period: Period, today: LocalDate) {
    val mix = remember(ledger, period) { ledger.spendMix(period) }
    if (mix.total == 0L) return
    val days = period.elapsed(today).coerceAtLeast(1)
    Panel("Bills and everyday spending") {
        Column {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Everyday", style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary)
                    Text(Money.whole(mix.everyday), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "About ${Money.whole(mix.everyday / days)} a day",
                        style = MaterialTheme.typography.labelMedium,
                        color = Pop.TextMuted,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Rent and bills", style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary)
                    Text(Money.whole(mix.fixed), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${mix.fixed * 100 / mix.total}% of spending",
                        style = MaterialTheme.typography.labelMedium,
                        color = Pop.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            StackedBar(listOf(mix.everyday to Pop.Lime, mix.fixed to Pop.ChartNeutral).filter { it.first > 0 })
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(Pop.Lime, "Everyday")
                LegendDot(Pop.ChartNeutral, "Rent and bills")
            }
        }
    }
}

@Composable
private fun CurveSection(ledger: Ledger, settings: Settings, period: Period, previous: Period, today: LocalDate) {
    val current = remember(ledger, period) { ledger.dailySpend(period).runningTotal() }
    val before = remember(ledger, previous) { ledger.dailySpend(previous).runningTotal() }
    Panel("How the month is going", "Total spent so far, day by day") {
        SpendCurve(current, before, settings.monthlyBudget, period.elapsed(today), period.start)
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
private fun CategorySection(ledger: Ledger, period: Period, nav: Nav) {
    val slices = remember(ledger, period) { ledger.byCategory(period) }
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
            val tags = ledger.tagsIn(slice.key, period).take(3).joinToString("  ") {
                "#${ledger.tagById[it.key]?.name.orEmpty()} ${Money.compact(it.amount)}"
            }
            val summary = if (limit > 0) {
                "${Money.whole(slice.amount)} of ${Money.whole(limit)}"
            } else {
                "${slice.count} ${if (slice.count == 1) "entry" else "entries"} · ${slice.amount * 100 / total}%"
            }
            RowDivider(start = if (index == 0) 0.dp else 68.dp)
            Column {
                GroupRow(
                    title = category?.name ?: "Uncategorised",
                    subtitle = if (tags.isEmpty()) summary else "$summary\n$tags",
                    leading = { IconBadge(iconFor(category), Pop.categoryPop(category?.color ?: -1)) },
                    trailing = { Text(Money.whole(slice.amount), style = MaterialTheme.typography.titleSmall) },
                    onClick = if (category != null) {
                        { nav.filtered(category = category.id, period = period.token) }
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
private fun PayeeSection(ledger: Ledger, period: Period, nav: Nav) {
    val payees = remember(ledger, period) { ledger.byPayee(period).take(5) }
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
                onClick = { nav.filtered(query = payee.payee, period = period.token) },
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
private fun MonthsSection(ledger: Ledger, settings: Settings, cycles: Cycles, period: Period) {
    val months = remember(ledger, period) { cycles.lastMonths(period, 6) }
    fun name(month: Period) = month.mainMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    Panel("Spent each month") {
        ColumnBars(months.map { name(it) to ledger.spentIn(it) }, 0, color = Pop.Lime)
    }
    Panel("Saved each month", if (settings.savingsTarget > 0) "Orange line is your ${Money.whole(settings.savingsTarget)} goal" else null) {
        ColumnBars(months.map { name(it) to ledger.savedIn(it) }, settings.savingsTarget)
    }
}

/** How spending divides between tags, and how each one moved since last month. */
@Composable
private fun TagsTab(ledger: Ledger, period: Period, previous: Period, nav: Nav) {
    val stats = remember(ledger, period) { ledger.tagStats(period, previous) }
    val spent = ledger.spentIn(period)
    val untagged = remember(ledger, period) { ledger.untaggedSpend(period) }
    if (stats.isEmpty()) {
        EmptyState("No tags this month", "Add a tag like #football while logging a payment to follow it here.")
        return
    }
    val tagged = spent - untagged
    Panel("Tagged spending") {
        Column {
            Text(Money.whole(tagged), style = MaterialTheme.typography.titleLarge)
            Text(
                "of ${Money.whole(spent)} has a tag",
                style = MaterialTheme.typography.bodySmall,
                color = Pop.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Meter(value = tagged.toFloat() / spent.coerceAtLeast(1), color = Pop.Lavender)
            Text(
                "A payment with two tags counts under both.",
                style = MaterialTheme.typography.labelMedium,
                color = Pop.TextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    val peak = maxOf(stats.maxOf { it.amount }, stats.maxOf { it.previous })
    Spacer(Modifier.height(24.dp))
    SectionLabel("Your tags")
    Group {
        stats.forEachIndexed { index, stat ->
            if (index > 0) RowDivider()
            Column(
                Modifier
                    .fillMaxWidth()
                    .press { nav.tag(stat.tagId) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${ledger.tagById[stat.tagId]?.name.orEmpty()}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(Money.whole(stat.amount), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Meter(
                    value = stat.amount.toFloat() / peak,
                    color = Pop.Lavender,
                    height = 5.dp,
                    marker = (stat.previous.toFloat() / peak).takeIf { stat.previous > 0 },
                    markerColor = Pop.TextSecondary,
                )
                Text(
                    tagSummary(stat.count, stat.amount, stat.previous),
                    style = MaterialTheme.typography.bodySmall,
                    color = Pop.TextSecondary,
                )
            }
        }
    }
}

private fun tagSummary(count: Int, amount: Long, previous: Long): String {
    val times = if (count == 1) "Once" else "$count times · about ${Money.whole(amount / count)} each"
    val change = amount - previous
    return when {
        previous == 0L -> times
        change > 0 -> "$times · ${Money.whole(change)} more than last month"
        change < 0 -> "$times · ${Money.whole(-change)} less than last month"
        else -> times
    }
}
