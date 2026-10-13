package ir.kaveh.yaddashtyar.util

import android.content.Context
import ir.kaveh.yaddashtyar.data.Attachment
import java.io.File
import java.util.zip.ZipFile

/** استخراج متن فایل‌ها برای جست‌وجوی محتوایی. هرگز استثنا نمی‌دهد؛ در شکست، رشتهٔ خالی. */
object TextIndex {
    private const val MAX_CHARS = 200_000
    private var pdfReady = false

    fun extract(ctx: Context, file: File, a: Attachment): String {
        return try {
            val e = a.name.substringAfterLast('.', "").lowercase()
            val raw = when {
                FileUtil.kindOf(a) == Kind.TEXT -> if (file.length() < 2_000_000) file.readText() else ""
                e == "docx" -> zipText(file, "word/document.xml")?.let { xmlToText(it) }.orEmpty()
                e == "odt" || e == "ods" || e == "odp" -> zipText(file, "content.xml")?.let { xmlToText(it) }.orEmpty()
                e == "xlsx" -> zipText(file, "xl/sharedStrings.xml")?.let { xmlToText(it) }.orEmpty()
                e == "pdf" || a.mime == "application/pdf" -> pdfText(ctx, file)
                else -> ""
            }
            raw.replace(Regex("[ \\t\\u200c]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n").trim().take(MAX_CHARS)
        } catch (t: Throwable) {
            ""
        }
    }

    private fun zipText(file: File, entry: String): String? =
        ZipFile(file).use { z ->
            val e = z.getEntry(entry) ?: return null
            z.getInputStream(e).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

    private fun xmlToText(xml: String): String = xml
        .replace(Regex("</w:p>|</text:p>|</text:h>|<w:br/>|<text:line-break/>|</si>"), "\n")
        .replace(Regex("<w:tab/>|<text:tab/>"), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    private fun pdfText(ctx: Context, file: File): String {
        if (file.length() > 40_000_000) return ""
        if (!pdfReady) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
            pdfReady = true
        }
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
            val st = com.tom_roush.pdfbox.text.PDFTextStripper()
            st.startPage = 1
            st.endPage = minOf(doc.numberOfPages, 150)
            return st.getText(doc)
        }
    }
}
