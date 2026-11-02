package com.farhan.paisa.domain

import com.farhan.paisa.data.PayMethod
import org.junit.Assert.assertEquals
import org.junit.Test

/** UPI paid from a credit card, in the format HDFC sends, with the personal details changed. */
class CardUpiSmsTest {
    private val hdfc = "AD-HDFCBK-S"
    private val body = "Txn Rs.1234.50\nOn HDFC Bank Card 9911\nAt *payu@hdfc \nby UPI 600000000200\nOn 01-11\nNot You?\nCall 18002586161/SMS BLOCK CC 9911 to 7308080808"

    @Test
    fun `hdfc card payment by upi is read in full`() {
        val sms = requireNotNull(SmsParser.parse(hdfc, body))
        assertEquals(SmsKind.SPENT, sms.kind)
        assertEquals(1_234_50L, sms.amount)
        assertEquals("9911", sms.last4)
        assertEquals("Payu", sms.counterparty)
        assertEquals("600000000200", sms.ref)
        assertEquals(PayMethod.UPI, sms.method)
    }

    @Test
    fun `a card message with no year falls back to when it arrived`() {
        val sms = requireNotNull(SmsParser.parse(hdfc, body))
        assertEquals(null, sms.date)
    }

    @Test
    fun `a declined card payment in the same format is still ignored`() {
        assertEquals(null, SmsParser.parse(hdfc, "Txn Rs.1234.50\nOn HDFC Bank Card 9911\nAt *payu@hdfc \ndeclined"))
    }

    @Test
    fun `masked names with no real letters are left blank to fill in`() {
        val sms = requireNotNull(SmsParser.parse(hdfc, "Received Rs.600 in your HDFC Bank A/c *7730 from *02-2 on 01/11/26. Ref 600000000201"))
        assertEquals("", sms.counterparty)
    }
}
