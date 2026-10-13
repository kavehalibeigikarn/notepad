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
import ir.kaveh.yaddashtyar.data.Deadline
import ir.kaveh.yaddashtyar.data.Note
import ir.kaveh.yaddashtyar.data.deadlineList
import ir.kaveh.yaddashtyar.data.encodeDeadlines
import ir.kaveh.yaddashtyar.data.parseChecklist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "reminders2"
    const val CHANNEL_DEADLINE = "deadlines"
    const val EXTRA_OPEN = "openNoteId"
    const val ACTION_FIRE = "ir.kaveh.yaddashtyar.REMINDER_FIRE"
    const val ACTION_SNOOZE = "ir.kaveh.yaddashtyar.REMINDER_SNOOZE"
    const val ACTION_DEADLINE = "ir.kaveh.yaddashtyar.DEADLINE_FIRE"
    const val ACTION_DEADLINE_DONE = "ir.kaveh.yaddashtyar.DEADLINE_DONE"

    /** Hour of the day (local time) at which "N days before" deadline reminders ring. */
    private const val DEADLINE_HOUR = 9

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun setAlarm(ctx: Context, pi: PendingIntent, at: Long) {
        val am = alarmManager(ctx)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun alarmIntent(ctx: Context, noteId: Long): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra("noteId", noteId)
        return PendingIntent.getBroadcast(ctx, noteId.toInt(), i, flags())
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel("reminders")
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "یادآوری‌ها", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "اعلان یادآوری یادداشت‌ها (صدای آلارم)"
                // اگر زنگ کامل اجرا نشد، اعلان با صدای آلارم گوشی پخش شود
                ch.setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                ch.enableVibration(true)
                nm.createNotificationChannel(ch)
            }
            if (nm.getNotificationChannel(CHANNEL_DEADLINE) == null) {
                val ch = NotificationChannel(CHANNEL_DEADLINE, "مهلت‌های قانونی", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "یادآوری مهلت‌های قانونی و موعدها"
                nm.createNotificationChannel(ch)
            }
        }
    }

    // ---------- single reminder per note ----------

    /** Schedules (or cancels, when [at] is 0 / in the past) the reminder of a note. */
    fun schedule(context: Context, noteId: Long, at: Long) {
        val ctx = context.applicationContext
        val pi = alarmIntent(ctx, noteId)
        alarmManager(ctx).cancel(pi)
        if (at <= System.currentTimeMillis()) return
        setAlarm(ctx, pi, at)
    }

    fun cancel(context: Context, noteId: Long) = schedule(context, noteId, 0)

    fun cancelNotification(context: Context, noteId: Long) {
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
    }

    fun alarmMode(ctx: Context): Boolean =
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("alarmMode", true)

    private fun previewOf(n: Note): String {
        val raw = n.body.ifBlank { parseChecklist(n.checklist).firstOrNull()?.text ?: "" }.trim().take(200)
        return raw.ifEmpty { "زمان یادآوری فرا رسید" }
    }

    /** یادآور یادداشت: به‌صورت زنگ هشدار (پیش‌فرض) یا اعلان معمولی. */
    fun notify(context: Context, n: Note) {
        val ctx = context.applicationContext
        if (alarmMode(ctx)) {
            try {
                AlarmService.start(ctx, n.id, n.title.ifBlank { "یادآوری یادداشت" }, previewOf(n))
                return
            } catch (e: Exception) {
                // اجازهٔ شروع سرویس نبود؛ اعلان معمولی
            }
        }
        notifyPlain(ctx, n.id, n.title.ifBlank { "یادآوری یادداشت" }, previewOf(n))
    }

    fun notifyPlain(context: Context, noteId: Long, title: String, text: String) {
        val ctx = context.applicationContext
        ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, noteId)
        val openPi = PendingIntent.getActivity(ctx, noteId.toInt(), open, flags())
        val snooze = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE)
            .putExtra("noteId", noteId)
        val snoozePi = PendingIntent.getBroadcast(ctx, noteId.toInt(), snooze, flags())
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "یادآوری یادداشت" })
            .setContentText(text.ifBlank { "زمان یادآوری فرا رسید" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "۱۰ دقیقه بعد", snoozePi)
        try {
            NotificationManagerCompat.from(ctx).notify(noteId.toInt(), b.build())
        } catch (e: SecurityException) {
            // notification permission missing: nothing we can do
        }
    }

    // ---------- deadlines (several alarms per deadline) ----------

    fun dlCode(noteId: Long, dlId: Long, offset: Int): Int = "dl:$noteId:$dlId:$offset".hashCode()

    private fun dlIntent(ctx: Context, noteId: Long, dlId: Long, offset: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_DEADLINE)
            .putExtra("noteId", noteId)
            .putExtra("dlId", dlId)
            .putExtra("offset", offset)
        return PendingIntent.getBroadcast(ctx, dlCode(noteId, dlId, offset), i, flags())
    }

    /** When the "[offset] days before" reminder of a deadline rings. */
    fun triggerAt(dueAt: Long, offset: Int): Long {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate().minusDays(offset.toLong())
        return day.atTime(DEADLINE_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    }

    /** Cancels the alarms of [old] and schedules those of [new] (open deadlines only). */
    fun scheduleDeadlines(context: Context, noteId: Long, old: List<Deadline>, new: List<Deadline>) {
        val ctx = context.applicationContext
        val am = alarmManager(ctx)
        old.forEach { d -> d.offsets.forEach { o -> am.cancel(dlIntent(ctx, noteId, d.id, o)) } }
        val now = System.currentTimeMillis()
        new.filter { !it.done }.forEach { d ->
            d.offsets.forEach { o ->
                val t = triggerAt(d.dueAt, o)
                if (t > now) setAlarm(ctx, dlIntent(ctx, noteId, d.id, o), t)
            }
        }
    }

    fun cancelDeadlines(context: Context, noteId: Long, list: List<Deadline>) =
        scheduleDeadlines(context, noteId, list, emptyList())

    fun cancelCode(context: Context, code: Int) {
        NotificationManagerCompat.from(context).cancel(code)
    }

    fun notifyDeadline(context: Context, n: Note, d: Deadline, offset: Int) {
        val ctx = context.applicationContext
        ensureChannel(ctx)
        val code = dlCode(n.id, d.id, offset)
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, n.id)
        val openPi = PendingIntent.getActivity(ctx, code, open, flags())
        val done = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_DEADLINE_DONE)
            .putExtra("noteId", n.id)
            .putExtra("dlId", d.id)
            .putExtra("offset", offset)
        val donePi = PendingIntent.getBroadcast(ctx, code, done, flags())
        val left = Dates.daysUntil(d.dueAt)
        val text = when {
            left < 0 -> "مهلت ${(-left).toInt().fa()} روز پیش تمام شده است"
            left == 0L -> "امروز آخرین روز مهلت است"
            left == 1L -> "فردا آخرین روز مهلت است"
            else -> "${left.toInt().fa()} روز تا پایان مهلت (${Dates.full(d.dueAt)})"
        }
        val b = NotificationCompat.Builder(ctx, CHANNEL_DEADLINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("مهلت: " + d.title)
            .setContentText(text)
            .setSubText(n.title.ifBlank { "یادداشت" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + if (d.basis.isNotBlank()) "\n" + d.basis else ""))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "انجام شد", donePi)
        try {
            NotificationManagerCompat.from(ctx).notify(code, b.build())
        } catch (e: SecurityException) {
            // notification permission missing
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("noteId", -1L)
        if (id < 0) return
        val dlId = intent.getLongExtra("dlId", -1L)
        val offset = intent.getIntExtra("offset", 0)
        val app = context.applicationContext
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(app).dao()
                val n = dao.get(id)
                if (n != null) {
                    when (action) {
                        Reminders.ACTION_SNOOZE -> if (!n.trashed) {
                            AlarmService.stop(app)
                            val at = System.currentTimeMillis() + 10 * 60_000L
                            dao.update(n.copy(remindAt = at))
                            Reminders.schedule(app, id, at)
                            Reminders.cancelNotification(app, id)
                        }
                        Reminders.ACTION_DEADLINE -> if (!n.trashed) {
                            val d = n.deadlineList().firstOrNull { it.id == dlId }
                            if (d != null && !d.done) Reminders.notifyDeadline(app, n, d, offset)
                        }
                        Reminders.ACTION_DEADLINE_DONE -> {
                            val old = n.deadlineList()
                            val upd = old.map { if (it.id == dlId) it.copy(done = true) else it }
                            dao.update(n.copy(deadlines = encodeDeadlines(upd)))
                            Reminders.scheduleDeadlines(app, id, old, upd)
                            Reminders.cancelCode(app, Reminders.dlCode(id, dlId, offset))
                        }
                        else -> if (!n.trashed) Reminders.notify(app, n)
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
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED &&
            a != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                AppDb.get(app).dao().allNotes()
                    .filter { !it.trashed }
                    .forEach { n ->
                        if (n.remindAt > now) Reminders.schedule(app, n.id, n.remindAt)
                        val dl = n.deadlineList()
                        if (dl.isNotEmpty()) Reminders.scheduleDeadlines(app, n.id, emptyList(), dl)
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
