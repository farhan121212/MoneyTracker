package com.farhan.paisa.domain

import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Period(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        fun of(month: YearMonth) = Period(month.atDay(1), month.atEndOfMonth())
    }
}

fun Txn.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

/** Compares timestamps, because converting every entry to a date gets slow over the years. */
fun Ledger.txnsIn(period: Period): List<Txn> {
    val zone = ZoneId.systemDefault()
    val from = period.start.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = period.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return txns.filter { it.date >= from && it.date < to }
}

/** Your own part of an expense: what you paid minus what others owe you back for it. */
fun Ledger.myShare(txn: Txn): Long {
    if (txn.type != TxType.EXPENSE) return 0
    val others = sharesByTxn[txn.id]?.sumOf { it.amount } ?: 0
    return (txn.amount - others).coerceAtLeast(0)
}

/**
 * Current money in an account. Credit cards come back negative, by the amount owed.
 *
 * Spending on a card lowers its balance the moment you swipe, and paying the bill is a
 * transfer, so the same money is never counted as spent twice.
 */
fun Ledger.balanceOf(account: Account): Long = balances[account.id] ?: 0

/** Money you actually have: bank and cash balances, ignoring what is owed on cards. */
fun Ledger.cashOnHand(): Long =
    activeAccounts.filter { it.kind != AccountKind.CREDIT_CARD }.sumOf { balanceOf(it) }

/** An overpaid card sits in credit, which can't be used to pay off a different card. */
fun Ledger.totalCardDebt(): Long =
    activeAccounts.filter { it.kind == AccountKind.CREDIT_CARD }.sumOf { (-balanceOf(it)).coerceAtLeast(0) }

data class FlowTotals(val moneyIn: Long, val moneyOut: Long)

/** Everything that entered and left one account during [period], transfers included. */
fun Ledger.accountFlow(account: Account, period: Period): FlowTotals {
    var moneyIn = 0L
    var moneyOut = 0L
    for (txn in txnsIn(period)) {
        if (txn.accountId == account.id) {
            when (txn.type) {
                TxType.EXPENSE, TxType.TRANSFER -> moneyOut += txn.amount
                TxType.INCOME, TxType.SETTLEMENT -> moneyIn += txn.amount
            }
        }
        if (txn.type == TxType.TRANSFER && txn.toAccountId == account.id) moneyIn += txn.amount
    }
    return FlowTotals(moneyIn, moneyOut)
}

fun Ledger.spentIn(period: Period): Long = txnsIn(period).sumOf { myShare(it) }

/** Money you earned. A refund is your own money coming back, so it doesn't count. */
fun Ledger.incomeIn(period: Period): Long =
    txnsIn(period).filter { it.type == TxType.INCOME && categoryById[it.categoryId]?.icon != "refund" }.sumOf { it.amount }

/** Net money that went into savings accounts during [period]. */
fun Ledger.savedIn(period: Period): Long {
    val savings = accounts.filter { it.role == AccountRole.SAVINGS }
    return savings.sumOf { val flow = accountFlow(it, period); flow.moneyIn - flow.moneyOut }
}

/** What [personId] still owes you: their split shares minus everything they have paid back. */
fun Ledger.owedBy(personId: Long): Long = owedByPerson[personId] ?: 0

fun Ledger.totalOwedToYou(): Long = people.sumOf { owedBy(it.id).coerceAtLeast(0) }

data class BudgetStatus(
    val budget: Long,
    val spent: Long,
    val left: Long,
    val daysLeft: Int,
    /** How much can be spent per remaining day to end the month on budget. */
    val perDay: Long,
    /** Fraction of the budget used, can go above 1. */
    val used: Float,
    /** Fraction of the month gone, today included. */
    val monthGone: Float,
    /** Positive when spending is running ahead of an even pace. */
    val aheadOfPace: Long,
)

