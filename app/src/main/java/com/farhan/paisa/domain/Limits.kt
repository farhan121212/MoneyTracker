package com.farhan.paisa.domain

import com.farhan.paisa.data.Ledger

enum class LimitKind(val prefix: String) { CATEGORY("c"), TAG("t") }

data class LimitStatus(val kind: LimitKind, val id: Long, val name: String, val limit: Long, val spent: Long) {
    val used: Float get() = spent.toFloat() / limit
    val left: Long get() = limit - spent
    val percent: Int get() = (spent * 100 / limit).toInt()
    val key: String get() = "${kind.prefix}$id"
}

/** Every limit you've set, the closest to running out first. */
fun Ledger.limitStatuses(period: Period): List<LimitStatus> {
    val byCategory = byCategory(period).associateBy { it.key }
    val byTag = byTag(period).associateBy { it.key }
    val fromCategories = categories.filter { it.monthlyLimit > 0 && !it.archived && !it.income }.map {
        LimitStatus(LimitKind.CATEGORY, it.id, it.name, it.monthlyLimit, byCategory[it.id]?.amount ?: 0)
    }
    val fromTags = tags.filter { it.monthlyLimit > 0 }.map {
        LimitStatus(LimitKind.TAG, it.id, "#${it.name}", it.monthlyLimit, byTag[it.id]?.amount ?: 0)
    }
    return (fromCategories + fromTags).sortedByDescending { it.used }
}

private const val NEARLY = 80
private const val FULL = 100
private const val LOG_SEPARATOR = '|'

data class LimitAlert(val status: LimitStatus, val threshold: Int) {
    val title: String
        get() = if (threshold == FULL) "${status.name} went over its limit" else "${status.name} is at ${status.percent}% of its limit"
}

data class LimitCheck(val alerts: List<LimitAlert>, val log: String)

/**
 * Which warnings are due, and the log to remember them by. The log belongs to one month, so
 * everything starts fresh with the next. Passing 100% in one payment sends only that warning.
 */
fun checkLimits(statuses: List<LimitStatus>, period: Period, log: String): LimitCheck {
    val parts = log.split(LOG_SEPARATOR)
    val sent = if (parts.first() == period.token) parts.drop(1).toMutableSet() else mutableSetOf()
    val alerts = statuses.mapNotNull { status ->
        val threshold = listOf(FULL, NEARLY).firstOrNull { status.percent >= it } ?: return@mapNotNull null
        val fresh = "${status.key}:$threshold" !in sent
        listOf(FULL, NEARLY).filter { it <= threshold }.forEach { sent += "${status.key}:$it" }
        if (fresh) LimitAlert(status, threshold) else null
    }
    return LimitCheck(alerts, (listOf(period.token) + sent.sorted()).joinToString(LOG_SEPARATOR.toString()))
}
