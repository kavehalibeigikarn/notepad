package ir.kaveh.yaddashtyar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.kaveh.yaddashtyar.data.Deadline
import ir.kaveh.yaddashtyar.util.Candidate
import ir.kaveh.yaddashtyar.util.Dates
import ir.kaveh.yaddashtyar.util.Extraction
import ir.kaveh.yaddashtyar.util.LegalDates
import ir.kaveh.yaddashtyar.util.basisText
import ir.kaveh.yaddashtyar.util.fa

/** One extracted deadline in the review list. */
data class Pick(val c: Candidate, val dueAt: Long?, val selected: Boolean = dueAt != null)

val OFFSET_OPTIONS = listOf(30, 14, 7, 3, 1, 0)

fun offsetLabel(o: Int): String = when (o) {
    0 -> "همان روز"
    7 -> "۱ هفته قبل"
    else -> "${o.fa()} روز قبل"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OffsetChips(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OFFSET_OPTIONS.forEach { o ->
            FilterChip(
                selected = o in selected,
                onClick = { onChange(if (o in selected) selected - o else selected + o) },
                label = { Text(offsetLabel(o)) }
            )
        }
    }
}

@Composable
fun AiSettingsDialog(
    initialKey: String,
    initialModel: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(initialKey) }
    var model by remember { mutableStateOf(initialModel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیمات هوش مصنوعی") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "برای خواندن سند و استخراج مهلت‌ها از API شخصی Claude استفاده می‌شود. کلید فقط روی همین گوشی ذخیره می‌شود.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("کلید API (sk-ant-…)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("نام مدل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "رازداری: با هر استخراج، محتوای همان سند برای تحلیل به سرور Anthropic ارسال می‌شود. پیش از ارسال اسناد موکل، با تعهد رازداری حرفه‌ای خود تطبیق دهید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key, model) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun ConsentDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ارسال سند برای تحلیل") },
        text = {
            Text(
                "برای خواندن سند، فایل انتخاب‌شده به سرویس Claude (شرکت Anthropic) ارسال می‌شود. " +
                    "اگر سند حاوی اطلاعات محرمانهٔ موکل است، پیش از ادامه مطمئن شوید این کار با تعهد رازداری شما سازگار است."
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("متوجه‌ام، ادامه") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun LoadingDialog(fileName: String, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("در حال خواندن سند و استخراج مهلت‌ها…", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    fileName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel) { Text("انصراف") }
            }
        }
    }
}

