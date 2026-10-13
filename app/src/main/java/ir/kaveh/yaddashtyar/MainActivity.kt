package ir.kaveh.yaddashtyar

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ir.kaveh.yaddashtyar.ui.LockScreen
import ir.kaveh.yaddashtyar.ui.RegisterScreen
import ir.kaveh.yaddashtyar.ui.UpdateDialog
import ir.kaveh.yaddashtyar.ui.UpdateRequiredScreen
import ir.kaveh.yaddashtyar.util.Cfg
import ir.kaveh.yaddashtyar.util.UpdateLevel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.kaveh.yaddashtyar.ui.EditorScreen
import ir.kaveh.yaddashtyar.ui.HomeScreen
import ir.kaveh.yaddashtyar.ui.NotesViewModel
import ir.kaveh.yaddashtyar.ui.theme.YaddashtTheme
import ir.kaveh.yaddashtyar.util.Reminders

class MainActivity : ComponentActivity() {

    private val vm: NotesViewModel by lazy { ViewModelProvider(this)[NotesViewModel::class.java] }

    private var locked by mutableStateOf(false)
    private var unlocking = false
    private var bgAt = 0L

    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        unlocking = false
        if (it.resultCode == RESULT_OK) locked = false
    }

    /** باز کردن قفل با صفحهٔ استاندارد اندروید (اثر انگشت / چهره / الگو / رمز گوشی). */
    private fun requestUnlock() {
        if (unlocking) return
        val km = getSystemService(KeyguardManager::class.java)
        val i = km?.createConfirmDeviceCredentialIntent("یادداشت‌یار", "برای ادامه، قفل گوشی را باز کنید")
        if (i == null) {
            // قفل صفحه از گوشی برداشته شده؛ قفل برنامه بی‌معنی است
            locked = false
            vm.setLock(false)
        } else {
            unlocking = true
            unlockLauncher.launch(i)
        }
    }

    override fun onStart() {
        super.onStart()
        if (vm.lockEnabled.value && !unlocking) {
            val away = SystemClock.elapsedRealtime() - bgAt
            if (bgAt == 0L || away > Cfg.LOCK_AFTER_SECONDS * 1000L) locked = true
            if (locked) window.decorView.post { requestUnlock() }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!unlocking) bgAt = SystemClock.elapsedRealtime()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // با قفل برنامه، تصویر برنامه در لیست اخیر و اسکرین‌شات مخفی می‌شود
        lifecycleScope.launch {
            vm.lockEnabled.collect { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        if (savedInstanceState == null) {
            handleOpen(intent)
            handleShare(intent)
        }
        setContent {
            YaddashtTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val reg by vm.showRegister.collectAsStateWithLifecycle()
                        val up by vm.update.collectAsStateWithLifecycle()
                        val exportLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("application/zip")
                        ) { uri -> if (uri != null) vm.exportTo(uri) }
                        when {
                            locked -> LockScreen(onUnlock = ::requestUnlock)
                            up.level == UpdateLevel.BLOCKED -> UpdateRequiredScreen(up, onBackup = {
                                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                                exportLauncher.launch("yaddasht-backup-$stamp.zip")
                            })
                            reg -> RegisterScreen(onRegister = vm::register, onSkip = vm::skipRegister)
                            else -> App()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpen(intent)
        handleShare(intent)
    }

    /** Tapping a reminder notification opens that note. */
    private fun handleOpen(i: Intent?) {
        val id = i?.getLongExtra(Reminders.EXTRA_OPEN, -1L) ?: -1L
        if (id >= 0) vm.pendingOpen.value = id
    }

    /** Text / files shared from other apps become a new note. */
    private fun handleShare(i: Intent?) {
        if (i == null) return
        val uris = ArrayList<Uri>()
        when (i.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { uris.addAll(it) }
            }
            else -> return
        }
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
        if (text.isNullOrBlank() && subject.isNullOrBlank() && uris.isEmpty()) return
        vm.createFromShare(text, subject, uris)
    }
}

@Composable
private fun App(vm: NotesViewModel = viewModel()) {
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastId by remember { mutableLongStateOf(0L) }
    val pending by vm.pendingOpen.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val p = pending
        if (p != null) {
            editing = p
            vm.pendingOpen.value = null
        }
    }
    LaunchedEffect(editing) { editing?.let { lastId = it } }

    val up by vm.update.collectAsStateWithLifecycle()
    var updateShown by rememberSaveable { mutableStateOf(false) }
    if (!updateShown && (up.level == UpdateLevel.AVAILABLE || up.level == UpdateLevel.LIMITED)) {
        UpdateDialog(up, onDismiss = { updateShown = true })
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(vm = vm, onOpen = { editing = it })
        AnimatedVisibility(
            visible = editing != null,
            enter = slideInVertically { it / 10 } + fadeIn(),
            exit = slideOutVertically { it / 10 } + fadeOut()
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                EditorScreen(vm = vm, noteId = editing ?: lastId, onClose = { editing = null })
            }
        }
    }
}
