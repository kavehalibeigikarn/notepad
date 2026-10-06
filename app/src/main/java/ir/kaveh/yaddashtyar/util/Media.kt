package ir.kaveh.yaddashtyar.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import java.util.UUID

fun formatClock(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60).faDigits()
}

/** Records a voice note to an .m4a file inside the app's attachment folder. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    var recording by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set

    fun start(): Boolean {
        val f = File(FileUtil.dir(ctx), UUID.randomUUID().toString() + ".m4a")
        var r: MediaRecorder? = null
        return try {
            r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            startedAt = SystemClock.elapsedRealtime()
            elapsedMs = 0
            recording = true
            true
        } catch (e: Exception) {
            try { r?.release() } catch (e2: Exception) { }
            f.delete()
            rec = null
            file = null
            recording = false
            false
        }
    }

    fun tick() {
        if (recording) elapsedMs = SystemClock.elapsedRealtime() - startedAt
    }

    /** Stops recording; returns the file, or null if the recording was too short / failed. */
    fun stop(): File? {
        val r = rec ?: return null
        val f = file
        var ok = true
        try { r.stop() } catch (e: RuntimeException) { ok = false }
        try { r.release() } catch (e: Exception) { }
        val dur = SystemClock.elapsedRealtime() - startedAt
        rec = null
        file = null
        recording = false
        if (!ok || f == null || dur < 700) {
            f?.delete()
            return null
        }
        return f
    }

    fun cancel() {
        val r = rec
        if (r != null) {
            try { r.stop() } catch (e: Exception) { }
            try { r.release() } catch (e: Exception) { }
        }
        file?.delete()
        rec = null
        file = null
        recording = false
    }
}

/** Minimal audio player for voice-note attachments (one at a time). */
class AudioController {
    private var mp: MediaPlayer? = null

    var activeId by mutableLongStateOf(-1L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var duration by mutableIntStateOf(0)
        private set

    fun toggle(file: File, id: Long) {
        val cur = mp
        if (activeId == id && cur != null) {
            if (cur.isPlaying) {
                cur.pause()
                isPlaying = false
            } else {
                cur.start()
                isPlaying = true
            }
            return
        }
        release()
        try {
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                isPlaying = false
                position = 0
                it.seekTo(0)
            }
            p.prepare()
            duration = p.duration
            p.start()
            mp = p
            activeId = id
            isPlaying = true
            position = 0
        } catch (e: Exception) {
            release()
        }
    }

    fun poll() {
        val p = mp
        if (p != null && isPlaying) position = p.currentPosition
    }

    fun release() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        activeId = -1L
        isPlaying = false
        position = 0
        duration = 0
    }
}
