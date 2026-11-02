package com.farhan.paisa.domain

import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Recurring
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CyclesTest {
    private fun day(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)
    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `a start day of one gives plain calendar months`() {
        val period = Cycles(1).around(day(10, 15))
        assertEquals(Period.of(YearMonth.of(2026, 10)), period)
        assertTrue(period.isCalendarMonth)
        assertEquals("October 2026", period.label)
    }

    @Test
    fun `a start day of thirty runs from the thirtieth to the twenty ninth`() {
        val cycles = Cycles(30)
        val expected = Period(day(10, 30), day(11, 29))
        assertEquals(expected, cycles.around(day(10, 30)))
        assertEquals(expected, cycles.around(day(11, 2)))
        assertEquals(expected, cycles.around(day(11, 29)))
        assertEquals("30 Oct – 29 Nov", expected.label)
        assertFalse(expected.isCalendarMonth)
    }

    @Test
    fun `the day before the start belongs to the earlier month`() {
        assertEquals(Period(day(9, 30), day(10, 29)), Cycles(30).around(day(10, 29)))
    }

    @Test
    fun `february clamps a start day it doesn't have`() {
        assertEquals(Period(day(1, 30), day(2, 27)), Cycles(30).around(day(2, 10)))
        assertEquals(Period(day(2, 28), day(3, 29)), Cycles(30).around(day(3, 1)))
    }

    @Test
    fun `months follow each other with no gaps or overlaps across a year`() {
        val cycles = Cycles(30, listOf(day(10, 28), day(12, 2)))
        var period = cycles.around(day(1, 15))
        repeat(13) {
            val next = cycles.after(period)
            assertEquals(period.end.plusDays(1), next.start)
            assertEquals(period, cycles.before(next))
            period = next
        }
    }

    @Test
    fun `salary landing early or late moves the start of the month`() {
        val early = Cycles(30, listOf(day(10, 28)))
        assertEquals(day(10, 28), early.around(day(10, 28)).start)
        assertEquals(Period(day(9, 30), day(10, 27)), early.around(day(10, 27)))

        val late = Cycles(30, listOf(day(12, 2)))
        assertEquals(Period(day(10, 30), day(12, 1)), late.around(day(11, 15)))
        assertEquals(day(12, 2), late.around(day(12, 2)).start)
    }

    @Test
    fun `salary far from the planned day is ignored`() {
        assertEquals(day(10, 30), Cycles(30, listOf(day(10, 10))).around(day(11, 2)).start)
    }

    @Test
    fun `last months come oldest first and end with the latest`() {
        val cycles = Cycles(30)
        val months = cycles.lastMonths(cycles.around(day(11, 2)), 3)
        assertEquals(listOf(day(8, 30), day(9, 30), day(10, 30)), months.map { it.start })
    }

    @Test
    fun `period tokens round trip`() {
        val period = Period(day(10, 30), day(11, 29))
        assertEquals(period, Period.parse(period.token))
    }

    private val salary = Category(id = 1, name = "Salary", icon = "salary", income = true)

    @Test
    fun `logged salaries set the month when following salary`() {
        val l = Ledger(
            categories = listOf(salary),
            txns = listOf(Txn(id = 1, type = TxType.INCOME, amount = 100, accountId = 1, categoryId = 1, date = millis(day(10, 28)))),
        )
        assertEquals(day(10, 28), l.cycles(30, followSalary = true).around(day(11, 2)).start)
        assertEquals(day(10, 30), l.cycles(30, followSalary = false).around(day(11, 2)).start)
        assertEquals(day(11, 1), l.cycles(1, followSalary = true).around(day(11, 2)).start)
    }

    @Test
    fun `pace and days left follow the custom month`() {
        val period = Period(day(10, 30), day(11, 29))
        val status = budgetStatus(30_000_00, 10_000_00, day(11, 2), period = period)
        assertEquals(28, status.daysLeft)
        assertEquals(4f / 31, status.monthGone, 0.001f)
    }

    @Test
    fun `daily spend lines up with a month that starts mid calendar month`() {
        val l = Ledger(txns = listOf(Txn(id = 1, type = TxType.EXPENSE, amount = 500, accountId = 1, date = millis(day(11, 1)))))
        val daily = l.dailySpend(Period(day(10, 30), day(11, 29)))
        assertEquals(31, daily.size)
        assertEquals(500L, daily[2])
    }

    @Test
    fun `a bill due on the fifth falls in the next calendar month of a custom month`() {
        val rent = Recurring(id = 1, name = "Rent", amount = 19_950_00, day = 31)
        val fifth = Recurring(id = 2, name = "Internet", amount = 999_00, day = 5)
        val period = Period(day(10, 30), day(11, 29))
        val l = Ledger(
            recurring = listOf(rent, fifth),
            txns = listOf(Txn(id = 1, type = TxType.EXPENSE, amount = 19_950_00, accountId = 1, payee = "Rent", date = millis(day(10, 31)))),
        )
        val bills = l.billsFor(day(11, 2), period).associateBy { it.bill.name }
        assertEquals(day(10, 31), bills.getValue("Rent").due)
        assertTrue(bills.getValue("Rent").paid)
        assertEquals(day(11, 5), bills.getValue("Internet").due)
        assertEquals(999_00, l.billsAhead(day(11, 2), period))
    }
}
