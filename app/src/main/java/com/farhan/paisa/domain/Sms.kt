package com.farhan.paisa.domain

import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.DetectedStatus
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.TxType
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

enum class SmsKind { SPENT, RECEIVED, CARD_PAID }

data class ParsedSms(
    val kind: SmsKind,
    val amount: Long,
    val last4: String,
    val counterparty: String,
    val ref: String,
    val method: PayMethod,
    val date: LocalDate?,
)

/**
 * Turns a bank SMS into a payment, or null when it isn't one.
 *
 * A message has to pass every check, because a missed payment is one you can still add by hand
 * while a promo posing as a payment would pollute your records:
 *  1. it comes from a known bank sender and isn't marked promotional
 *  2. it isn't an OTP, a failure, a bill reminder or an advert
 *  3. it has an amount and says money moved
 *  4. it names an account or card by its digits, which promos can't do
 */
object SmsParser {
    private val KNOWN_SENDERS = setOf(
        "KOTAKB", "KOTKBK", "HDFCBK", "HDFCCC", "AXISBK", "AXISCC", "ICICIB", "ICICIT", "SBIINB", "SBIUPI",
        "SBICRD", "SBIPSG", "SBYONO", "YESBNK", "IDFCFB", "INDUSB", "PNBSMS", "BOBTXN", "BOBSMS", "CANBNK",
        "UNIONB", "RBLBNK", "RBLCRD", "FEDBNK", "AUBANK", "IDBIBK", "BOIIND", "CITIBK", "SCBANK", "HSBCIN",
        "AMEXIN", "ONECRD", "PAYTMB", "JUPTER", "FIMONY",
    )

