package com.farhan.paisa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun `groups digits the Indian way`() {
        assertEquals("₹150", Money.format(150_00))
        assertEquals("₹1,500", Money.format(1_500_00))
        assertEquals("₹12,345", Money.format(12_345_00))
        assertEquals("₹1,23,456", Money.format(1_23_456_00))
        assertEquals("₹12,34,56,789", Money.format(12_34_56_789_00))
    }

    @Test
    fun `shows paise only when there are any`() {
        assertEquals("₹150.50", Money.format(150_50))
        assertEquals("₹0.05", Money.format(5))
        assertEquals("₹150", Money.whole(150_99))
    }

    @Test
    fun `signs`() {
        assertEquals("−₹150", Money.format(-150_00))
        assertEquals("+₹150", Money.format(150_00, sign = true))
    }

    @Test
    fun `compact form`() {
        assertEquals("₹850", Money.compact(850_00))
        assertEquals("₹12.5K", Money.compact(12_500_00))
        assertEquals("₹1.2L", Money.compact(1_20_000_00))
        assertEquals("₹3Cr", Money.compact(3_00_00_000_00))
    }

    @Test
    fun `parses user input into paise exactly`() {
        assertEquals(150_00L, Money.parse("150"))
        assertEquals(1_500_50L, Money.parse("1,500.50"))
        assertEquals(19_99L, Money.parse("19.99"))
        assertEquals(10L, Money.parse("0.1"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
    }

    @Test
    fun `editable form round trips`() {
        listOf(150_00L, 150_50L, 150_05L, 5L).forEach {
            assertEquals(it, Money.parse(Money.editable(it)))
        }
    }

    @Test
    fun `keypad stops after two decimals and one dot`() {
        var text = ""
        listOf("1", "2", ".", "5", "0", "9", ".").forEach { text = Keypad.press(text, it) }
        assertEquals("12.50", text)
    }

    @Test
    fun `keypad replaces a leading zero and handles dot first`() {
        assertEquals("5", Keypad.press("0", "5"))
        assertEquals("0.", Keypad.press("", "."))
    }

    @Test
    fun `keypad backspace and display`() {
        assertEquals("12", Keypad.press("123", Keypad.BACKSPACE))
        assertEquals("", Keypad.press("", Keypad.BACKSPACE))
        assertEquals("12,345.5", Keypad.display("12345.5"))
        assertEquals("0", Keypad.display(""))
    }

    @Test
    fun `keypad limits the size of an amount`() {
        var text = ""
        repeat(12) { text = Keypad.press(text, "9") }
        assertEquals(9, text.length)
    }

    @Test
    fun `equal share leaves the odd paise with the payer`() {
        val each = equalShare(1_000_00, 3)
        assertEquals(333_33L, each)
        assertEquals(1L, 1_000_00 - each * 3)
    }
}
