package ir.kaveh.yaddashtyar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.kaveh.yaddashtyar.data.Attachment
import ir.kaveh.yaddashtyar.data.NoteWithFiles
import ir.kaveh.yaddashtyar.data.parseChecklist
import ir.kaveh.yaddashtyar.data.tagList
import ir.kaveh.yaddashtyar.ui.theme.noteBg
import ir.kaveh.yaddashtyar.util.Dates
import ir.kaveh.yaddashtyar.util.FileUtil
import ir.kaveh.yaddashtyar.util.Kind
import ir.kaveh.yaddashtyar.util.fa
import ir.kaveh.yaddashtyar.util.highlight
import ir.kaveh.yaddashtyar.util.snippetAround

fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.IMAGE -> Icons.Rounded.Image
    Kind.AUDIO -> Icons.Rounded.MusicNote
    Kind.VIDEO -> Icons.Rounded.Movie
    Kind.PDF -> Icons.Rounded.PictureAsPdf
    Kind.DOC, Kind.TEXT -> Icons.Rounded.Description
    Kind.ARCHIVE -> Icons.Rounded.Folder
    Kind.OTHER -> Icons.Rounded.AttachFile
}

@Composable
fun GroupHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label == PINNED_LABEL) {
            Icon(
                Icons.Rounded.PushPin, null,
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.fa(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    n: NoteWithFiles,
    terms: List<String>,
    dateOf: Long,
    inTrash: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val note = n.note
    val cs = MaterialTheme.colorScheme
    val hlColor = cs.tertiary.copy(alpha = 0.35f)
    val images = remember(n.files) { n.files.filter { FileUtil.kindOf(it) == Kind.IMAGE } }
    val others = remember(n.files) { n.files.filter { FileUtil.kindOf(it) != Kind.IMAGE } }
    val items = remember(note.checklist) { parseChecklist(note.checklist) }
    val doneCount = items.count { it.done }
    val tags = remember(note.tags) { note.tagList() }
    val snippet = remember(note.body, terms) { snippetAround(note.body, terms) }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = noteBg(note.color)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = if (note.color == 0) BorderStroke(1.dp, cs.outlineVariant) else null
    ) {
        Column {
            if (images.isNotEmpty()) {
                val shown = images.take(3)
                Row(
                    Modifier.fillMaxWidth().height(if (shown.size == 1) 150.dp else 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    shown.forEach { a ->
                        AsyncImage(
                            model = remember(a.path) { FileUtil.fileOf(ctx, a) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxWidth().height(if (shown.size == 1) 150.dp else 96.dp)
                        )
                    }
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (note.title.isBlank()) AnnotatedStringEmpty else highlight(note.title, terms, hlColor),
                        modifier = Modifier.weight(1f).padding(top = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (note.title.isBlank()) cs.outline else cs.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!inTrash) {
                        IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (note.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "سنجاق",
                                modifier = Modifier.size(19.dp),
                                tint = if (note.pinned) cs.tertiary else cs.outline
                            )
                        }
                    }
                }
                if (snippet.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        highlight(snippet, terms, hlColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    items.take(4).forEach { it0 ->
                        Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (it0.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                null, Modifier.size(17.dp),
                                tint = if (it0.done) cs.primary else cs.outline
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                highlight(it0.text, terms, hlColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (it0.done) cs.outline else cs.onSurface,
                                textDecoration = if (it0.done) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (items.size > 4) {
                        Text(
                            "+${(items.size - 4).fa()} مورد دیگر",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.outline,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { doneCount.toFloat() / items.size },
                            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            trackColor = cs.onSurface.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${doneCount.fa()}/${items.size.fa()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                if (others.isNotEmpty() || images.size > 3) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        others.take(3).forEach { a -> FileChip(a, terms, hlColor) }
                        if (others.size > 3) SmallPill("+${(others.size - 3).fa()} فایل")
                        if (images.size > 3) SmallPill("+${(images.size - 3).fa()} تصویر")
                    }
                }
                val hasMeta = note.topic.isNotBlank() || tags.isNotEmpty()
                if (hasMeta) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (note.topic.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(10.dp), color = cs.primary.copy(alpha = 0.12f)) {
                                Text(
                                    highlight(note.topic.trim(), terms, hlColor),
                                    Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.primary
                                )
                            }
                        }
                        tags.take(4).forEach { t ->
                            Text(
                                highlight("#$t", terms, hlColor),
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.primary
                            )
                        }
                        if (tags.size > 4) {
                            Text("+${(tags.size - 4).fa()}", style = MaterialTheme.typography.labelMedium, color = cs.outline)
                        }
                    }
                }
                if (note.remindAt > 0) {
                    val future = note.remindAt > System.currentTimeMillis()
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Alarm, null, Modifier.size(15.dp),
                            tint = if (future) cs.tertiary else cs.outline
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            Dates.remind(note.remindAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (future) cs.tertiary else cs.outline,
                            fontWeight = if (future) FontWeight.Medium else FontWeight.Normal,
                            textDecoration = if (future) null else TextDecoration.LineThrough
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    Dates.relative(dateOf),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.outline
                )
                if (inTrash) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onRestore) {
                            Icon(Icons.Rounded.RestoreFromTrash, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("بازیابی")
                        }
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp), tint = cs.error)
                            Spacer(Modifier.width(4.dp))
                            Text("حذف دائمی", color = cs.error)
                        }
                    }
                }
            }
        }
    }
}

private val AnnotatedStringEmpty = androidx.compose.ui.text.AnnotatedString("بدون عنوان")

@Composable
private fun FileChip(a: Attachment, terms: List<String>, hl: Color) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(10.dp), color = cs.onSurface.copy(alpha = 0.07f)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(kindIcon(FileUtil.kindOf(a)), null, Modifier.size(15.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            Text(
                highlight(a.name, terms, hl),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
    }
}

@Composable
private fun SmallPill(text: String) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(10.dp), color = cs.onSurface.copy(alpha = 0.07f)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant
        )
    }
}
