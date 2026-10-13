package ir.kaveh.yaddashtyar.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.kaveh.yaddashtyar.MainActivity
import ir.kaveh.yaddashtyar.ui.theme.YaddashtTheme
import ir.kaveh.yaddashtyar.util.AlarmService
import ir.kaveh.yaddashtyar.util.Reminders
import ir.kaveh.yaddashtyar.util.ReminderReceiver

/** صفحهٔ تمام‌صفحهٔ زنگ: روی قفل‌صفحه هم باز می‌شود. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val noteId = intent.getLongExtra("noteId", -1L)
        val title = intent.getStringExtra("title").orEmpty()
        val text = intent.getStringExtra("text").orEmpty()

        setContent {
            YaddashtTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val active by AlarmService.active.collectAsStateWithLifecycle()
                    LaunchedEffect(active) { if (!active) finish() }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize().padding(28.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.Alarm, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(20.dp))
                            Text(
                                title.ifBlank { "یادآوری" },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                            )
                            if (text.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text, textAlign = TextAlign.Center, maxLines = 6,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(40.dp))
                            Button(
                                onClick = { AlarmService.stop(this@AlarmActivity); finish() },
                                modifier = Modifier.fillMaxWidth().height(60.dp)
                            ) { Text("خاموش", style = MaterialTheme.typography.titleMedium) }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    sendBroadcast(
                                        Intent(this@AlarmActivity, ReminderReceiver::class.java)
                                            .setAction(Reminders.ACTION_SNOOZE).putExtra("noteId", noteId)
                                    )
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth().height(60.dp)
                            ) { Text("۱۰ دقیقه بعد", style = MaterialTheme.typography.titleMedium) }
                            TextButton(onClick = {
                                AlarmService.stop(this@AlarmActivity)
                                startActivity(
                                    Intent(this@AlarmActivity, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        .putExtra(Reminders.EXTRA_OPEN, noteId)
                                )
                                finish()
                            }) { Text("باز کردن یادداشت") }
                        }
                    }
                }
            }
        }
    }
}
