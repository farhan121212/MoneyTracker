package com.farhan.paisa.domain

import com.farhan.paisa.data.Account
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.Category
import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.DetectedStatus
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Person
import com.farhan.paisa.data.SplitShare
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Money coming in. These follow the wording banks commonly use, not messages from a real
 * account, so add a real sample here whenever a bank words it differently.
 */
class IncomingSmsTest {
    private val hdfc = "AD-HDFCBK-S"
    private val axis = "AD-AXISBK-S"
    private val kotak = "AX-KOTAKB-S"
    private val icici = "VM-ICICIB-S"
    private val sbi = "AD-SBIUPI-S"

    private fun parsed(sender: String, body: String) = requireNotNull(SmsParser.parse(sender, body)) { body }

    @Test
    fun `upi money in, worded like kotak`() {
        val sms = parsed(kotak, "Received Rs.1500.00 in your Kotak Bank A/c X5521 from RAHUL SHARMA on 30-10-26. UPI Ref 600000000099.")
        assertEquals(SmsKind.RECEIVED, sms.kind)
        assertEquals(1_500_00L, sms.amount)
        assertEquals("5521", sms.last4)
        assertEquals("Rahul Sharma", sms.counterparty)
        assertEquals("600000000099", sms.ref)
    }

    @Test
    fun `credit alert with a upi id, worded like hdfc`() {
        val sms = parsed(hdfc, "Credit Alert! Rs.500.00 credited to HDFC Bank A/c XX7730 on 30-10-26 from VPA rahul.sharma@okaxis (UPI 600000000123).")
        assertEquals(50_000L, sms.amount)
        assertEquals("7730", sms.last4)
        assertEquals("Rahul Sharma", sms.counterparty)
        assertEquals("600000000123", sms.ref)
    }

    @Test
    fun `a upi id that is only a phone number gives no name`() {
        val sms = parsed(hdfc, "Rs.500.00 credited to a/c XX7730 on 30-10-26 by a/c linked to VPA 9876543210@ybl (UPI Ref No 600000000124).")
        assertEquals("", sms.counterparty)
    }

    @Test
    fun `info line naming the sender, worded like axis, ignores the balance`() {
        val sms = parsed(axis, "INR 2,000.00 credited to A/c no. XX7730 on 30-10-26 at 10:15:00 IST. Info- UPI/P2A/600000000125/PRIYA NAIR/HDFC. Avl Bal- INR 45,210.50.")
        assertEquals(2_000_00L, sms.amount)
        assertEquals("Priya Nair", sms.counterparty)
        assertEquals("600000000125", sms.ref)
    }

    @Test
    fun `balance quoted first is not taken as the amount`() {
        assertEquals(500_00L, parsed(hdfc, "Avl Bal Rs.12000.00 in A/c XX7730. Rs.500.00 credited on 30-10-26. Ref 600000000126").amount)
    }

    @Test
    fun `account wording used by icici and sbi, with month names`() {
        val icic = parsed(icici, "Acct XX5521 is credited with Rs 750.00 on 30-Oct-26 from ROHIT VERMA. UPI:600000000127")
        assertEquals("5521", icic.last4)
        assertEquals("Rohit Verma", icic.counterparty)
        assertEquals(LocalDate.of(2026, 10, 30), icic.date)
        assertEquals("600000000127", icic.ref)

        val state = parsed(sbi, "Dear UPI user A/C X7730 credited by Rs500.00 on 30Oct26 by ROHIT VERMA. Ref No 600000000128")
        assertEquals(500_00L, state.amount)
        assertEquals("Rohit Verma", state.counterparty)
        assertEquals(LocalDate.of(2026, 10, 30), state.date)
    }

    @Test
    fun `when the sender's account comes first, yours is the one used`() {
        assertEquals("7730", parsed(hdfc, "Received Rs.500 from A/c XX1111 in your HDFC Bank A/C *7730 on 30/10/26. Ref 600000000129").last4)
    }

    @Test
    fun `salary is named as salary`() {
        val sms = parsed(hdfc, "Update! INR 85,000.00 deposited in HDFC Bank A/c XX7730 on 01-OCT-26 for NEFT Cr-ICIC0001234-ACME PVT LTD-SALARY OCT 2026. Avl bal INR 1,30,000.00.")
        assertEquals(85_000_00L, sms.amount)
        assertEquals(SmsParser.SALARY, sms.counterparty)
        assertEquals(PayMethod.NET_BANKING, sms.method)
    }

    @Test
    fun `bank transfer names the sender`() {
        assertEquals("Acme Pvt Ltd", parsed(hdfc, "Update! INR 5,000.00 deposited in HDFC Bank A/c XX7730 on 01-OCT-26 for NEFT Cr-ICIC0001234-ACME PVT LTD-FREELANCE. Avl bal INR 90,000.00.").counterparty)
    }

    @Test
    fun `refunds are recognised, on accounts and cards`() {
        assertEquals("Refund from Amazon", parsed(kotak, "Refund of Rs.899.00 credited to Kotak Bank A/c X5521 from AMAZON on 30-10-26. Ref 600000000130").counterparty)
        assertEquals(SmsKind.RECEIVED, parsed(axis, "INR 1200 reversed to your Axis Bank Credit Card no. XX9046 on 30-10-26.").kind)
    }