/** [billsAhead] is money already spoken for, so it comes off what's safe to spend today. */
fun budgetStatus(budget: Long, spent: Long, today: LocalDate, billsAhead: Long = 0): BudgetStatus {
    val days = today.lengthOfMonth()
    val daysLeft = days - today.dayOfMonth + 1
    val left = budget - spent - billsAhead
    return BudgetStatus(
        budget = budget,
        spent = spent,
        left = left,
        daysLeft = daysLeft,
        perDay = left.coerceAtLeast(0) / daysLeft,
        used = if (budget > 0) (spent + billsAhead).toFloat() / budget else 0f,
        monthGone = today.dayOfMonth.toFloat() / days,
        aheadOfPace = spent - budget * today.dayOfMonth / days,
    )
}

data class CardStatus(
    val owed: Long,
    /** The part of [owed] on the latest statement that still has to be paid. */
    val billed: Long,
    /** Spending since the latest statement, which goes on the next one. */
    val unbilled: Long,
    val statementDate: LocalDate?,
    val dueDate: LocalDate?,
    val daysToDue: Int?,
    val overdue: Boolean,
    val available: Long,
)

fun Ledger.cardStatus(card: Account, today: LocalDate): CardStatus {
    val owed = (-balanceOf(card)).coerceAtLeast(0)
    val available = (card.creditLimit - owed).coerceAtLeast(0)
    if (card.statementDay == 0) {
        return CardStatus(owed, owed, 0, null, null, null, false, available)
    }

    val statement = latestDayOfMonth(card.statementDay, today)
    var charged = card.opening
    var paid = 0L
    for (txn in txns) {
        if (txn.accountId == card.id) {
            when (txn.type) {
                TxType.EXPENSE -> if (!txn.localDate().isAfter(statement)) charged += txn.amount
                TxType.TRANSFER -> if (!txn.localDate().isAfter(statement)) charged += txn.amount
                TxType.INCOME, TxType.SETTLEMENT -> paid += txn.amount
            }
        }
        if (txn.type == TxType.TRANSFER && txn.toAccountId == card.id) paid += txn.amount
    }
    val billed = (charged - paid).coerceAtLeast(0)
    val due = if (card.dueDay == 0) null else nextDayOfMonth(card.dueDay, statement)
    val daysToDue = due?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    return CardStatus(
        owed = owed,
        billed = billed,
        unbilled = (owed - billed).coerceAtLeast(0),
        statementDate = statement,
        dueDate = due,
        daysToDue = daysToDue,
        overdue = billed > 0 && daysToDue != null && daysToDue < 0,
        available = available,
    )
}

/** The most recent [day] of a month on or before [date]. */
internal fun latestDayOfMonth(day: Int, date: LocalDate): LocalDate {
    val thisMonth = date.withDayOfMonth(day.coerceAtMost(date.lengthOfMonth()))
    if (!thisMonth.isAfter(date)) return thisMonth
    val previous = date.minusMonths(1)
    return previous.withDayOfMonth(day.coerceAtMost(previous.lengthOfMonth()))
}

/** The first [day] of a month strictly after [date]. */
internal fun nextDayOfMonth(day: Int, date: LocalDate): LocalDate {
    val thisMonth = date.withDayOfMonth(day.coerceAtMost(date.lengthOfMonth()))
    if (thisMonth.isAfter(date)) return thisMonth
    val next = date.plusMonths(1)
    return next.withDayOfMonth(day.coerceAtMost(next.lengthOfMonth()))
}

data class Slice(val key: Long, val amount: Long, val count: Int)

/** Your spending per category, largest first. Uncategorised spending uses key [NONE]. */
fun Ledger.byCategory(period: Period): List<Slice> =
    txnsIn(period).filter { myShare(it) > 0 }
        .groupBy { it.categoryId ?: NONE }
        .map { (key, items) -> Slice(key, items.sumOf { myShare(it) }, items.size) }
        .sortedByDescending { it.amount }

