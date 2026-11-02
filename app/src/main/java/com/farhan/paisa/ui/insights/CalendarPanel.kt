package com.farhan.paisa.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.WEEKDAY_LETTERS
import com.farhan.paisa.domain.billsFor
import com.farhan.paisa.domain.dailySpend
import com.farhan.paisa.domain.elapsed
import com.farhan.paisa.domain.everydaySpend
import com.farhan.paisa.domain.heatLevels
import com.farhan.paisa.domain.localDate
import com.farhan.paisa.domain.spentIn
import com.farhan.paisa.domain.txnsIn
import com.farhan.paisa.domain.weekdayAverages
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.LegendDot
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.StatTile
import com.farhan.paisa.ui.components.TxnRow
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One hue from dim to bright, for how a day ranks against the rest of the month. */
private fun shade(level: Int): Color = when (level) {
    0 -> Pop.Raised
    1 -> Pop.Lime.copy(alpha = 0.2f)
    2 -> Pop.Lime.copy(alpha = 0.42f)
    3 -> Pop.Lime.copy(alpha = 0.7f)
    else -> Pop.Lime
}

private val DayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val CellShape = RoundedCornerShape(10.dp)

@Composable
fun CalendarPanel(ledger: Ledger, settings: Settings, period: Period, today: LocalDate, nav: Nav) {
    val daily = remember(ledger, period) { ledger.dailySpend(period) }
    val levels = remember(daily) { heatLevels(daily) }
    val incomeDays = remember(ledger, period) {
        ledger.txnsIn(period).filter { it.type == TxType.INCOME }.map { it.localDate() }.toSet()
    }
    val billDays = remember(ledger, period, today) {
        ledger.billsFor(today, period).filterNot { it.paid }.map { it.due }.toSet()
    }
    var selected by remember(period) { mutableStateOf(defaultDay(period, today, daily)) }
    val elapsed = period.elapsed(today)

    Spacer(Modifier.height(16.dp))
    Group {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WEEKDAY_LETTERS.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Pop.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val blanks = period.start.dayOfWeek.value - 1
            val cells: List<Int?> = List(blanks) { null } + (0 until period.days).toList()
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { index ->
                        if (index == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val date = period.start.plusDays(index.toLong())
                            DayCell(
                                date = date,
                                amount = daily[index],
                                level = levels[index],
                                future = date.isAfter(today),
                                today = date == today,
                                selected = date == selected,
                                income = date in incomeDays,
                                billDue = date in billDays,
                                onClick = { selected = date },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Legend()
        }
    }

    Spacer(Modifier.height(12.dp))
    DayDetails(ledger, selected, nav)

    val everyday = remember(ledger, period) { ledger.everydaySpend(period) }
    val free = daily.take(elapsed).count { it <= 0L }
    val average = if (elapsed > 0) everyday.take(elapsed).sum() / elapsed else 0
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Daily average", Money.whole(average), Modifier.weight(1f), "Without rent and bills")
        StatTile("No-spend days", free.toString(), Modifier.weight(1f), "So far this month")
    }

    val weekdays = remember(ledger, period, today) { ledger.weekdayAverages(period, today) }
    if (weekdays.any { it > 0 }) {
        Panel("Which days cost the most", "Average per day, without rent and bills") {
            ColumnBars(
                WEEKDAY_LETTERS.mapIndexed { index, letter -> letter to weekdays[index] },
                0,
                color = Pop.Lime,
                peakOnly = true,
            )
        }
    }
}

private fun defaultDay(period: Period, today: LocalDate, daily: LongArray): LocalDate {
    if (today in period) return today
    val busiest = daily.indices.maxByOrNull { daily[it] } ?: 0
    return period.start.plusDays(busiest.toLong())
}

@Composable
private fun DayCell(
    date: LocalDate,
    amount: Long,
    level: Int,
    future: Boolean,
    today: Boolean,
    selected: Boolean,
    income: Boolean,
    billDue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onBright = level >= 3
    val ink = if (onBright) Pop.OnPop else Pop.Text
    Box(
        modifier
            .aspectRatio(0.8f)
            .clip(CellShape)
            .background(if (future) Pop.Raised.copy(alpha = 0.35f) else shade(level))
            .then(
                when {
                    selected -> Modifier.border(2.dp, Pop.Text, CellShape)
                    today -> Modifier.border(1.dp, Pop.TextSecondary, CellShape)
                    else -> Modifier
                },
            )
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
    ) {
        Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (income) Box(Modifier.size(6.dp).clip(CircleShape).background(if (onBright) Pop.OnPop else Pop.Mint))
            if (billDue) Box(Modifier.size(6.dp).clip(CircleShape).background(if (onBright) Pop.OnPop else Pop.Sky))
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 5.dp, end = 2.dp)) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (future) Pop.TextMuted else ink,
                maxLines = 1,
            )
            if (amount > 0) {
                Text(
                    Money.compact(amount).removePrefix("₹"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = if (onBright) Pop.OnPopSoft else Pop.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = Pop.TextMuted)
        Spacer(Modifier.width(4.dp))
        (0..4).forEach { level ->
            Box(Modifier.padding(end = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(shade(level)))
        }
        Spacer(Modifier.width(2.dp))
        Text("More", style = MaterialTheme.typography.labelSmall, color = Pop.TextMuted)
        Spacer(Modifier.weight(1f))
        LegendDot(Pop.Mint, "Money in")
        Spacer(Modifier.width(10.dp))
        LegendDot(Pop.Sky, "Bill due")
    }
    Text(
        "Shades rank your days, so one rent payment doesn't hide the rest.",
        style = MaterialTheme.typography.labelSmall,
        color = Pop.TextMuted,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DayDetails(ledger: Ledger, date: LocalDate, nav: Nav) {
    val entries = remember(ledger, date) { ledger.txnsIn(Period(date, date)) }
    val spent = remember(ledger, date) { ledger.spentIn(Period(date, date)) }
    Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(date.format(DayFormat), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (spent > 0) Text("You spent ${Money.format(spent)}", style = MaterialTheme.typography.bodySmall, color = Pop.TextSecondary)
    }
    Group {
        if (entries.isEmpty()) {
            EmptyState("Nothing this day", "Tap another day to see what you spent.", Modifier.fillMaxWidth())
        }
        entries.forEachIndexed { index, txn ->
            if (index > 0) RowDivider(start = 68.dp)
            TxnRow(ledger, txn) { nav.txn(txn.id) }
        }
    }
}
