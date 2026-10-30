package com.farhan.paisa.data

/**
 * An immutable snapshot of everything in the app. Screens read from this and the functions
 * in [com.farhan.paisa.domain] work it out, so the money maths stays testable without Android.
 */
data class Ledger(
    val accounts: List<Account> = emptyList(),
    /** Newest first. */
    val txns: List<Txn> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val txnTags: List<TxnTag> = emptyList(),
    val people: List<Person> = emptyList(),
    val shares: List<SplitShare> = emptyList(),
    val detected: List<Detected> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalEntries: List<GoalEntry> = emptyList(),
) {
    val accountById: Map<Long, Account> = accounts.associateBy { it.id }
    val categoryById: Map<Long, Category> = categories.associateBy { it.id }
    val tagById: Map<Long, Tag> = tags.associateBy { it.id }
    val personById: Map<Long, Person> = people.associateBy { it.id }
    val txnById: Map<Long, Txn> = txns.associateBy { it.id }
    val sharesByTxn: Map<Long, List<SplitShare>> = shares.groupBy { it.txnId }
    val tagIdsByTxn: Map<Long, List<Long>> = txnTags.groupBy({ it.txnId }, { it.tagId })

    val detectedById: Map<Long, Detected> = detected.associateBy { it.id }
    val recurringById: Map<Long, Recurring> = recurring.associateBy { it.id }
    val goalById: Map<Long, Goal> = goals.associateBy { it.id }

    /**
     * Current money in each account. Snapshots are built off the main thread, so working this
     * out once here means screens never add up every entry while they draw.
     */
    val balances: Map<Long, Long> = buildBalances()

    /** What each person still owes you: their split shares minus what they've paid back. */
    val owedByPerson: Map<Long, Long> = buildOwed()

    private fun buildBalances(): Map<Long, Long> {
        val totals = HashMap<Long, Long>()
        for (account in accounts) {
            totals[account.id] = if (account.kind == AccountKind.CREDIT_CARD) -account.opening else account.opening
        }
        for (txn in txns) {
            val change = when (txn.type) {
                TxType.EXPENSE, TxType.TRANSFER -> -txn.amount
                TxType.INCOME, TxType.SETTLEMENT -> txn.amount
            }
            totals.computeIfPresent(txn.accountId) { _, balance -> balance + change }
            if (txn.type == TxType.TRANSFER) {
                txn.toAccountId?.let { to -> totals.computeIfPresent(to) { _, balance -> balance + txn.amount } }
            }
        }
        return totals
    }

    private fun buildOwed(): Map<Long, Long> {
        val totals = HashMap<Long, Long>()
        for (share in shares) totals.merge(share.personId, share.amount, Long::plus)
        for (txn in txns) {
            val person = txn.personId
            if (txn.type == TxType.SETTLEMENT && person != null) totals.merge(person, -txn.amount, Long::plus)
        }
        return totals
    }

    val activeAccounts: List<Account> get() = accounts.filterNot { it.archived }
    val toReview: List<Detected> get() = detected.filter { it.status == DetectedStatus.PENDING }
}
