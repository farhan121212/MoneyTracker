package com.farhan.paisa.work

import android.content.Context
import com.farhan.paisa.MainActivity
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Repository
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.checkLimits
import com.farhan.paisa.domain.cycles
import com.farhan.paisa.domain.limitStatuses
import com.farhan.paisa.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val SETTLE_MILLIS = 500L
private const val LIMIT_ID = 5_000

/**
 * Reacts to changes in your data. This only runs when the database or settings change, so it
 * never wakes the phone by itself: widgets stay current and a limit warning comes straight away.
 */
object Watcher {
    @OptIn(FlowPreview::class)
    fun start(context: Context, repository: Repository, scope: CoroutineScope) {
        scope.launch {
            combine(repository.ledger, repository.settings.settings) { ledger, settings -> ledger to settings }
                .debounce(SETTLE_MILLIS)
                .collect { (ledger, settings) ->
                    // Runs before the check so that erasing everything clears the widgets too.
                    Widgets.show(context, ledger, settings)
                    if (settings.onboarded && settings.limitAlerts) warnAboutLimits(context, repository, ledger, settings)
                }
        }
    }

    private suspend fun warnAboutLimits(context: Context, repository: Repository, ledger: Ledger, settings: Settings) {
        val period = ledger.cycles(settings).around(LocalDate.now())
        val check = checkLimits(ledger.limitStatuses(period), period, settings.limitAlertLog)
        check.alerts.forEach { alert ->
            val status = alert.status
            val body = if (status.left < 0) {
                "${Money.whole(-status.left)} over ${Money.whole(status.limit)}"
            } else {
                "${Money.whole(status.left)} left until ${period.end.format(DateTimeFormatter.ofPattern("d MMM"))}"
            }
            Reminders.notify(context, LIMIT_ID + status.key.hashCode() % LIMIT_ID, alert.title, body, MainActivity.ACTION_LIMITS)
        }
        if (check.log != settings.limitAlertLog) repository.settings.update { it.copy(limitAlertLog = check.log) }
    }
}
