package com.farhan.paisa.data

import androidx.room.withTransaction
import com.farhan.paisa.domain.SmsAction
import com.farhan.paisa.domain.resolveSms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val version: Int = 1,
    val settings: Settings,
    val accounts: List<Account>,
    val txns: List<Txn>,
    val categories: List<Category>,
    val tags: List<Tag>,
    val txnTags: List<TxnTag>,
    val people: List<Person>,
    val shares: List<SplitShare>,
    val detected: List<Detected> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalEntries: List<GoalEntry> = emptyList(),
)

class Repository(private val db: PaisaDb, val settings: SettingsStore) {
    private val dao = db.dao()

    val ledger: Flow<Ledger> = combine(
        combine(dao.accounts(), dao.txns(), dao.categories()) { a, t, c -> Triple(a, t, c) },
        combine(dao.tags(), dao.txnTags(), dao.people(), dao.shares()) { t, tt, p, s -> Parts(t, tt, p, s) },
        combine(dao.detected(), dao.recurring(), dao.goals(), dao.goalEntries()) { d, r, g, e -> Plans(d, r, g, e) },
    ) { (accounts, txns, categories), parts, plans ->
        Ledger(
            accounts, txns, categories, parts.tags, parts.txnTags, parts.people, parts.shares,
            plans.detected, plans.recurring, plans.goals, plans.goalEntries,
        )
    }

    private data class Parts(
        val tags: List<Tag>,
        val txnTags: List<TxnTag>,
        val people: List<Person>,
        val shares: List<SplitShare>,
    )

    private data class Plans(
        val detected: List<Detected>,
        val recurring: List<Recurring>,
        val goals: List<Goal>,
        val goalEntries: List<GoalEntry>,
    )

    /** Files a payment read from SMS. Returns it only when it's new and needs reviewing. */
    suspend fun ingest(incoming: Detected): Detected? = db.withTransaction {
        when (val action = resolveSms(dao.allDetected(), incoming)) {
            SmsAction.Skip -> null
            is SmsAction.Add -> incoming.takeIf { dao.insertDetected(incoming) != -1L }
            is SmsAction.Merge -> {
                dao.updateDetected(action.updated)
                if (action.retiredKey.isNotEmpty()) {
                    dao.insertDetected(incoming.copy(dedupKey = action.retiredKey, status = DetectedStatus.DISMISSED))
                }
                null
            }
        }
    }

    /** Logs a reviewed payment, and learns which account its digits belong to. */
    suspend fun confirmDetected(
        detected: Detected,
        txn: Txn,
        tagIds: Collection<Long>,
        shares: List<Pair<Long, Long>>,
    ) = db.withTransaction {
        val id = saveTxn(txn, tagIds, shares)
        dao.updateDetected(detected.copy(status = DetectedStatus.CONFIRMED, txnId = id))
        val accounts = dao.allAccounts().associateBy { it.id }
        learnDigits(accounts[txn.accountId], detected.last4)
        learnDigits(accounts[txn.toAccountId], detected.toLast4)
    }

    private suspend fun learnDigits(account: Account?, digits: String) {
        if (account != null && account.last4.isBlank() && digits.isNotBlank()) {
            dao.updateAccount(account.copy(last4 = digits))
        }
    }

    suspend fun dismissDetected(detected: Detected) {
        dao.updateDetected(detected.copy(status = DetectedStatus.DISMISSED))
    }

    suspend fun saveRecurring(bill: Recurring) {
        if (bill.id == 0L) dao.insertRecurring(bill) else dao.updateRecurring(bill)
    }

    suspend fun deleteRecurring(bill: Recurring) = dao.deleteRecurring(bill)

    suspend fun saveGoal(goal: Goal): Long =
        if (goal.id == 0L) dao.insertGoal(goal) else goal.id.also { dao.updateGoal(goal) }

    suspend fun deleteGoal(goal: Goal) = dao.deleteGoal(goal)

    /** Sets money aside for a goal. A positive [transfer] also moves it into savings first. */
    suspend fun addToGoal(goalId: Long, amount: Long, date: Long, transfer: Txn?) = db.withTransaction {
        if (transfer != null) dao.insertTxn(transfer)
        dao.insertGoalEntry(GoalEntry(goalId = goalId, amount = amount, date = date))
    }

    /** Inserts or updates a transaction along with its tags and split shares. */
    suspend fun saveTxn(txn: Txn, tagIds: Collection<Long>, shares: List<Pair<Long, Long>>): Long =
        db.withTransaction {
            val id = if (txn.id == 0L) {
                dao.insertTxn(txn)
            } else {
                dao.updateTxn(txn)
                dao.clearTagsFor(txn.id)
                dao.clearSharesFor(txn.id)
                txn.id
            }
            dao.insertTxnTags(tagIds.map { TxnTag(id, it) })
            dao.insertShares(shares.filter { it.second > 0 }.map { (person, amount) -> SplitShare(txnId = id, personId = person, amount = amount) })
            id
        }

