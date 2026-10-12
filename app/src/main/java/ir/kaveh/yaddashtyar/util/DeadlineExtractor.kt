package ir.kaveh.yaddashtyar.util

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.GregorianCalendar
import android.icu.util.ULocale
import android.util.Base64
import ir.kaveh.yaddashtyar.data.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/** A calendar date exactly as written in a document (never converted by the model). */
data class JDate(val calendar: String, val y: Int, val m: Int, val d: Int)

data class Candidate(
    val title: String,
    val type: String,
    val quote: String,
    val baseEvent: String,
    val baseDate: JDate?,
    val periodDays: Int?,
    val periodMonths: Int?,
    val explicitDue: JDate?,
    val confidence: String,
    val notes: String
)

data class Extraction(val documentType: String, val summary: String, val items: List<Candidate>)

sealed class ExtractOutcome {
    data class Ok(val result: Extraction) : ExtractOutcome()
    data class Fail(val message: String) : ExtractOutcome()
}

/** Date arithmetic is done here (ICU calendars), never by the language model. */
object LegalDates {
    private val PERSIAN = ULocale("fa_IR@calendar=persian")

    private fun calFor(d: JDate): Calendar =
        if (d.calendar == "gregorian") GregorianCalendar() else Calendar.getInstance(PERSIAN)

    private fun setDate(c: Calendar, d: JDate) {
        c.clear()
        c.set(Calendar.YEAR, d.y)
        c.set(Calendar.MONTH, d.m - 1)
        c.set(Calendar.DAY_OF_MONTH, d.d)
        c.set(Calendar.HOUR_OF_DAY, 12)
    }

    fun noon(d: JDate): Long {
        val c = calFor(d)
        setDate(c, d)
        return c.timeInMillis
    }

    /** Months are added first, then days; the notification day itself is not counted. */
    fun addPeriod(base: JDate, days: Int?, months: Int?): Long {
        val c = calFor(base)
        setDate(c, base)
        if (months != null && months > 0) c.add(Calendar.MONTH, months)
        if (days != null && days > 0) c.add(Calendar.DAY_OF_MONTH, days)
        return c.timeInMillis
    }

    fun dueFor(c: Candidate): Long? {
        c.explicitDue?.let { return noon(it) }
        val b = c.baseDate
        if (b != null && ((c.periodDays ?: 0) > 0 || (c.periodMonths ?: 0) > 0)) {
            return addPeriod(b, c.periodDays, c.periodMonths)
        }
        return null
    }

    fun fmt(d: JDate): String = "${d.y}/${d.m}/${d.d}".faDigits()
}

fun periodText(c: Candidate): String {
    val parts = ArrayList<String>()
    if ((c.periodMonths ?: 0) > 0) parts.add("${c.periodMonths!!.fa()} ماه")
    if ((c.periodDays ?: 0) > 0) parts.add("${c.periodDays!!.fa()} روز")
    return parts.joinToString(" و ")
}

fun basisText(c: Candidate): String {
    val sb = StringBuilder()
    if (c.baseEvent.isNotBlank()) {
        sb.append("مبنا: ").append(c.baseEvent)
        c.baseDate?.let { sb.append(" (").append(LegalDates.fmt(it)).append(")") }
    }
    val p = periodText(c)
    if (p.isNotEmpty()) {
        if (sb.isNotEmpty()) sb.append("؛ ")
        sb.append("مهلت: ").append(p)
    }
    return sb.toString()
}

object DeadlineExtractor {
    const val DEFAULT_MODEL = "claude-sonnet-5-5"
    private const val MAX_BYTES = 20L * 1024 * 1024

