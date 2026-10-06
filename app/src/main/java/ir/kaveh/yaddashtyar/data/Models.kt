package ir.kaveh.yaddashtyar.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

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
        note.topic.isBlank() && note.tags.isBlank() && files.isEmpty()