    private val SENDER = Regex("""^[A-Z0-9]{2}-([A-Z0-9]{4,8})(?:-([A-Z]))?$""")
    private val FOOTER = Regex(
        """(?i)\b(not (done )?(by )?you|if not (you|done)|not yours|did ?n[o']?t do|report fraud|sms block|to block)\b.*$""",
    )
    private val NOT_A_PAYMENT = Regex(
        """(?i)\b(otp|one[- ]time|verification code|password|cvv|failed|declined|unsuccessful|rejected|insufficient|""" +
            """could not|not processed|will be|is due|due on|due date|due by|amount due|min(imum)? (amount|due)|overdue|""" +
            """statement|upcoming|requested|request from|collect request|mandate (set|created|registered))\b""",
    )
    private val ADVERT = Regex(
        """(?i)\b(offers?|win|won|winner|pre-?approved|loan|apply|shop|click|hurry|limited|discount|voucher|coupon|""" +
            """eligible|upgrade|insurance|kyc|get|free|congratulations|lucky|claim|redeem|instantly|jackpot|prize|lottery)\b|%\s*off""",
    )
    private val LINK = Regex("""(?i)https?://|www\.""")
    private val AMOUNT = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    /** Sits just before a balance or limit, which is a figure in the message but not the payment. */
    private val BALANCE_LABEL = Regex("""(?i)\b(avl|avail\w*|bal(ance)?|limit)\b[^0-9]{0,16}$""")
    private val SPENT = Regex("""(?i)\b(sent|spent|debited|paid|purchased?|withdrawn|charged|txn of|transaction of)\b""")
    private val RECEIVED = Regex("""(?i)\b(received|credited|deposited|refund(ed)?|reversed|reversal)\b""")
    private val REFUNDED = Regex("""(?i)\b(refund(ed)?|reversed|reversal)\b""")
    private val SALARY_WORD = Regex("""(?i)\b(salary|sal)\b""")
    private val CARD_PAYMENT = Regex(
        """(?i)payment\b.*\b(received|credited)\b.*\bcard\b|\bcard\b.*\b(received|credited)\b.*\bpayment\b|\bpayment\b.*\b(on|towards|to|for)\b.*\bcard\b.*\b(received|credited)\b""",
    )
    private val ACCOUNT = Regex(
        """(?i)\b(?:a/?c|acct|account|card)\b(?:\s+(?:no\.?|number|ending(?:\s+(?:with|in))?))?\s*[:.]?\s*[x*•.]*\s*(\d{3,})""",
    )
    private val REFERENCE = Regex(
        """(?i)\b(?:upi\s*ref(?:erence)?|ref(?:erence)?|utr|rrn|txn\s*(?:id|no))(?:\s*no\.?)?\s*[:#.\-]?\s*([a-z0-9]{6,})""",
    )
    /** The reference in "(UPI 123456789012)" or "UPI/P2A/123456789012/NAME". */
    private val UPI_NUMBER = Regex("""(?i)\bUPI[\s:/\-]+(?:[A-Z0-9]+/)?(\d{9,})""")
    private val DATE = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""")
    private val NAMED_DATE = Regex("""(?i)\b(\d{1,2})[- ]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[- ,]*(\d{2,4})\b""")
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val STOP = """(?=\s+on\s+\d|\s+on\s+[a-z]{3}|\s+ref\b|\s+upi\b|\s+avl\b|\s+dated\b|\s+by\b|\s+via\b|\s*\(|\.\s|\.$|$)"""
    private val TO = Regex("""(?i)\bto\s+(.+?)$STOP""")
    private val AT = Regex("""(?i)\bat\s+(.+?)$STOP""")
    private val FROM = Regex("""(?i)\bfrom\s+(.+?)$STOP""")
    private val VPA = Regex("""(?i)\bVPA\s+([^\s(),]+)""")
    private val UPI_NAME = Regex("""(?i)\bUPI/[A-Z0-9]+/\d+/([^/.]+)""")
    private val BANK_TRANSFER_NAME = Regex("""(?i)\b(?:NEFT|IMPS|RTGS)(?:\s*Cr)?[\s-]+[A-Z0-9]+-([^-.]+)""")
    private val BY_NAME = Regex("""\bby\s+([A-Z][A-Z. ]+[A-Z])(?=\s+on\b|\s+ref\b|\.|$)""")
    /** Says the account is someone else's, in "from a/c XX1111" or "by A/c XX1111". */
    private val THEIR_ACCOUNT = Regex("""(?i)\b(from|by)\s+(\S+\s+){0,3}$""")

    const val SALARY = "Salary"
    const val REFUND = "Refund"

    fun senderCode(address: String): String? = SENDER.matchEntire(address.trim().uppercase())?.groupValues?.get(1)

    fun isTrusted(address: String, extraSenders: Set<String> = emptySet()): Boolean {
        val match = SENDER.matchEntire(address.trim().uppercase()) ?: return false
        val (code, label) = match.destructured
        return label != "P" && (code in KNOWN_SENDERS || code in extraSenders)
    }

    fun parse(address: String, body: String, extraSenders: Set<String> = emptySet()): ParsedSms? {
        if (!isTrusted(address, extraSenders)) return null
        // The "Not you?" footer holds links and phone numbers that aren't part of the payment.
        val text = FOOTER.replace(body.replace(Regex("""\s+"""), " ").trim(), "").trim()
        if (NOT_A_PAYMENT.containsMatchIn(text) || LINK.containsMatchIn(text)) return null

        val amount = amountOf(text) ?: return null
        val kind = kindOf(text) ?: return null
        val last4 = accountOf(text, kind)
        if (last4.isEmpty()) return null

        val method = methodOf(text)
        val payee = counterparty(text, kind, method)
        // A payee can be called "Coffee Shop", so its name is left out of the search for adverts.
        if (ADVERT.containsMatchIn(if (payee.isEmpty()) text else text.replace(payee, ""))) return null

        return ParsedSms(
            kind = kind,
            amount = amount,
            last4 = last4,
            counterparty = displayName(text, kind, payee),
            ref = REFERENCE.find(text)?.groupValues?.get(1) ?: UPI_NUMBER.find(text)?.groupValues?.get(1).orEmpty(),
            method = method,
            date = dateOf(text),
        )
    }

    /** The first amount that isn't a balance or limit quoted alongside the payment. */
    private fun amountOf(text: String): Long? =
        AMOUNT.findAll(text)
            .firstOrNull { !BALANCE_LABEL.containsMatchIn(text.substring(0, it.range.first)) }
            ?.let { Money.parse(it.groupValues[1]) }
            ?.takeIf { it > 0 }

    /** Your account. When money comes in, the message can name the sender's account first. */
    private fun accountOf(text: String, kind: SmsKind): String {
        val accounts = ACCOUNT.findAll(text).toList()
        val yours = if (kind == SmsKind.RECEIVED) {
            accounts.firstOrNull { !THEIR_ACCOUNT.containsMatchIn(text.substring(0, it.range.first)) }
        } else {
            accounts.firstOrNull()
        }
        return yours?.groupValues?.get(1)?.takeLast(4).orEmpty()
    }

    private fun displayName(text: String, kind: SmsKind, payee: String): String {
        // A UPI ID like "rahul.sharma@okaxis" reads as a name; "9876543210@ybl" is only a number.
        val name = tidyName(payee.substringBefore('@').replace('.', ' ').replace('_', ' '))
            .takeUnless { it.all { char -> char.isDigit() || char == ' ' } }
            .orEmpty()
        return when {
            kind != SmsKind.RECEIVED -> name
            SALARY_WORD.containsMatchIn(text) -> SALARY
            REFUNDED.containsMatchIn(text) -> if (name.isEmpty()) REFUND else "$REFUND from $name"
            else -> name
        }
    }

    private fun kindOf(text: String): SmsKind? {
        if (CARD_PAYMENT.containsMatchIn(text)) return SmsKind.CARD_PAID
        val spent = SPENT.find(text)?.range?.first
        val received = RECEIVED.find(text)?.range?.first
        return when {
            spent == null && received == null -> null
            received == null -> SmsKind.SPENT
            spent == null -> SmsKind.RECEIVED
            // "debited from your account and credited to X" is decided by whichever comes first.
            spent < received -> SmsKind.SPENT
            else -> SmsKind.RECEIVED
        }
    }

    private fun methodOf(text: String): PayMethod {
        val lower = text.lowercase()
        return when {
            "upi" in lower -> PayMethod.UPI
            "card" in lower -> PayMethod.CARD
            "nach" in lower || "ecs" in lower || "auto" in lower -> PayMethod.AUTO_DEBIT
            "neft" in lower || "imps" in lower || "rtgs" in lower -> PayMethod.NET_BANKING
            else -> PayMethod.UPI
        }
    }

    private fun counterparty(text: String, kind: SmsKind, method: PayMethod): String {
        val patterns = when {
            kind == SmsKind.RECEIVED -> listOf(VPA, UPI_NAME, BANK_TRANSFER_NAME, FROM, BY_NAME)
            method == PayMethod.CARD -> listOf(AT, TO)
            else -> listOf(TO, AT)
        }
        return patterns.asSequence()
            .mapNotNull { it.find(text)?.groupValues?.get(1)?.trim() }
            // "from A/c XX1111" names an account rather than a person.
            .firstOrNull { !ACCOUNT.containsMatchIn(it) }
            .orEmpty()
    }

    /** "ROHIT   VERMA" becomes "Rohit Verma". Mixed-case names are left alone. */
    fun tidyName(raw: String): String {
        val name = raw.replace(Regex("""\s+"""), " ").trim()
        if (name.any { it.isLowerCase() } && name.any { it.isUpperCase() }) return name
        return name.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    private fun dateOf(text: String): LocalDate? {
        val numeric = DATE.find(text)?.destructured?.let { (day, month, year) -> Triple(day, month.toInt(), year) }
        val named = NAMED_DATE.find(text)?.destructured?.let { (day, month, year) ->
            Triple(day, MONTHS.indexOf(month.lowercase()) + 1, year)
        }
        val (day, month, year) = numeric ?: named ?: return null
        val fullYear = if (year.length == 2) 2000 + year.toInt() else year.toInt()
        return runCatching { LocalDate.of(fullYear, month, day.toInt()) }.getOrNull()
    }

    fun toDetected(
        parsed: ParsedSms,
        body: String,
        sentAt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Detected {
        val moment = Instant.ofEpochMilli(sentAt).atZone(zone)
        val date = parsed.date?.let { moment.with(it) } ?: moment
        val cardPayment = parsed.kind == SmsKind.CARD_PAID
        return Detected(
            dedupKey = if (parsed.ref.isNotEmpty()) "ref:${parsed.ref}" else "msg:${fingerprint(body, sentAt)}",
            type = when (parsed.kind) {
                SmsKind.SPENT -> TxType.EXPENSE
                SmsKind.RECEIVED -> TxType.INCOME
                SmsKind.CARD_PAID -> TxType.TRANSFER
            },
            amount = parsed.amount,
            counterparty = parsed.counterparty,
            last4 = if (cardPayment) "" else parsed.last4,
            toLast4 = if (cardPayment) parsed.last4 else "",
            method = parsed.method,
            date = date.toInstant().toEpochMilli(),
        )
    }

    /** Stays the same whether a message arrives live or is found later in the inbox. */
    private fun fingerprint(body: String, sentAt: Long): String {
        val text = body.replace(Regex("""\s+"""), " ").trim() + "|" + sentAt / 60_000
        return MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .take(10)
            .joinToString("") { "%02x".format(it) }
    }
}

sealed interface SmsAction {
    data object Skip : SmsAction
    data class Add(val detected: Detected) : SmsAction

    /** Rewrites [updated], and keeps [retiredKey] so its message isn't offered again. */
    data class Merge(val updated: Detected, val retiredKey: String) : SmsAction
}

private val MERGE_WINDOW_MILLIS = 3 * 24 * 60 * 60 * 1000L

/**
 * Decides what a newly read payment means given those already seen:
 *  - the same reference on the same account is a repeat
 *  - the same reference on two accounts, one out and one in, is a move between your accounts
 *  - a card bill payment is matched with the bank debit that paid it, so it isn't spending
 */
fun resolveSms(existing: List<Detected>, incoming: Detected): SmsAction {
    val same = existing.firstOrNull { it.dedupKey == incoming.dedupKey }
    if (same != null) {
        val opposite = same.status == DetectedStatus.PENDING &&
            same.last4 != incoming.last4 &&
            setOf(same.type, incoming.type) == setOf(TxType.EXPENSE, TxType.INCOME)
        if (!opposite) return SmsAction.Skip
        val (from, to) = if (same.type == TxType.EXPENSE) same to incoming else incoming to same
        val merged = same.copy(type = TxType.TRANSFER, last4 = from.last4, toLast4 = to.last4, counterparty = "")
        return SmsAction.Merge(merged, "")
    }

    fun close(other: Detected) = other.status == DetectedStatus.PENDING &&
        other.amount == incoming.amount &&
        abs(other.date - incoming.date) <= MERGE_WINDOW_MILLIS

    if (incoming.type == TxType.TRANSFER) {
        val debit = existing.firstOrNull { close(it) && it.type == TxType.EXPENSE && it.method != PayMethod.CARD }
        if (debit != null) {
            return SmsAction.Merge(debit.copy(type = TxType.TRANSFER, toLast4 = incoming.toLast4, counterparty = ""), incoming.dedupKey)
        }
    }
    if (incoming.type == TxType.EXPENSE && incoming.method != PayMethod.CARD) {
        val cardPayment = existing.firstOrNull { close(it) && it.type == TxType.TRANSFER && it.last4.isEmpty() }
        if (cardPayment != null) {
            return SmsAction.Merge(cardPayment.copy(last4 = incoming.last4), incoming.dedupKey)
        }
    }
    return SmsAction.Add(incoming)
}
