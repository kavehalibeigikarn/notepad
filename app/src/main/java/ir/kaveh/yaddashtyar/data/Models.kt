package ir.kaveh.yaddashtyar.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val checklist: String = "",
    val topic: String = "",
    val tags: String = "",
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val remindAt: Long = 0,
    val deadlines: String = "",
    val history: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val name: String,
    val mime: String,
    val size: Long,
    val path: String,
    val addedAt: Long,
    val text: String = ""
)

data class NoteWithFiles(
    @Embedded val note: Note,
    @Relation(parentColumn = "id", entityColumn = "noteId") val files: List<Attachment>
)

data class CheckItem(val id: Long, val text: String, val done: Boolean)

fun Note.tagList(): List<String> =
    if (tags.isBlank()) emptyList() else tags.split("\n").filter { it.isNotBlank() }

fun parseChecklist(s: String): List<CheckItem> =
    if (s.isEmpty()) emptyList()
    else s.split("\n").mapIndexed { i, l -> CheckItem(i.toLong(), l.drop(1), l.startsWith("1")) }

fun encodeChecklist(items: List<CheckItem>): String =
    items.filter { it.text.isNotBlank() }
        .joinToString("\n") { (if (it.done) "1" else "0") + it.text.replace("\n", " ") }

fun NoteWithFiles.isBlankNote(): Boolean =
    note.title.isBlank() && note.body.isBlank() && note.checklist.isBlank() &&
        note.topic.isBlank() && note.tags.isBlank() && files.isEmpty() && note.deadlines.isBlank()

/** A legal / hard deadline attached to a note. [dueAt] is local noon of the due day. */
data class Deadline(
    val id: Long,
    val title: String,
    val dueAt: Long,
    val basis: String = "",
    val quote: String = "",
    val offsets: List<Int> = listOf(7, 3, 1, 0),
    val done: Boolean = false
)

fun parseDeadlines(s: String): List<Deadline> {
    if (s.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(s)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val offs = o.optJSONArray("offsets")
            Deadline(
                id = o.optLong("id"),
                title = o.optString("title"),
                dueAt = o.optLong("dueAt"),
                basis = o.optString("basis"),
                quote = o.optString("quote"),
                offsets = if (offs == null) emptyList() else (0 until offs.length()).map { offs.getInt(it) },
                done = o.optBoolean("done")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun encodeDeadlines(list: List<Deadline>): String {
    if (list.isEmpty()) return ""
    val arr = JSONArray()
    list.forEach { d ->
        arr.put(
            JSONObject().put("id", d.id).put("title", d.title).put("dueAt", d.dueAt)
                .put("basis", d.basis).put("quote", d.quote)
                .put("offsets", JSONArray(d.offsets)).put("done", d.done)
        )
    }
    return arr.toString()
}

fun Note.deadlineList(): List<Deadline> = parseDeadlines(deadlines)

fun Note.nearestOpenDeadline(): Deadline? = deadlineList().filter { !it.done }.minByOrNull { it.dueAt }

/** Update stamps (millis, oldest first): one per calendar day the note content was edited. */
fun Note.historyList(): List<Long> = history.split("\n").mapNotNull { it.toLongOrNull() }

/** Edits on the creation day are not logged (creation date covers it); same-day edits keep the latest time. */
fun appendHistory(history: String, createdAt: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    fun day(ts: Long) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val list = history.split("\n").mapNotNull { it.toLongOrNull() }.toMutableList()
    val lastDay = if (list.isEmpty()) day(createdAt) else day(list.last())
    if (day(now) == lastDay) {
        if (list.isNotEmpty()) list[list.size - 1] = now
    } else {
        list.add(now)
    }
    return list.takeLast(100).joinToString("\n")
}
