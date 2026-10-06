package ir.kaveh.yaddashtyar.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.kaveh.yaddashtyar.data.AppDb
import ir.kaveh.yaddashtyar.data.Attachment
import ir.kaveh.yaddashtyar.data.CheckItem
import ir.kaveh.yaddashtyar.data.Note
import ir.kaveh.yaddashtyar.data.NoteWithFiles
import ir.kaveh.yaddashtyar.data.encodeChecklist
import ir.kaveh.yaddashtyar.data.isBlankNote
import ir.kaveh.yaddashtyar.data.parseChecklist
import ir.kaveh.yaddashtyar.data.tagList
import ir.kaveh.yaddashtyar.util.Dates
import ir.kaveh.yaddashtyar.util.FileUtil
import ir.kaveh.yaddashtyar.util.Kind
import ir.kaveh.yaddashtyar.util.Reminders
import ir.kaveh.yaddashtyar.util.fa
import ir.kaveh.yaddashtyar.util.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ViewMode(val title: String) {
    ALL("همه یادداشت‌ها"), PINNED("سنجاق‌شده‌ها"), ARCHIVE("بایگانی"), TRASH("سطل زباله")
}

enum class SortMode(val label: String) {
    UPDATED("آخرین ویرایش"), CREATED("جدیدترین"), OLDEST("قدیمی‌ترین"), TITLE("عنوان (الفبا)")
}

enum class GroupMode(val label: String) {
    TIME("بر اساس زمان"), TOPIC("بر اساس موضوع"), TITLE("بر اساس عنوان"), NONE("بدون گروه‌بندی")
}

enum class FileFilter(val label: String) {
    ANY("پیوست‌دار"), IMAGE("تصویر"), MEDIA("صوت و ویدیو"), DOC("سند")
}

const val NO_TOPIC = "\u0000none"
const val PINNED_LABEL = "سنجاق‌شده"

data class Filters(
    val view: ViewMode = ViewMode.ALL,
    val topic: String? = null,
    val tag: String? = null,
    val file: FileFilter? = null,
    val reminder: Boolean = false,
    val sort: SortMode = SortMode.UPDATED,
    val group: GroupMode = GroupMode.TIME,
    val grid: Boolean = true
) {
    val active: Boolean get() = topic != null || tag != null || file != null || reminder
}

data class NoteGroup(val label: String, val notes: List<NoteWithFiles>)
data class Counts(val all: Int = 0, val pinned: Int = 0, val archive: Int = 0, val trash: Int = 0)

data class HomeState(
    val loaded: Boolean = false,
    val groups: List<NoteGroup> = emptyList(),
    val shown: Int = 0,
    val totalNotes: Int = 0,
    val counts: Counts = Counts(),
    val topics: List<Pair<String, Int>> = emptyList(),
    val tags: List<Pair<String, Int>> = emptyList(),
    val terms: List<String> = emptyList()
)

data class Draft(
    val title: String,
    val body: String,
    val topic: String,
    val tags: List<String>,
    val color: Int,
    val pinned: Boolean,
    val archived: Boolean,
    val items: List<CheckItem>,
    val remindAt: Long
)

private const val ALPHA = "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی"

private fun letterOf(title: String): String {
    val t = norm(title.trim()).trimStart { !it.isLetterOrDigit() }
    if (t.isEmpty()) return ""
    val c = when (val first = t[0]) {
        'آ', 'أ', 'إ', 'ٱ' -> 'ا'
        'ة' -> 'ه'
        'ؤ' -> 'و'
        'ئ' -> 'ی'
        else -> first
    }
    return if (c.isDigit()) "#" else c.uppercaseChar().toString()
}

