package ir.kaveh.yaddashtyar.ui

import android.icu.util.Calendar
import android.icu.util.PersianCalendar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.kaveh.yaddashtyar.util.Dates
import ir.kaveh.yaddashtyar.util.fa
import ir.kaveh.yaddashtyar.util.faDigits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val PERSIAN_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)
private val WEEK_DAYS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/** Persian (Jalali) y / m(0-11) / d of a timestamp. */
private fun toJ(ts: Long): Triple<Int, Int, Int> {
    val c = PersianCalendar()
    c.timeInMillis = ts
    return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
}

/** Offset of the 1st day from Saturday (0..6) and the number of days of that Jalali month. */
private fun monthInfo(y: Int, m: Int): Pair<Int, Int> {
    val c = PersianCalendar()
    c.clear()
    c.set(Calendar.YEAR, y)
    c.set(Calendar.MONTH, m)
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 12)
    val dow = c.get(Calendar.DAY_OF_WEEK) // Sunday=1 .. Saturday=7
    val len = c.getActualMaximum(Calendar.DAY_OF_MONTH)
    return (dow % 7) to len
}

private fun toMillis(y: Int, m: Int, d: Int, h: Int, min: Int): Long {
    val c = PersianCalendar()
    c.clear()
    c.set(Calendar.YEAR, y)
    c.set(Calendar.MONTH, m)
    c.set(Calendar.DAY_OF_MONTH, d)
    c.set(Calendar.HOUR_OF_DAY, h)
    c.set(Calendar.MINUTE, min)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun two(v: Int): String = String.format(Locale.US, "%02d", v).faDigits()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderDialog(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val base = remember {
        if (initial > now) initial
        else java.time.ZonedDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()
    }
    val j0 = remember { toJ(base) }
    val z0 = remember { Instant.ofEpochMilli(base).atZone(zone) }
    val today = remember { toJ(now) }

    var jy by remember { mutableIntStateOf(j0.first) }
    var jm by remember { mutableIntStateOf(j0.second) }
    var jd by remember { mutableIntStateOf(j0.third) }
    var viewY by remember { mutableIntStateOf(j0.first) }
    var viewM by remember { mutableIntStateOf(j0.second) }
    var hour by remember { mutableIntStateOf(z0.hour) }
    var minute by remember { mutableIntStateOf(z0.minute) }

    fun applyTs(ts: Long) {
        val j = toJ(ts)
        val z = Instant.ofEpochMilli(ts).atZone(zone)
        jy = j.first; jm = j.second; jd = j.third
        viewY = j.first; viewM = j.second
        hour = z.hour; minute = z.minute
    }

    fun at(days: Long, h: Int): Long =
        LocalDate.now(zone).plusDays(days).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    val presets = buildList {
        add("۱ ساعت دیگر" to now + 3_600_000L)
        if (at(0, 20) > now) add("امشب ۲۰:۰۰" to at(0, 20))
        add("فردا ۹:۰۰" to at(1, 9))
        add("۳ روز دیگر ۹:۰۰" to at(3, 9))
        add("هفتهٔ بعد ۹:۰۰" to at(7, 9))
    }

    val chosen = toMillis(jy, jm, jd, hour, minute)
    val valid = chosen > now

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Alarm, null, tint = cs.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Text("یادآوری", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    presets.forEach { (label, ts) ->
                        AssistChip(onClick = { applyTs(ts) }, label = { Text(label) })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                // month header
                val canPrev = viewY * 12 + viewM > today.first * 12 + today.second
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        enabled = canPrev,
                        onClick = {
                            if (viewM == 0) { viewM = 11; viewY -= 1 } else viewM -= 1
                        }
                    ) { Icon(Icons.Rounded.KeyboardArrowRight, "ماه قبل") }
                    Text(
                        "${PERSIAN_MONTHS[viewM]} ${viewY.fa()}",
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        if (viewM == 11) { viewM = 0; viewY += 1 } else viewM += 1
                    }) { Icon(Icons.Rounded.KeyboardArrowLeft, "ماه بعد") }
                }
                Row(Modifier.fillMaxWidth()) {
                    WEEK_DAYS.forEach {
                        Text(
                            it, Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium, color = cs.outline
                        )
                    }
                }
                val (offset, len) = monthInfo(viewY, viewM)
                val rows = (offset + len + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val day = r * 7 + c - offset + 1
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (day in 1..len) {
                                    val ymd = viewY * 10000 + viewM * 100 + day
                                    val tymd = today.first * 10000 + today.second * 100 + today.third
                                    val past = ymd < tymd
                                    val selected = viewY == jy && viewM == jm && day == jd
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(if (selected) cs.primary else Color.Transparent)
                                            .then(
                                                if (ymd == tymd && !selected)
                                                    Modifier.border(BorderStroke(1.dp, cs.primary), CircleShape)
                                                else Modifier
                                            )
                                            .clickable(enabled = !past) {
                                                jy = viewY; jm = viewM; jd = day
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            day.fa(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                selected -> cs.onPrimary
                                                past -> cs.outline.copy(alpha = 0.45f)
                                                else -> cs.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                // time
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Stepper(two(hour), onUp = { hour = (hour + 1) % 24 }, onDown = { hour = (hour + 23) % 24 })
                    Text(
                        " : ", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Stepper(two(minute), onUp = { minute = (minute + 5) % 60 }, onDown = { minute = (minute + 55) % 60 })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (valid) "یادآوری: " + Dates.remind(chosen) else "زمان انتخاب‌شده گذشته است",
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (valid) cs.onSurfaceVariant else cs.error
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (initial > 0) {
                        TextButton(onClick = onClear) { Text("حذف یادآوری", color = cs.error) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    Spacer(Modifier.width(6.dp))
                    Button(enabled = valid, onClick = { onConfirm(chosen) }) { Text("تأیید") }
                }
            }
        }
    }
}

@Composable
private fun Stepper(text: String, onUp: () -> Unit, onDown: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onUp) { Icon(Icons.Rounded.KeyboardArrowUp, "افزایش") }
        Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHighest) {
            Text(
                text,
                Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onDown) { Icon(Icons.Rounded.KeyboardArrowDown, "کاهش") }
    }
}
