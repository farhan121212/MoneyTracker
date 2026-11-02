package com.farhan.paisa.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.farhan.paisa.MainActivity
import com.farhan.paisa.PaisaApp
import com.farhan.paisa.R
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Settings
import com.farhan.paisa.domain.DueItem
import com.farhan.paisa.domain.DueKind
import com.farhan.paisa.domain.HomeSnapshot
import com.farhan.paisa.domain.Money
import com.farhan.paisa.domain.Tone
import com.farhan.paisa.domain.homeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HIDDEN = "••••"
private const val BAR_STEPS = 1000
private const val NEXT_DAY_WINDOW_MILLIS = 15 * 60 * 1000L

private val ShortDate = DateTimeFormatter.ofPattern("d MMM")

/** Draws the home screen widgets. The numbers come from [homeSnapshot], the same maths as Home. */
object Widgets {
    enum class Kind(val provider: Class<out AppWidgetProvider>, val title: String, val subtitle: String) {
        TODAY(TodayWidget::class.java, "Safe today", "What you can spend today"),
        MONTH(MonthWidget::class.java, "This month", "What's left, with a quick add button"),
        UPCOMING(UpcomingWidget::class.java, "Coming up", "Your next bills and card payments"),
    }

    private fun Context.component(kind: Kind) = ComponentName(this, kind.provider)

    fun canPin(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun pin(context: Context, kind: Kind) {
        AppWidgetManager.getInstance(context).requestPinAppWidget(context.component(kind), null, null)
    }

    /** Called by the system when a widget is added or a day turns over. */
    suspend fun refresh(context: Context) {
        val repository = (context.applicationContext as PaisaApp).repository
        show(context, repository.ledger.first(), repository.settings.current())
    }

    fun show(context: Context, ledger: Ledger, settings: Settings) {
        val manager = AppWidgetManager.getInstance(context)
        val placed = Kind.entries.associateWith { manager.getAppWidgetIds(context.component(it)) }.filterValues { it.isNotEmpty() }
        if (placed.isEmpty()) return
        val today = LocalDate.now()
        val snapshot = ledger.homeSnapshot(settings, today)
        placed.forEach { (kind, ids) ->
            val views = when (kind) {
                Kind.TODAY -> todayViews(context, snapshot)
                Kind.MONTH -> monthViews(context, snapshot)
                Kind.UPCOMING -> upcomingViews(context, snapshot, today)
            }
            manager.updateAppWidget(ids, views)
        }
        scheduleNextDay(context, today)
    }

    /**
     * Waits for the phone to wake on its own instead of waking it, since nobody's looking at
     * midnight. A plain inexact alarm can run hours late, so this gives it a window instead.
     */
    private fun scheduleNextDay(context: Context, today: LocalDate) {
        val nextDay = today.plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val refresh = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefresher::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC, nextDay, NEXT_DAY_WINDOW_MILLIS, refresh)
    }

