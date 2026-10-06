package ir.kaveh.yaddashtyar.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import ir.kaveh.yaddashtyar.data.Attachment
import java.io.File
import java.util.UUID

enum class Kind { IMAGE, AUDIO, VIDEO, PDF, DOC, TEXT, ARCHIVE, OTHER }

object FileUtil {
    private val docExt = setOf("doc", "docx", "odt", "rtf", "ppt", "pptx", "xls", "xlsx", "ods", "epub")
    private val textExt = setOf(
        "txt", "md", "json", "xml", "log", "csv", "js", "ts", "py", "html", "css", "yml", "yaml",
        "ini", "mq4", "mq5", "mqh", "pine", "kt", "java", "c", "cpp", "h", "sh", "sql"
    )
    private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz")

    fun dir(ctx: Context): File = File(ctx.filesDir, "attachments").apply { mkdirs() }

    fun fileOf(ctx: Context, a: Attachment): File = File(dir(ctx), a.path)

    fun kindOf(a: Attachment): Kind {
        val m = a.mime.lowercase()
        val e = a.name.substringAfterLast('.', "").lowercase()
        return when {
            m.startsWith("image/") -> Kind.IMAGE
            m.startsWith("audio/") -> Kind.AUDIO
            m.startsWith("video/") -> Kind.VIDEO
            m == "application/pdf" || e == "pdf" -> Kind.PDF
            e in docExt -> Kind.DOC
            m.startsWith("text/") || e in textExt -> Kind.TEXT
            e in archiveExt -> Kind.ARCHIVE
            else -> Kind.OTHER
        }
    }

    /** Copies the picked document into app-private storage. Call from a background thread. */
    fun importUri(ctx: Context, uri: Uri, noteId: Long): Attachment? {
        val cr = ctx.contentResolver
        var name = "file"
        var size = 0L
        try {
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (e: Exception) {
            // ignore: fall back to defaults
        }
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..8 }
        val mime = cr.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext ?: "")
            ?: "application/octet-stream"
        val stored = UUID.randomUUID().toString() + (if (ext != null) ".$ext" else "")
        val out = File(dir(ctx), stored)
        val input = cr.openInputStream(uri) ?: return null
        try {
            input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        } catch (e: Exception) {
            out.delete()
            return null
        }
        if (size <= 0) size = out.length()
        val probe = Attachment(noteId = noteId, name = name, mime = mime, size = size, path = stored, addedAt = 0)
        val text = if (kindOf(probe) == Kind.TEXT && out.length() < 600_000) {
            runCatching { out.readText().take(200_000) }.getOrDefault("")
        } else ""
        return probe.copy(addedAt = System.currentTimeMillis(), text = text)
    }

    private fun uriFor(ctx: Context, a: Attachment): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", fileOf(ctx, a))

    fun open(ctx: Context, a: Attachment) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(ctx, a), a.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "باز کردن فایل ممکن نشد", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(ctx: Context, a: Attachment) {
        try {
            val i = Intent(Intent.ACTION_SEND)
                .setType(a.mime)
                .putExtra(Intent.EXTRA_STREAM, uriFor(ctx, a))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(i, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(ctx, "اشتراک‌گذاری ممکن نشد", Toast.LENGTH_SHORT).show()
        }
    }
}
