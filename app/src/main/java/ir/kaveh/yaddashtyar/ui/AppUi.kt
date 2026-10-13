package ir.kaveh.yaddashtyar.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.kaveh.yaddashtyar.util.Cfg
import ir.kaveh.yaddashtyar.util.Registration
import ir.kaveh.yaddashtyar.util.UpdateLevel
import ir.kaveh.yaddashtyar.util.UpdateStatus
import ir.kaveh.yaddashtyar.util.fa
import ir.kaveh.yaddashtyar.util.faDigits

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

/** صفحهٔ ثبت‌نام: نام (اختیاری) + شماره موبایل + رضایت صریح. */
@Composable
fun RegisterScreen(onRegister: (name: String, phone: String) -> Unit, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier.statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("به یادداشت‌یار خوش آمدید", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "برای اطلاع از نسخه‌های جدید و قابلیت‌های تازه، شمارهٔ موبایل خود را ثبت کنید. ثبت‌نام اختیاری است و یادداشت‌های شما همیشه فقط روی همین گوشی می‌ماند.",
                color = cs.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("نام (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone, onValueChange = { phone = it; error = null },
                label = { Text("شمارهٔ موبایل") }, placeholder = { Text("09123456789") },
                singleLine = true, isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text(
                    "موافقم که شمارهٔ من برای اطلاع‌رسانی دربارهٔ نسخه‌ها و خدمات یادداشت‌یار نگه‌داری شود و پیام دریافت کنم. هر زمان بخواهم می‌توانم حذف آن را بخواهم.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (Cfg.PRIVACY_URL.isNotBlank()) {
                TextButton(onClick = { openUrl(ctx, Cfg.PRIVACY_URL) }) { Text("سیاست حریم خصوصی") }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val p = Registration.normalizePhone(phone)
                    when {
                        p == null -> error = "شمارهٔ موبایل معتبر نیست (مثال: ۰۹۱۲۳۴۵۶۷۸۹)"
                        !consent -> error = "برای ثبت‌نام، تأیید رضایت لازم است"
                        else -> onRegister(name, p)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("ثبت‌نام") }
            TextButton(onClick = onSkip) { Text("بعداً") }
        }
    }
}

/** پیام نسخهٔ جدید: در دورهٔ مهلت قابل‌بستن است؛ بعد از مهلت، توضیح محدودیت. */
@Composable
fun UpdateDialog(st: UpdateStatus, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val limited = st.level == UpdateLevel.LIMITED
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text(if (limited) "لطفاً برنامه را به‌روزرسانی کنید" else "نسخهٔ جدید منتشر شده") },
        text = {
            Column {
                if (st.message.isNotBlank()) {
                    Text(st.message); Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (limited)
                        "مهلت استفاده از نسخهٔ قبلی تمام شده است. ساخت یادداشت تازه، پیوست و ضبط جدید تا نصب نسخهٔ جدید غیرفعال است؛ یادداشت‌های شما سالم است و می‌توانید آن‌ها را ببینید، جست‌وجو کنید و پشتیبان بگیرید. با نصب نسخهٔ جدید روی همین برنامه، هیچ داده‌ای پاک نمی‌شود."
                    else
                        "نسخهٔ ${st.latestName.faDigits()} آماده است و حدود ${st.daysLeft.fa()} روز دیگر نسخهٔ فعلی برخی قابلیت‌ها را از دست می‌دهد. نصب نسخهٔ جدید داده‌های شما را پاک نمی‌کند."
                )
            }
        },
        confirmButton = { Button(onClick = { openUrl(ctx, st.url); onDismiss() }) { Text("به‌روزرسانی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (limited) "بستن" else "بعداً") } }
    )
}

/** نسخهٔ خیلی قدیمی: فقط آپدیت و گرفتن پشتیبان. داده‌های کاربر گروگان گرفته نمی‌شود. */
@Composable
fun UpdateRequiredScreen(st: UpdateStatus, onBackup: () -> Unit) {
    val ctx = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("به‌روزرسانی ضروری است", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                st.message.ifBlank { "این نسخه دیگر پشتیبانی نمی‌شود. نسخهٔ جدید را نصب کنید؛ یادداشت‌های شما حفظ می‌شود." },
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { openUrl(ctx, st.url) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("دریافت نسخهٔ جدید")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("گرفتن پشتیبان از یادداشت‌ها")
            }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("یادداشت‌یار قفل است", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("باز کردن با اثر انگشت / رمز گوشی")
            }
        }
    }
}

/** رمز اختیاری برای پشتیبان. خالی = بدون رمز. */
@Composable
fun ExportPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پشتیبان‌گیری") },
        text = {
            Column {
                Text("برای محافظت از فایل پشتیبان می‌توانید رمز بگذارید. اگر رمز را فراموش کنید، فایل قابل بازیابی نیست. خالی بگذارید تا بدون رمز ذخیره شود.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true,
                    label = { Text("رمز (اختیاری)") },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }) { Text("ادامه") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun ImportPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پشتیبان رمزدار") },
        text = {
            OutlinedTextField(
                value = pw, onValueChange = { pw = it }, singleLine = true,
                label = { Text("رمز پشتیبان") },
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = pw.isNotEmpty()) { Text("بازیابی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

fun canScheduleExact(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

/** راهنمای تنظیمات گوشی برای اینکه یادآورها دقیق و بی‌وقفه کار کنند. */
@Composable
fun ReminderGuideDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val exactOk = canScheduleExact(ctx)

    fun go(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {
            try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیمات یادآوری") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("بعضی گوشی‌ها برای صرفه‌جویی باتری، برنامه‌ها را می‌بندند و یادآور به‌موقع نمی‌رسد. این سه مورد را بررسی کنید:")
                if (Build.VERSION.SDK_INT >= 31) {
                    Text(
                        if (exactOk) "✅ زمان‌بندی دقیق مجاز است" else "⚠️ زمان‌بندی دقیق مجاز نیست؛ یادآورها ممکن است چند دقیقه دیرتر برسند.",
                        fontWeight = FontWeight.Medium
                    )
                    if (!exactOk) OutlinedButton(onClick = {
                        go(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }) { Text("اجازهٔ هشدار دقیق") }
                }
                Text("برای نمایش صفحهٔ زنگ روی قفل‌صفحه و پخش صدای آلارم، اعلان‌ها و «نمایش تمام‌صفحه» باید مجاز باشند. صدای زنگ از صدای «آلارم» گوشی پخش می‌شود؛ حجم آلارم را از تنظیمات صدا بالا ببرید.")
                if (Build.VERSION.SDK_INT >= 34) OutlinedButton(onClick = {
                    go(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                }) { Text("اجازهٔ نمایش تمام‌صفحه") }
                OutlinedButton(onClick = {
                    try {
                        ir.kaveh.yaddashtyar.util.AlarmService.start(ctx, -1L, "آزمایش زنگ", "اگر صدا شنیدید، یادآورها درست کار می‌کنند")
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(ctx, "شروع زنگ ممکن نشد؛ اجازهٔ اعلان و باتری را بررسی کنید", android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text("▶ آزمایش زنگ هشدار") }
                Text("اگر در آزمایش صدایی نیامد: حالت «مزاحم نشوید» (DND) را خاموش کنید یا برای یادداشت‌یار استثنا بگذارید، و اعلان‌های برنامه را روشن کنید.")
                Text("۱) اعلان‌ها باید روشن باشند.")
                OutlinedButton(onClick = {
                    go(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }) { Text("تنظیمات اعلان") }
                Text("۲) بهینه‌سازی باتری را برای یادداشت‌یار «بدون محدودیت» کنید.")
                OutlinedButton(onClick = { go(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                    Text("تنظیمات باتری")
                }
                Text("۳) در شیائومی، سامسونگ، اوپو، هوآوی و مشابه: در تنظیمات گوشی برنامه را «اجرای خودکار / Autostart» و «بدون محدودیت پس‌زمینه» کنید و آن را از برنامه‌های اخیر با کشیدن حذف نکنید.")
                OutlinedButton(onClick = {
                    go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }) { Text("اطلاعات برنامه") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("متوجه شدم") } }
    )
}
