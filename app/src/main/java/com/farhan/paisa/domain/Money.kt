package com.farhan.paisa.domain

import java.math.BigDecimal
import kotlin.math.abs

/** Formatting and parsing of paise amounts, using Indian digit grouping (1,23,456). */
object Money {
    fun format(paise: Long, sign: Boolean = false): String {
        val magnitude = abs(paise)
        val rupees = magnitude / 100
        val remainder = magnitude % 100
        val prefix = when {
            paise < 0 -> "−"
            sign && paise > 0 -> "+"
            else -> ""
        }
        val fraction = if (remainder == 0L) "" else ".%02d".format(remainder)
        return "$prefix₹${group(rupees)}$fraction"
    }

    /** Whole rupees only, for places where the exact paise would be noise. */
    fun whole(paise: Long): String {
        val prefix = if (paise < 0) "−" else ""
        return "$prefix₹${group(abs(paise) / 100)}"
    }

    /** Short form for chart axes and tight spaces: ₹850, ₹12.5K, ₹1.2L, ₹3.4Cr. */
    fun compact(paise: Long): String {
        val rupees = abs(paise) / 100.0
        val prefix = if (paise < 0) "−" else ""
        val body = when {
            rupees >= 1_00_00_000 -> trim(rupees / 1_00_00_000) + "Cr"
            rupees >= 1_00_000 -> trim(rupees / 1_00_000) + "L"
            rupees >= 1_000 -> trim(rupees / 1_000) + "K"
            else -> rupees.toLong().toString()
        }
        return "$prefix₹$body"
    }

    /** Parses user input such as "1,500.50" into paise. Returns null when it is not a number. */
    fun parse(text: String): Long? {
        val clean = text.replace(",", "").replace("₹", "").trim()
        if (clean.isEmpty()) return null
        return clean.toBigDecimalOrNull()?.multiply(BigDecimal(100))?.toLong()
    }

    /** Plain editable form of an amount: 1500 or 1500.5, with no grouping. */
    fun editable(paise: Long): String {
        val rupees = paise / 100
        val remainder = paise % 100
        return when {
            remainder == 0L -> rupees.toString()
            remainder % 10 == 0L -> "$rupees.${remainder / 10}"
            else -> "$rupees.%02d".format(remainder)
        }
    }

    private fun trim(value: Double): String {
        val text = "%.1f".format(value)
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    fun group(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val tail = digits.takeLast(3)
        val head = digits.dropLast(3)
        val pairs = head.reversed().chunked(2).joinToString(",").reversed()
        return "$pairs,$tail"
    }
}
