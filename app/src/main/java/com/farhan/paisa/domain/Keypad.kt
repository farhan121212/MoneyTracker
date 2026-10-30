package com.farhan.paisa.domain

/** Rules for typing an amount on the in-app keypad. */
object Keypad {
    const val BACKSPACE = "back"
    private const val MAX_RUPEE_DIGITS = 9

    fun press(current: String, key: String): String = when (key) {
        BACKSPACE -> current.dropLast(1)
        "." -> when {
            "." in current -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> digit(current, key)
    }

    private fun digit(current: String, key: String): String {
        val dot = current.indexOf('.')
        return when {
            current == "0" -> key
            dot >= 0 && current.length - dot > 2 -> current
            dot < 0 && current.length >= MAX_RUPEE_DIGITS -> current
            else -> current + key
        }
    }

    /** "12345.5" becomes "12,345.5" while the amount is being typed. */
    fun display(text: String): String {
        if (text.isEmpty()) return "0"
        val rupees = text.substringBefore('.')
        val grouped = Money.group(rupees.toLongOrNull() ?: 0)
        return if ('.' in text) "$grouped.${text.substringAfter('.')}" else grouped
    }
}