    suspend fun deleteTxn(id: Long) = dao.deleteTxn(id)

    suspend fun saveAccount(account: Account): Long =
        if (account.id == 0L) dao.insertAccount(account) else account.id.also { dao.updateAccount(account) }

    /** Accounts with history are archived so that past transactions stay intact. */
    suspend fun removeAccount(account: Account) {
        if (dao.txnCountFor(account.id) > 0) dao.updateAccount(account.copy(archived = true)) else dao.deleteAccount(account)
    }

    suspend fun saveCategory(category: Category): Long =
        if (category.id == 0L) dao.insertCategory(category) else category.id.also { dao.updateCategory(category) }

    suspend fun deleteCategory(category: Category) = db.withTransaction {
        dao.detachCategory(category.id)
        dao.detachBills(category.id)
        dao.deleteCategory(category)
    }

    suspend fun addTag(name: String): Long = dao.insertTag(Tag(name = name))
    suspend fun renameTag(tag: Tag) = dao.updateTag(tag)
    suspend fun deleteTag(tag: Tag) = dao.deleteTag(tag)

    suspend fun addPerson(name: String): Long = dao.insertPerson(Person(name = name))
    suspend fun renamePerson(person: Person) = dao.updatePerson(person)

    suspend fun deletePerson(person: Person) = db.withTransaction {
        dao.deleteSettlementsBy(person.id)
        dao.deletePerson(person)
    }

    suspend fun exportBackup(): Backup = Backup(
        settings = settings.current(),
        accounts = dao.allAccounts(),
        txns = dao.allTxns(),
        categories = dao.allCategories(),
        tags = dao.allTags(),
        txnTags = dao.allTxnTags(),
        people = dao.allPeople(),
        shares = dao.allShares(),
        detected = dao.allDetected(),
        recurring = dao.allRecurring(),
        goals = dao.allGoals(),
        goalEntries = dao.allGoalEntries(),
    )

    /** Replaces everything in the app with the contents of [backup]. */
    suspend fun restore(backup: Backup) {
        db.withTransaction {
            clearTables()
            dao.insertAccounts(backup.accounts)
            dao.insertCategories(backup.categories)
            dao.insertTags(backup.tags)
            dao.insertPeople(backup.people)
            dao.insertTxns(backup.txns)
            dao.insertTxnTags(backup.txnTags)
            dao.insertShares(backup.shares)
            dao.insertDetected(backup.detected)
            dao.insertRecurring(backup.recurring)
            dao.insertGoals(backup.goals)
            dao.insertGoalEntries(backup.goalEntries)
        }
        settings.update { backup.settings }
    }

    suspend fun eraseEverything() {
        db.withTransaction { clearTables() }
        settings.update { Settings() }
    }

    suspend fun insertDefaultCategories() {
        dao.insertCategories(DefaultCategories)
    }

    private suspend fun clearTables() {
        // Join rows go with their parents through the foreign keys.
        dao.clearTxns()
        dao.clearTags()
        dao.clearPeople()
        dao.clearCategories()
        dao.clearAccounts()
        dao.clearDetected()
        dao.clearRecurring()
        dao.clearGoals()
    }
}

val DefaultCategories: List<Category> = listOf(
    Category(name = "Food & dining", icon = "food", color = 0, sortOrder = 0),
    Category(name = "Groceries", icon = "groceries", color = 1, sortOrder = 1),
    Category(name = "Transport", icon = "transport", color = 2, sortOrder = 2),
    Category(name = "Sports", icon = "sports", color = 3, sortOrder = 3),
    Category(name = "Shopping", icon = "shopping", color = 4, sortOrder = 4),
    Category(name = "Bills", icon = "bills", color = 5, sortOrder = 5),
    Category(name = "Entertainment", icon = "entertainment", color = 6, sortOrder = 6),
    Category(name = "Home & rent", icon = "home", color = 7, sortOrder = 7),
    Category(name = "Subscriptions", icon = "subscriptions", sortOrder = 8),
    Category(name = "Health", icon = "health", sortOrder = 9),
    Category(name = "Travel", icon = "travel", sortOrder = 10),
    Category(name = "Other", icon = "other", sortOrder = 11),
    Category(name = "Salary", icon = "salary", income = true, color = 0, sortOrder = 20),
    Category(name = "Freelance", icon = "work", income = true, color = 1, sortOrder = 21),
    Category(name = "Refund", icon = "refund", income = true, color = 2, sortOrder = 22),
    Category(name = "Gift", icon = "gift", income = true, color = 3, sortOrder = 23),
    Category(name = "Other income", icon = "other", income = true, sortOrder = 24),
)
