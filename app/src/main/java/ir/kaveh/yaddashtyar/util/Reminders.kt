package ir.kaveh.yaddashtyar.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.kaveh.yaddashtyar.MainActivity
import ir.kaveh.yaddashtyar.R
import ir.kaveh.yaddashtyar.data.AppDb
import ir.kaveh.yaddashtyar.data.Note
import ir.kaveh.yaddashtyar.data.parseChecklist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Reminders {
    const val CHANNEL = "reminders"
    const val EXTRA_OPEN = "openNoteId"
    const val ACTION_FIRE = "ir.kaveh.yaddashtyar.REMINDER_FIRE"
    const val ACTION_SNOOZE = "ir.kaveh.yaddashtyar.REMINDER_SNOOZE"

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun alarmIntent(ctx: Context, noteId: Long): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra("noteId", noteId)
        return PendingIntent.getBroadcast(ctx, noteId.toInt(), i, flags())
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "یادآوری‌ها", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "اعلان یادآوری یادداشت‌ها"
                nm.createNotificationChannel(ch)
            }
        }
    }

    /** Schedules (or cancels, when [at] is 0 / in the past) the reminder of a note. */
    fun schedule(context: Context, noteId: Long, at: Long) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(ctx, noteId)
        am.cancel(pi)
        if (at <= System.currentTimeMillis()) return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, noteId: Long) = schedule(context, noteId, 0)

    fun cancelNotification(context: Context, noteId: Long) {
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
    }

    fun notify(context: Context, n: Note) {
        val ctx = context.applicationContext
        ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, n.id)
        val openPi = PendingIntent.getActivity(ctx, n.id.toInt(), open, flags())
        val snooze = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE)
            .putExtra("noteId", n.id)
        val snoozePi = PendingIntent.getBroadcast(ctx, n.id.toInt(), snooze, flags())
        val raw = n.body.ifBlank { parseChecklist(n.checklist).firstOrNull()?.text ?: "" }.trim().take(200)
        val text = raw.ifEmpty { "زمان یادآوری فرا رسید" }
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title.ifBlank { "یادآوری یادداشت" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "۱۰ دقیقه بعد", snoozePi)
        try {
            NotificationManagerCompat.from(ctx).notify(n.id.toInt(), b.build())
        } catch (e: SecurityException) {
            // notification permission missing: nothing we can do
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("noteId", -1L)
        if (id < 0) return
        val app = context.applicationContext
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(app).dao()
                val n = dao.get(id)
                if (n != null && !n.trashed) {
                    if (action == Reminders.ACTION_SNOOZE) {
                        val at = System.currentTimeMillis() + 10 * 60_000L
                        dao.update(n.copy(remindAt = at))
                        Reminders.schedule(app, id, at)
                        Reminders.cancelNotification(app, id)
                    } else {
                        Reminders.notify(app, n)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                AppDb.get(app).dao().allNotes()
                    .filter { !it.trashed && it.remindAt > now }
                    .forEach { Reminders.schedule(app, it.id, it.remindAt) }
            } finally {
                pending.finish()
            }
        }
    }
}
