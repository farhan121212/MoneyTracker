package com.farhan.paisa.domain

import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val FIXED_CATEGORY_ICONS = setOf("home", "bills", "subscriptions")

/**
 * Rent and bills are decided once a month, while everything else is decided day to day. A single
 * rent payment can be most of a month's spending and would hide every pattern in the rest.
 */
fun Ledger.isFixed(txn: Txn): Boolean =
    categoryById[txn.categoryId]?.icon in FIXED_CATEGORY_ICONS || recurring.any { matchesBill(txn, it) }

data class SpendMix(val fixed: Long, val everyday: Long) {
    val total: Long get() = fixed + everyday
}

fun Ledger.spendMix(period: Period): SpendMix {
    var fixed = 0L
    var everyday = 0L
    for (txn in txnsIn(period)) {
        if (isFixed(txn)) fixed += myShare(txn) else everyday += myShare(txn)
    }
    for (refund in refundsIn(period)) {
        if (refund.original?.let { isFixed(it) } == true) fixed -= refund.txn.amount else everyday -= refund.txn.amount
    }
    return SpendMix(fixed.coerceAtLeast(0), everyday.coerceAtLeast(0))
}

/** Days of [period] that have happened by [today]. */
fun Period.elapsed(today: LocalDate): Int =
    (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(0, days)

/** Everyday spending per day of a period, leaving out rent and bills. */
fun Ledger.everydaySpend(period: Period): LongArray {
    val days = LongArray(period.days)
    for (txn in txnsIn(period)) {
        val day = ChronoUnit.DAYS.between(period.start, txn.localDate()).toInt()
        when {
            isRefund(txn) -> if (originalOf(txn)?.let { isFixed(it) } != true) days[day] -= txn.amount
            !isFixed(txn) -> days[day] += myShare(txn)
        }
    }
    return days
}

/** Average everyday spend for each weekday, Monday first, counting only days that have happened. */
fun Ledger.weekdayAverages(period: Period, today: LocalDate): LongArray {
    val daily = everydaySpend(period)
    val totals = LongArray(7)
    val counts = IntArray(7)
    for (index in 0 until period.elapsed(today)) {
        val weekday = period.start.plusDays(index.toLong()).dayOfWeek.value - 1
        totals[weekday] += daily[index]
        counts[weekday] += 1
    }
    return LongArray(7) { if (counts[it] == 0) 0 else totals[it] / counts[it] }
}

/**
 * Ranks each day into five shades, 0 for no spending and 4 for the highest. Ranking by position
 * rather than by amount means one rent day doesn't leave the rest of the month looking blank.
 */
fun heatLevels(daily: LongArray): IntArray {
    val spending = daily.filter { it > 0 }.sorted()
    if (spending.isEmpty()) return IntArray(daily.size)
    fun at(share: Double) = spending[((spending.size - 1) * share).toInt()]
    val low = at(0.25)
    val middle = at(0.5)
    val high = at(0.75)
    return IntArray(daily.size) { index ->
        val amount = daily[index]
        when {
            amount <= 0L -> 0
            amount <= low -> 1
            amount <= middle -> 2
            amount <= high -> 3
            else -> 4
        }
    }
}

enum class HighlightKind { PACE, FIXED, FREQUENT, PEAK, SPLIT, WEEKEND }

data class Highlight(val kind: HighlightKind, val title: String, val detail: String)

/** The few things most worth knowing about a period, in plain sentences. */
fun Ledger.highlights(period: Period, previous: Period, today: LocalDate): List<Highlight> {
    val txns = txnsIn(period)
    if (txns.none { myShare(it) > 0 }) return emptyList()
    val elapsed = period.elapsed(today)
    val mix = spendMix(period)
    return listOfNotNull(
        paceHighlight(period, previous, elapsed),
        fixedHighlight(mix, elapsed),
        frequentHighlight(period),
        peakHighlight(period),
        weekendHighlight(period, today),
        splitHighlight(txns),
    )
}

private fun Ledger.paceHighlight(period: Period, previous: Period, elapsed: Int): Highlight? {
    if (elapsed == 0) return null
    val now = dailySpend(period).take(elapsed).sum()
    val before = dailySpend(previous).take(elapsed).sum()
    if (before == 0L) return null
    val change = now - before
    val title = when {
        change > 0 -> "${Money.whole(change)} more than last month so far"
        change < 0 -> "${Money.whole(-change)} less than last month so far"
        else -> "Level with last month so far"
    }
    return Highlight(HighlightKind.PACE, title, "${Money.whole(now)} against ${Money.whole(before)} by day $elapsed")
}

private fun fixedHighlight(mix: SpendMix, elapsed: Int): Highlight? {
    if (mix.fixed == 0L || mix.total == 0L) return null
    val perDay = if (elapsed > 0) mix.everyday / elapsed else 0
    return Highlight(
        HighlightKind.FIXED,
        "${mix.fixed * 100 / mix.total}% went to rent and bills",
        "Everyday spending is ${Money.whole(mix.everyday)}, about ${Money.whole(perDay)} a day",
    )
}

private fun Ledger.frequentHighlight(period: Period): Highlight? {
    val top = byPayee(period).filter { it.count >= 3 }.maxByOrNull { it.amount } ?: return null
    return Highlight(
        HighlightKind.FREQUENT,
        "${top.count} payments to ${top.payee}",
        "${Money.whole(top.amount)} in total, about ${Money.whole(top.amount / top.count)} each",
    )
}

private fun Ledger.peakHighlight(period: Period): Highlight? {
    val daily = everydaySpend(period)
    val top = daily.indices.maxByOrNull { daily[it] } ?: return null
    if (daily[top] == 0L || daily.count { it > 0 } < 2) return null
    val date = period.start.plusDays(top.toLong())
    return Highlight(
        HighlightKind.PEAK,
        "${date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))} cost the most",
        "${Money.whole(daily[top])} of everyday spending",
    )
}

private fun Ledger.weekendHighlight(period: Period, today: LocalDate): Highlight? {
    // A pattern needs a full week or two to mean anything.
    if (period.elapsed(today) < 14) return null
    val averages = weekdayAverages(period, today)
    val weekdays = averages.take(5).average()
    val weekend = averages.drop(5).average()
    if (weekdays <= 0 || weekend <= 0) return null
    val ratio = weekend / weekdays
    if (ratio in 0.8..1.25) return null
    val title = if (ratio > 1) "Weekends cost more" else "Weekdays cost more"
    return Highlight(
        HighlightKind.WEEKEND,
        title,
        "${Money.whole(weekend.toLong())} a day on weekends, ${Money.whole(weekdays.toLong())} on weekdays",
    )
}

private fun Ledger.splitHighlight(txns: List<Txn>): Highlight? {
    val split = txns.filter { it.type == TxType.EXPENSE && it.id in sharesByTxn }
    if (split.isEmpty()) return null
    val covered = split.sumOf { txn -> sharesByTxn[txn.id].orEmpty().sumOf { it.amount } }
    return Highlight(
        HighlightKind.SPLIT,
        "Splitting saved you ${Money.whole(covered)}",
        "${split.size} ${if (split.size == 1) "bill" else "bills"} shared with others",
    )
}

data class TagStat(val tagId: Long, val amount: Long, val count: Int, val previous: Long)

fun Ledger.tagStats(period: Period, previous: Period): List<TagStat> {
    val before = byTag(previous).associateBy { it.key }
    return byTag(period).map { TagStat(it.key, it.amount, it.count, before[it.key]?.amount ?: 0) }
}

/** Spending with no tag at all. */
fun Ledger.untaggedSpend(period: Period): Long {
    val spent = txnsIn(period).filter { tagIdsByTxn[it.id].isNullOrEmpty() }.sumOf { myShare(it) }
    val refunded = refundsIn(period).filter { tagIdsByTxn[it.original?.id].isNullOrEmpty() }.sumOf { it.txn.amount }
    return (spent - refunded).coerceAtLeast(0)
}

/** Your tags often act as sub-categories, such as #cab inside Transport. */
fun Ledger.tagsIn(categoryId: Long, period: Period): List<Slice> =
    txnsIn(period).filter { it.categoryId == categoryId && myShare(it) > 0 }
        .flatMap { txn -> tagIdsByTxn[txn.id].orEmpty().map { it to txn } }
        .groupBy({ it.first }, { it.second })
        .map { (key, items) -> Slice(key, items.sumOf { myShare(it) }, items.size) }
        .sortedByDescending { it.amount }

fun Ledger.taggedWith(tagId: Long): List<Txn> = txns.filter { tagId in tagIdsByTxn[it.id].orEmpty() }

fun Ledger.spentOnTag(tagId: Long, period: Period): Long = byTag(period).firstOrNull { it.key == tagId }?.amount ?: 0

/** Roughly how many days apart a tag's payments are, once there are enough to say. */
fun Ledger.tagRhythm(tagId: Long): Int? {
    val days = taggedWith(tagId).map { it.localDate() }.distinct().sorted()
    if (days.size < 3) return null
    return (ChronoUnit.DAYS.between(days.first(), days.last()) / (days.size - 1)).toInt().coerceAtLeast(1)
}

data class Share(val name: String, val amount: Long, val count: Int)

/** Who a tag's money usually goes to. */
fun Ledger.topPayeesFor(tagId: Long, limit: Int = 4): List<Share> =
    taggedWith(tagId).filter { it.payee.isNotBlank() && myShare(it) > 0 }
        .groupBy { it.payee.trim().lowercase() }
        .map { (_, items) -> Share(items.first().payee.trim(), items.sumOf { myShare(it) }, items.size) }
        .sortedByDescending { it.amount }
        .take(limit)

/** A tag you nearly always use with a category, so it can be filled in for you. */
fun Ledger.usualTagFor(categoryId: Long): Long? {
    val recent = txns.asSequence().filter { it.categoryId == categoryId }.take(30).toList()
    if (recent.size < 2) return null
    val best = recent.flatMap { tagIdsByTxn[it.id].orEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }
    return best?.takeIf { it.value >= 2 && it.value * 2 > recent.size }?.key
}

/** The person you split with most, offered as a one-tap 50/50. */
fun Ledger.usualSplitPartner(today: LocalDate): Long? {
    val since = today.minusDays(60)
    return shares
        .filter { share -> txnById[share.txnId]?.localDate()?.isAfter(since) == true }
        .groupingBy { it.personId }
        .eachCount()
        .filterValues { it >= 2 }
        .maxByOrNull { it.value }
        ?.key
}

/** Categories you use most come first, so the one you want is usually already on screen. */
fun Ledger.categoriesByUse(income: Boolean): List<com.farhan.paisa.data.Category> {
    val uses = txns.asSequence().take(300).mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
    return categories
        .filter { it.income == income && !it.archived }
        .sortedWith(compareByDescending<com.farhan.paisa.data.Category> { uses[it.id] ?: 0 }.thenBy { it.sortOrder })
}

val WEEKDAY_LETTERS: List<String> = DayOfWeek.entries.map { it.name.take(1) }
