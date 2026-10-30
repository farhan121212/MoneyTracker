package com.farhan.paisa.domain

import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.DetectedStatus
import com.farhan.paisa.data.Goal
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Person
import com.farhan.paisa.data.Recurring
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Matches the digits in an SMS to one of your accounts. */
fun Ledger.accountFor(last4: String): Account? {
    if (last4.isBlank()) return null
    return activeAccounts.firstOrNull { it.last4.isNotBlank() && (it.last4.endsWith(last4) || last4.endsWith(it.last4)) }
}

data class Suggestion(val payee: String, val categoryId: Long?, val tagIds: List<Long>)

/**
 * What to fill in for a detected payment. Once you've confirmed "Zepto Marketplace Pr" as
 * Zepto under Groceries, the next one from that name arrives filled in the same way.
 */
fun Ledger.suggestionFor(detected: Detected): Suggestion {
    val name = detected.counterparty.trim()
    val earlier = this.detected
        .filter {
            it.status == DetectedStatus.CONFIRMED && it.type == detected.type && it.counterparty.equals(name, ignoreCase = true)
        }
        .firstNotNullOfOrNull { txnById[it.txnId] }
    // A bank's "Rohit Verma" still matches a payee you've logged as "Rohit".
    val full = simplified(name)
    val known = earlier ?: txns.firstOrNull {
        val payee = simplified(it.payee)
        it.type == detected.type && payee.length >= 4 && (payee == full || payee in full)
    }
    if (known != null) return Suggestion(known.payee.ifBlank { name }, known.categoryId, tagIdsByTxn[known.id].orEmpty())
    return Suggestion(name, incomeCategoryFor(detected), emptyList())
}

/** Salary and refunds are named in the message, so they don't need teaching. */
private fun Ledger.incomeCategoryFor(detected: Detected): Long? {
    if (detected.type != TxType.INCOME) return null
    val icon = when {
        detected.counterparty.equals(SmsParser.SALARY, ignoreCase = true) -> "salary"
        detected.counterparty.startsWith(SmsParser.REFUND, ignoreCase = true) -> "refund"
        else -> return null
    }
    return categories.firstOrNull { it.income && it.icon == icon && !it.archived }?.id
}

/** Someone who owes you and whose name matches money you just received. */
fun Ledger.repayerFor(detected: Detected): Person? {
    if (detected.type != TxType.INCOME) return null
    val sender = simplified(detected.counterparty)
    if (sender.length < 3) return null
    return people.firstOrNull { person ->
        val name = simplified(person.name)
        owedBy(person.id) > 0 && name.length >= 3 && (name in sender || sender in name)
    }
}

/**
 * An entry already logged by hand for the same amount that day. Limited to one day so that a
 * regular payment, like a turf share every other day, isn't flagged each time.
 */
fun Ledger.possibleDuplicate(detected: Detected): Txn? {
    val date = java.time.Instant.ofEpochMilli(detected.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val linked = this.detected.mapNotNull { it.txnId }.toSet()
    return txns.firstOrNull {
        it.id !in linked && it.amount == detected.amount && it.type == detected.type && it.localDate() == date
    }
}

data class BillStatus(val bill: Recurring, val due: LocalDate, val paid: Boolean, val daysToDue: Int)

private fun simplified(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

/** A payment counts towards a bill when the name lines up and the amount is close. */
fun matchesBill(txn: Txn, bill: Recurring): Boolean {
    if (txn.type != TxType.EXPENSE) return false
    val payee = simplified(txn.payee)
    val name = simplified(bill.name)
    if (payee.isEmpty() || name.isEmpty()) return false
    val named = payee.contains(name) || name.contains(payee)
    return named && abs(txn.amount - bill.amount) * 4 <= bill.amount
}

fun Ledger.billsFor(today: LocalDate): List<BillStatus> {
    val month = YearMonth.from(today)
    val thisMonth = txnsIn(Period.of(month))
    return recurring
        .map { bill ->
            val due = month.atDay(bill.day.coerceIn(1, month.lengthOfMonth()))
            BillStatus(bill, due, thisMonth.any { matchesBill(it, bill) }, ChronoUnit.DAYS.between(today, due).toInt())
        }
        .sortedWith(compareBy({ it.paid }, { it.due }))
}

/** Money for bills that haven't been paid yet this month. */
fun Ledger.billsAhead(today: LocalDate): Long = billsFor(today).filterNot { it.paid }.sumOf { it.bill.amount }

/**
 * Payments that look like a monthly bill: the same payee and amount in two or more of the last
 * three months, about once a month, and not already being tracked. A friend you pay every other
 * day doesn't qualify.
 */
fun Ledger.suggestedBills(today: LocalDate): List<Txn> {
    val thisMonth = YearMonth.from(today)
    val months = (0L..2L).map { thisMonth.minusMonths(it) }.toSet()
    return txns
        .filter { it.type == TxType.EXPENSE && it.payee.isNotBlank() && YearMonth.from(it.localDate()) in months }
        .groupBy { it.payee.trim().lowercase() to it.amount }
        .values
        .filter { group ->
            val perMonth = group.groupingBy { YearMonth.from(it.localDate()) }.eachCount()
            perMonth.size >= 2 && perMonth.values.all { it == 1 }
        }
        .map { it.first() }
        .filter { txn -> recurring.none { matchesBill(txn, it) } }
}

/** Total in your savings accounts. */
fun Ledger.savingsTotal(): Long =
    activeAccounts.filter { it.role == AccountRole.SAVINGS && it.kind != AccountKind.CREDIT_CARD }.sumOf { balanceOf(it) }

fun Ledger.savedTowards(goalId: Long): Long = goalEntries.filter { it.goalId == goalId }.sumOf { it.amount }

fun Ledger.assignedToGoals(): Long = goals.sumOf { savedTowards(it.id) }

/** Savings that aren't set aside for any goal. */
fun Ledger.freeSavings(): Long = savingsTotal() - assignedToGoals()

/** How much a month reaches the goal by its deadline, or null when no deadline is set. */
fun Goal.monthlyNeeded(saved: Long, today: LocalDate): Long? {
    val deadline = targetDay?.let(LocalDate::ofEpochDay) ?: return null
    val remaining = (target - saved).coerceAtLeast(0)
    if (remaining == 0L) return 0
    val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(deadline)).coerceAtLeast(1)
    return (remaining + months - 1) / months
}