fun Ledger.byTag(period: Period): List<Slice> =
    txnsIn(period).filter { myShare(it) > 0 }
        .flatMap { txn -> tagIdsByTxn[txn.id].orEmpty().map { it to txn } }
        .groupBy({ it.first }, { it.second })
        .map { (key, items) -> Slice(key, items.sumOf { myShare(it) }, items.size) }
        .sortedByDescending { it.amount }

data class PayeeTotal(val payee: String, val amount: Long, val count: Int)

fun Ledger.byPayee(period: Period): List<PayeeTotal> =
    txnsIn(period).filter { myShare(it) > 0 && it.payee.isNotBlank() }
        .groupBy { it.payee.trim().lowercase() }
        .map { (_, items) -> PayeeTotal(items.first().payee.trim(), items.sumOf { myShare(it) }, items.size) }
        .sortedByDescending { it.amount }

fun Ledger.byMethod(period: Period): List<Pair<PayMethod, Long>> =
    txnsIn(period).filter { myShare(it) > 0 }
        .groupBy { it.method }
        .map { (method, items) -> method to items.sumOf { myShare(it) } }
        .sortedByDescending { it.second }

/** Spending per day of the month. Index 0 is the first of the month. */
fun Ledger.dailySpend(month: YearMonth): LongArray {
    val days = LongArray(month.lengthOfMonth())
    for (txn in txnsIn(Period.of(month))) days[txn.localDate().dayOfMonth - 1] += myShare(txn)
    return days
}

fun LongArray.runningTotal(): LongArray {
    var total = 0L
    return LongArray(size) { index -> total += this[index]; total }
}

data class FlowLink(val accountId: Long, val target: Long, val amount: Long)

/**
 * Where money went in [period]: from each account into a category, or into savings.
 * Card bill payments stay out of it because the spending already shows on the card.
 */
fun Ledger.moneyFlow(period: Period): List<FlowLink> {
    val totals = HashMap<Pair<Long, Long>, Long>()
    for (txn in txnsIn(period)) {
        when (txn.type) {
            TxType.EXPENSE -> {
                val share = myShare(txn)
                if (share > 0) totals.merge(txn.accountId to (txn.categoryId ?: NONE), share, Long::plus)
            }
            TxType.TRANSFER -> {
                val from = accountById[txn.accountId]
                val to = accountById[txn.toAccountId]
                if (to?.role == AccountRole.SAVINGS && to.kind != AccountKind.CREDIT_CARD && from?.role != AccountRole.SAVINGS) {
                    totals.merge(txn.accountId to SAVED, txn.amount, Long::plus)
                }
            }
            else -> Unit
        }
    }
    return totals.map { (key, amount) -> FlowLink(key.first, key.second, amount) }
}

const val NONE = -1L
const val SAVED = -2L
const val OTHER = -3L

data class QuickRepeat(val latest: Txn, val count: Int)

/** Payments you keep making, such as a turf share, offered back as one-tap entries. */
fun Ledger.quickRepeats(today: LocalDate, limit: Int = 6): List<QuickRepeat> {
    val since = today.minusDays(60)
    return txns.asSequence()
        .filter { it.type == TxType.EXPENSE && it.payee.isNotBlank() && it.localDate().isAfter(since) }
        .groupBy { it.payee.trim().lowercase() to it.amount }
        .values
        .filter { it.size >= 2 }
        .sortedWith(compareByDescending<List<Txn>> { it.size }.thenByDescending { it.first().date })
        .take(limit)
        .map { QuickRepeat(it.first(), it.size) }
}

/** Distinct payee names, most recently used first. */
fun Ledger.recentPayees(): List<String> =
    (txns.filter { it.payee.isNotBlank() }.map { it.payee.trim() } + people.map { it.name })
        .distinctBy { it.lowercase() }

fun Ledger.latestForPayee(payee: String): Txn? {
    val key = payee.trim().lowercase()
    return txns.firstOrNull { it.type == TxType.EXPENSE && it.payee.trim().lowercase() == key }
}

/** One person's part of an even split. Any leftover paise stay with whoever paid the bill. */
fun equalShare(total: Long, ways: Int): Long = if (ways <= 0) 0 else total / ways
