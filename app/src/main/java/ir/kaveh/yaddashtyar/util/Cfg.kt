package ir.kaveh.yaddashtyar.util

/**
 * تنظیمات سروری برنامه. بعد از آماده شدن سرور، این‌ها را پر کنید.
 * اگر خالی باشند، ثبت‌نام فقط روی گوشی ذخیره می‌شود و بررسی نسخهٔ جدید انجام نمی‌شود.
 */
object Cfg {
    /** آدرس دریافت ثبت‌نام (فایل server/register.php). مثال: https://example.ir/yy/register.php */
    const val REGISTER_URL = ""

    /** آدرس فایل نسخه (server/version.json). مثال: https://example.ir/yy/version.json */
    const val UPDATE_URL = ""

    /** صفحهٔ برنامه در کافه‌بازار */
    const val STORE_URL = "https://cafebazaar.ir/app/ir.kaveh.yaddashtyar"

    /** آدرس سیاست حریم خصوصی (برای بازار لازم است) */
    const val PRIVACY_URL = ""

    /** تعداد دفعاتی که صفحهٔ ثبت‌نام بعد از «بعداً» دوباره نشان داده می‌شود */
    const val MAX_REGISTER_PROMPTS = 3

    /** فاصلهٔ (بر حسب تعداد اجرا) بین دو بار نمایش مجدد */
    const val PROMPT_EVERY_LAUNCHES = 7

    /** بعد از چند ثانیه در پس‌زمینه بودن، قفل برنامه دوباره فعال شود */
    const val LOCK_AFTER_SECONDS = 30
}
