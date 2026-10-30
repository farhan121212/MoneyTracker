package com.farhan.paisa.data

import android.content.Context
import android.net.Uri
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.localDate
import com.farhan.paisa.domain.myShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Exports {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun writeBackup(context: Context, uri: Uri, repository: Repository) {
        val text = json.encodeToString(repository.exportBackup())
        write(context, uri, text)
    }

    suspend fun readBackup(context: Context, uri: Uri): Backup = withContext(Dispatchers.IO) {
        val stream = requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open the file" }
        stream.bufferedReader().use { json.decodeFromString<Backup>(it.readText()) }
    }

    suspend fun writeCsv(context: Context, uri: Uri, ledger: Ledger) {
        write(context, uri, toCsv(ledger))
    }

    fun toCsv(ledger: Ledger): String = buildString {
        appendLine("date,type,amount,your_share,account,to_account,category,payee,method,tags,note")
        ledger.txns.forEach { txn ->
            val cells = listOf(
                txn.localDate().toString(),
                txn.type.name,
                Money.editable(txn.amount),
                Money.editable(ledger.myShare(txn)),
                ledger.accountById[txn.accountId]?.name.orEmpty(),
                ledger.accountById[txn.toAccountId]?.name.orEmpty(),
                ledger.categoryById[txn.categoryId]?.name.orEmpty(),
                txn.payee,
                txn.method.label,
                ledger.tagIdsByTxn[txn.id].orEmpty().mapNotNull { ledger.tagById[it]?.name }.joinToString(" "),
                txn.note,
            )
            appendLine(cells.joinToString(",") { escape(it) })
        }
    }

    private fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' }) "\"${cell.replace("\"", "\"\"")}\"" else cell

    private suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Could not open the file" }
        stream.bufferedWriter().use { it.write(text) }
    }
}
