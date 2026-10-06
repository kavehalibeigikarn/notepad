package ir.kaveh.yaddashtyar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ir.kaveh.yaddashtyar.data.Attachment
import ir.kaveh.yaddashtyar.data.CheckItem
import ir.kaveh.yaddashtyar.data.parseChecklist
import ir.kaveh.yaddashtyar.data.tagList
import ir.kaveh.yaddashtyar.ui.theme.NoteAccents
import ir.kaveh.yaddashtyar.ui.theme.noteBg
import ir.kaveh.yaddashtyar.util.AudioController
import ir.kaveh.yaddashtyar.util.Dates
import ir.kaveh.yaddashtyar.util.FileUtil
import ir.kaveh.yaddashtyar.util.Kind
import ir.kaveh.yaddashtyar.util.VoiceRecorder
import ir.kaveh.yaddashtyar.util.fa
import ir.kaveh.yaddashtyar.util.fileSize
import ir.kaveh.yaddashtyar.util.formatClock
import ir.kaveh.yaddashtyar.util.norm
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: NotesViewModel, noteId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val nwf by remember(noteId) { vm.observeNote(noteId) }.collectAsStateWithLifecycle(initialValue = null)
    val home by vm.state.collectAsStateWithLifecycle()

    var loaded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(0) }
    var pinned by remember { mutableStateOf(false) }
    var archived by remember { mutableStateOf(false) }
    var remindAt by remember { mutableLongStateOf(0L) }
    val tags = remember { mutableStateListOf<String>() }
    val items = remember { mutableStateListOf<CheckItem>() }
    var tagInput by remember { mutableStateOf("") }
    var focusItem by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf<Attachment?>(null) }
    var menu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var cameraMenu by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var topicFocused by remember { mutableStateOf(false) }
    var tagFocused by remember { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    val recorder = remember { VoiceRecorder(ctx) }
    val audio = remember { AudioController() }
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            audio.release()
        }
    }
    LaunchedEffect(recorder.recording) {
        while (recorder.recording) {
            recorder.tick()
            delay(250)
        }
    }
    LaunchedEffect(audio.isPlaying) {
        while (audio.isPlaying) {
            audio.poll()
            delay(200)
        }
    }

    LaunchedEffect(nwf != null) {
        val n = nwf?.note
        if (n != null && !loaded) {
            title = n.title; body = n.body; topic = n.topic
            color = n.color; pinned = n.pinned; archived = n.archived; remindAt = n.remindAt
            tags.clear(); tags.addAll(n.tagList())
            items.clear(); items.addAll(parseChecklist(n.checklist))
            loaded = true
        }
    }

    val draft = Draft(title, body, topic, tags.toList(), color, pinned, archived, items.toList(), remindAt)
    LaunchedEffect(draft, loaded) {
        if (loaded) {
            delay(400)
            vm.saveDraft(noteId, draft)
        }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun addTag(raw: String) {
        val t = raw.trim().trimStart('#').replace(Regex("\\s+"), "_").replace(",", "").replace("،", "")
        if (t.isNotEmpty() && tags.none { norm(it) == norm(t) }) tags.add(t)
    }

    fun finalDraft(): Draft? {
        if (!loaded) return null
        addTag(tagInput)
        tagInput = ""
        return Draft(title, body, topic, tags.toList(), color, pinned, archived, items.toList(), remindAt)
    }

    fun stamp(): String {
        val now = System.currentTimeMillis()
        return Dates.full(now) + " " + Dates.time(now)
    }

    fun finishRecording() {
        val f = recorder.stop()
        if (f != null) vm.addLocalFile(noteId, f, "ضبط صدا " + stamp() + ".m4a", "audio/mp4")
        else toast("ضبط خیلی کوتاه بود")
    }

    fun close() {
        if (recorder.recording) finishRecording()
        vm.closeEditor(noteId, finalDraft())
        onClose()
    }

    BackHandler { close() }

    // ---------- launchers ----------
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(noteId, uris)
    }

    fun startRecording() {
        audio.release()
        if (!recorder.start()) toast("شروع ضبط ممکن نشد")
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else toast("اجازهٔ دسترسی به میکروفون داده نشد")
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingCapture
        pendingCapture = null
        if (name != null) {
            val f = File(FileUtil.dir(ctx), name)
            if (ok) vm.addLocalFile(noteId, f, "عکس " + stamp() + ".jpg", "image/jpeg") else f.delete()
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val name = pendingCapture
        pendingCapture = null
        if (name != null) {
            val f = File(FileUtil.dir(ctx), name)
            if (ok) vm.addLocalFile(noteId, f, "فیلم " + stamp() + ".mp4", "video/mp4") else f.delete()
        }
    }

    fun capture(video: Boolean) {
        val name = UUID.randomUUID().toString() + if (video) ".mp4" else ".jpg"
        val f = File(FileUtil.dir(ctx), name)
        pendingCapture = name
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            if (video) videoLauncher.launch(uri) else photoLauncher.launch(uri)
        } catch (e: Exception) {
            pendingCapture = null
            toast("برنامهٔ دوربین پیدا نشد")
        }
    }

    val bg by animateColorAsState(
        if (color == 0) cs.background else noteBg(color), label = "editorBg"
    )

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "بازگشت") }
                },
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            "سنجاق",
                            tint = if (pinned) cs.tertiary else cs.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "بیشتر") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (archived) "خارج کردن از بایگانی" else "بایگانی") },
                                leadingIcon = { Icon(if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                                onClick = { menu = false; archived = !archived; close() }
                            )
                            DropdownMenuItem(
                                text = { Text("ساخت کپی") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = { menu = false; vm.duplicate(noteId, finalDraft()) }
                            )
                            DropdownMenuItem(
                                text = { Text("انتقال به سطل زباله", color = cs.error) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = cs.error) },
                                onClick = {
                                    menu = false
                                    if (recorder.recording) recorder.cancel()
                                    vm.trashFromEditor(noteId, finalDraft())
                                    onClose()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Surface(color = bg) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                ) {
                    if (recorder.recording) {
                        RecordingBar(
                            elapsedMs = recorder.elapsedMs,
                            onCancel = { recorder.cancel() },
                            onStop = { finishRecording() }
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(onClick = { colorMenu = true }) {
                                    Icon(Icons.Rounded.Palette, "رنگ یادداشت", tint = cs.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        NoteAccents.forEachIndexed { i, c ->
                                            val selected = color == i
                                            Box(
                                                Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .then(
                                                        if (selected) Modifier.border(2.5.dp, cs.onSurface, CircleShape)
                                                        else Modifier
                                                    )
                                                    .clickable { color = i; colorMenu = false },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (i == 0) Box(Modifier.size(16.dp).clip(CircleShape).background(cs.surface))
                                            }
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { showReminder = true }) {
                                Icon(
                                    Icons.Rounded.Alarm, "یادآوری",
                                    tint = if (remindAt > System.currentTimeMillis()) cs.tertiary else cs.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                val nid = (items.maxOfOrNull { it.id } ?: -1L) + 1
                                items.add(CheckItem(nid, "", false))
                                focusItem = nid
                            }) { Icon(Icons.Rounded.CheckBox, "افزودن چک‌لیست", tint = cs.onSurfaceVariant) }
                            IconButton(onClick = { filePicker.launch("*/*") }) {
                                Icon(Icons.Rounded.AttachFile, "افزودن پیوست", tint = cs.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { cameraMenu = true }) {
                                    Icon(Icons.Rounded.CameraAlt, "دوربین", tint = cs.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = cameraMenu, onDismissRequest = { cameraMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("عکس گرفتن") },
                                        leadingIcon = { Icon(Icons.Rounded.CameraAlt, null) },
                                        onClick = { cameraMenu = false; capture(false) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("فیلم گرفتن") },
                                        leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                                        onClick = { cameraMenu = false; capture(true) }
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) startRecording()
                                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Icon(Icons.Rounded.Mic, "ضبط صدا", tint = cs.primary) }
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            // title
            Box(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = cs.onSurface, fontWeight = FontWeight.Bold, lineHeight = 36.sp
                    ),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth()
                )
                if (title.isEmpty()) {
                    Text(
                        "عنوان",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.outline.copy(alpha = 0.7f),
                        lineHeight = 36.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // topic
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = topic,
                        onValueChange = { topic = it.replace("\n", " ") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { topicFocused = it.isFocused }
                    )
                    if (topic.isEmpty()) {
                        Text("موضوع (مثلاً کار، شخصی، ایده)", style = MaterialTheme.typography.bodyMedium, color = cs.outline)
                    }
                }
            }
            if (topicFocused) {
                val sugg = home.topics.map { it.first }
                    .filter { it != topic.trim() && (topic.isBlank() || norm(it).contains(norm(topic))) }
                    .take(8)
                if (sugg.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sugg.forEach { s -> SuggestionChip(onClick = { topic = s }, label = { Text(s) }) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // tags
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tags.toList().forEach { t ->
                    Surface(shape = RoundedCornerShape(10.dp), color = cs.primary.copy(alpha = 0.14f)) {
                        Row(
                            Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#$t", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                            Icon(
                                Icons.Rounded.Close, "حذف برچسب",
                                Modifier.padding(start = 4.dp).size(16.dp).clickable { tags.remove(t) },
                                tint = cs.primary
                            )
                        }
                    }
                }
                Box(Modifier.widthIn(min = 120.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = tagInput,
                        onValueChange = { v ->
                            if (v.endsWith(" ") || v.endsWith(",") || v.endsWith("،") || v.contains("\n")) {
                                addTag(v); tagInput = ""
                            } else tagInput = v
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTag(tagInput); tagInput = "" }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { tagFocused = it.isFocused }
                    )
                    if (tagInput.isEmpty()) {
                        Text(
                            if (tags.isEmpty()) "برچسب اضافه کنید…" else "برچسب جدید",
                            style = MaterialTheme.typography.bodyMedium, color = cs.outline
                        )
                    }
                }
            }
            if (tagFocused) {
                val sugg = home.tags.map { it.first }
                    .filter { t -> tags.none { norm(it) == norm(t) } && (tagInput.isBlank() || norm(t).contains(norm(tagInput))) }
                    .take(8)
                if (sugg.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sugg.forEach { s ->
                            SuggestionChip(onClick = { addTag(s); tagInput = "" }, label = { Text("#$s") })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // reminder
            ReminderRow(remindAt = remindAt, onClick = { showReminder = true }, onClear = { remindAt = 0L })
            Spacer(Modifier.height(14.dp))

            // body
            Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, lineHeight = 28.sp),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth()
                )
                if (body.isEmpty()) {
                    Text("بنویسید…", style = MaterialTheme.typography.bodyLarge, color = cs.outline.copy(alpha = 0.7f))
                }
            }

            // checklist
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                items.toList().forEach { item ->
                    key(item.id) {
                        CheckRow(
                            item = item,
                            requestFocus = focusItem == item.id,
                            onFocused = { focusItem = null },
                            onChange = { c ->
                                val idx = items.indexOfFirst { it.id == c.id }
                                if (idx >= 0) items[idx] = c
                            },
                            onNext = {
                                val idx = items.indexOfFirst { it.id == item.id }
                                if (idx >= 0 && items[idx].text.isNotBlank()) {
                                    val nid = (items.maxOfOrNull { it.id } ?: -1L) + 1
                                    items.add(idx + 1, CheckItem(nid, "", false))
                                    focusItem = nid
                                }
                            },
                            onRemove = { items.removeAll { it.id == item.id } }
                        )
                    }
                }
                TextButton(onClick = {
                    val nid = (items.maxOfOrNull { it.id } ?: -1L) + 1
                    items.add(CheckItem(nid, "", false))
                    focusItem = nid
                }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("افزودن مورد")
                }
            }

            // attachments
            val files = nwf?.files.orEmpty()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AttachFile, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (files.isEmpty()) "پیوست‌ها" else "پیوست‌ها (${files.size.fa()})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { filePicker.launch("*/*") }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("افزودن فایل")
                }
            }
            if (files.isEmpty()) {
                Surface(
                    onClick = { filePicker.launch("*/*") },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, cs.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "تصویر، PDF، صوت، ویدیو یا هر فایل دیگری را پیوست کنید؛ یا با دکمه‌های پایین عکس بگیرید و صدا ضبط کنید",
                        Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.outline
                    )
                }
            } else {
                val imgs = files.filter { FileUtil.kindOf(it) == Kind.IMAGE }
                val rest = files.filter { FileUtil.kindOf(it) != Kind.IMAGE }
                if (imgs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imgs.forEach { a ->
                            Box(Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).clickable { preview = a }) {
                                AsyncImage(
                                    model = remember(a.path) { FileUtil.fileOf(ctx, a) },
                                    contentDescription = a.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable { vm.removeFile(a) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Close, "حذف", Modifier.size(15.dp), tint = Color.White) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                rest.forEach { a ->
                    if (FileUtil.kindOf(a) == Kind.AUDIO) {
                        AudioRow(
                            a = a,
                            audio = audio,
                            file = FileUtil.fileOf(ctx, a),
                            onShare = { FileUtil.share(ctx, a) },
                            onRemove = {
                                if (audio.activeId == a.id) audio.release()
                                vm.removeFile(a)
                            }
                        )
                    } else {
                        FileRow(
                            a = a,
                            onOpen = { FileUtil.open(ctx, a) },
                            onShare = { FileUtil.share(ctx, a) },
                            onRemove = { vm.removeFile(a) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            val n = nwf?.note
            if (n != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "ایجاد: ${Dates.relative(n.createdAt)}  ·  ویرایش: ${Dates.relative(n.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.outline
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showReminder) {
        ReminderDialog(
            initial = remindAt,
            onConfirm = { ts ->
                remindAt = ts
                showReminder = false
                ensureNotificationPermission()
            },
            onClear = { remindAt = 0L; showReminder = false },
            onDismiss = { showReminder = false }
        )
    }

    val pv = preview
    if (pv != null) ImagePreview(pv, onDismiss = { preview = null })
}

@Composable
private fun RecordingBar(elapsedMs: Long, onCancel: () -> Unit, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val blink = rememberInfiniteTransition(label = "rec")
    val a by blink.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recDot"
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).alpha(a).clip(CircleShape).background(cs.error))
        Spacer(Modifier.width(10.dp))
        Text(
            "در حال ضبط…  " + formatClock(elapsedMs),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "لغو ضبط", tint = cs.onSurfaceVariant) }
        Button(onClick = onStop) {
            Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("ذخیره")
        }
    }
}

@Composable
private fun ReminderRow(remindAt: Long, onClick: () -> Unit, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val set = remindAt > 0
    val future = remindAt > System.currentTimeMillis()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (set && future) cs.tertiary.copy(alpha = 0.16f) else cs.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Alarm, null, Modifier.size(18.dp),
            tint = if (set && future) cs.tertiary else cs.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (!set) "افزودن یادآوری"
            else (if (future) "یادآوری: " else "یادآوری گذشته: ") + Dates.remind(remindAt),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (!set) cs.outline else cs.onSurface
        )
        if (set) {
            Icon(
                Icons.Rounded.Close, "حذف یادآوری",
                Modifier.size(18.dp).clickable(onClick = onClear),
                tint = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CheckRow(
    item: CheckItem,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onChange: (CheckItem) -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fr = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            delay(80)
            runCatching { fr.requestFocus() }
            onFocused()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.done, onCheckedChange = { onChange(item.copy(done = it)) })
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = item.text,
                onValueChange = { onChange(item.copy(text = it.replace("\n", ""))) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (item.done) cs.outline else cs.onSurface,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                ),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                modifier = Modifier.fillMaxWidth().focusRequester(fr)
            )
            if (item.text.isEmpty()) {
                Text("مورد جدید", style = MaterialTheme.typography.bodyLarge, color = cs.outline.copy(alpha = 0.7f))
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, "حذف مورد", Modifier.size(18.dp), tint = cs.outline)
        }
    }
}

@Composable
private fun AudioRow(
    a: Attachment,
    audio: AudioController,
    file: File,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val active = audio.activeId == a.id
    val playing = active && audio.isPlaying
    val fraction = if (active && audio.duration > 0) audio.position.toFloat() / audio.duration else 0f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.onSurface.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(cs.primary)
                    .clickable { audio.toggle(file, a.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "توقف" else "پخش",
                    tint = cs.onPrimary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    trackColor = cs.onSurface.copy(alpha = 0.12f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (active && audio.duration > 0)
                        formatClock(audio.position.toLong()) + " / " + formatClock(audio.duration.toLong())
                    else a.size.fileSize(),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.outline
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "اشتراک‌گذاری", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "حذف", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
        }
    }
}

@Composable
private fun FileRow(a: Attachment, onOpen: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = cs.onSurface.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(kindIcon(FileUtil.kindOf(a)), null, tint = cs.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.size.fileSize(), style = MaterialTheme.typography.labelSmall, color = cs.outline)
            }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "اشتراک‌گذاری", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "حذف", Modifier.size(20.dp), tint = cs.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ImagePreview(a: Attachment, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = remember(a.path) { FileUtil.fileOf(ctx, a) },
                contentDescription = a.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                    .transformable(state)
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp).windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                ) { Icon(Icons.Rounded.Close, "بستن", tint = Color.White) }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { FileUtil.share(ctx, a) },
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                ) { Icon(Icons.Rounded.Share, "اشتراک‌گذاری", tint = Color.White) }
            }
        }
    }
}
