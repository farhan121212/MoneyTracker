package com.farhan.paisa.domain

import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Person
import com.farhan.paisa.data.SplitShare
import com.farhan.paisa.data.Tag
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxnTag
import com.farhan.paisa.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalcTest {
    private val daily = Account(id = 1, name = "Daily", kind = AccountKind.BANK, opening = 50_000_00)
    private val savings = Account(id = 2, name = "Savings", kind = AccountKind.BANK, role = AccountRole.SAVINGS, opening = 1_00_000_00)
    private val card = Account(
        id = 3,
        name = "Card",
        kind = AccountKind.CREDIT_CARD,
        opening = 5_000_00,
        creditLimit = 1_00_000_00,
        statementDay = 5,
        dueDay = 25,
    )
    private val sports = Category(id = 1, name = "Sports", icon = "sports")
    private val food = Category(id = 2, name = "Food", icon = "food")
    private val shubham = Person(id = 1, name = "Shubham")
    private val rahul = Person(id = 2, name = "Rahul")
    private val october = Period.of(YearMonth.of(2026, 10))

    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun txn(
        id: Long,
        type: TxType,
        amount: Long,
        date: LocalDate,
        account: Long = 1,
        to: Long? = null,
        category: Long? = null,
        payee: String = "",
        person: Long? = null,
        method: PayMethod = PayMethod.UPI,
    ) = Txn(
        id = id,
        type = type,
        amount = amount,
        accountId = account,
        toAccountId = to,
        categoryId = category,
        payee = payee,
        personId = person,
        method = method,
        date = millis(date),
    )

    private fun ledger(
        txns: List<Txn>,
        shares: List<SplitShare> = emptyList(),
        tags: List<Tag> = emptyList(),
        txnTags: List<TxnTag> = emptyList(),
    ) = Ledger(
        accounts = listOf(daily, savings, card),
        txns = txns,
        categories = listOf(sports, food),
        tags = tags,
        txnTags = txnTags,
        people = listOf(shubham, rahul),
        shares = shares,
    )

    private val oct10 = LocalDate.of(2026, 10, 10)

    @Test
    fun `expense lowers a bank balance and income raises it`() {
        val l = ledger(listOf(txn(1, TxType.EXPENSE, 150_00, oct10), txn(2, TxType.INCOME, 1_000_00, oct10)))
        assertEquals(50_850_00, l.balanceOf(daily))
    }

    @Test
    fun `transfer moves money without changing total cash`() {
        val l = ledger(listOf(txn(1, TxType.TRANSFER, 10_000_00, oct10, account = 1, to = 2)))
        assertEquals(40_000_00, l.balanceOf(daily))
        assertEquals(1_10_000_00, l.balanceOf(savings))
        assertEquals(1_50_000_00, l.cashOnHand())
    }

    @Test
    fun `card spending raises what is owed and leaves the bank alone`() {
        val l = ledger(listOf(txn(1, TxType.EXPENSE, 3_000_00, oct10, account = 3, method = PayMethod.CARD)))
        assertEquals(-8_000_00, l.balanceOf(card))
        assertEquals(50_000_00, l.balanceOf(daily))
        assertEquals(8_000_00, l.totalCardDebt())
    }

    @Test
    fun `paying a card bill is not counted as spending a second time`() {
        val l = ledger(
            listOf(
                txn(1, TxType.EXPENSE, 3_000_00, oct10, account = 3),
                txn(2, TxType.TRANSFER, 8_000_00, oct10.plusDays(1), account = 1, to = 3),
            ),
        )
        assertEquals(3_000_00, l.spentIn(october))
        assertEquals(0, l.balanceOf(card))
        assertEquals(42_000_00, l.balanceOf(daily))
    }

    @Test
    fun `an overpaid card does not cancel out debt on another card`() {
        val second = card.copy(id = 4, opening = 2_000_00)
        val l = ledger(listOf(txn(1, TxType.TRANSFER, 3_000_00, oct10, account = 1, to = 4)))
            .copy(accounts = listOf(daily, savings, card, second))
        assertEquals(5_000_00, l.totalCardDebt())
    }

    @Test
    fun `split bill only counts your share as spending`() {
        val bill = txn(1, TxType.EXPENSE, 3_000_00, oct10, account = 3, category = 2)
        val l = ledger(
            listOf(bill),
            shares = listOf(SplitShare(1, 1, 1, 1_000_00), SplitShare(2, 1, 2, 1_000_00)),
        )
        assertEquals(1_000_00, l.myShare(bill))
        assertEquals(1_000_00, l.spentIn(october))
        assertEquals(-8_000_00, l.balanceOf(card))
        assertEquals(2_000_00, l.totalOwedToYou())
    }

    @Test
    fun `a settlement reaches the account but is not income`() {
        val l = ledger(
            listOf(
                txn(1, TxType.EXPENSE, 3_000_00, oct10, account = 3),
                txn(2, TxType.SETTLEMENT, 600_00, oct10, account = 1, person = 1),
            ),
            shares = listOf(SplitShare(1, 1, 1, 1_000_00)),
        )
        assertEquals(400_00, l.owedBy(1))
        assertEquals(0, l.incomeIn(october))
        assertEquals(50_600_00, l.balanceOf(daily))
    }

    @Test
    fun `saved counts net movement into savings accounts`() {
        val l = ledger(
            listOf(
                txn(1, TxType.TRANSFER, 15_000_00, oct10, account = 1, to = 2),
                txn(2, TxType.TRANSFER, 2_000_00, oct10, account = 2, to = 1),
                txn(3, TxType.EXPENSE, 500_00, oct10, account = 2),
            ),
        )
        assertEquals(12_500_00, l.savedIn(october))
    }

    @Test
    fun `budget status on a mid month day`() {
        val status = budgetStatus(30_000_00, 20_000_00, oct10)
        assertEquals(10_000_00, status.left)
        assertEquals(22, status.daysLeft)
        assertEquals(10_000_00L / 22, status.perDay)
        assertTrue(status.aheadOfPace > 0)
    }

    @Test
    fun `budget status when over the limit never shows a negative daily amount`() {
        val status = budgetStatus(10_000_00, 12_000_00, oct10)
        assertEquals(-2_000_00, status.left)
        assertEquals(0, status.perDay)
    }

    @Test
    fun `last day of the month leaves one day`() {
        assertEquals(1, budgetStatus(1_000_00, 0, LocalDate.of(2026, 10, 31)).daysLeft)
    }

    @Test
    fun `card status splits owed into billed and unbilled`() {
        val today = LocalDate.of(2026, 10, 20)
        val l = ledger(
            listOf(
                txn(1, TxType.EXPENSE, 2_000_00, LocalDate.of(2026, 10, 3), account = 3),
                txn(2, TxType.EXPENSE, 1_000_00, LocalDate.of(2026, 10, 12), account = 3),
            ),
        )
        val status = l.cardStatus(card, today)
        assertEquals(8_000_00, status.owed)
        assertEquals(7_000_00, status.billed)
        assertEquals(1_000_00, status.unbilled)
        assertEquals(LocalDate.of(2026, 10, 5), status.statementDate)
        assertEquals(LocalDate.of(2026, 10, 25), status.dueDate)
        assertEquals(5, status.daysToDue)
        assertFalse(status.overdue)
        assertEquals(92_000_00, status.available)
    }

    @Test
    fun `a paid statement is no longer due and leftover payment reduces unbilled`() {
        val today = LocalDate.of(2026, 10, 20)
        val l = ledger(
            listOf(
                txn(1, TxType.EXPENSE, 1_000_00, LocalDate.of(2026, 10, 12), account = 3),
                txn(2, TxType.TRANSFER, 5_500_00, LocalDate.of(2026, 10, 14), account = 1, to = 3),
            ),
        )
        val status = l.cardStatus(card, today)
        assertEquals(0, status.billed)
        assertEquals(500_00, status.owed)
        assertEquals(500_00, status.unbilled)
    }

    @Test
    fun `unpaid statement past its due date is overdue`() {
        val today = LocalDate.of(2026, 10, 30)
        assertTrue(ledger(emptyList()).cardStatus(card, today).overdue)
    }

    @Test
    fun `due date rolls into the next month when due day is before statement day`() {
        val next = card.copy(statementDay = 20, dueDay = 8)
        val status = ledger(emptyList()).cardStatus(next, LocalDate.of(2026, 10, 25))
        assertEquals(LocalDate.of(2026, 10, 20), status.statementDate)
        assertEquals(LocalDate.of(2026, 11, 8), status.dueDate)
    }

    @Test
    fun `statement day of 31 clamps in short months`() {
        assertEquals(LocalDate.of(2026, 2, 28), latestDayOfMonth(31, LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun `card without dates reports everything as billed`() {
        val plain = card.copy(statementDay = 0, dueDay = 0)
        val status = ledger(emptyList()).cardStatus(plain, oct10)
        assertEquals(5_000_00, status.billed)
        assertNull(status.dueDate)
    }

    @Test
    fun `breakdowns use your share and sort largest first`() {
        val l = ledger(
            listOf(
                txn(1, TxType.EXPENSE, 150_00, oct10, category = 1, payee = "Shubham"),
                txn(2, TxType.EXPENSE, 150_00, oct10, category = 1, payee = "shubham "),
                txn(3, TxType.EXPENSE, 1_000_00, oct10, category = 2, payee = "Swiggy"),
            ),
            shares = listOf(SplitShare(1, 3, 2, 900_00)),
            tags = listOf(Tag(1, "football")),
            txnTags = listOf(TxnTag(1, 1), TxnTag(2, 1)),
        )
        assertEquals(listOf(1L, 2L), l.byCategory(october).map { it.key })
        assertEquals(300_00, l.byTag(october).single().amount)
        assertEquals(2, l.byPayee(october).first().count)
        assertEquals(300_00, l.byPayee(october).first().amount)
    }

    @Test
    fun `money flow adds savings but leaves out card bills`() {
        val l = ledger(
            listOf(
                txn(1, TxType.EXPENSE, 500_00, oct10, category = 1),
                txn(2, TxType.TRANSFER, 10_000_00, oct10, account = 1, to = 2),
                txn(3, TxType.TRANSFER, 5_000_00, oct10, account = 1, to = 3),
            ),
        )
        val links = l.moneyFlow(october).associateBy { it.target }
        assertEquals(2, links.size)
        assertEquals(500_00, links.getValue(1L).amount)
        assertEquals(10_000_00, links.getValue(SAVED).amount)
    }

    @Test
    fun `daily spend lines up with the day of the month and accumulates`() {
        val l = ledger(listOf(txn(1, TxType.EXPENSE, 100_00, LocalDate.of(2026, 10, 1)), txn(2, TxType.EXPENSE, 50_00, LocalDate.of(2026, 10, 3))))
        val daily = l.dailySpend(YearMonth.of(2026, 10))
        assertEquals(31, daily.size)
        assertEquals(100_00, daily[0])
        assertEquals(150_00, daily.runningTotal()[30])
    }

    @Test
    fun `repeats need at least two matches and ignore old payments`() {
        val today = LocalDate.of(2026, 10, 30)
        val l = ledger(
            listOf(
                txn(1, TxType.EXPENSE, 150_00, today.minusDays(2), payee = "Shubham"),
                txn(2, TxType.EXPENSE, 150_00, today.minusDays(4), payee = "Shubham"),
                txn(3, TxType.EXPENSE, 999_00, today.minusDays(1), payee = "Once"),
                txn(4, TxType.EXPENSE, 300_00, today.minusDays(90), payee = "Old"),
                txn(5, TxType.EXPENSE, 300_00, today.minusDays(91), payee = "Old"),
            ),
        )
        val repeats = l.quickRepeats(today)
        assertEquals(1, repeats.size)
        assertEquals("Shubham", repeats.single().latest.payee)
        assertEquals(2, repeats.single().count)
    }
}
