package com.farhan.paisa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** All money is stored as whole paise so that sums never pick up floating point error. */

@Serializable
enum class AccountKind { BANK, CREDIT_CARD, CASH }

/** What an account is for. Savings accounts count towards the monthly savings target. */
@Serializable
enum class AccountRole { SPENDING, SAVINGS }

@Serializable
enum class TxType { EXPENSE, INCOME, TRANSFER, SETTLEMENT }

@Serializable
enum class PayMethod(val label: String) {
    UPI("UPI"),
    CARD("Card"),
    NET_BANKING("Net banking"),
    AUTO_DEBIT("Auto-debit"),
    CASH("Cash"),
}

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
    val role: AccountRole = AccountRole.SPENDING,
    val last4: String = "",
    /** Balance on the day the account was added. For a credit card, the amount already owed. */
    val opening: Long = 0,
    val creditLimit: Long = 0,
    /** Day of the month the card statement is generated, 0 when unknown. */
    val statementDay: Int = 0,
    /** Day of the month the card bill is due, 0 when unknown. */
    val dueDay: Int = 0,
    val accent: Int = 0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** Index into the chart palette, or -1 for a neutral colour. */
    val color: Int = -1,
    val income: Boolean = false,
    /** Monthly limit for this category, 0 when there is none. */
    val monthlyLimit: Long = 0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(
    tableName = "txns",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("personId")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amount: Long,
    val accountId: Long,
    /** Destination of a transfer. Paying a card bill is a transfer into the card. */
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** Who the money went to, or who it came from for income. */
    val payee: String = "",
    /** The person paying you back, for a settlement. */
    val personId: Long? = null,
    val method: PayMethod = PayMethod.UPI,
    val note: String = "",
    val date: Long,
)

@Serializable
@Entity(
    tableName = "txn_tags",
    primaryKeys = ["txnId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = Txn::class, parentColumns = ["id"], childColumns = ["txnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TxnTag(val txnId: Long, val tagId: Long)

@Serializable
enum class DetectedStatus { PENDING, CONFIRMED, DISMISSED }

/**
 * A payment found in a bank SMS. It waits here until you confirm it, so nothing read from a
 * message can change a balance by itself. The text of the SMS is never stored.
 */
@Serializable
@Entity(tableName = "detected", indices = [Index(value = ["dedupKey"], unique = true), Index("status")])
data class Detected(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identifies the payment, so the same one is never offered twice. */
    val dedupKey: String,
    val type: TxType,
    val amount: Long,
    /** The other party, as written in the SMS. */
    val counterparty: String,
    /** Last digits of the account the money left, or arrived in for income. */
    val last4: String,
    /** Last digits of the receiving account of a transfer. */
    val toLast4: String = "",
    val method: PayMethod,
    val date: Long,
    val status: DetectedStatus = DetectedStatus.PENDING,
    val txnId: Long? = null,
)

/** A bill that comes every month, such as rent or a subscription. */
@Serializable
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    /** Day of the month it is due. */
    val day: Int,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val method: PayMethod = PayMethod.UPI,
)

/** A named target inside your savings. The money itself stays in the savings account. */
@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    /** Epoch day to reach the goal by, when there is a deadline. */
    val targetDay: Long? = null,
    val accent: Int = 0,
)

/** Money set aside for a goal. Negative when taken back out. */
@Serializable
@Entity(
    tableName = "goal_entries",
    indices = [Index("goalId")],
    foreignKeys = [
        ForeignKey(entity = Goal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class GoalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amount: Long,
    val date: Long,
)

/** The part of an expense that someone else owes you. The rest of it is your own spending. */
@Serializable
@Entity(
    tableName = "split_shares",
    indices = [Index("txnId"), Index("personId")],
    foreignKeys = [
        ForeignKey(entity = Txn::class, parentColumns = ["id"], childColumns = ["txnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SplitShare(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val personId: Long,
    val amount: Long,
)
