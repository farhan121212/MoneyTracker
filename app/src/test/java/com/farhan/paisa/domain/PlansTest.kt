package com.farhan.paisa.domain

import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.DetectedStatus
import com.farhan.paisa.data.Goal
import com.farhan.paisa.data.GoalEntry
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Recurring
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlansTest {
    private val today = LocalDate.of(2026, 10, 20)
    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun spend(id: Long, payee: String, amount: Long, date: LocalDate, category: Long? = null) =
        Txn(id = id, type = TxType.EXPENSE, amount = amount, accountId = 1, payee = payee, categoryId = category, date = millis(date))

    private val kotak = Account(id = 1, name = "Kotak", kind = AccountKind.BANK, last4 = "5521", opening = 50_000_00)
    private val savings = Account(id = 2, name = "Savings", kind = AccountKind.BANK, role = AccountRole.SAVINGS, opening = 1_00_000_00)
    private val netflix = Recurring(id = 1, name = "Netflix", amount = 649_00, day = 5)
    private val rent = Recurring(id = 2, name = "Rent", amount = 18_000_00, day = 25)

    @Test
    fun `digits match an account whichever side is longer`() {
        val l = Ledger(accounts = listOf(kotak.copy(last4 = "5521")))
        assertEquals(1L, l.accountFor("5521")?.id)
        assertEquals(1L, Ledger(accounts = listOf(kotak.copy(last4 = "155521"))).accountFor("5521")?.id)
        assertNull(l.accountFor("7730"))
        assertNull(l.accountFor(""))
    }

    private fun detected(id: Long, name: String, status: DetectedStatus = DetectedStatus.PENDING, txnId: Long? = null, amount: Long = 458_00) =
        Detected(id = id, dedupKey = "k$id", type = TxType.EXPENSE, amount = amount, counterparty = name, last4 = "5521", method = PayMethod.UPI, date = millis(today), status = status, txnId = txnId)

    @Test
    fun `a confirmed name teaches how the next one is filled in`() {
        val l = Ledger(
            txns = listOf(spend(7, "Zepto", 300_00, today, category = 2)),
            categories = listOf(Category(id = 2, name = "Groceries", icon = "groceries")),
            detected = listOf(detected(1, "Zepto Marketplace Pr", DetectedStatus.CONFIRMED, txnId = 7)),
        )
        val suggestion = l.suggestionFor(detected(2, "ZEPTO MARKETPLACE PR"))
        assertEquals("Zepto", suggestion.payee)
        assertEquals(2L, suggestion.categoryId)
    }

    @Test
    fun `an unknown name is offered as it was written`() {
        val suggestion = Ledger().suggestionFor(detected(2, "New Shop"))
        assertEquals("New Shop", suggestion.payee)
        assertNull(suggestion.categoryId)
    }

    @Test
    fun `a hand logged entry for the same amount and day is flagged as a likely repeat`() {
        val l = Ledger(txns = listOf(spend(1, "Zepto", 458_00, today)))
        assertNotNull(l.possibleDuplicate(detected(2, "Zepto Marketplace Pr")))
        assertNull(l.possibleDuplicate(detected(2, "Zepto Marketplace Pr", amount = 459_00)))
    }

    @Test
    fun `a regular payment from an earlier day is not called a repeat`() {
        val l = Ledger(txns = listOf(spend(1, "Shubham", 458_00, today.minusDays(1))))
        assertNull(l.possibleDuplicate(detected(2, "Shubham")))
    }

    @Test
    fun `an entry that came from another sms isn't called a repeat`() {
        val l = Ledger(
            txns = listOf(spend(1, "Shubham", 458_00, today)),
            detected = listOf(detected(1, "Shubham", DetectedStatus.CONFIRMED, txnId = 1)),
        )
        assertNull(l.possibleDuplicate(detected(2, "Shubham")))
    }

    @Test
    fun `bills are paid by a matching name and a close amount`() {
        assertTrue(matchesBill(spend(1, "Netflix", 649_00, today), netflix))
        assertTrue(matchesBill(spend(1, "NETFLIX.COM", 649_00, today), netflix))
        assertTrue(matchesBill(spend(1, "Netflix", 699_00, today), netflix))
        assertFalse(matchesBill(spend(1, "Netflix", 1_500_00, today), netflix))
        assertFalse(matchesBill(spend(1, "Swiggy", 649_00, today), netflix))
    }

    @Test
    fun `unpaid bills come off what is safe to spend`() {
        val l = Ledger(
            txns = listOf(spend(1, "Netflix", 649_00, today.withDayOfMonth(5))),
            recurring = listOf(netflix, rent),
        )
        assertEquals(18_000_00, l.billsAhead(today))
        val status = budgetStatus(40_000_00, 10_000_00, today, l.billsAhead(today))
        assertEquals(12_000_00, status.left)
        assertEquals(1_000_00, status.perDay)
    }

    @Test
    fun `a bill paid in an earlier month is due again`() {
        val l = Ledger(txns = listOf(spend(1, "Netflix", 649_00, today.minusMonths(1))), recurring = listOf(netflix))
        assertEquals(649_00, l.billsAhead(today))
    }

    @Test
    fun `bill day clamps in short months`() {
        val l = Ledger(recurring = listOf(netflix.copy(day = 31)))
        assertEquals(LocalDate.of(2026, 2, 28), l.billsFor(LocalDate.of(2026, 2, 10)).single().due)
    }

    @Test
    fun `monthly payments are suggested but a frequent payee is not`() {
        val l = Ledger(
            txns = listOf(
                spend(1, "Netflix", 649_00, today.withDayOfMonth(5)),
                spend(2, "Netflix", 649_00, today.minusMonths(1).withDayOfMonth(5)),
                spend(3, "Shubham", 150_00, today.minusDays(1)),
                spend(4, "Shubham", 150_00, today.minusDays(3)),
                spend(5, "Shubham", 150_00, today.minusMonths(1)),
                spend(6, "Shubham", 150_00, today.minusMonths(1).minusDays(2)),
                spend(7, "Once", 999_00, today),
            ),
        )
        assertEquals(listOf("Netflix"), l.suggestedBills(today).map { it.payee })
    }

    @Test
    fun `a bill you already track is not suggested again`() {
        val l = Ledger(
            txns = listOf(
                spend(1, "Netflix", 649_00, today.withDayOfMonth(5)),
                spend(2, "Netflix", 649_00, today.minusMonths(1).withDayOfMonth(5)),
            ),
            recurring = listOf(netflix),
        )
        assertTrue(l.suggestedBills(today).isEmpty())
    }

    @Test
    fun `goals divide your savings without moving it`() {
        val l = Ledger(
            accounts = listOf(kotak, savings),
            goals = listOf(Goal(id = 1, name = "Emergency", target = 1_50_000_00), Goal(id = 2, name = "Goa", target = 30_000_00)),
            goalEntries = listOf(
                GoalEntry(1, 1, 60_000_00, 0),
                GoalEntry(2, 1, -10_000_00, 0),
                GoalEntry(3, 2, 12_000_00, 0),
            ),
        )
        assertEquals(50_000_00, l.savedTowards(1))
        assertEquals(62_000_00, l.assignedToGoals())
        assertEquals(38_000_00, l.freeSavings())
        assertEquals(1_00_000_00, l.savingsTotal())
    }

    @Test
    fun `monthly amount needed rounds up and counts whole months`() {
        val goal = Goal(name = "Goa", target = 30_000_00, targetDay = LocalDate.of(2027, 1, 15).toEpochDay())
        assertEquals(6_000_00L, goal.monthlyNeeded(12_000_00, today))
        assertEquals(10_000_01L, goal.copy(target = 30_000_03).monthlyNeeded(0, today))
        assertEquals(0L, goal.monthlyNeeded(30_000_00, today))
        assertNull(goal.copy(targetDay = null).monthlyNeeded(0, today))
    }
}
