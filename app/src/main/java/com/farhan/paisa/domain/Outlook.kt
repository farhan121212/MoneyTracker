package com.farhan.paisa.domain

import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A guess from one or two days swings too wildly to be worth showing. */
private const val FORECAST_MIN_DAYS = 3
private const val PLAN_SHOWS_FOR_DAYS = 7L

/** Where the month ends up if everyday spending carries on as it has so far. */
data class Forecast(val total: Long, val budget: Long) {
    val margin: Long get() = budget - total
    val over: Boolean get() = budget > 0 && margin < 0

    val message: String
        get() {
            val nearest = roundedToHundred(kotlin.math.abs(margin))
            return when {
                budget == 0L -> "At this pace you'll spend about ${Money.whole(roundedToHundred(total))} this month"
                nearest == 0L -> "On pace to finish right on your limit"
                over -> "At this pace you'll finish ${Money.whole(nearest)} over your limit"
                else -> "On pace to finish ${Money.whole(nearest)} under your limit"
            }
        }
}

/** Exact rupees would claim more certainty than a forecast has. */
private fun roundedToHundred(paise: Long): Long = (paise + 50_00) / 100_00 * 100_00

fun Ledger.forecast(period: Period, today: LocalDate, budget: Long, billsAhead: Long): Forecast? {
    if (today !in period) return null
    val elapsed = period.elapsed(today)
    val remaining = period.days - elapsed
    if (elapsed < FORECAST_MIN_DAYS || remaining == 0) return null
    val spent = spentIn(period)
    if (spent == 0L && billsAhead == 0L) return null
    val perDay = everydaySpend(period).take(elapsed).sum().coerceAtLeast(0) / elapsed
    return Forecast(spent + perDay * remaining + billsAhead, budget)
}

/** What's left of your money once everything already spoken for is taken out. */
data class RealMoney(val cash: Long, val cards: Long, val bills: Long) {
    val free: Long get() = cash - cards - bills
}

fun Ledger.realMoney(today: LocalDate, period: Period): RealMoney =
    RealMoney(cashOnHand(), totalCardDebt(), billsAhead(today, period))

/** Money in accounts you pay from day to day, which is what bills actually come out of. */
fun Ledger.spendingBalance(): Long =
    activeAccounts.filter { it.kind != AccountKind.CREDIT_CARD && it.role == AccountRole.SPENDING }.sumOf { balanceOf(it) }

enum class DueKind { CARD_BILL, CARD_NEXT, BILL }

data class DueItem(
    val date: LocalDate,
    val name: String,
    val amount: Long,
    val kind: DueKind,
    /** The card or bill this came from. */
    val ownerId: Long,
    /** A bill paid by card lands on that card, so no money leaves the bank yet. */
    val onCard: Boolean = false,
)

/** Everything coming due from [period] to the end of [next]: card bills first by date, then by size. */
fun Ledger.dueItems(today: LocalDate, period: Period, next: Period): List<DueItem> = buildList {
    activeAccounts.filter { it.kind == AccountKind.CREDIT_CARD }.forEach { card ->
        val status = cardStatus(card, today)
        val due = status.dueDate
        if (status.billed > 0 && due != null) add(DueItem(due, card.name, status.billed, DueKind.CARD_BILL, card.id))
        if (status.unbilled > 0 && card.statementDay > 0 && card.dueDay > 0) {
            val statement = nextDayOfMonth(card.statementDay, today)
            add(DueItem(nextDayOfMonth(card.dueDay, statement), card.name, status.unbilled, DueKind.CARD_NEXT, card.id))
        }
    }
    (billsFor(today, period) + billsFor(today, next)).filterNot { it.paid }.forEach { status ->
        val onCard = accountById[status.bill.accountId]?.kind == AccountKind.CREDIT_CARD
        add(DueItem(status.due, status.bill.name, status.bill.amount, DueKind.BILL, status.bill.id, onCard))
    }
}.filterNot { it.date.isAfter(next.end) }.sortedWith(compareBy({ it.date }, { it.kind != DueKind.CARD_BILL }, { -it.amount }))

data class DueStep(
    val item: DueItem,
    /** What stays in your everyday accounts once this is paid. */
    val left: Long,
)

data class DuePlan(
    val balance: Long,
    /** Due before your next salary, so today's money has to cover these. */
    val beforeSalary: List<DueStep>,
    val afterSalary: List<DueItem>,
    /** Cards with money owed but no due date set, so they can't be placed in time. */
    val undated: List<Account>,
) {
    val total: Long get() = beforeSalary.filterNot { it.item.onCard }.sumOf { it.item.amount }
    val shortBy: Long get() = (-(beforeSalary.minOfOrNull { it.left } ?: 0)).coerceAtLeast(0)
}