private fun confidenceLabel(c: String): String = when (c.lowercase()) {
    "high" -> "اطمینان بالا"
    "low" -> "اطمینان کم"
    else -> "اطمینان متوسط"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtractReviewDialog(
    result: Extraction,
    onSave: (List<Pick>, Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val picks: SnapshotStateList<Pick> = remember {
        result.items.map { Pick(it, LegalDates.dueFor(it)) }.toMutableStateList()
    }
    var offsets by remember { mutableStateOf(setOf(7, 3, 1, 0)) }
    var pickIndex by remember { mutableStateOf<Int?>(null) }
    val chosenCount = picks.count { it.selected && it.dueAt != null }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 640.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, null, tint = cs.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Text("مهلت‌های یافت‌شده", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (result.documentType.isNotBlank() || result.summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOf(result.documentType, result.summary).filter { it.isNotBlank() }.joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (picks.isEmpty()) {
                        Text("در این سند مهلت صریحی پیدا نشد.", Modifier.padding(vertical = 16.dp), color = cs.onSurfaceVariant)
                    }
                    picks.forEachIndexed { i, p ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cs.onSurface.copy(alpha = 0.06f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(
                                    checked = p.selected && p.dueAt != null,
                                    enabled = p.dueAt != null,
                                    onCheckedChange = { picks[i] = p.copy(selected = it) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(p.c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (p.c.quote.isNotBlank()) {
                                        Text(
                                            "«" + p.c.quote + "»",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val due = p.dueAt
                                    if (due != null) {
                                        Text(
                                            Dates.full(due) + " · " + Dates.countdown(due),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = cs.primary
                                        )
                                    } else {
                                        Text(
                                            "تاریخ مبنا در سند نیست" +
                                                (if (p.c.baseEvent.isNotBlank()) " (" + p.c.baseEvent + ")" else "") +
                                                " — تاریخ را خودتان تعیین کنید",
                                            style = MaterialTheme.typography.bodySmall, color = cs.error
                                        )
                                    }
                                    val basis = basisText(p.c)
                                    if (basis.isNotBlank()) {
                                        Text(basis, style = MaterialTheme.typography.labelSmall, color = cs.outline)
                                    }
                                    if (p.c.notes.isNotBlank()) {
                                        Text(p.c.notes, style = MaterialTheme.typography.labelSmall, color = cs.outline)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            confidenceLabel(p.c.confidence),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (p.c.confidence.equals("high", true)) cs.primary else cs.tertiary
                                        )
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { pickIndex = i }) {
                                            Text(if (due == null) "تعیین تاریخ" else "تغییر تاریخ")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (picks.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("زمان یادآوری (ساعت ۹ صبح)", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        OffsetChips(offsets) { offsets = it }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "این نتیجه پیشنهادی است و جای بررسی وکیل را نمی‌گیرد. روز ابلاغ در شمارش لحاظ نشده و تعطیلات رسمی در نظر گرفته نمی‌شوند؛ تاریخ‌ها را با متن سند و قانون تطبیق دهید.",
                            style = MaterialTheme.typography.labelSmall, color = cs.error
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(if (picks.isEmpty()) "بستن" else "انصراف") }
                    Spacer(Modifier.weight(1f))
                    if (picks.isNotEmpty()) {
                        Button(
                            enabled = chosenCount > 0 && offsets.isNotEmpty(),
                            onClick = { onSave(picks.filter { it.selected && it.dueAt != null }, offsets) }
                        ) { Text("افزودن ${chosenCount.fa()} مهلت") }
                    }
                }
            }
        }
    }

    val idx = pickIndex
    if (idx != null && idx < picks.size) {
        ReminderDialog(
            initial = picks[idx].dueAt ?: 0L,
            onConfirm = { ts ->
                picks[idx] = picks[idx].copy(dueAt = ts, selected = true)
                pickIndex = null
            },
            onClear = { pickIndex = null },
            onDismiss = { pickIndex = null },
            dateOnly = true
        )
    }
}

@Composable
fun DeadlineEditDialog(
    existing: Deadline?,
    onSave: (Deadline) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var dueAt by remember { mutableStateOf(existing?.dueAt ?: 0L) }
    var offsets by remember { mutableStateOf((existing?.offsets ?: listOf(7, 3, 1, 0)).toSet()) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "مهلت جدید" else "ویرایش مهلت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان (مثلاً مهلت تجدیدنظر)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    onClick = { picking = true },
                    shape = RoundedCornerShape(14.dp),
                    color = cs.onSurface.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Event, null, tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (dueAt > 0) Dates.full(dueAt) + " · " + Dates.countdown(dueAt) else "انتخاب تاریخ پایان مهلت",
                            color = if (dueAt > 0) cs.onSurface else cs.outline
                        )
                    }
                }
                Text("یادآوری (ساعت ۹ صبح)", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                OffsetChips(offsets) { offsets = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && dueAt > 0,
                onClick = {
                    onSave(
                        Deadline(
                            id = existing?.id ?: 0L,
                            title = title.trim(),
                            dueAt = dueAt,
                            basis = existing?.basis ?: "",
                            quote = existing?.quote ?: "",
                            offsets = offsets.sortedDescending(),
                            done = existing?.done ?: false
                        )
                    )
                }
            ) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("حذف", color = cs.error) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )

    if (picking) {
        ReminderDialog(
            initial = dueAt,
            onConfirm = { ts -> dueAt = ts; picking = false },
            onClear = { picking = false },
            onDismiss = { picking = false },
            dateOnly = true
        )
    }
}

@Composable
fun DeadlinesSection(
    deadlines: List<Deadline>,
    canExtract: Boolean,
    onAdd: () -> Unit,
    onExtract: () -> Unit,
    onEdit: (Deadline) -> Unit,
    onToggle: (Deadline) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Event, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                "مهلت‌های قانونی",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (canExtract) {
                TextButton(onClick = onExtract) { Text("استخراج از سند") }
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("افزودن")
            }
        }
        deadlines.sortedBy { it.dueAt }.forEach { d ->
            val left = Dates.daysUntil(d.dueAt)
            val urgent = !d.done && left <= 3
            Surface(
                onClick = { onEdit(d) },
                shape = RoundedCornerShape(16.dp),
                color = if (urgent) cs.error.copy(alpha = 0.12f) else cs.onSurface.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(Modifier.padding(end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(checked = d.done, onCheckedChange = { onToggle(d) })
                    Column(Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(
                            d.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (d.done) TextDecoration.LineThrough else null
                        )
                        Text(
                            Dates.full(d.dueAt) + if (d.done) " · انجام شد" else " · " + Dates.countdown(d.dueAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (urgent) cs.error else cs.onSurfaceVariant
                        )
                        if (d.basis.isNotBlank()) {
                            Text(d.basis, style = MaterialTheme.typography.labelSmall, color = cs.outline)
                        }
                        if (d.quote.isNotBlank()) {
                            Text(
                                "«" + d.quote + "»",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.outline, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (d.offsets.isNotEmpty() && !d.done) {
                            Text(
                                "یادآوری: " + d.offsets.sortedDescending().joinToString("، ") { offsetLabel(it) },
                                style = MaterialTheme.typography.labelSmall, color = cs.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
