package com.farhan.paisa.domain

import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Person
import com.farhan.paisa.data.Recurring
import com.farhan.paisa.data.SplitShare
import com.farhan.paisa.data.Tag
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxnTag
import com.farhan.paisa.data.TxType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PatternsTest {
    private val october = Period.of(YearMonth.of(2026, 10))
    private val september = Period.of(YearMonth.of(2026, 9))
    private val endOfMonth = LocalDate.of(2026, 10, 31)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val rent = Category(id = 1, name = "Home & rent", icon = "home")
    private val bills = Category(id = 2, name = "Bills", icon = "bills")
    private val food = Category(id = 3, name = "Food", icon = "food", sortOrder = 1)
    private val cab = Category(id = 4, name = "Transport", icon = "transport", sortOrder = 2)
    private val categories = listOf(rent, bills, food, cab)

    private fun spend(id: Long, amount: Long, date: LocalDate, category: Long? = 3, payee: String = "") =
        Txn(id = id, type = TxType.EXPENSE, amount = amount, accountId = 1, categoryId = category, payee = payee, date = millis(date))

    @Test
    fun `rent and bills are fixed and the rest is everyday`() {
        val l = Ledger(
            categories = categories,
            txns = listOf(spend(1, 19_950_00, oct(1), 1), spend(2, 4_500_00, oct(1), 2), spend(3, 500_00, oct(2))),
        )
        assertEquals(SpendMix(24_450_00, 500_00), l.spendMix(october))
    }

    @Test
    fun `a tracked bill in any category counts as fixed`() {
        val l = Ledger(
            categories = categories,
            recurring = listOf(Recurring(id = 1, name = "Gym", amount = 1_000_00, day = 1)),
        )
        assertTrue(l.isFixed(spend(1, 1_000_00, oct(1), 3, "Gym")))
        assertFalse(l.isFixed(spend(1, 1_000_00, oct(1), 3, "Swiggy")))
    }

    @Test
    fun `weekday averages leave out rent and days that haven't happened`() {
        // 5 Oct 2026 is a Monday and 12 Oct the next one.
        val l = Ledger(
            categories = categories,
            txns = listOf(spend(1, 300_00, oct(5)), spend(2, 19_950_00, oct(5), 1), spend(3, 100_00, oct(12))),
        )
        val averages = l.weekdayAverages(october, oct(13))
        assertEquals(200_00L, averages[0])
    }

    @Test
    fun `one huge day doesn't wash out the shades of the others`() {
        val levels = heatLevels(longArrayOf(0, 100, 200, 300, 400, 2_000_000))
        assertArrayEquals(intArrayOf(0, 1, 1, 2, 3, 4), levels)
        assertTrue(heatLevels(longArrayOf(0, 0)).all { it == 0 })
    }

    @Test
    fun `highlights explain rent, frequent payees, splits and the biggest day`() {
        val l = Ledger(
            categories = categories,
            people = listOf(Person(1, "Neha")),
            txns = listOf(
                spend(1, 20_000_00, oct(1), 1),
                spend(2, 300_00, oct(2), payee = "Blinkit"),
                spend(3, 300_00, oct(3), payee = "Blinkit"),
                spend(4, 300_00, oct(3), payee = "Blinkit"),
                spend(5, 2_000_00, oct(4), payee = "Dinner"),
            ),
            shares = listOf(SplitShare(1, 5, 1, 1_000_00)),
        )
        val found = l.highlights(october, september, endOfMonth).associateBy { it.kind }
        assertEquals("91% went to rent and bills", found.getValue(HighlightKind.FIXED).title)
        assertEquals("3 payments to Blinkit", found.getValue(HighlightKind.FREQUENT).title)
        assertEquals("Splitting saved you ₹1,000", found.getValue(HighlightKind.SPLIT).title)
        assertEquals("Sunday, 4 Oct cost the most", found.getValue(HighlightKind.PEAK).title)
    }

    @Test
    fun `comparing with last month uses the same number of days`() {
        val l = Ledger(
            categories = categories,
            txns = listOf(
                spend(1, 500_00, oct(2)),
                spend(2, 800_00, LocalDate.of(2026, 9, 2)),
                spend(3, 9_000_00, LocalDate.of(2026, 9, 20)),
            ),
        )
        val pace = l.highlights(october, september, oct(5)).first { it.kind == HighlightKind.PACE }
        assertEquals("₹300 less than last month so far", pace.title)
    }

    @Test
    fun `no spending gives no highlights`() {
        assertTrue(Ledger().highlights(october, september, endOfMonth).isEmpty())
    }

    @Test
    fun `weekend pattern waits for at least two weeks of data`() {
        val l = Ledger(categories = categories, txns = listOf(spend(1, 5_000_00, oct(3)), spend(2, 100_00, oct(5))))
        assertFalse(l.highlights(october, september, oct(10)).any { it.kind == HighlightKind.WEEKEND })
        assertTrue(l.highlights(october, september, oct(20)).any { it.kind == HighlightKind.WEEKEND })
    }

    private val foodTag = Tag(1, "food")
    private val cabTag = Tag(2, "cab")

    private fun tagged() = Ledger(
        categories = categories,
        tags = listOf(foodTag, cabTag),
        txns = listOf(
            spend(1, 300_00, oct(2), payee = "Swiggy"),
            spend(2, 200_00, oct(4), payee = "Swiggy"),
            spend(3, 100_00, oct(6), payee = "Cafe"),
            spend(4, 50_00, oct(6), 4),
            spend(5, 400_00, LocalDate.of(2026, 9, 10)),
            spend(6, 700_00, oct(7), 1),
        ),
        txnTags = listOf(TxnTag(1, 1), TxnTag(2, 1), TxnTag(3, 1), TxnTag(4, 2), TxnTag(5, 1)),
    )

    @Test
    fun `tag stats compare with the month before`() {
        val stats = tagged().tagStats(october, september).associateBy { it.tagId }
        assertEquals(TagStat(1, 600_00, 3, 400_00), stats[1L])
        assertEquals(TagStat(2, 50_00, 1, 0), stats[2L])
    }

    @Test
    fun `untagged spending and tags within a category are separated`() {
        val l = tagged()
        assertEquals(700_00, l.untaggedSpend(october))
        assertEquals(listOf(1L), l.tagsIn(3, october).map { it.key })
        assertEquals(600_00, l.spentOnTag(1, october))
    }

    @Test
    fun `tag rhythm needs a few days and top payees are ranked`() {
        val l = tagged()
        assertNotNull(l.tagRhythm(1))
        assertNull(l.tagRhythm(2))
        assertEquals("Swiggy", l.topPayeesFor(1).first().name)
        assertEquals(500_00, l.topPayeesFor(1).first().amount)
    }

    @Test
    fun `a tag used with most of a category is filled in for you`() {
        assertEquals(1L, tagged().usualTagFor(3))
    }

    @Test
    fun `a tag used only once or for a minority isn't assumed`() {
        val l = Ledger(
            txns = listOf(spend(1, 100, oct(1), 2), spend(2, 100, oct(2), 2), spend(3, 100, oct(3), 2)),
            txnTags = listOf(TxnTag(1, 1)),
        )
        assertNull(l.usualTagFor(2))
    }

    @Test
    fun `the person you split with most is offered`() {
        val l = Ledger(
            people = listOf(Person(1, "Neha"), Person(2, "Rahul")),
            txns = listOf(spend(1, 100, oct(1)), spend(2, 100, oct(2)), spend(3, 100, oct(3))),
            shares = listOf(SplitShare(1, 1, 1, 50), SplitShare(2, 2, 1, 50), SplitShare(3, 3, 2, 50)),
        )
        assertEquals(1L, l.usualSplitPartner(oct(5)))
        assertNull(l.usualSplitPartner(oct(5).plusMonths(3)))
    }

    @Test
    fun `categories you use most come first`() {
        val l = Ledger(categories = categories, txns = listOf(spend(1, 100, oct(1), 4), spend(2, 100, oct(2), 4), spend(3, 100, oct(3), 3)))
        assertEquals(listOf(4L, 3L), l.categoriesByUse(income = false).take(2).map { it.id })
    }

    @Test
    fun `the usual category beats the latest one for a shop that sells two things`() {
        val detected = com.farhan.paisa.data.Detected(
            dedupKey = "k",
            type = TxType.EXPENSE,
            amount = 100,
            counterparty = "Blinkit",
            last4 = "1111",
            method = com.farhan.paisa.data.PayMethod.UPI,
            date = millis(oct(9)),
        )
        val l = Ledger(
            categories = categories,
            txns = listOf(
                spend(1, 1_770_00, oct(8), 4, "Blinkit"),
                spend(2, 300_00, oct(7), 3, "Blinkit"),
                spend(3, 300_00, oct(6), 3, "Blinkit"),
            ),
        )
        assertEquals(3L, l.suggestionFor(detected).categoryId)
    }
}
