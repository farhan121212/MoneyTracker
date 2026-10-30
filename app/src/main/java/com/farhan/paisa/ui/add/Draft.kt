package com.farhan.paisa.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.PayMethod
import com.farhan.paisa.data.Txn
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.accountFor
import com.farhan.paisa.domain.equalShare
import com.farhan.paisa.domain.latestForPayee
import com.farhan.paisa.domain.localDate
import com.farhan.paisa.domain.suggestionFor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything the user has entered so far on the add and edit screens. */
class Draft(private val ledger: Ledger, val editing: Txn?, val detected: Detected? = null) {
    var type by mutableStateOf(TxType.EXPENSE)
    var amountText by mutableStateOf("")
    var payee by mutableStateOf("")
    var categoryId by mutableStateOf<Long?>(null)
    var accountId by mutableStateOf<Long?>(null)
    var toAccountId by mutableStateOf<Long?>(null)
    var method by mutableStateOf(PayMethod.UPI)
    var note by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    val tagIds = mutableStateListOf<Long>()

    val splitPeople = mutableStateListOf<Long>()
    var splitEqual by mutableStateOf(true)
    var includeMe by mutableStateOf(true)
    val customShares = mutableStateMapOf<Long, String>()

    val amount: Long get() = Money.parse(amountText) ?: 0

    /** Shares that others owe, as (person, paise). In equal mode the amounts always add up. */
    fun shares(): List<Pair<Long, Long>> {
        if (splitPeople.isEmpty()) return emptyList()
        if (!splitEqual) {
            return splitPeople.map { it to (Money.parse(customShares[it].orEmpty()) ?: 0) }
        }
        val ways = splitPeople.size + if (includeMe) 1 else 0
        val each = equalShare(amount, ways)
        val leftover = if (includeMe) 0 else amount - each * ways
        return splitPeople.mapIndexed { index, person -> person to if (index == 0) each + leftover else each }
    }

    val myShare: Long get() = amount - shares().sumOf { it.second }

    val valid: Boolean
        get() = amount > 0 && accountId != null && when (type) {
            TxType.TRANSFER -> toAccountId != null && toAccountId != accountId
            TxType.EXPENSE -> myShare >= 0
            else -> true
        }

    fun selectAccount(id: Long) {
        accountId = id
        when (ledger.accountById[id]?.kind) {
            AccountKind.CREDIT_CARD -> method = PayMethod.CARD
            AccountKind.CASH -> method = PayMethod.CASH
            else -> if (method == PayMethod.CASH || method == PayMethod.CARD) method = PayMethod.UPI
        }
    }

    /** Picking a known payee brings back how you usually pay them. */
    fun applyPayee(name: String) {
        payee = name
        val last = ledger.latestForPayee(name) ?: return
        if (categoryId == null) categoryId = last.categoryId
        if (tagIds.isEmpty()) tagIds.addAll(ledger.tagIdsByTxn[last.id].orEmpty())
    }

    fun applyRepeat(txn: Txn) {
        type = TxType.EXPENSE
        amountText = Money.editable(txn.amount)
        payee = txn.payee
        categoryId = txn.categoryId
        selectAccount(txn.accountId)
        method = txn.method
        tagIds.clear()
        tagIds.addAll(ledger.tagIdsByTxn[txn.id].orEmpty())
    }

    fun toTxn(): Txn {
        val original = editing
        val zone = ZoneId.systemDefault()
        val detectedAt = detected?.date?.takeIf { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date }
        val millis = when {
            original != null && original.localDate() == date -> original.date
            detectedAt != null -> detectedAt
            else -> date.atTime(LocalTime.now()).atZone(zone).toInstant().toEpochMilli()
        }
        return Txn(
            id = original?.id ?: 0,
            type = type,
            amount = amount,
            accountId = requireNotNull(accountId),
            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
            categoryId = if (type == TxType.TRANSFER) null else categoryId,
            payee = if (type == TxType.TRANSFER) "" else payee.trim(),
            personId = original?.personId,
            method = method,
            note = note.trim(),
            date = millis,
        )
    }

    companion object {
        fun create(
            ledger: Ledger,
            type: TxType,
            editId: Long,
            from: Long,
            to: Long,
            amount: Long,
            detectedId: Long,
            billId: Long,
        ): Draft {
            val editing = ledger.txnById[editId]
            val detected = ledger.detectedById[detectedId]
            val draft = Draft(ledger, editing, detected)
            if (detected != null) {
                val suggestion = ledger.suggestionFor(detected)
                draft.type = detected.type
                draft.amountText = Money.editable(detected.amount)
                draft.payee = suggestion.payee
                draft.categoryId = suggestion.categoryId
                draft.tagIds.addAll(suggestion.tagIds)
                draft.method = detected.method
                draft.date = Instant.ofEpochMilli(detected.date).atZone(ZoneId.systemDefault()).toLocalDate()
                // Left empty when the digits aren't known, so a guess can't land on the wrong account.
                draft.accountId = ledger.accountFor(detected.last4)?.id
                draft.toAccountId = ledger.accountFor(detected.toLast4)?.id
                return draft
            }
            val bill = ledger.recurringById[billId]
            if (bill != null) {
                draft.amountText = Money.editable(bill.amount)
                draft.payee = bill.name
                draft.categoryId = bill.categoryId
                (bill.accountId?.takeIf { ledger.accountById[it]?.archived == false } ?: ledger.activeAccounts.firstOrNull()?.id)
                    ?.let { draft.selectAccount(it) }
                draft.method = bill.method
                return draft
            }
            if (editing != null) {
                draft.type = editing.type
                draft.amountText = Money.editable(editing.amount)
                draft.payee = editing.payee
                draft.categoryId = editing.categoryId
                draft.accountId = editing.accountId
                draft.toAccountId = editing.toAccountId
                draft.method = editing.method
                draft.note = editing.note
                draft.date = editing.localDate()
                draft.tagIds.addAll(ledger.tagIdsByTxn[editing.id].orEmpty())
                val shares = ledger.sharesByTxn[editing.id].orEmpty()
                if (shares.isNotEmpty()) {
                    draft.splitEqual = false
                    shares.forEach {
                        draft.splitPeople.add(it.personId)
                        draft.customShares[it.personId] = Money.editable(it.amount)
                    }
                }
                return draft
            }
            draft.type = type
            if (amount > 0) draft.amountText = Money.editable(amount)
            val lastUsed = ledger.txns.firstOrNull { it.type == TxType.EXPENSE }?.accountId
                ?.takeIf { ledger.accountById[it]?.archived == false }
            val account = from.takeIf { it != 0L } ?: lastUsed ?: ledger.activeAccounts.firstOrNull()?.id
            account?.let { draft.selectAccount(it) }
            draft.toAccountId = to.takeIf { it != 0L }
            return draft
        }
    }
}
