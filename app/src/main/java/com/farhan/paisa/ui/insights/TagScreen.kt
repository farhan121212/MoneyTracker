package com.farhan.paisa.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.cycles
import com.farhan.paisa.domain.myShare
import com.farhan.paisa.domain.spentOnTag
import com.farhan.paisa.domain.tagRhythm
import com.farhan.paisa.domain.taggedWith
import com.farhan.paisa.domain.topPayeesFor
import com.farhan.paisa.domain.txnsIn
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.FullScreen
import com.farhan.paisa.ui.components.Group
import com.farhan.paisa.ui.components.GroupRow
import com.farhan.paisa.ui.components.NavHeader
import com.farhan.paisa.ui.components.PushedPadding
import com.farhan.paisa.ui.components.RowDivider
import com.farhan.paisa.ui.components.SectionLabel
import com.farhan.paisa.ui.components.StatTile
import com.farhan.paisa.ui.components.TxnRow
import com.farhan.paisa.ui.theme.Pop
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Everything about one tag: what it costs, how often, how that's moving, and who gets paid. */
@Composable
fun TagScreen(ledger: Ledger, settings: Settings, nav: Nav, id: Long) {
    val tag = ledger.tagById[id] ?: return
    val today = remember { LocalDate.now() }
    val cycles = remember(ledger, settings) { ledger.cycles(settings) }
    val period = cycles.around(today)
    val months = remember(ledger, id) { cycles.lastMonths(period, 6) }
    val thisMonth = ledger.spentOnTag(id, period)
    val entries = remember(ledger, id) { ledger.taggedWith(id) }
    val monthEntries = ledger.txnsIn(period).count { it in entries && ledger.myShare(it) > 0 }
    val rhythm = remember(ledger, id) { ledger.tagRhythm(id) }
    val payees = remember(ledger, id) { ledger.topPayeesFor(id) }

    FullScreen {
        NavHeader("#${tag.name}", { nav.back() })
        LazyColumn(contentPadding = PushedPadding) {
            item {
                Text("This month", style = MaterialTheme.typography.bodyLarge, color = Pop.TextSecondary, modifier = Modifier.padding(start = 4.dp))
                Text(Money.whole(thisMonth), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Times this month", monthEntries.toString(), Modifier.weight(1f))
                    StatTile(
                        "How often",
                        rhythm?.let { if (it == 1) "Daily" else "Every $it days" } ?: "Too early",
                        Modifier.weight(1f),
                    )
                }
                Panel("Month by month") {
                    ColumnBars(
                        months.map { it.mainMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to ledger.spentOnTag(id, it) },
                        0,
                        peakOnly = true,
                    )
                }
            }
            if (payees.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Usually paid to")
                    Group {
                        payees.forEachIndexed { index, payee ->
                            if (index > 0) RowDivider()
                            GroupRow(
                                title = payee.name,
                                subtitle = "${payee.count} ${if (payee.count == 1) "payment" else "payments"}",
                                trailing = { Text(Money.whole(payee.amount), style = MaterialTheme.typography.titleSmall) },
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                SectionLabel(
                    text = "Recent",
                    action = if (entries.size > 10) "See all" else null,
                    onAction = { nav.filtered(tag = id) },
                )
                Group {
                    entries.take(10).forEachIndexed { index, txn ->
                        if (index > 0) RowDivider(start = 68.dp)
                        TxnRow(ledger, txn) { nav.txn(txn.id) }
                    }
                }
            }
        }
    }
}
