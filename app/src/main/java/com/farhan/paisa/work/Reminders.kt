package com.farhan.paisa.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.farhan.paisa.MainActivity
import com.farhan.paisa.PaisaApp
import com.farhan.paisa.R
import com.farhan.paisa.data.AccountKind
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.billsFor
import com.farhan.paisa.domain.cardStatus
import com.farhan.paisa.domain.localDate
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Reminders {
    private const val WORK_NAME = "evening-check"
    private const val CHANNEL = "reminders"
    private val EVENING = LocalTime.of(21, 0)

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        val nextToday = now.toLocalDate().atTime(EVENING)
        val next = if (now.isBefore(nextToday)) nextToday else nextToday.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun notify(context: Context, id: Int, title: String, body: String, action: String? = null) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

/** Runs each evening: nudges you to log the day and warns about card bills coming due. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as PaisaApp).repository
        val settings = repository.settings.current()
        if (!settings.onboarded) return Result.success()
        val ledger = repository.ledger.first()
        val today = LocalDate.now()

        if (settings.dailyReminder && ledger.txns.none { it.localDate() == today }) {
            Reminders.notify(applicationContext, DAILY_ID, "Anything to log today?", "Add today's payments while you still remember them.")
        }
        if (settings.dueReminder) {
            ledger.billsFor(today).filter { !it.paid && it.daysToDue in 0..1 }.forEach { status ->
                Reminders.notify(
                    applicationContext,
                    BILL_ID + status.bill.id.toInt(),
                    "${status.bill.name} is due ${if (status.daysToDue == 0) "today" else "tomorrow"}",
                    Money.format(status.bill.amount),
                )
            }
            ledger.activeAccounts.filter { it.kind == AccountKind.CREDIT_CARD }.forEach { card ->
                val status = ledger.cardStatus(card, today)
                val days = status.daysToDue
                if (status.billed > 0 && days != null && (days in DUE_WARNINGS || status.overdue)) {
                    val whenDue = when {
                        status.overdue -> "is overdue"
                        days == 0 -> "is due today"
                        days == 1 -> "is due tomorrow"
                        else -> "is due in $days days"
                    }
                    Reminders.notify(
                        applicationContext,
                        DUE_ID + card.id.toInt(),
                        "${card.name} bill",
                        "${Money.format(status.billed)} $whenDue",
                    )
                }
            }
        }
        return Result.success()
    }

    private companion object {
        const val DAILY_ID = 1
        const val DUE_ID = 100
        const val BILL_ID = 10_000
        val DUE_WARNINGS = setOf(0, 1, 3)
    }
}