    @Test
    fun `spam about money arriving is rejected`() {
        listOf(
            "Amount credited 4000, shop from our place",
            "Rs.4000 credited to your account",
            "Rs.4000 credited to your account! Shop now at our store",
            "Get Rs.4000 credited to A/c XX7730 instantly",
            "Rs.5000 will be credited to A/c XX7730 on your first order",
            "Congrats! Rs.2000 received in A/c XX7730. Claim here",
            "You've won a prize of Rs.50000, credited to A/c XX7730",
        ).forEach { assertNull(it, SmsParser.parse(hdfc, it)) }
    }

    @Test
    fun `a failed credit is ignored`() {
        assertNull(SmsParser.parse(hdfc, "Rs.500 could not be credited to A/c XX7730"))
    }

    private val today = LocalDate.of(2026, 10, 20)
    private val millis = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val kotakAccount = Account(id = 1, name = "Kotak", kind = AccountKind.BANK, last4 = "5521")
    private val rahul = Person(id = 1, name = "Rahul")
    private val salary = Category(id = 5, name = "Salary", icon = "salary", income = true)
    private val refund = Category(id = 6, name = "Refund", icon = "refund", income = true)
    private val food = Category(id = 7, name = "Food", icon = "food")

    private fun incoming(name: String) = Detected(
        dedupKey = "k",
        type = TxType.INCOME,
        amount = 500_00,
        counterparty = name,
        last4 = "5521",
        method = PayMethod.UPI,
        date = millis,
    )

    private fun owing(owed: Long) = Ledger(
        accounts = listOf(kotakAccount),
        people = listOf(rahul),
        txns = listOf(Txn(id = 9, type = TxType.EXPENSE, amount = 2_000_00, accountId = 1, date = millis)),
        shares = listOf(SplitShare(1, 9, 1, owed)),
    )

    @Test
    fun `someone who owes you is recognised from a fuller name`() {
        assertEquals(1L, owing(800_00).repayerFor(incoming("Rahul Sharma"))?.id)
    }

    @Test
    fun `no one is a repayer when they owe nothing or the name differs`() {
        assertNull(owing(0).repayerFor(incoming("Rahul Sharma")))
        assertNull(owing(800_00).repayerFor(incoming("Priya Nair")))
        assertNull(owing(800_00).repayerFor(incoming("")))
    }

    @Test
    fun `money paid out never counts as a repayment`() {
        assertNull(owing(800_00).repayerFor(incoming("Rahul Sharma").copy(type = TxType.EXPENSE)))
    }

    @Test
    fun `salary and refunds pick their category`() {
        val l = Ledger(categories = listOf(salary, refund))
        assertEquals(5L, l.suggestionFor(incoming(SmsParser.SALARY)).categoryId)
        assertEquals(6L, l.suggestionFor(incoming("Refund from Amazon")).categoryId)
        assertNull(l.suggestionFor(incoming("Someone")).categoryId)
    }

    @Test
    fun `an expense category is never suggested for money coming in`() {
        val l = Ledger(
            categories = listOf(food, salary),
            txns = listOf(Txn(id = 1, type = TxType.EXPENSE, amount = 300_00, accountId = 1, categoryId = 7, payee = "Rahul", date = millis)),
        )
        assertNull(l.suggestionFor(incoming("Rahul")).categoryId)
    }

    @Test
    fun `earlier income from the same person is remembered`() {
        val l = Ledger(
            categories = listOf(salary),
            txns = listOf(Txn(id = 1, type = TxType.INCOME, amount = 300_00, accountId = 1, categoryId = 5, payee = "Client", date = millis)),
            detected = listOf(incoming("Client Co").copy(id = 1, status = DetectedStatus.CONFIRMED, txnId = 1)),
        )
        assertEquals(5L, l.suggestionFor(incoming("Client Co")).categoryId)
    }

    @Test
    fun `refunds and repayments aren't counted as earnings`() {
        val month = YearMonth.from(today)
        val l = Ledger(
            accounts = listOf(kotakAccount),
            categories = listOf(salary, refund),
            txns = listOf(
                Txn(id = 1, type = TxType.INCOME, amount = 85_000_00, accountId = 1, categoryId = 5, date = millis),
                Txn(id = 2, type = TxType.INCOME, amount = 899_00, accountId = 1, categoryId = 6, date = millis),
                Txn(id = 3, type = TxType.SETTLEMENT, amount = 500_00, accountId = 1, personId = 1, date = millis),
            ),
        )
        assertEquals(85_000_00L, l.incomeIn(Period.of(month)))
        assertEquals(86_399_00L, l.balanceOf(kotakAccount))
    }

    @Test
    fun `balances stay right for accounts a transfer touches`() {
        val card = Account(id = 2, name = "Card", kind = AccountKind.CREDIT_CARD, opening = 5_000_00)
        val l = Ledger(
            accounts = listOf(kotakAccount.copy(opening = 10_000_00), card),
            txns = listOf(Txn(id = 1, type = TxType.TRANSFER, amount = 2_000_00, accountId = 1, toAccountId = 2, date = millis)),
        )
        assertEquals(8_000_00L, l.balanceOf(kotakAccount))
        assertEquals(-3_000_00L, l.balanceOf(card))
    }
}
