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

    val activeAccounts: List<Account> get() = accounts.filterNot { it.archived }
    val toReview: List<Detected> get() = detected.filter { it.status == DetectedStatus.PENDING }
}
