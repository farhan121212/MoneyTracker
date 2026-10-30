package com.farhan.paisa.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.farhan.paisa.MainActivity
import com.farhan.paisa.PaisaApp
import com.farhan.paisa.data.Detected
import com.farhan.paisa.data.Settings
import com.farhan.paisa.data.TxType
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.SmsParser
import com.farhan.paisa.work.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

object SmsSync {
    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private suspend fun handle(context: Context, settings: Settings, address: String, body: String, sentAt: Long): Detected? {
        val parsed = SmsParser.parse(address, body, settings.extraSenderSet) ?: return null
        val repository = (context.applicationContext as PaisaApp).repository
        return repository.ingest(SmsParser.toDetected(parsed, body, sentAt))
    }

    /** Handles one message as it arrives. */
    suspend fun onMessage(context: Context, address: String, body: String, sentAt: Long) {
        val settings = (context.applicationContext as PaisaApp).repository.settings.current()
        if (!settings.smsEnabled) return
        val found = handle(context, settings, address, body, sentAt) ?: return
        val name = found.counterparty
        val amount = Money.format(found.amount)
        val title = when {
            found.type == TxType.TRANSFER -> amount
            found.type != TxType.INCOME -> amount + if (name.isEmpty()) "" else " to $name"
            name == SmsParser.SALARY -> "Salary of $amount received"
            name.startsWith(SmsParser.REFUND) -> "$name: $amount"
            else -> "$amount received" + if (name.isEmpty()) "" else " from $name"
        }
        Reminders.notify(
            context = context,
            id = found.dedupKey.hashCode(),
            title = title,
            body = "Tap to add it",
            action = MainActivity.ACTION_REVIEW,
        )
    }

    /** Reads the inbox from [since] onwards and returns how many new payments it found. */
    suspend fun scan(context: Context, since: Long): Int = withContext(Dispatchers.IO) {
        val settings = (context.applicationContext as PaisaApp).repository.settings.current()
        if (!hasPermission(context)) return@withContext 0
        val columns = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE_SENT, Telephony.Sms.DATE)
        var found = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            columns,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0) ?: continue
                val body = cursor.getString(1) ?: continue
                val sentAt = cursor.getLong(2).takeIf { it > 0 } ?: cursor.getLong(3)
                if (handle(context, settings, address, body, sentAt) != null) found += 1
            }
        }
        found
    }

    /** Phones that stop background apps can miss a message, so opening the app double-checks. */
    suspend fun catchUp(context: Context) {
        val settings = (context.applicationContext as PaisaApp).repository.settings.current()
        if (!settings.smsEnabled) return
        scan(context, maxOf(settings.smsSince, System.currentTimeMillis() - 3 * DAY_MILLIS))
    }

    /** Brings in the last [days] days, for when you've asked for past payments. */
    suspend fun importPast(context: Context, days: Int): Int =
        scan(context, System.currentTimeMillis() - days * DAY_MILLIS)
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = parts.firstOrNull() ?: return
        val address = first.originatingAddress ?: return
        // A long SMS arrives in pieces.
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsSync.onMessage(context.applicationContext, address, body, first.timestampMillis)
            } finally {
                pending.finish()
            }
        }
    }
}
