package com.farhan.paisa.domain

import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val RECAP_SHOWS_FOR_DAYS = 10L
private const val RECAP_MIN_ENTRIES = 5

data class Recap(
    val period: Period,
    val spent: Long,
    val earned: Long,
    val saved: Long,
    val everyday: Long,
    val fixed: Long,
    val perDay: Long,
    val previousSpent: Long,
    val topCategory: Slice?,
    val topPayee: PayeeTotal?,
    val topTag: Slice?,
    val busiestDay: LocalDate?,
    val busiestAmount: Long,
    val noSpendDays: Int,
    val entries: Int,
    val splitSaved: Long,
    val limitsKept: Int,
    val limitsSet: Int,
)

fun Ledger.recap(period: Period, previous: Period): Recap {
    val mix = spendMix(period)
    val everydayByDay = everydaySpend(period)
    val busiest = everydayByDay.indices.maxByOrNull { everydayByDay[it] }?.takeIf { everydayByDay[it] > 0 }
    val entries = txnsIn(period).filter { myShare(it) > 0 }
    val limits = limitStatuses(period)
    return Recap(
        period = period,
        spent = spentIn(period),
        earned = incomeIn(period),
        saved = savedIn(period),
        everyday = mix.everyday,
        fixed = mix.fixed,
        perDay = mix.everyday / period.days,
        previousSpent = spentIn(previous),
        topCategory = byCategory(period).firstOrNull(),
        topPayee = byPayee(period).firstOrNull(),
        topTag = byTag(period).firstOrNull(),
        busiestDay = busiest?.let { period.start.plusDays(it.toLong()) },
        busiestAmount = busiest?.let { everydayByDay[it] } ?: 0,
        noSpendDays = dailySpend(period).count { it <= 0 },
        entries = entries.size,
        splitSaved = entries.sumOf { txn -> sharesByTxn[txn.id].orEmpty().sumOf { it.amount } },
        limitsKept = limits.count { it.left >= 0 },
        limitsSet = limits.size,
    )
}

/** Last month's recap is offered at the start of a new month, once there's enough to say. */
fun Ledger.recapDue(today: LocalDate, current: Period, previous: Period, settings: Settings): Boolean =
    settings.recapSeen != previous.token &&
        ChronoUnit.DAYS.between(current.start, today) in 0 until RECAP_SHOWS_FOR_DAYS &&
        txnsIn(previous).count { myShare(it) > 0 } >= RECAP_MIN_ENTRIES
