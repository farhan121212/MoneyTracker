package com.farhan.paisa.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.farhan.paisa.MainViewModel
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.NONE
import com.farhan.paisa.domain.Period
import com.farhan.paisa.domain.Recap
import com.farhan.paisa.domain.cycles
import com.farhan.paisa.domain.recap
import com.farhan.paisa.ui.Nav
import com.farhan.paisa.ui.components.CardShape
import com.farhan.paisa.ui.components.CircleButton
import com.farhan.paisa.ui.components.Gutter
import com.farhan.paisa.ui.components.drawCardArt
import com.farhan.paisa.ui.theme.Pop
import java.time.format.DateTimeFormatter

private class RecapPage(val color: Color, val art: Int, val label: String, val big: String, val lines: List<String>)

private fun Ledger.pagesFor(recap: Recap): List<RecapPage> = buildList {
    val month = recap.period.label
    val change = recap.spent - recap.previousSpent
    add(
        RecapPage(
            Pop.Lime, 0, "You spent in $month", Money.whole(recap.spent),
            listOfNotNull(
                "${recap.entries} payments",
                "Earned ${Money.whole(recap.earned)}",
                when {
                    recap.previousSpent == 0L -> null
                    change > 0 -> "${Money.whole(change)} more than the month before"
                    change < 0 -> "${Money.whole(-change)} less than the month before"
                    else -> "The same as the month before"
                },
            ),
        ),
    )
    add(
        RecapPage(
            Pop.Lavender, 2, "Everyday spending", Money.whole(recap.everyday),
            listOf("About ${Money.whole(recap.perDay)} a day", "Rent and bills were another ${Money.whole(recap.fixed)}"),
        ),
    )
    recap.topCategory?.let { top ->
        val name = categoryById[top.key]?.name?.takeIf { top.key != NONE } ?: "Uncategorised"
        add(
            RecapPage(
                Pop.Tangerine, 5, "Most went to", name,
                listOfNotNull(
                    Money.whole(top.amount),
                    recap.topPayee?.let { "Top payee: ${it.payee}, ${Money.whole(it.amount)}" },
                    recap.topTag?.let { tag -> tagById[tag.key]?.let { "Top tag: #${it.name}, ${Money.whole(tag.amount)}" } },
                ),
            ),
        )
    }
    add(
        RecapPage(
            Pop.Pink, 3, "No-spend days", recap.noSpendDays.toString(),
            listOfNotNull(
                recap.busiestDay?.let { "Busiest day was ${it.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))}, ${Money.whole(recap.busiestAmount)}" },
                "Splitting saved you ${Money.whole(recap.splitSaved)}".takeIf { recap.splitSaved > 0 },
            ),
        ),
    )
    add(
        RecapPage(
            Pop.Sky, 7, "You saved", Money.whole(recap.saved),
            listOfNotNull(
                "${recap.limitsKept} of ${recap.limitsSet} limits kept".takeIf { recap.limitsSet > 0 },
                if (recap.saved > 0) "That's money that stays yours" else "A new month is a fresh start",
            ),
        ),
    )
}

/** A swipe through the month that just ended, in the same blocks of colour as Home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecapScreen(vm: MainViewModel, ledger: Ledger, settings: Settings, nav: Nav, token: String) {
    val period = remember(token) { Period.parse(token) }
    val previous = remember(ledger, settings, period) { ledger.cycles(settings).before(period) }
    val pages = remember(ledger, period) { ledger.run { pagesFor(recap(period, previous)) } }
    val pager = rememberPagerState { pages.size }

    LaunchedEffect(token) {
        vm.write { it.settings.update { current -> current.copy(recapSeen = token) } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pop.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(if (index <= pager.currentPage) Pop.Text else Pop.Raised),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            CircleButton(Icons.Rounded.Close, "Close", { nav.back() })
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Box(
                Modifier
                    .padding(horizontal = Gutter, vertical = 8.dp)
                    .fillMaxSize()
                    .clip(CardShape)
                    .background(page.color),
            ) {
                // The art scales with its box, so it keeps a card's shape on a page this tall.
                Box(Modifier.fillMaxWidth().aspectRatio(1.2f).drawBehind { drawCardArt(page.art) })
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Spacer(Modifier.weight(1f))
                    Text(page.label, style = MaterialTheme.typography.titleMedium, color = Pop.OnPop)
                    Text(page.big, style = MaterialTheme.typography.displayLarge, color = Pop.OnPop)
                    Spacer(Modifier.height(12.dp))
                    page.lines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyLarge, color = Pop.OnPop, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (index == pages.lastIndex) "See you next month" else "Swipe for more",
                        style = MaterialTheme.typography.labelMedium,
                        color = Pop.OnPopSoft,
                    )
                }
            }
        }
    }
}