    private fun opening(context: Context, action: String): PendingIntent = PendingIntent.getActivity(
        context,
        action.hashCode(),
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun todayViews(context: Context, s: HomeSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setOnClickPendingIntent(android.R.id.background, opening(context, Intent.ACTION_MAIN))
        views.setOnClickPendingIntent(R.id.add, opening(context, MainActivity.ACTION_ADD))
        views.setInt(
            android.R.id.background,
            "setBackgroundResource",
            when {
                s.locked || s.tone == Tone.GOOD -> R.drawable.widget_bg_lime
                s.tone == Tone.WATCH -> R.drawable.widget_bg_watch
                else -> R.drawable.widget_bg_over
            },
        )
        val spent = "${Money.whole(s.spentToday)} spent today"
        when {
            s.locked -> views.show("Paisa", HIDDEN, "Locked")
            !s.hasLimit -> views.show("Spent today", Money.whole(s.spentToday), "Set a limit to see what's safe")
            s.tone == Tone.OVER -> views.show("Over your limit", Money.whole(-s.left), spent)
            s.tone == Tone.WATCH -> views.show("To stay on limit", Money.whole(s.perDay), spent)
            else -> views.show("Safe today", Money.whole(s.perDay), spent)
        }
        return views
    }

    private fun RemoteViews.show(label: String, amount: String, detail: String) {
        setTextViewText(R.id.label, label)
        setTextViewText(R.id.amount, amount)
        setTextViewText(R.id.detail, detail)
    }

    private fun monthViews(context: Context, s: HomeSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        views.setOnClickPendingIntent(android.R.id.background, opening(context, Intent.ACTION_MAIN))
        views.setOnClickPendingIntent(R.id.add, opening(context, MainActivity.ACTION_ADD))
        views.setOnClickPendingIntent(R.id.review, opening(context, MainActivity.ACTION_REVIEW))
        val review = s.toReview > 0 && !s.locked
        views.setViewVisibility(R.id.review, if (review) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.review, "${s.toReview} to review")

        val bars = listOf(R.id.bar_good to Tone.GOOD, R.id.bar_watch to Tone.WATCH, R.id.bar_over to Tone.OVER)
        val showBar = s.hasLimit && !s.locked
        bars.forEach { (bar, tone) ->
            views.setViewVisibility(bar, if (showBar && s.tone == tone) View.VISIBLE else View.GONE)
            views.setProgressBar(bar, BAR_STEPS, (s.used.coerceIn(0f, 1f) * BAR_STEPS).toInt(), false)
        }

        val until = s.until.format(ShortDate)
        when {
            s.locked -> {
                views.show("Paisa", HIDDEN, "Locked")
                views.setTextViewText(R.id.side, "")
            }
            !s.hasLimit -> {
                views.show("Spent until $until", Money.whole(s.spent), "${Money.whole(s.spentToday)} today")
                views.setTextViewText(R.id.side, "")
            }
            s.left < 0 -> {
                views.show("Over your limit", Money.whole(-s.left), "Until $until")
                views.setTextViewText(R.id.side, "${Money.whole(s.spent)} of ${Money.whole(s.budget)}")
            }
            else -> {
                val pace = if (s.tone == Tone.WATCH) "a day to stay on limit" else "a day is safe"
                views.show("Left until $until", Money.whole(s.left), "${Money.whole(s.perDay)} $pace")
                views.setTextViewText(R.id.side, "${Money.whole(s.spent)} of ${Money.whole(s.budget)}")
            }
        }
        return views
    }

    private fun upcomingViews(context: Context, s: HomeSnapshot, today: LocalDate): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_upcoming)
        views.setOnClickPendingIntent(android.R.id.background, opening(context, MainActivity.ACTION_DUE))
        val rows = listOf(
            Triple(R.id.row1, R.id.row1_name, R.id.row1_when) to R.id.row1_amount,
            Triple(R.id.row2, R.id.row2_name, R.id.row2_when) to R.id.row2_amount,
            Triple(R.id.row3, R.id.row3_name, R.id.row3_when) to R.id.row3_amount,
        )
        val items = if (s.locked) emptyList() else s.upcoming
        rows.forEachIndexed { index, (ids, amount) ->
            val (row, name, whenDue) = ids
            val item = items.getOrNull(index)
            views.setViewVisibility(row, if (item == null) View.GONE else View.VISIBLE)
            if (item != null) {
                views.setTextViewText(name, nameOf(item))
                views.setTextViewText(whenDue, dueWords(item, today))
                views.setTextViewText(amount, Money.whole(item.amount))
            }
        }
        views.setViewVisibility(R.id.empty, if (items.isEmpty()) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.empty, if (s.locked) "Open Paisa to see" else "Nothing due soon")
        return views
    }

    private fun nameOf(item: DueItem) = if (item.kind == DueKind.BILL) item.name else "${item.name} bill"

    private fun dueWords(item: DueItem, today: LocalDate): String = when {
        item.date.isBefore(today) -> "Overdue"
        item.date == today -> "Today"
        item.date == today.plusDays(1) -> "Tomorrow"
        else -> item.date.format(ShortDate)
    }
}

/** A widget does nothing of its own: any of them being added or updated redraws all of them. */
abstract class PaisaWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        redraw(context, goAsync())
    }
}

class TodayWidget : PaisaWidget()
class MonthWidget : PaisaWidget()
class UpcomingWidget : PaisaWidget()

class WidgetRefresher : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        redraw(context, goAsync())
    }
}

private fun redraw(context: Context, pending: BroadcastReceiver.PendingResult) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Widgets.refresh(context.applicationContext)
        } finally {
            pending.finish()
        }
    }
}