    private const val SYSTEM = """You are an assistant for lawyers. You read a legal document (court decision, notification/ابلاغیه, contract, formal notice, petition, etc., usually in Persian) and extract every LEGAL DEADLINE or date-bound obligation it mentions, so that the lawyer can be reminded in time.

Rules:
- Only extract deadlines that the document states explicitly (for example "ظرف بیست روز از تاریخ ابلاغ قابل تجدیدنظر است" or "جلسه رسیدگی در تاریخ ۱۴۰۴/۰۷/۱۵") or that follow directly from a rule written in the document. Do NOT invent deadlines from your general legal knowledge and do NOT guess dates.
- Typical items: appeal / objection windows (اعتراض، تجدیدنظر، واخواهی، فرجام‌خواهی)، hearing dates (جلسه دادرسی، وقت رسیدگی)، payment due dates، response or compliance deadlines (پاسخ، ارائه لایحه، رفع نقص، اجرای رأی)، contract expiry or notice dates.
- Persian documents usually use Jalali dates such as ۱۴۰۴/۰۷/۱۵. Report every date exactly as written: calendar "jalali" with numeric y, m, d. Use "gregorian" only when the document itself uses Gregorian dates. NEVER convert between calendars yourself. Convert Persian digits to ordinary integers.
- If the deadline is a period counted from an event (for example "20 days from notification"), return baseEvent (short Persian name of the event, e.g. "ابلاغ رأی"), baseDate (the date of that event if the document states it, otherwise null) and periodDays or periodMonths ("بیست روز" = periodDays 20, "یک ماه" = periodMonths 1, "دو ماه" = periodMonths 2). If the document itself states the final date, return it as explicitDueDate (and also the period if stated).
- If the date from which the period runs is not in the document, set baseDate to null. Do not use the document's own date as baseDate unless the text says the period runs from it.
- title: a short Persian label such as "مهلت تجدیدنظرخواهی" or "جلسه رسیدگی".
- quote: the exact sentence or phrase from the document that supports the item (max 220 characters, original language).
- confidence: "high" when explicit and unambiguous, "medium" when some interpretation is needed, "low" when unclear.
- notes: a short Persian caveat if relevant, otherwise an empty string.
- If there are no deadlines, return an empty items array.

Return ONLY one JSON object, with no markdown fences and no commentary, in exactly this shape:
{"documentType":"string","summary":"one short Persian sentence","items":[{"type":"appeal|hearing|payment|response|other","title":"string","quote":"string","baseEvent":"string","baseDate":{"calendar":"jalali","y":1404,"m":7,"d":15},"periodDays":20,"periodMonths":null,"explicitDueDate":null,"confidence":"high","notes":""}]}"""

    suspend fun extract(ctx: Context, file: File, a: Attachment, apiKey: String, model: String): ExtractOutcome =
        withContext(Dispatchers.IO) {
            try {
                call(file, a, apiKey, model.ifBlank { DEFAULT_MODEL })
            } catch (e: IOException) {
                ExtractOutcome.Fail("اتصال اینترنت برقرار نیست یا سرور پاسخ نداد")
            } catch (e: Exception) {
                ExtractOutcome.Fail("خطا در پردازش سند: " + (e.message ?: e.javaClass.simpleName))
            }
        }

