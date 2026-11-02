package com.farhan.paisa.domain

import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.AccountRole
import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.PayeeRule
import com.farhan.paisa.data.Recurring
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.SplitShare
import com.farhan.paisa.data.Tag
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxnTag
import com.farhan.paisa.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class OutlookTest {
    private val october = Period.of(YearMonth.of(2026, 10))
    private val november = Period.of(YearMonth.of(2026, 11))
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val bank = Account(id = 1, name = "Daily", kind = AccountKind.BANK, opening = 50_000_00)
    private val savings = Account(id = 2, name = "Savings", kind = AccountKind.BANK, role = AccountRole.SAVINGS, opening = 1_00_000_00)
    private val card = Account(id = 3, name = "Card", kind = AccountKind.CREDIT_CARD, statementDay = 5, dueDay = 25)

    private val food = Category(id = 1, name = "Food", icon = "food", monthlyLimit = 1_000_00)
    private val shopping = Category(id = 2, name = "Shopping", icon = "shopping")
    private val rent = Category(id = 3, name = "Home & rent", icon = "home")
    private val refundCategory = Category(id = 4, name = "Refund", icon = "refund", income = true)
    private val salaryCategory = Category(id = 5, name = "Salary", icon = "salary", income = true)
    private val categories = listOf(food, shopping, rent, refundCategory, salaryCategory)

    private fun spend(id: Long, amount: Long, date: LocalDate, category: Category = food, payee: String = "Cafe", account: Account = bank) =
        Txn(id = id, type = TxType.EXPENSE, amount = amount, accountId = account.id, categoryId = category.id, payee = payee, date = millis(date))

    private fun refund(id: Long, amount: Long, date: LocalDate, payee: String) =
        Txn(id = id, type = TxType.INCOME, amount = amount, accountId = bank.id, categoryId = refundCategory.id, payee = payee, date = millis(date))

    private fun salary(id: Long, amount: Long, date: LocalDate) =
        Txn(id = id, type = TxType.INCOME, amount = amount, accountId = bank.id, categoryId = salaryCategory.id, payee = "Salary", date = millis(date))

    private fun ledger(vararg txns: Txn, recurring: List<Recurring> = emptyList(), accounts: List<Account> = listOf(bank, savings, card)) =
        Ledger(accounts = accounts, txns = txns.sortedByDescending { it.date }, categories = categories, recurring = recurring)

    @Test
    fun `a refund lowers spending but is not income`() {
        val l = ledger(spend(1, 2_000_00, oct(3), shopping, "Myntra"), refund(2, 500_00, oct(6), "Refund from Myntra"))
        assertEquals(1_500_00, l.spentIn(october))
        assertEquals(0, l.incomeIn(october))
    }

    @Test
    fun `a refund comes off the category of the purchase it matches`() {
        val l = ledger(
            spend(1, 2_000_00, oct(3), shopping, "Myntra"),
            spend(2, 700_00, oct(4), food, "Cafe"),
            refund(3, 500_00, oct(6), "Refund from Myntra"),
        )
        val slices = l.byCategory(october).associate { it.key to it.amount }
        assertEquals(1_500_00L, slices[shopping.id])
        assertEquals(700_00L, slices[food.id])
        assertEquals(1_500_00L, l.byPayee(october).first { it.payee == "Myntra" }.amount)
    }

    @Test
    fun `a refund with no matching purchase still lowers the total`() {
        val l = ledger(spend(1, 700_00, oct(4)), refund(2, 200_00, oct(6), "Refund"))
        assertEquals(500_00, l.spentIn(october))
        assertEquals(700_00, l.byCategory(october).single().amount)
    }

    @Test
    fun `a refund lands on its own day and daily totals still add up`() {
        val l = ledger(spend(1, 700_00, oct(4), shopping, "Myntra"), refund(2, 900_00, oct(6), "Myntra"))
        val daily = l.dailySpend(october)
        assertEquals(700_00, daily[3])
        assertEquals(-900_00, daily[5])
        assertEquals(0, l.spentIn(october))
    }

    @Test
    fun `a refund of a rent payment comes off bills rather than everyday`() {
        val l = ledger(
            spend(1, 20_000_00, oct(1), rent, "Landlord"),
            spend(2, 1_000_00, oct(2), food),
            refund(3, 5_000_00, oct(3), "Landlord"),
        )
        val mix = l.spendMix(october)
        assertEquals(15_000_00, mix.fixed)
        assertEquals(1_000_00, mix.everyday)
        assertEquals(0, l.everydaySpend(october)[2])
    }

    @Test
    fun `a buried refund can't push a total below zero`() {
        val l = ledger(refund(1, 900_00, oct(6), "Refund"))
        assertEquals(0, l.spentIn(october))
        assertEquals(0, l.spendMix(october).total)
    }

    @Test
    fun `forecast adds the everyday pace and bills still to come`() {
        val l = ledger(
            spend(1, 20_000_00, oct(1), rent, "Landlord"),
            spend(2, 300_00, oct(2)),
            spend(3, 300_00, oct(5)),
            spend(4, 400_00, oct(9)),
        )
        val forecast = assertNotNullForecast(l.forecast(october, oct(10), 40_000_00, 2_000_00))
        // Spent 21,000, everyday 100 a day for 21 more days, 2,000 of bills.
        assertEquals(25_100_00, forecast.total)
        assertEquals(14_900_00, forecast.margin)
        assertEquals("On pace to finish ₹14,900 under your limit", forecast.message)
    }

    @Test
    fun `forecast warns when the pace runs past the limit`() {
        val l = ledger(spend(1, 3_000_00, oct(1)), spend(2, 3_000_00, oct(5)))
        val forecast = assertNotNullForecast(l.forecast(october, oct(6), 10_000_00, 0))
        assertTrue(forecast.over)
        assertEquals("At this pace you'll finish ₹21,000 over your limit", forecast.message)
    }

    @Test
    fun `forecast without a limit just gives the total`() {
        val l = ledger(spend(1, 3_000_00, oct(1)))
        val forecast = assertNotNullForecast(l.forecast(october, oct(10), 0, 0))
        assertEquals("At this pace you'll spend about ₹9,300 this month", forecast.message)
    }

    @Test
    fun `forecast waits for a few days of data and stops on the last day`() {
        val l = ledger(spend(1, 3_000_00, oct(1)))
        assertNull(l.forecast(october, oct(2), 10_000_00, 0))
        assertNull(l.forecast(october, oct(31), 10_000_00, 0))
        assertNull(l.forecast(october, november.start, 10_000_00, 0))
        assertNull(ledger().forecast(october, oct(10), 10_000_00, 0))
    }

    @Test
    fun `real money takes out cards and bills`() {
        val l = ledger(
            spend(1, 12_000_00, oct(2), account = card),
            recurring = listOf(Recurring(id = 1, name = "Gym", amount = 1_500_00, day = 20)),
        )
        val real = l.realMoney(oct(10), october)
        assertEquals(1_50_000_00, real.cash)
        assertEquals(12_000_00, real.cards)
        assertEquals(1_500_00, real.bills)
        assertEquals(1_36_500_00, real.free)
    }

    @Test
    fun `due plan shows what today's money must cover before salary`() {
        val l = ledger(
            spend(1, 60_000_00, oct(2), account = card),
            spend(2, 500_00, oct(8), account = card),
            recurring = listOf(Recurring(id = 1, name = "Gym", amount = 1_500_00, day = 20)),
        )
        val plan = l.duePlan(oct(10), october, november)
        assertEquals(50_000_00, plan.balance)
        assertEquals(listOf("Gym", "Card"), plan.beforeSalary.map { it.item.name })
        assertEquals(listOf(48_500_00L, -11_500_00L), plan.beforeSalary.map { it.left })
        assertEquals(11_500_00, plan.shortBy)
        assertTrue(plan.afterSalary.any { it.kind == DueKind.CARD_NEXT && it.amount == 500_00L })
    }

    @Test
    fun `a bill paid by card leaves the bank balance alone`() {
        val l = ledger(recurring = listOf(Recurring(id = 1, name = "Netflix", amount = 649_00, day = 20, accountId = card.id)))
        val plan = l.duePlan(oct(10), october, november)
        assertEquals(50_000_00, plan.beforeSalary.single().left)
        assertEquals(0, plan.total)
    }

    @Test
    fun `cards without dates are called out`() {
        val blank = card.copy(id = 4, name = "No dates", statementDay = 0, dueDay = 0)
        val l = ledger(spend(1, 500_00, oct(2), account = blank), accounts = listOf(bank, blank))
        val plan = l.duePlan(oct(10), october, november)
        assertEquals(listOf(blank), plan.undated)
        assertTrue(plan.beforeSalary.isEmpty())
    }

    @Test
    fun `salary plan divides what landed`() {
        val l = ledger(
            salary(1, 85_000_00, oct(1)),
            spend(2, 18_000_00, oct(2), rent, "Landlord"),
            spend(3, 10_000_00, LocalDate.of(2026, 9, 20), account = card),
            recurring = listOf(
                Recurring(id = 1, name = "Landlord", amount = 18_000_00, day = 3),
                Recurring(id = 2, name = "Gym", amount = 2_000_00, day = 20),
            ),
        )
        val plan = assertNotNullPlan(l.salaryPlan(oct(6), october, 20_000_00))
        assertFalse(plan.estimated)
        assertEquals(20_000_00, plan.billsTotal)
        assertEquals(10_000_00, plan.cardsTotal)
        assertEquals(35_000_00, plan.everyday)
        assertEquals(1_129_03, plan.perDay)
    }

    @Test
    fun `salary plan estimates from the last salary until this one lands`() {
        val l = ledger(salary(1, 85_000_00, LocalDate.of(2026, 9, 30)))
        val plan = assertNotNullPlan(l.salaryPlan(oct(3), october, 0))
        assertTrue(plan.estimated)
        assertEquals(85_000_00, plan.salary)
        assertNull(ledger().salaryPlan(oct(3), october, 0))
    }

    @Test
    fun `salary plan is offered for a week unless dismissed or done`() {
        val l = ledger(
            salary(1, 85_000_00, oct(1)),
            recurring = listOf(Recurring(id = 1, name = "Gym", amount = 2_000_00, day = 20)),
        )
        assertTrue(l.salaryPlanDue(oct(3), october, Settings()))
        assertFalse(l.salaryPlanDue(oct(8), october, Settings()))
        assertFalse(l.salaryPlanDue(oct(3), october, Settings(planDismissed = october.token)))
        assertFalse(ledger(salary(1, 85_000_00, oct(1))).salaryPlanDue(oct(3), october, Settings()))
    }

    @Test
    fun `limits cover categories and tags, closest to running out first`() {
        val cab = Tag(id = 1, name = "cab", monthlyLimit = 500_00)
        val ride = spend(1, 450_00, oct(2), shopping, "Uber")
        val l = Ledger(
            accounts = listOf(bank),
            txns = listOf(spend(2, 100_00, oct(3)), ride),
            categories = categories,
            tags = listOf(cab),
            txnTags = listOf(TxnTag(ride.id, cab.id)),
        )
        val statuses = l.limitStatuses(october)
        assertEquals(listOf("#cab", "Food"), statuses.map { it.name })
        assertEquals(90, statuses.first().percent)
        assertEquals(900_00, statuses.last().left)
    }

    @Test
    fun `a split only counts your share against a limit`() {
        val meal = spend(1, 1_000_00, oct(2))
        val l = ledger(meal).copy(shares = listOf(SplitShare(txnId = meal.id, personId = 1, amount = 500_00)))
        assertEquals(500_00, l.limitStatuses(october).single().spent)
    }

    private fun limit(spent: Long) = LimitStatus(LimitKind.CATEGORY, 1, "Food", 1_000_00, spent)

    @Test
    fun `a limit warns once at 80 percent and once at 100`() {
        val first = checkLimits(listOf(limit(700_00)), october, "")
        assertTrue(first.alerts.isEmpty())

        val nearly = checkLimits(listOf(limit(820_00)), october, first.log)
        assertEquals(80, nearly.alerts.single().threshold)
        assertEquals("Food is at 82% of its limit", nearly.alerts.single().title)

        assertTrue(checkLimits(listOf(limit(850_00)), october, nearly.log).alerts.isEmpty())

        val over = checkLimits(listOf(limit(1_050_00)), october, nearly.log)
        assertEquals(100, over.alerts.single().threshold)
        assertTrue(checkLimits(listOf(limit(1_100_00)), october, over.log).alerts.isEmpty())
    }

    @Test
    fun `jumping past 100 percent sends one warning and a new month starts fresh`() {
        val over = checkLimits(listOf(limit(1_500_00)), october, "")
        assertEquals(100, over.alerts.single().threshold)
        assertEquals("Food went over its limit", over.alerts.single().title)
        assertEquals(100, checkLimits(listOf(limit(1_500_00)), november, over.log).alerts.single().threshold)
    }

    @Test
    fun `a rule renames new payments and finds the usual category`() {
        val earlier = spend(1, 300_00, oct(2), shopping, "Cab")
        val l = ledger(earlier).copy(payeeRules = listOf(PayeeRule(phrase = "ravi kumar", name = "Cab")))
        val suggestion = l.suggestionFor(detected("RAVI KUMAR K"))
        assertEquals("Cab", suggestion.payee)
        assertEquals(shopping.id, suggestion.categoryId)
        assertEquals("Someone Else", l.suggestionFor(detected("Someone Else")).payee)
    }

    @Test
    fun `a rule beats what a name was confirmed as before`() {
        val logged = spend(1, 300_00, oct(2), shopping, "Ravi Kumar")
        val l = ledger(logged).copy(
            detected = listOf(detected("Ravi Kumar").copy(id = 9, status = com.farhan.paisa.data.DetectedStatus.CONFIRMED, txnId = logged.id)),
            payeeRules = listOf(PayeeRule(phrase = "Ravi", name = "Cab")),
        )
        assertEquals("Cab", l.suggestionFor(detected("Ravi Kumar")).payee)
    }

    @Test
    fun `a rule needs a few letters and ignores spacing`() {
        assertFalse(PayeeRule(phrase = "ab", name = "X").matches("Abhinav"))
        assertTrue(PayeeRule(phrase = "chai point", name = "Chai").matches("CHAIPOINT BLR"))
    }

    private fun detected(name: String) = Detected(
        dedupKey = name,
        type = TxType.EXPENSE,
        amount = 100_00,
        counterparty = name,
        last4 = "",
        method = PayMethod.UPI,
        date = millis(oct(5)),
    )

    @Test
    fun `recap sums up the month`() {
        val meal = spend(2, 1_000_00, oct(2))
        val l = ledger(
            salary(1, 85_000_00, oct(1)),
            meal,
            spend(3, 20_000_00, oct(3), rent, "Landlord"),
            spend(4, 500_00, oct(4)),
            spend(5, 900_00, LocalDate.of(2026, 9, 10)),
        ).copy(shares = listOf(SplitShare(txnId = meal.id, personId = 1, amount = 400_00)))
        val recap = l.recap(october, Period.of(YearMonth.of(2026, 9)))
        assertEquals(21_100_00, recap.spent)
        assertEquals(85_000_00, recap.earned)
        assertEquals(900_00, recap.previousSpent)
        assertEquals(rent.id, recap.topCategory?.key)
        assertEquals(oct(2), recap.busiestDay)
        assertEquals(600_00, recap.busiestAmount)
        assertEquals(28, recap.noSpendDays)
        assertEquals(400_00, recap.splitSaved)
        assertEquals(0, recap.limitsKept)
        assertEquals(1, recap.limitsSet)
    }

    @Test
    fun `recap is offered early in the new month until opened`() {
        val l = ledger(*Array(5) { spend(it + 1L, 100_00, oct(it + 1)) })
        val nov = { day: Int -> LocalDate.of(2026, 11, day) }
        assertTrue(l.recapDue(nov(2), november, october, Settings()))
        assertFalse(l.recapDue(nov(15), november, october, Settings()))
        assertFalse(l.recapDue(nov(2), november, october, Settings(recapSeen = october.token)))
        assertFalse(ledger(spend(1, 100_00, oct(1))).recapDue(nov(2), november, october, Settings()))
    }

    @Test
    fun `widget snapshot reads the limit and hides nothing by itself`() {
        val l = ledger(spend(1, 2_000_00, oct(1)), spend(2, 500_00, oct(10)))
        val snapshot = l.homeSnapshot(Settings(monthlyBudget = 10_000_00), oct(10))
        assertTrue(snapshot.hasLimit)
        assertEquals(7_500_00, snapshot.left)
        assertEquals(500_00, snapshot.spentToday)
        assertEquals(Tone.GOOD, snapshot.tone)
        assertEquals(oct(31), snapshot.until)
        assertFalse(snapshot.locked)
        assertTrue(l.homeSnapshot(Settings(lockEnabled = true), oct(10)).locked)
    }

    @Test
    fun `widget snapshot warns when ahead of pace and when over`() {
        val l = ledger(spend(1, 6_000_00, oct(1)))
        assertEquals(Tone.WATCH, l.homeSnapshot(Settings(monthlyBudget = 10_000_00), oct(2)).tone)
        assertEquals(Tone.OVER, l.homeSnapshot(Settings(monthlyBudget = 5_000_00), oct(2)).tone)
    }

    private fun assertNotNullForecast(forecast: Forecast?): Forecast {
        assertNotNull(forecast)
        return forecast!!
    }

    private fun assertNotNullPlan(plan: SalaryPlan?): SalaryPlan {
        assertNotNull(plan)
        return plan!!
    }
}
