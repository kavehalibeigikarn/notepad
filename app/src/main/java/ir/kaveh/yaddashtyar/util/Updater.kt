package ir.kaveh.yaddashtyar.util

import android.content.Context
import ir.kaveh.yaddashtyar.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class UpdateLevel {
    /** نسخهٔ فعلی به‌روز است یا اطلاعاتی در دسترس نیست */
    OK,
    /** نسخهٔ جدید آمده؛ هنوز مهلت دارید */
    AVAILABLE,
    /** مهلت تمام شده؛ ساخت یادداشت/پیوست/ضبط جدید غیرفعال است (دیدن و خروجی گرفتن آزاد) */
    LIMITED,
    /** نسخه خیلی قدیمی است؛ فقط صفحهٔ آپدیت و گرفتن پشتیبان */
    BLOCKED
}

data class UpdateStatus(
    val level: UpdateLevel = UpdateLevel.OK,
    val latestName: String = "",
    val daysLeft: Int = 0,
    val url: String = Cfg.STORE_URL,
    val message: String = ""
)

/**
 * فایل version.json روی سرور:
 * { "latestVersionCode": 5, "latestVersionName": "1.4", "releasedAt": "2026-11-01",
 *   "graceDays": 30, "minSupportedVersionCode": 3, "storeUrl": "...", "message": "..." }
 * داده‌های کاربر در هیچ حالتی پاک یا قفل نمی‌شود؛ فقط قابلیت‌های جدید محدود می‌شود.
 */
object Updater {
    private const val P = "update"
    private const val DAY = 24L * 60 * 60 * 1000

    fun compute(ctx: Context, now: Long = System.currentTimeMillis()): UpdateStatus {
        val raw = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString("json", null) ?: return UpdateStatus()
        return try {
            val j = JSONObject(raw)
            val latest = j.optInt("latestVersionCode", 0)
            val min = j.optInt("minSupportedVersionCode", 0)
            val grace = j.optInt("graceDays", 30)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val released = fmt.parse(j.optString("releasedAt"))?.time ?: now
            val url = j.optString("storeUrl").ifBlank { Cfg.STORE_URL }
            val msg = j.optString("message")
            val name = j.optString("latestVersionName")
            val cur = BuildConfig.VERSION_CODE
            when {
                cur >= latest -> UpdateStatus()
                min > cur -> UpdateStatus(UpdateLevel.BLOCKED, name, 0, url, msg)
                now >= released + grace * DAY -> UpdateStatus(UpdateLevel.LIMITED, name, 0, url, msg)
                else -> {
                    val left = ((released + grace * DAY - now) / DAY).toInt() + 1
                    UpdateStatus(UpdateLevel.AVAILABLE, name, left, url, msg)
                }
            }
        } catch (e: Exception) {
            UpdateStatus()
        }
    }

    /** از ترد پس‌زمینه. اگر شبکه نبود، آخرین اطلاعات ذخیره‌شده معتبر می‌ماند. */
    fun refresh(ctx: Context) {
        if (Cfg.UPDATE_URL.isBlank()) return
        val sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val last = sp.getLong("checkedAt", 0)
        if (System.currentTimeMillis() - last < 3 * 60 * 60 * 1000) return
        try {
            val c = URL(Cfg.UPDATE_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            if (c.responseCode == 200) {
                val text = c.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text) // اعتبارسنجی
                sp.edit().putString("json", text).putLong("checkedAt", System.currentTimeMillis()).apply()
            }
            c.disconnect()
        } catch (_: Exception) {
        }
    }
}
