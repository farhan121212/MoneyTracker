package com.farhan.paisa.domain

import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.DetectedStatus
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SmsTest {
    private val kotak = "AX-KOTAKB-S"
    private val axis = "AD-AXISBK-S"
    private val hdfc = "AD-HDFCBK-T"

    // In the exact format the banks use, with the personal details changed.
    private val kotakPerson = "Sent Rs.125.00 from Kotak Bank A/c X5521 to ROHIT   VERMA on 30-10-26. UPI Ref 600000002034. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val kotakShop = "Sent Rs.458.00 from Kotak Bank A/c X5521 to ZEPTO MARKETPLACE PR on 07-10-26. UPI Ref 600000019862. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
    private val axisCard = "Spent INR 8750 \nusing Axis Bank Credit Card no. XX9046 on 29-10-26 18:44:56 IST at \nMAKEMYTRIP. \nAvl limit: INR 41230.50. Not you? SMS BLOCK 9046 to +919951860002."
    private val hdfcUpi = "Sent Rs.380.00\nFrom HDFC Bank A/C *7730\nTo Firstclub1\nOn 29/10/26\nRef 600000021416\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"

    @Test
    fun `kotak payment to a person`() {
        val sms = requireNotNull(SmsParser.parse(kotak, kotakPerson))
        assertEquals(SmsKind.SPENT, sms.kind)
        assertEquals(125_00L, sms.amount)
        assertEquals("5521", sms.last4)
        assertEquals("Rohit Verma", sms.counterparty)
        assertEquals("600000002034", sms.ref)
        assertEquals(PayMethod.UPI, sms.method)
        assertEquals(LocalDate.of(2026, 10, 30), sms.date)
    }

    @Test
    fun `kotak payment to a shop`() {
        val sms = requireNotNull(SmsParser.parse(kotak, kotakShop))
        assertEquals(458_00L, sms.amount)
        assertEquals("Zepto Marketplace Pr", sms.counterparty)
        assertEquals(LocalDate.of(2026, 10, 7), sms.date)
    }

    @Test
    fun `axis credit card spend uses the spend and not the available limit`() {
        val sms = requireNotNull(SmsParser.parse(axis, axisCard))
        assertEquals(SmsKind.SPENT, sms.kind)
        assertEquals(8_750_00L, sms.amount)
        assertEquals("9046", sms.last4)
        assertEquals("Makemytrip", sms.counterparty)
        assertEquals(PayMethod.CARD, sms.method)
        assertEquals(LocalDate.of(2026, 10, 29), sms.date)
    }

    @Test
    fun `hdfc multi line upi payment`() {
        val sms = requireNotNull(SmsParser.parse(hdfc, hdfcUpi))
        assertEquals(380_00L, sms.amount)
        assertEquals("7730", sms.last4)
        assertEquals("Firstclub1", sms.counterparty)
        assertEquals("600000021416", sms.ref)
        assertEquals(PayMethod.UPI, sms.method)
        assertEquals(LocalDate.of(2026, 10, 29), sms.date)
    }

    @Test
    fun `the promo from the example is rejected even from a bank sender`() {
        assertNull(SmsParser.parse(hdfc, "Amount credited 4000, shop from our place"))
        assertNull(SmsParser.parse(hdfc, "Rs 4000 credited, shop from our place today"))
    }

    @Test
    fun `a promo with no account digits is rejected even without promo words`() {
        assertNull(SmsParser.parse(hdfc, "Rs.4000 credited to your account"))
    }

    @Test
    fun `promotional senders and strangers are rejected whatever the text says`() {
        assertNull(SmsParser.parse("AD-HDFCBK-P", hdfcUpi))
        assertNull(SmsParser.parse("AD-SHOPIT-S", kotakPerson))
        assertNull(SmsParser.parse("+919876543210", kotakPerson))
    }

    @Test
    fun `adverts from a real bank sender are rejected`() {
        listOf(
            "Rs.50000 pre-approved loan credited to A/c X5521 instantly! Apply now",
            "Congratulations! Win Rs.5000 on your Card XX9046. Click https://bit.ly/x",
            "Spend Rs.2000 on Card XX9046 and get up to 20% off",
            "Limited offer: Rs.1000 cashback credited on Card XX9046",
        ).forEach { assertNull(it, SmsParser.parse(axis, it)) }
    }

    @Test
    fun `a payee whose name looks like an advert is still a payment`() {
        val sms = SmsParser.parse(kotak, "Sent Rs.300.00 from Kotak Bank A/c X5521 to THE BARBER SHOP on 30-10-26. UPI Ref 600000002011.")
        assertEquals("The Barber Shop", sms?.counterparty)
        assertEquals(30_000L, SmsParser.parse(axis, "Spent INR 300 using Axis Bank Credit Card no. XX9046 at LUCKY OFFER STORE on 29-10-26")?.amount)
    }

    @Test
    fun `non payments from a real bank sender are ignored`() {
        listOf(
            "123456 is your OTP for Rs.500 at AMAZON on Card XX9046",
            "Payment of Rs.500 from A/c X5521 failed",
            "Txn of INR 500 on Card XX9046 declined due to insufficient limit",
            "Your statement for Card XX9046 is ready. Total amount due INR 5000",
            "Payment of INR 5000 on Card XX9046 is due on 05-11-26",
            "Your credit limit on Card XX9046 is now Rs.100000",
            "Shubham has requested Rs.100 from A/c X5521 by UPI",
        ).forEach { assertNull(it, SmsParser.parse(axis, it)) }
    }

    @Test
    fun `a link left after the footer counts against the message`() {
        assertNull(SmsParser.parse(kotak, "Sent Rs.125.00 from A/c X5521. Details at https://kotak.example/x"))
    }

    @Test
    fun `money received`() {
        val sms = requireNotNull(SmsParser.parse(kotak, "Received Rs.1500.00 in your Kotak Bank A/c X5521 from RAHUL SHARMA on 30-10-26. UPI Ref 600000002099."))
        assertEquals(SmsKind.RECEIVED, sms.kind)
        assertEquals(1_500_00L, sms.amount)
        assertEquals("Rahul Sharma", sms.counterparty)
    }

    @Test
    fun `debited then credited is a payment`() {
        val sms = requireNotNull(SmsParser.parse(hdfc, "Rs.200 debited from a/c XX7730 and credited to shop@okaxis. Ref 123456789"))
        assertEquals(SmsKind.SPENT, sms.kind)
        assertEquals("Shop", sms.counterparty)
    }

    @Test
    fun `card bill payment is recognised`() {
        val sms = requireNotNull(SmsParser.parse(axis, "Payment of INR 5000 received on your Axis Bank Credit Card no. XX9046 on 30-10-26."))
        assertEquals(SmsKind.CARD_PAID, sms.kind)
        assertEquals("9046", sms.last4)
    }

    @Test
    fun `senders parse with and without a label`() {
        assertEquals("KOTAKB", SmsParser.senderCode("AX-KOTAKB-S"))
        assertEquals("KOTAKB", SmsParser.senderCode("VM-KOTAKB"))
        assertNull(SmsParser.senderCode("+919876543210"))
        assertFalse(SmsParser.isTrusted("AX-NEWBNK-S"))
        assertTrue(SmsParser.isTrusted("AX-NEWBNK-S", setOf("NEWBNK")))
    }

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private fun millis(day: Int, hour: Int = 12) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun detected(sender: String, body: String, sentAt: Long): Detected =
        SmsParser.toDetected(requireNotNull(SmsParser.parse(sender, body)), body, sentAt, zone)

    @Test
    fun `date comes from the message and time from when it was sent`() {
        val found = detected(kotak, kotakPerson, millis(31, 9))
        val moment = java.time.Instant.ofEpochMilli(found.date).atZone(zone)
        assertEquals(LocalDate.of(2026, 10, 30), moment.toLocalDate())
        assertEquals(9, moment.hour)
    }

    @Test
    fun `the same message seen twice is skipped`() {
        val first = detected(kotak, kotakPerson, millis(30))
        assertEquals(SmsAction.Skip, resolveSms(listOf(first), first))
    }

    @Test
    fun `a dismissed payment is never offered again`() {
        val first = detected(kotak, kotakPerson, millis(30)).copy(status = DetectedStatus.DISMISSED)
        assertEquals(SmsAction.Skip, resolveSms(listOf(first), detected(kotak, kotakPerson, millis(30))))
    }

    @Test
    fun `messages without a reference still dedupe between live and inbox reads`() {
        val first = detected(axis, axisCard, millis(29))
        assertEquals(SmsAction.Skip, resolveSms(listOf(first), detected(axis, axisCard, millis(29))))
    }

    @Test
    fun `two identical payments with different references are both kept`() {
        val first = detected(kotak, kotakPerson, millis(30))
        val second = kotakPerson.replace("600000002034", "600000002035")
        assertTrue(resolveSms(listOf(first), detected(kotak, second, millis(30))) is SmsAction.Add)
    }

    @Test
    fun `sending between your own accounts becomes a transfer`() {
        val out = detected(kotak, "Sent Rs.5000.00 from Kotak Bank A/c X5521 to MY NAME on 30-10-26. UPI Ref 111222333444.", millis(30))
        val arrived = detected(hdfc, "Received Rs.5000.00 in your HDFC Bank A/C *7730 from MY NAME on 30/10/26. Ref 111222333444", millis(30))
        val action = resolveSms(listOf(out), arrived) as SmsAction.Merge
        assertEquals(TxType.TRANSFER, action.updated.type)
        assertEquals("5521", action.updated.last4)
        assertEquals("7730", action.updated.toLast4)
    }

    @Test
    fun `a card bill merges with the bank debit that paid it, in either order`() {
        val debit = detected(kotak, "Sent Rs.5000.00 from Kotak Bank A/c X5521 to CRED CLUB on 30-10-26. UPI Ref 111222333555.", millis(30))
        val paid = detected(axis, "Payment of INR 5000 received on your Axis Bank Credit Card no. XX9046 on 30-10-26.", millis(30))

        val first = resolveSms(listOf(debit), paid) as SmsAction.Merge
        assertEquals(TxType.TRANSFER, first.updated.type)
        assertEquals("5521", first.updated.last4)
        assertEquals("9046", first.updated.toLast4)
        assertEquals(paid.dedupKey, first.retiredKey)

        val second = resolveSms(listOf(paid), debit) as SmsAction.Merge
        assertEquals("5521", second.updated.last4)
        assertEquals("9046", second.updated.toLast4)
    }

    @Test
    fun `a card purchase of the same amount is not mistaken for the bill payment`() {
        val paid = detected(axis, "Payment of INR 8750 received on your Axis Bank Credit Card no. XX9046 on 30-10-26.", millis(30))
        assertNotNull(resolveSms(listOf(paid), detected(axis, axisCard, millis(30))) as? SmsAction.Add)
    }
}
