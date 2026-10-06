package ir.kaveh.yaddashtyar.util

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Persian-aware normalisation used for searching. */
fun norm(s: String): String {
    val sb = StringBuilder(s.length)
    for (raw in s.lowercase()) {
        when (raw) {
            'ي' -> sb.append('ی')
            'ك' -> sb.append('ک')
            'ۀ' -> sb.append('ه')
            '\u200c' -> sb.append(' ')
            in '\u064B'..'\u065F', '\u0670' -> {}
            in '۰'..'۹' -> sb.append('0' + (raw - '۰'))
            in '٠'..'٩' -> sb.append('0' + (raw - '٠'))
            else -> sb.append(raw)
        }
    }
    return sb.toString()
}

/** Length-preserving normalisation (for highlight offsets). */
private fun norm1(c: Char): Char = when (c) {
    'ي' -> 'ی'
    'ك' -> 'ک'
    in '۰'..'۹' -> '0' + (c - '۰')
    in '٠'..'٩' -> '0' + (c - '٠')
    else -> c.lowercaseChar()
}

private fun norm1(s: String): String = String(CharArray(s.length) { norm1(s[it]) })

fun highlight(text: String, terms: List<String>, bg: Color): AnnotatedString {
    if (terms.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val low = norm1(text)
    val ranges = ArrayList<IntRange>()
    for (t in terms) {
        val nt = norm1(t)
        if (nt.isEmpty()) continue
        var i = low.indexOf(nt)
        while (i >= 0) {
            ranges.add(i until i + nt.length)
            i = low.indexOf(nt, i + nt.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { addStyle(SpanStyle(background = bg), it.first, it.last + 1) }
    }
}

fun snippetAround(text: String, terms: List<String>, max: Int = 220): String {
    val t = text.trim().replace(Regex("\\n{2,}"), "\n")
    if (terms.isNotEmpty()) {
        val low = norm1(t)
        var idx = -1
        for (term in terms) {
            val i = low.indexOf(norm1(term))
            if (i >= 0 && (idx < 0 || i < idx)) idx = i
        }
        if (idx > 80) return "…" + t.substring(idx - 30).take(max)
    }
    return t.take(max)
}

fun String.faDigits(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(if (c in '0'..'9') '۰' + (c - '0') else c)
    return sb.toString()
}

fun Int.fa(): String = toString().faDigits()

fun Long.fileSize(): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val s = when {
        this < 1024 -> return "$this بایت".faDigits()
        this < mb -> String.format(Locale.US, "%.1f کیلوبایت", this / kb)
        this < gb -> String.format(Locale.US, "%.1f مگابایت", this / mb)
        else -> String.format(Locale.US, "%.1f گیگابایت", this / gb)
    }
    return s.faDigits().replace(".", "٫")
}

object Dates {
    private val loc = ULocale("fa_IR@calendar=persian")
    private val dayFmt = SimpleDateFormat("d MMMM y", loc)
    private val monthFmt = SimpleDateFormat("MMMM y", loc)
    private val timeFmt = SimpleDateFormat("HH:mm", loc)

    fun full(ts: Long): String = synchronized(this) { dayFmt.format(Date(ts)) }
    fun month(ts: Long): String = synchronized(this) { monthFmt.format(Date(ts)) }
    fun time(ts: Long): String = synchronized(this) { timeFmt.format(Date(ts)) }

    private fun daysAgo(ts: Long): Long {
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(d, LocalDate.now(zone))
    }

    fun bucket(ts: Long): String {
        val d = daysAgo(ts)
        return when {
            d <= 0 -> "امروز"
            d == 1L -> "دیروز"
            d < 7 -> "هفتهٔ اخیر"
            d < 30 -> "۳۰ روز اخیر"
            else -> month(ts)
        }
    }

    /** Used for reminders (future-aware). */
    fun remind(ts: Long): String {
        val d = daysAgo(ts)
        return when (d) {
            0L -> "امروز، " + time(ts)
            -1L -> "فردا، " + time(ts)
            1L -> "دیروز، " + time(ts)
            else -> full(ts) + "، " + time(ts)
        }
    }

    fun relative(ts: Long): String {
        val d = daysAgo(ts)
        return when {
            d <= 0 -> "امروز، " + time(ts)
            d == 1L -> "دیروز، " + time(ts)
            else -> full(ts)
        }
    }
}
