package ir.kaveh.yaddashtyar.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.kaveh.yaddashtyar.R
import ir.kaveh.yaddashtyar.ui.AlarmActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * زنگ هشدار یادآوری: مثل ساعت بیدارباش با صدای آلارم گوشی (حتی در حالت بی‌صدا)،
 * لرزش و صفحهٔ تمام‌صفحه روی قفل. بعد از ۳ دقیقه خودش قطع می‌شود و یک اعلان معمولی می‌ماند.
 */
class AlarmService : Service() {
    companion object {
        const val CHANNEL = "alarm"
        const val ACTION_START = "ir.kaveh.yaddashtyar.ALARM_START"
        const val ACTION_STOP = "ir.kaveh.yaddashtyar.ALARM_STOP"
        private const val NOTIF_ID = 7001
        private const val MAX_MS = 3 * 60_000L

        /** صفحهٔ زنگ با این مقدار می‌فهمد کی باید بسته شود */
        val active = MutableStateFlow(false)

        fun start(ctx: Context, noteId: Long, title: String, text: String) {
            val i = Intent(ctx, AlarmService::class.java).setAction(ACTION_START)
                .putExtra("noteId", noteId).putExtra("title", title).putExtra("text", text)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            if (active.value) ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0.25f
    private var noteId = -1L
    private var title = ""
    private var text = ""

    private val ramp = object : Runnable {
        override fun run() {
            volume = (volume + 0.15f).coerceAtMost(1f)
            try { player?.setVolume(volume, volume) } catch (_: Exception) {}
            if (volume < 1f) handler.postDelayed(this, 2000)
        }
    }
    private val timeout = Runnable {
        val t = title; val x = text; val id = noteId
        shutdown()
        Reminders.notifyPlain(applicationContext, id, t, x)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            else -> {
                noteId = intent?.getLongExtra("noteId", -1L) ?: -1L
                title = intent?.getStringExtra("title").orEmpty()
                text = intent?.getStringExtra("text").orEmpty()
                ensureChannel()
                val n = buildNotification()
                val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
                ServiceCompat.startForeground(this, NOTIF_ID, n, type)
                active.value = true
                startSound()
                handler.removeCallbacks(timeout)
                handler.postDelayed(timeout, MAX_MS)
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "زنگ هشدار یادآوری", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "پخش صدای هشدار مثل ساعت زنگ‌دار"
                ch.setSound(null, null)
                ch.enableVibration(false)
                ch.setBypassDnd(true)
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val screen = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("noteId", noteId).putExtra("title", title).putExtra("text", text)
        val screenPi = PendingIntent.getActivity(this, 1, screen, flags)
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, AlarmService::class.java).setAction(ACTION_STOP), flags
        )
        val snooze = Intent(this, ReminderReceiver::class.java)
            .setAction(Reminders.ACTION_SNOOZE).putExtra("noteId", noteId)
        val snoozePi = PendingIntent.getBroadcast(this, noteId.toInt(), snooze, flags)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "یادآوری" })
            .setContentText(text.ifBlank { "زمان یادآوری فرا رسید" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(screenPi)
            .setFullScreenIntent(screenPi, true)
            .addAction(0, "خاموش", stopPi)
            .addAction(0, "۱۰ دقیقه بعد", snoozePi)
            .build()
    }

    private fun startSound() {
        releasePlayer()
        raiseAlarmVolume()
        // چند منبع صدا به ترتیب امتحان می‌شود؛ آخری فایل خود برنامه است و همیشه کار می‌کند
        val sources = ArrayList<(MediaPlayer) -> Unit>()
        listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).distinct().forEach { uri -> sources.add { mp -> mp.setDataSource(applicationContext, uri) } }
        sources.add { mp ->
            resources.openRawResourceFd(R.raw.alarm_beep).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
        }
        for (src in sources) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                src(mp)
                mp.isLooping = true
                volume = 0.4f
                mp.setVolume(volume, volume)
                mp.prepare()
                mp.start()
                player = mp
                handler.postDelayed(ramp, 2000)
                break
            } catch (_: Exception) {
                try { mp.release() } catch (_: Exception) {}
            }
        }
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator = v
            val pattern = longArrayOf(0, 900, 700)
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") v?.vibrate(pattern, 0)
        } catch (_: Exception) {
        }
    }

    /** مثل هر ساعت زنگ‌دار: اگر حجم آلارم صفر یا خیلی کم است، تا حد قابل‌شنیدن بالا می‌رود. */
    private fun raiseAlarmVolume() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val cur = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            val want = (max * 0.7f).toInt().coerceAtLeast(1)
            if (cur < want) am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, want, 0)
        } catch (_: Exception) {
        }
    }

    private fun releasePlayer() {
        handler.removeCallbacks(ramp)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
    }

    private fun shutdown() {
        handler.removeCallbacks(timeout)
        releasePlayer()
        active.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        releasePlayer()
        active.value = false
        super.onDestroy()
    }
}