fun Ledger.duePlan(today: LocalDate, period: Period, next: Period): DuePlan {
    val (before, after) = dueItems(today, period, next).partition { !it.date.isAfter(period.end) }
    val balance = spendingBalance()
    var left = balance
    val steps = before.map { item ->
        if (!item.onCard) left -= item.amount
        DueStep(item, left)
    }
    val undated = activeAccounts.filter { it.kind == AccountKind.CREDIT_CARD && (it.dueDay == 0 || it.statementDay == 0) && balanceOf(it) < 0 }
    return DuePlan(balance, steps, after, undated)
}

private fun Ledger.salariesIn(period: Period): List<Txn> =
    txnsIn(period).filter { it.type == TxType.INCOME && categoryById[it.categoryId]?.icon == "salary" }

data class CardPlan(val card: Account, val due: Long, val paid: Long)

/** How a salary divides up: what's spoken for, what you want to keep, and what's left to live on. */
data class SalaryPlan(
    val salary: Long,
    val landed: LocalDate,
    /** No salary has landed this month yet, so this uses the last one. */
    val estimated: Boolean,
    val bills: List<BillStatus>,
    val cards: List<CardPlan>,
    val savingsGoal: Long,
    val saved: Long,
    val days: Int,
) {
    val billsTotal: Long get() = bills.sumOf { it.bill.amount }
    val cardsTotal: Long get() = cards.sumOf { it.due + it.paid }
    val everyday: Long get() = salary - billsTotal - cardsTotal - savingsGoal
    val perDay: Long get() = everyday.coerceAtLeast(0) / days
    val settled: Boolean get() = bills.all { it.paid } && cards.all { it.due == 0L } && saved >= savingsGoal
}

fun Ledger.salaryPlan(today: LocalDate, period: Period, savingsGoal: Long): SalaryPlan? {
    val thisMonth = salariesIn(period)
    val earlier = txns.firstOrNull { it.type == TxType.INCOME && categoryById[it.categoryId]?.icon == "salary" }
    val landed = thisMonth.firstOrNull() ?: earlier ?: return null
    val cards = activeAccounts.filter { it.kind == AccountKind.CREDIT_CARD }
        .map { card ->
            val paid = txnsIn(period).filter { it.type == TxType.TRANSFER && it.toAccountId == card.id }.sumOf { it.amount }
            CardPlan(card, cardStatus(card, today).billed, paid)
        }
        .filter { it.due + it.paid > 0 }
    return SalaryPlan(
        salary = if (thisMonth.isEmpty()) landed.amount else thisMonth.sumOf { it.amount },
        landed = landed.localDate(),
        estimated = thisMonth.isEmpty(),
        bills = billsFor(today, period),
        cards = cards,
        savingsGoal = savingsGoal,
        saved = savedIn(period).coerceAtLeast(0),
        days = period.days,
    )
}

/** Offered for the first week after salary lands, until it's done or you wave it away. */
fun Ledger.salaryPlanDue(today: LocalDate, period: Period, settings: Settings): Boolean {
    if (settings.planDismissed == period.token) return false
    val plan = salaryPlan(today, period, settings.savingsTarget) ?: return false
    if (plan.estimated || plan.settled) return false
    return ChronoUnit.DAYS.between(plan.landed, today) in 0 until PLAN_SHOWS_FOR_DAYS
}

enum class Tone { GOOD, WATCH, OVER }

/** The few numbers shown outside the app, worked out once for all the home screen widgets. */
data class HomeSnapshot(
    val hasLimit: Boolean,
    val left: Long,
    val perDay: Long,
    val spent: Long,
    val budget: Long,
    val spentToday: Long,
    val used: Float,
    val until: LocalDate,
    val tone: Tone,
    val toReview: Int,
    val upcoming: List<DueItem>,
    /** With app lock on, nothing about your money shows on the home screen. */
    val locked: Boolean,
)

fun Ledger.homeSnapshot(settings: Settings, today: LocalDate): HomeSnapshot {
    val cycles = cycles(settings)
    val period = cycles.around(today)
    val spent = spentIn(period)
    val status = budgetStatus(settings.monthlyBudget, spent, today, billsAhead(today, period), period)
    return HomeSnapshot(
        hasLimit = settings.monthlyBudget > 0,
        left = status.left,
        perDay = status.perDay,
        spent = spent,
        budget = settings.monthlyBudget,
        spentToday = spentIn(Period(today, today)),
        used = status.used,
        until = period.end,
        tone = when {
            status.left < 0 -> Tone.OVER
            status.aheadOfPace > 0 -> Tone.WATCH
            else -> Tone.GOOD
        },
        toReview = toReview.size,
        upcoming = dueItems(today, period, cycles.after(period)).filter { it.kind != DueKind.CARD_NEXT }.take(3),
        locked = settings.lockEnabled,
    )
}