private fun letterRank(l: String): Int = when {
    l.isEmpty() -> 1000
    l == "#" -> 900
    ALPHA.indexOf(l[0]) >= 0 -> ALPHA.indexOf(l[0])
    l[0] in 'A'..'Z' -> 100 + (l[0] - 'A')
    else -> 500
}

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val dao = AppDb.get(ctx).dao()
    private val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** All note mutations run in order on one worker, so quick sequences can't race each other. */
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (job in queue) {
                try {
                    job()
                } catch (e: Exception) {
                    // keep the worker alive
                }
            }
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        queue.trySend(block)
    }

    val query = MutableStateFlow("")
    val filters = MutableStateFlow(
        Filters(
            sort = runCatching { SortMode.valueOf(prefs.getString("sort", "UPDATED") ?: "UPDATED") }
                .getOrDefault(SortMode.UPDATED),
            group = runCatching { GroupMode.valueOf(prefs.getString("group", "TIME") ?: "TIME") }
                .getOrDefault(GroupMode.TIME),
            grid = prefs.getBoolean("grid", true)
        )
    )

    /** One-shot snackbar messages. */
    val events = Channel<String>(Channel.BUFFERED)

    /** Set when a note should be opened (share intent, notification tap). */
    val pendingOpen = MutableStateFlow<Long?>(null)

    val state: StateFlow<HomeState> =
        combine(dao.observeAll(), filters, query) { all, f, q -> build(all, f, q) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    // ---------- filters ----------
    fun setQuery(q: String) { query.value = q }

    fun setView(v: ViewMode) = filters.update { it.copy(view = v, topic = null, tag = null) }

    private fun leaveHiddenViews(f: Filters): ViewMode =
        if (f.view == ViewMode.TRASH || f.view == ViewMode.ARCHIVE) ViewMode.ALL else f.view

    fun setTopic(t: String?) = filters.update { it.copy(topic = t, tag = null, view = leaveHiddenViews(it)) }
    fun setTag(t: String?) = filters.update { it.copy(tag = t, topic = null, view = leaveHiddenViews(it)) }
    fun toggleFile(ff: FileFilter) = filters.update { it.copy(file = if (it.file == ff) null else ff) }
    fun toggleReminder() = filters.update { it.copy(reminder = !it.reminder, view = leaveHiddenViews(it)) }
    fun clearFilters() = filters.update { it.copy(topic = null, tag = null, file = null, reminder = false) }

    fun setSort(s: SortMode) {
        prefs.edit().putString("sort", s.name).apply()
        filters.update { it.copy(sort = s) }
    }

    fun setGroup(g: GroupMode) {
        prefs.edit().putString("group", g.name).apply()
        filters.update { it.copy(group = g) }
    }

    fun setGrid(v: Boolean) {
        prefs.edit().putBoolean("grid", v).apply()
        filters.update { it.copy(grid = v) }
    }

    // ---------- list building ----------
    private fun hay(n: NoteWithFiles): String = norm(buildString {
        append(n.note.title).append('\n').append(n.note.body).append('\n')
        append(n.note.topic).append('\n').append(n.note.tags).append('\n')
        parseChecklist(n.note.checklist).forEach { append(it.text).append('\n') }
        n.files.forEach { append(it.name).append('\n').append(it.text).append('\n') }
    })

    private fun comparator(s: SortMode): Comparator<NoteWithFiles> = when (s) {
        SortMode.UPDATED -> compareByDescending<NoteWithFiles> { it.note.updatedAt }
        SortMode.CREATED -> compareByDescending<NoteWithFiles> { it.note.createdAt }
        SortMode.OLDEST -> compareBy<NoteWithFiles> { it.note.createdAt }
        SortMode.TITLE -> Comparator<NoteWithFiles> { a, b ->
            val ta = a.note.title.trim()
            val tb = b.note.title.trim()
            when {
                ta.isEmpty() && tb.isNotEmpty() -> 1
                ta.isNotEmpty() && tb.isEmpty() -> -1
                else -> ta.compareTo(tb, ignoreCase = true)
            }
        }
    }

    private fun build(allRaw: List<NoteWithFiles>, f: Filters, q: String): HomeState {
        val all = allRaw.filter { !it.isBlankNote() }
        val active = all.filter { !it.note.trashed && !it.note.archived }
        val counts = Counts(
            all = active.size,
            pinned = active.count { it.note.pinned },
            archive = all.count { !it.note.trashed && it.note.archived },
            trash = all.count { it.note.trashed }
        )
        val topics = active
            .filter { it.note.topic.isNotBlank() }
            .groupBy { norm(it.note.topic.trim()) }
            .map { (_, v) -> v.first().note.topic.trim() to v.size }
            .sortedBy { it.first }
        val tags = active
            .flatMap { n -> n.note.tagList() }
            .groupBy { norm(it) }
            .map { (_, v) -> v.first() to v.size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

        var list = all.filter { n ->
            val x = n.note
            when (f.view) {
                ViewMode.TRASH -> x.trashed
                ViewMode.ARCHIVE -> !x.trashed && x.archived
                ViewMode.PINNED -> !x.trashed && !x.archived && x.pinned
                ViewMode.ALL -> !x.trashed && !x.archived
            }
        }
        val ft = f.topic
        if (ft != null) {
            list = list.filter {
                if (ft == NO_TOPIC) it.note.topic.isBlank() else norm(it.note.topic.trim()) == norm(ft)
            }
        }
        val fg = f.tag
        if (fg != null) {
            list = list.filter { n -> n.note.tagList().any { norm(it) == norm(fg) } }
        }
        if (f.reminder) list = list.filter { it.note.remindAt > 0 }
        val ff = f.file
        if (ff != null) {
            list = list.filter { n ->
                val kinds = n.files.map { FileUtil.kindOf(it) }
                when (ff) {
                    FileFilter.ANY -> kinds.isNotEmpty()
                    FileFilter.IMAGE -> Kind.IMAGE in kinds
                    FileFilter.MEDIA -> kinds.any { it == Kind.AUDIO || it == Kind.VIDEO }
                    FileFilter.DOC -> kinds.any { it == Kind.PDF || it == Kind.DOC || it == Kind.TEXT }
                }
            }
        }
        val tokens = norm(q).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
            list = list.filter { n ->
                val h = hay(n)
                tokens.all { t ->
                    if (t.startsWith("#")) {
                        val g = t.drop(1)
                        g.isEmpty() || n.note.tagList().any { norm(it).contains(g) }
                    } else h.contains(t)
                }
            }
        }
        val terms = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && !it.startsWith("#") }
        return HomeState(
            loaded = true,
            groups = group(list, f),
            shown = list.size,
            totalNotes = all.size,
            counts = counts,
            topics = topics,
            tags = tags,
            terms = terms
        )
    }

    private fun group(list: List<NoteWithFiles>, f: Filters): List<NoteGroup> {
        val ordered = list.sortedWith(
            compareByDescending<NoteWithFiles> { it.note.pinned }.then(comparator(f.sort))
        )
        if (f.group == GroupMode.TOPIC) {
            val m = LinkedHashMap<String, MutableList<NoteWithFiles>>()
            ordered.forEach { m.getOrPut(norm(it.note.topic.trim())) { mutableListOf() }.add(it) }
            return m.entries
                .sortedWith(compareBy<Map.Entry<String, MutableList<NoteWithFiles>>> { it.key.isEmpty() }.thenBy { it.key })
                .map { e ->
                    NoteGroup(
                        if (e.key.isEmpty()) "بدون موضوع" else e.value.first().note.topic.trim(),
                        e.value
                    )
                }
        }
        if (f.group == GroupMode.TITLE) {
            val byTitle = list.sortedWith(
                compareByDescending<NoteWithFiles> { it.note.pinned }.then(comparator(SortMode.TITLE))
            )
            val m = LinkedHashMap<String, MutableList<NoteWithFiles>>()
            byTitle.forEach { m.getOrPut(letterOf(it.note.title)) { mutableListOf() }.add(it) }
            return m.entries
                .sortedBy { letterRank(it.key) }
                .map { e -> NoteGroup(if (e.key.isEmpty()) "بدون عنوان" else e.key, e.value) }
        }
        val pinned = if (f.view == ViewMode.TRASH) emptyList() else ordered.filter { it.note.pinned }
        val rest = ordered.filter { n -> pinned.none { it.note.id == n.note.id } }
        val result = ArrayList<NoteGroup>()
        if (pinned.isNotEmpty()) result.add(NoteGroup(PINNED_LABEL, pinned))
        if (f.group == GroupMode.NONE) {
            if (rest.isNotEmpty()) result.add(NoteGroup(if (pinned.isNotEmpty()) "سایر" else "", rest))
            return result
        }
        val byCreated = f.sort == SortMode.CREATED || f.sort == SortMode.OLDEST
        val m = LinkedHashMap<String, MutableList<NoteWithFiles>>()
        val newest = HashMap<String, Long>()
        for (n in rest) {
            val t = if (byCreated) n.note.createdAt else n.note.updatedAt
            val label = Dates.bucket(t)
            m.getOrPut(label) { mutableListOf() }.add(n)
            newest[label] = maxOf(newest[label] ?: 0L, t)
        }
        val sorted = m.entries.sortedBy { newest[it.key] ?: 0L }
            .let { if (f.sort == SortMode.OLDEST) it else it.reversed() }
        sorted.forEach { result.add(NoteGroup(it.key, it.value)) }
        return result
    }

    // ---------- notes ----------
    fun observeNote(id: Long) = dao.observe(id)

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val f = filters.value
            val id = withContext(Dispatchers.IO) {
                dao.insert(
                    Note(
                        topic = f.topic?.takeIf { it != NO_TOPIC } ?: "",
                        tags = f.tag ?: "",
                        pinned = f.view == ViewMode.PINNED
                    )
                )
            }
            onCreated(id)
        }
    }

    fun createFromShare(text: String?, subject: String?, uris: List<Uri>) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val nid = dao.insert(Note(title = subject.orEmpty(), body = text.orEmpty()))
                for (u in uris) {
                    FileUtil.importUri(ctx, u, nid)?.let { dao.insertFile(it) }
                }
                nid
            }
            pendingOpen.value = id
        }
    }

    private suspend fun saveDraftNow(id: Long, d: Draft) {
        val cur = dao.get(id) ?: return
        val tags = d.tags.joinToString("\n")
        val cl = encodeChecklist(d.items)
        val topic = d.topic.trim()
        if (cur.title == d.title && cur.body == d.body && cur.topic == topic && cur.tags == tags &&
            cur.checklist == cl && cur.color == d.color && cur.pinned == d.pinned &&
            cur.archived == d.archived && cur.remindAt == d.remindAt
        ) return
        dao.update(
            cur.copy(
                title = d.title, body = d.body, checklist = cl, topic = topic, tags = tags,
                color = d.color, pinned = d.pinned, archived = d.archived, remindAt = d.remindAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (cur.remindAt != d.remindAt) {
            Reminders.schedule(ctx, id, if (cur.trashed) 0L else d.remindAt)
        }
    }

    fun saveDraft(id: Long, d: Draft) = enqueue { saveDraftNow(id, d) }

    private suspend fun removeNote(id: Long) {
        Reminders.cancel(ctx, id)
        Reminders.cancelNotification(ctx, id)
        dao.filesOf(id).forEach { File(FileUtil.dir(ctx), it.path).delete() }
        dao.deleteNote(id)
    }

    fun closeEditor(id: Long, d: Draft?) = enqueue {
        if (d != null) saveDraftNow(id, d)
        val n = dao.get(id)
        if (n != null &&
            n.title.isBlank() && n.body.isBlank() && n.checklist.isBlank() &&
            n.topic.isBlank() && n.tags.isBlank() && dao.filesOf(id).isEmpty()
        ) removeNote(id)
    }

    fun trashFromEditor(id: Long, d: Draft?) = enqueue {
        if (d != null) saveDraftNow(id, d)
        val n = dao.get(id)
        if (n != null) {
            dao.update(n.copy(trashed = true, pinned = false))
            Reminders.cancel(ctx, id)
            events.trySend("به سطل زباله منتقل شد")
        }
    }

    private fun edit(id: Long, change: (Note) -> Note) = enqueue {
        dao.get(id)?.let { dao.update(change(it)) }
    }

    fun setPinned(id: Long, v: Boolean) = edit(id) { it.copy(pinned = v) }

    fun restore(id: Long) = enqueue {
        val n = dao.get(id)
        if (n != null) {
            dao.update(n.copy(trashed = false))
            Reminders.schedule(ctx, id, n.remindAt)
        }
    }

    fun setArchived(id: Long, v: Boolean) = edit(id) { it.copy(archived = v, pinned = if (v) false else it.pinned) }

    fun deletePermanently(id: Long) = enqueue { removeNote(id) }

    fun emptyTrash() = enqueue {
        dao.allNotes().filter { it.trashed }.forEach { removeNote(it.id) }
        events.trySend("سطل زباله خالی شد")
    }

    fun duplicate(id: Long, d: Draft?) = enqueue {
        if (d != null) saveDraftNow(id, d)
        val n = dao.get(id)
        if (n != null) {
            val now = System.currentTimeMillis()
            val newId = dao.insert(
                n.copy(
                    id = 0,
                    title = (n.title.ifBlank { "بدون عنوان" }) + " (کپی)",
                    pinned = false, remindAt = 0, createdAt = now, updatedAt = now
                )
            )
            for (a in dao.filesOf(id)) {
                val src = File(FileUtil.dir(ctx), a.path)
                if (!src.exists()) continue
                val ext = a.path.substringAfterLast('.', "")
                val name = UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else ""
                runCatching { src.copyTo(File(FileUtil.dir(ctx), name)) }
                    .onSuccess { dao.insertFile(a.copy(id = 0, noteId = newId, path = name)) }
            }
            events.trySend("کپی ساخته شد")
        }
    }

    // ---------- attachments ----------
    private suspend fun touch(noteId: Long) {
        dao.get(noteId)?.let { dao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun addFiles(noteId: Long, uris: List<Uri>) = enqueue {
        var ok = 0
        var fail = 0
        for (u in uris) {
            val a = FileUtil.importUri(ctx, u, noteId)
            if (a != null) {
                dao.insertFile(a)
                ok++
            } else fail++
        }
        if (ok > 0) touch(noteId)
        if (fail > 0) events.trySend("افزودن ${fail.fa()} فایل ناموفق بود")
    }

    /** Registers a file that already lives in the attachment folder (camera / voice recorder). */
    fun addLocalFile(noteId: Long, file: File, name: String, mime: String) = enqueue {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
        } else {
            dao.insertFile(
                Attachment(
                    noteId = noteId, name = name, mime = mime, size = file.length(),
                    path = file.name, addedAt = System.currentTimeMillis()
                )
            )
            touch(noteId)
        }
    }

    fun removeFile(a: Attachment) = enqueue {
        File(FileUtil.dir(ctx), a.path).delete()
        dao.deleteFile(a)
        touch(a.noteId)
    }

    // ---------- backup ----------
    fun exportTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notes = dao.allNotes()
                val files = dao.allFiles()
                val byNote = files.groupBy { it.noteId }
                val arr = JSONArray()
                for (n in notes) {
                    val o = JSONObject()
                        .put("title", n.title).put("body", n.body).put("checklist", n.checklist)
                        .put("topic", n.topic).put("tags", n.tags).put("color", n.color)
                        .put("pinned", n.pinned).put("archived", n.archived).put("trashed", n.trashed)
                        .put("remindAt", n.remindAt)
                        .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt)
                    val fa = JSONArray()
                    byNote[n.id]?.forEach { a ->
                        fa.put(
                            JSONObject().put("name", a.name).put("mime", a.mime).put("size", a.size)
                                .put("path", a.path).put("addedAt", a.addedAt).put("text", a.text)
                        )
                    }
                    o.put("files", fa)
                    arr.put(o)
                }
                val root = JSONObject().put("app", "yaddasht-yar").put("version", 2).put("notes", arr)
                val os = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("no stream")
                ZipOutputStream(os.buffered()).use { z ->
                    z.putNextEntry(ZipEntry("notes.json"))
                    z.write(root.toString().toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                    val dir = FileUtil.dir(ctx)
                    for (a in files) {
                        val f = File(dir, a.path)
                        if (f.exists()) {
                            z.putNextEntry(ZipEntry("files/" + a.path))
                            f.inputStream().use { it.copyTo(z) }
                            z.closeEntry()
                        }
                    }
                }
                events.trySend("فایل پشتیبان ذخیره شد")
            } catch (e: Exception) {
                events.trySend("پشتیبان‌گیری ناموفق بود")
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = FileUtil.dir(ctx)
                var json: String? = null
                val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
                ZipInputStream(input.buffered()).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val name = e.name
                        if (name == "notes.json") {
                            json = z.readBytes().toString(Charsets.UTF_8)
                        } else if (name.startsWith("files/")) {
                            val f = File(dir, File(name).name)
                            if (!f.exists()) f.outputStream().use { out -> z.copyTo(out) }
                        }
                        z.closeEntry()
                        e = z.nextEntry
                    }
                }
                val root = JSONObject(json ?: throw IOException("no notes.json"))
                val arr = root.getJSONArray("notes")
                val existing = dao.allNotes().map { it.createdAt to it.title }.toSet()
                var added = 0
                val now = System.currentTimeMillis()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val created = o.optLong("createdAt")
                    val title = o.optString("title")
                    if ((created to title) in existing) continue
                    val trashed = o.optBoolean("trashed")
                    val remindAt = o.optLong("remindAt")
                    val id = dao.insert(
                        Note(
                            title = title, body = o.optString("body"), checklist = o.optString("checklist"),
                            topic = o.optString("topic"), tags = o.optString("tags"), color = o.optInt("color"),
                            pinned = o.optBoolean("pinned"), archived = o.optBoolean("archived"),
                            trashed = trashed, remindAt = remindAt, createdAt = created,
                            updatedAt = o.optLong("updatedAt", created)
                        )
                    )
                    if (!trashed && remindAt > now) Reminders.schedule(ctx, id, remindAt)
                    val fa = o.optJSONArray("files")
                    if (fa != null) {
                        for (j in 0 until fa.length()) {
                            val fo = fa.getJSONObject(j)
                            dao.insertFile(
                                Attachment(
                                    noteId = id, name = fo.optString("name"), mime = fo.optString("mime"),
                                    size = fo.optLong("size"), path = File(fo.optString("path")).name,
                                    addedAt = fo.optLong("addedAt"), text = fo.optString("text")
                                )
                            )
                        }
                    }
                    added++
                }
                events.trySend("${added.fa()} یادداشت بازیابی شد")
            } catch (e: Exception) {
                events.trySend("فایل پشتیبان معتبر نیست")
            }
        }
    }
}
