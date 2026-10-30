package com.farhan.paisa.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.localDate
import com.farhan.paisa.domain.myShare
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.Chip
import com.farhan.paisa.ui.components.EmptyState
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.LargeTitle
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SearchField
import com.farhan.paisa.ui.components.TxnRow
import com.farhan.paisa.ui.components.iconFor
import com.farhan.paisa.ui.components.subtitleOf
import com.farhan.paisa.ui.components.titleOf
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ActivityFilter(
    val tagId: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val query: String = "",
    /** A year-month like 2026-10, or empty for all time. */
    val month: String = "",
    val pushed: Boolean = false,
)

private enum class Range(val label: String) { THIS_MONTH("This month"), LAST_MONTH("Last month"), ALL("All time") }

@Composable
fun ActivityScreen(ledger: Ledger, nav: Nav, initial: ActivityFilter) {
    var query by rememberSaveable { mutableStateOf(initial.query) }
    var tagId by rememberSaveable { mutableStateOf(initial.tagId) }
    var range by rememberSaveable { mutableStateOf(if (initial.pushed) Range.ALL else Range.THIS_MONTH) }
    var type by rememberSaveable { mutableStateOf<TxType?>(null) }
    val today = remember { LocalDate.now() }

    val period = when {
        initial.month.isNotEmpty() -> Period.of(YearMonth.parse(initial.month))
        range == Range.THIS_MONTH -> Period.of(YearMonth.from(today))
        range == Range.LAST_MONTH -> Period.of(YearMonth.from(today).minusMonths(1))
        else -> null
    }

    val visible = ledger.txns.filter { txn ->
        (period == null || txn.localDate() in period) &&
            (type == null || txn.type == type) &&
            (tagId == null || tagId in ledger.tagIdsByTxn[txn.id].orEmpty()) &&
            (initial.categoryId == null || txn.categoryId == initial.categoryId) &&
            (initial.accountId == null || txn.accountId == initial.accountId || txn.toAccountId == initial.accountId) &&
            (query.isBlank() || ledger.matches(txn, query))
    }
    val narrowed = query.isNotBlank() || tagId != null || type != null || initial.pushed
    val days = visible.groupBy { it.localDate() }.toList()

    Column(Modifier.fillMaxSize().background(Pop.Ink)) {
        if (initial.pushed) {
            val title = ledger.categoryById[initial.categoryId]?.name
                ?: ledger.accountById[initial.accountId]?.name
                ?: ledger.tagById[initial.tagId]?.let { "#${it.name}" }
                ?: initial.query.ifBlank { "Activity" }
            NavHeader(title, { nav.back() })
        } else {
            LargeTitle("Activity")
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                SearchField(query, { query = it }, "Search people, shops, notes", Modifier.padding(horizontal = Gutter))
                Spacer(Modifier.height(12.dp))
                if (initial.month.isEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Range.entries) { option ->
                            Chip(option.label, range == option, { range = option })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Chip("Spent", type == TxType.EXPENSE, { type = if (type == TxType.EXPENSE) null else TxType.EXPENSE }, selectedColor = Pop.Tangerine) }
                    item { Chip("Received", type == TxType.INCOME, { type = if (type == TxType.INCOME) null else TxType.INCOME }, selectedColor = Pop.Mint) }
                    item { Chip("Moved", type == TxType.TRANSFER, { type = if (type == TxType.TRANSFER) null else TxType.TRANSFER }, selectedColor = Pop.Sky) }
                    items(ledger.tags, key = { it.id }) { tag ->
                        Chip("#${tag.name}", tagId == tag.id, { tagId = if (tagId == tag.id) null else tag.id }, selectedColor = Pop.Lavender)
                    }
                }
                if (narrowed && visible.isNotEmpty()) {
                    Summary(ledger, visible)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (visible.isEmpty()) {
                item { EmptyState("Nothing here", if (narrowed) "Try a different filter or search." else "Payments you log show up here.") }
            }

            days.forEach { (date, txns) ->
                item(key = date.toString()) {
                    Column(Modifier.padding(horizontal = Gutter)) {
                        DayHeader(date, today, txns.sumOf { ledger.myShare(it) })
                        Group {
                            txns.forEachIndexed { index, txn ->
                                if (index > 0) RowDivider(start = 68.dp)
                                TxnRow(ledger, txn) { nav.txn(txn.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Ledger.matches(txn: Txn, query: String): Boolean {
    val needle = query.trim()
    return titleOf(txn).contains(needle, ignoreCase = true) ||
        subtitleOf(txn).contains(needle, ignoreCase = true) ||
        txn.note.contains(needle, ignoreCase = true) ||
        tagIdsByTxn[txn.id].orEmpty().any { tagById[it]?.name?.contains(needle.removePrefix("#"), ignoreCase = true) == true }
}

/** The running total for whatever is filtered, such as all turf spending this month. */
@Composable
private fun Summary(ledger: Ledger, visible: List<Txn>) {
    val spent = visible.sumOf { ledger.myShare(it) }
    Row(
        Modifier.padding(start = Gutter + 4.dp, end = Gutter + 4.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (visible.size == 1) "1 entry" else "${visible.size} entries",
            style = MaterialTheme.typography.bodyMedium,
            color = Pop.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (spent > 0) {
            Text("You spent ${Money.format(spent)}", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, spent: Long) {
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Pop.TextSecondary, modifier = Modifier.weight(1f))
        if (spent > 0) {
            Text(Money.format(spent), style = MaterialTheme.typography.labelMedium, color = Pop.TextMuted)
        }
    }
}