    private fun b64(f: File): String = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)

    private fun imageMime(a: Attachment): String? {
        val m = a.mime.lowercase()
        if (m in setOf("image/jpeg", "image/png", "image/gif", "image/webp")) return m
        return when (a.name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> null
        }
    }

    private fun zipText(file: File, entry: String): String? {
        ZipFile(file).use { z ->
            val e = z.getEntry(entry) ?: return null
            return z.getInputStream(e).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun xmlToText(xml: String): String = xml
        .replace(Regex("</w:p>|</text:p>|</text:h>|<w:br/>|<text:line-break/>"), "\n")
        .replace(Regex("<w:tab/>|<text:tab/>"), "\t")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    private fun readText(file: File, a: Attachment): String? {
        val e = a.name.substringAfterLast('.', "").lowercase()
        return when {
            e == "docx" -> zipText(file, "word/document.xml")?.let { xmlToText(it) }
            e == "odt" -> zipText(file, "content.xml")?.let { xmlToText(it) }
            FileUtil.kindOf(a) == Kind.TEXT -> file.readText()
            else -> null
        }
    }

    private fun dateOf(o: JSONObject?): JDate? {
        if (o == null) return null
        val y = o.optInt("y")
        val m = o.optInt("m")
        val d = o.optInt("d")
        if (y < 1 || m !in 1..12 || d !in 1..31) return null
        var cal = if (o.optString("calendar", "jalali").lowercase().startsWith("g")) "gregorian" else "jalali"
        // guard against a mislabelled calendar
        if (cal == "jalali" && y > 1700) cal = "gregorian"
        if (cal == "gregorian" && y in 1200..1600) cal = "jalali"
        return JDate(cal, y, m, d)
    }

    private fun intOrNull(o: JSONObject, k: String): Int? =
        if (o.has(k) && !o.isNull(k)) o.optInt(k).takeIf { it > 0 } else null

    private fun parse(reply: String): ExtractOutcome {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return ExtractOutcome.Fail("پاسخ مدل قابل خواندن نبود؛ دوباره تلاش کنید")
        val root = try {
            JSONObject(reply.substring(start, end + 1))
        } catch (e: Exception) {
            return ExtractOutcome.Fail("پاسخ مدل قابل خواندن نبود؛ دوباره تلاش کنید")
        }
        val arr = root.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<Candidate>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            items.add(
                Candidate(
                    title = o.optString("title").ifBlank { "مهلت" },
                    type = o.optString("type"),
                    quote = o.optString("quote"),
                    baseEvent = o.optString("baseEvent"),
                    baseDate = dateOf(o.optJSONObject("baseDate")),
                    periodDays = intOrNull(o, "periodDays"),
                    periodMonths = intOrNull(o, "periodMonths"),
                    explicitDue = dateOf(o.optJSONObject("explicitDueDate")),
                    confidence = o.optString("confidence", "medium"),
                    notes = o.optString("notes")
                )
            )
        }
        return ExtractOutcome.Ok(
            Extraction(root.optString("documentType"), root.optString("summary"), items)
        )
    }

    private fun call(file: File, a: Attachment, apiKey: String, model: String): ExtractOutcome {
        if (apiKey.isBlank()) return ExtractOutcome.Fail("کلید API تنظیم نشده است")
        if (!file.exists()) return ExtractOutcome.Fail("فایل پیدا نشد")
        val content = JSONArray()
        when (FileUtil.kindOf(a)) {
            Kind.PDF -> {
                if (file.length() > MAX_BYTES) return ExtractOutcome.Fail("حجم PDF بیش از ۲۰ مگابایت است")
                content.put(
                    JSONObject().put("type", "document").put(
                        "source",
                        JSONObject().put("type", "base64").put("media_type", "application/pdf").put("data", b64(file))
                    )
                )
            }
            Kind.IMAGE -> {
                val mt = imageMime(a) ?: return ExtractOutcome.Fail("فرمت تصویر پشتیبانی نمی‌شود (JPG، PNG، WebP)")
                if (file.length() > MAX_BYTES) return ExtractOutcome.Fail("حجم تصویر بیش از ۲۰ مگابایت است")
                content.put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject().put("type", "base64").put("media_type", mt).put("data", b64(file))
                    )
                )
            }
            else -> {
                val text = readText(file, a)
                    ?: return ExtractOutcome.Fail("این فرمت قابل خواندن نیست؛ سند را به PDF یا DOCX تبدیل کنید")
                if (text.isBlank()) return ExtractOutcome.Fail("متنی در سند پیدا نشد")
                content.put(JSONObject().put("type", "text").put("text", "متن سند:\n" + text.take(300_000)))
            }
        }
        val now = System.currentTimeMillis()
        content.put(
            JSONObject().put("type", "text").put(
                "text",
                "تاریخ امروز: " + Dates.full(now) + " (میلادی: " +
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now)) +
                    "). سند بالا را بخوان و مهلت‌های قانونی را طبق قواعد استخراج کن."
            )
        )
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4000)
            .put("system", SYSTEM)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30_000
        conn.readTimeout = 180_000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                } catch (e: Exception) {
                    null
                }
                return ExtractOutcome.Fail(
                    when (code) {
                        401 -> "کلید API نامعتبر است"
                        403 -> "دسترسی با این کلید مجاز نیست"
                        404 -> "مدل «$model» پیدا نشد؛ نام مدل را در تنظیمات بررسی کنید"
                        413 -> "فایل برای ارسال بزرگ است"
                        429 -> "سقف درخواست یا اعتبار حساب تمام شده است؛ کمی بعد دوباره امتحان کنید"
                        in 500..599 -> "سرور موقتاً در دسترس نیست؛ دوباره تلاش کنید"
                        else -> "خطای $code" + if (!msg.isNullOrBlank()) ": $msg" else ""
                    }
                )
            }
            val blocks = JSONObject(text).optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until blocks.length()) {
                val b = blocks.optJSONObject(i) ?: continue
                if (b.optString("type") == "text") sb.append(b.optString("text"))
            }
            return parse(sb.toString())
        } finally {
            conn.disconnect()
        }
    }
}
