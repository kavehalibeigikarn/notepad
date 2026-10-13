package ir.kaveh.yaddashtyar.util

import android.content.Context
import ir.kaveh.yaddashtyar.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** ثبت‌نام اختیاری با شمارهٔ موبایل (با رضایت صریح کاربر برای دریافت پیام‌های اطلاع‌رسانی). */
object Registration {
    private const val P = "reg"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)

    /** تبدیل ارقام فارسی/عربی و قالب‌های +98 / 0098 / 98 به 09xxxxxxxxx؛ در صورت نامعتبر بودن null. */
    fun normalizePhone(raw: String): String? {
        val sb = StringBuilder()
        for (ch in raw) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                ' ', '-', '+', '(', ')' -> {}
                else -> return null
            }
        }
        var d = sb.toString()
        if (d.startsWith("0098")) d = d.substring(4)
        else if (d.startsWith("98") && d.length == 12) d = d.substring(2)
        if (d.length == 10 && d.startsWith("9")) d = "0$d"
        return if (Regex("^09\\d{9}$").matches(d)) d else null
    }

    fun isRegistered(ctx: Context) = sp(ctx).getBoolean("done", false)
    fun phone(ctx: Context) = sp(ctx).getString("phone", "") ?: ""

    private fun installId(ctx: Context): String {
        val s = sp(ctx)
        return s.getString("iid", null) ?: UUID.randomUUID().toString().also { s.edit().putString("iid", it).apply() }
    }

    /** هر بار برنامه باز می‌شود صدا زده شود؛ تصمیم می‌گیرد صفحهٔ ثبت‌نام نشان داده شود یا نه. */
    fun shouldPrompt(ctx: Context): Boolean {
        val s = sp(ctx)
        if (s.getBoolean("done", false)) return false
        val launches = s.getInt("launches", 0) + 1
        s.edit().putInt("launches", launches).apply()
        val skips = s.getInt("skips", 0)
        if (skips >= Cfg.MAX_REGISTER_PROMPTS) return false
        if (skips == 0) return true
        return launches - s.getInt("lastAsk", 0) >= Cfg.PROMPT_EVERY_LAUNCHES
    }

    fun skip(ctx: Context) {
        val s = sp(ctx)
        s.edit().putInt("skips", s.getInt("skips", 0) + 1).putInt("lastAsk", s.getInt("launches", 0)).apply()
    }

    /** ذخیرهٔ محلی؛ ارسال به سرور در پس‌زمینه (و در صورت ناموفق بودن، بعداً دوباره). */
    fun save(ctx: Context, name: String, phone: String) {
        sp(ctx).edit()
            .putBoolean("done", true).putString("name", name.trim()).putString("phone", phone)
            .putBoolean("sent", false).putLong("consentAt", System.currentTimeMillis()).apply()
    }

    /** باید از ترد پس‌زمینه صدا زده شود. */
    fun flush(ctx: Context) {
        val s = sp(ctx)
        if (!s.getBoolean("done", false) || s.getBoolean("sent", false)) return
        if (Cfg.REGISTER_URL.isBlank()) return
        try {
            val body = JSONObject()
                .put("phone", s.getString("phone", ""))
                .put("name", s.getString("name", ""))
                .put("consent", true)
                .put("consentAt", s.getLong("consentAt", 0))
                .put("installId", installId(ctx))
                .put("edition", if (BuildConfig.AI_ENABLED) "full" else "lite")
                .put("versionCode", BuildConfig.VERSION_CODE)
                .toString()
            val c = URL(Cfg.REGISTER_URL).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode in 200..299) s.edit().putBoolean("sent", true).apply()
            c.disconnect()
        } catch (_: Exception) {
        }
    }
}
