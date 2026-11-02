package com.farhan.paisa.domain

import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** How far from the planned day a salary can land and still start the month. */
private const val SALARY_WINDOW_DAYS = 5

/**
 * Works out where "your month" begins and ends. With a start day of 1 these are plain calendar
 * months. With a start day of 30, a month runs from the 30th to the 29th, and if salary lands on
 * the 28th or the 2nd instead, the month simply starts on that day.
 */
class Cycles(private val startDay: Int, private val salaryDays: List<LocalDate> = emptyList()) {
    private fun planned(month: YearMonth): LocalDate = month.atDay(startDay.coerceIn(1, month.lengthOfMonth()))

    private fun start(month: YearMonth): LocalDate {
        val planned = planned(month)
        return salaryDays
            .filter { abs(ChronoUnit.DAYS.between(it, planned)) <= SALARY_WINDOW_DAYS }
            .minByOrNull { abs(ChronoUnit.DAYS.between(it, planned)) }
            ?: planned
    }

    /** The month that [date] falls in. */
    fun around(date: LocalDate): Period {
        var month = YearMonth.from(date).plusMonths(1)
        while (start(month).isAfter(date)) month = month.minusMonths(1)
        return Period(start(month), start(month.plusMonths(1)).minusDays(1))
    }

    fun before(period: Period): Period = around(period.start.minusDays(1))

    fun after(period: Period): Period = around(period.end.plusDays(1))

    /** The last [count] months ending with [latest], oldest first. */
    fun lastMonths(latest: Period, count: Int): List<Period> =
        generateSequence(latest) { before(it) }.take(count).toList().reversed()
}

fun Ledger.cycles(startDay: Int, followSalary: Boolean): Cycles {
    // Without a custom start day, months stay as plain calendar months.
    if (startDay <= 1 || !followSalary) return Cycles(startDay)
    val salaries = txns.filter { it.type == TxType.INCOME && categoryById[it.categoryId]?.icon == "salary" }
    return Cycles(startDay, salaries.map { it.localDate() })
}

fun Ledger.cycles(settings: Settings): Cycles = cycles(settings.monthStartDay, settings.followSalary)
