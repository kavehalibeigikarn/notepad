package ir.kaveh.yaddashtyar.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.kaveh.yaddashtyar.util.fa
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: NotesViewModel, onOpen: (Long) -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.receiveAsFlow().collect { snack.showSnackbar(it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importFrom(uri) }

    BackHandler(enabled = q.isNotEmpty() || f.active || f.view != ViewMode.ALL) {
        if (q.isNotEmpty()) vm.setQuery("")
        else if (f.active) vm.clearFilters()
        else vm.setView(ViewMode.ALL)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContent(
                st = st, f = f, vm = vm,
                closeDrawer = { scope.launch { drawer.close() } },
                onExport = {
                    scope.launch { drawer.close() }
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("yaddasht-backup-$stamp.zip")
                },
                onImport = {
                    scope.launch { drawer.close() }
                    importLauncher.launch(arrayOf("*/*"))
                }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            floatingActionButton = {
                if (f.view != ViewMode.TRASH) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.createNote(onOpen) },
                        icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("یادداشت جدید", fontWeight = FontWeight.Medium) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                TopSearch(
                    q = q, f = f,
                    onMenu = { scope.launch { drawer.open() } },
                    onQuery = vm::setQuery,
                    vm = vm
                )
                FilterRow(f = f, vm = vm)
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val title = when {
                        f.topic == NO_TOPIC -> "بدون موضوع"
                        f.topic != null -> f.topic ?: ""
                        f.tag != null -> "#" + (f.tag ?: "")
                        else -> f.view.title
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${st.shown.fa()} یادداشت",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    if (f.view == ViewMode.TRASH && st.shown > 0) {
                        TextButton(onClick = { confirmEmpty = true }) { Text("خالی کردن") }
                    }
                }

                if (!st.loaded) {
                    Box(Modifier.fillMaxSize())
                } else if (st.shown == 0) {
                    EmptyState(
                        searching = q.isNotEmpty() || f.active,
                        view = f.view,
                        hasAny = st.totalNotes > 0,
                        onNew = { vm.createNote(onOpen) }
                    )
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = if (f.grid) StaggeredGridCells.Adaptive(150.dp) else StaggeredGridCells.Fixed(1),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        st.groups.forEach { g ->
                            if (g.label.isNotEmpty()) {
                                item(key = "h_" + g.label, span = StaggeredGridItemSpan.FullLine) {
                                    GroupHeader(g.label, g.notes.size)
                                }
                            }
                            items(g.notes, key = { it.note.id }) { n ->
                                val byCreated = f.sort == SortMode.CREATED || f.sort == SortMode.OLDEST
                                NoteCard(
                                    n = n,
                                    terms = st.terms,
                                    dateOf = if (byCreated) n.note.createdAt else n.note.updatedAt,
                                    inTrash = f.view == ViewMode.TRASH,
                                    onClick = { onOpen(n.note.id) },
                                    onTogglePin = { vm.setPinned(n.note.id, !n.note.pinned) },
                                    onRestore = { vm.restore(n.note.id) },
                                    onDelete = { confirmDelete = n.note.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val del = confirmDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف دائمی؟") },
            text = { Text("این یادداشت و پیوست‌هایش برای همیشه پاک می‌شود.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePermanently(del); confirmDelete = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } }
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("خالی کردن سطل زباله؟") },
            text = { Text("همهٔ یادداشت‌های سطل زباله برای همیشه پاک می‌شوند.") },
            confirmButton = {
                TextButton(onClick = { vm.emptyTrash(); confirmEmpty = false }) {
                    Text("خالی کن", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun TopSearch(q: String, f: Filters, onMenu: () -> Unit, onQuery: (String) -> Unit, vm: NotesViewModel) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).height(56.dp)
    ) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "منو") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = q,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth()
                )
                if (q.isEmpty()) {
                    Text(
                        "جستجو در یادداشت‌ها، برچسب‌ها و پیوست‌ها…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
            if (q.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "پاک کردن") }
            } else {
                Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.Tune, "مرتب‌سازی و نمایش") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuLabel("مرتب‌سازی")
                    SortMode.values().forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            onClick = { vm.setSort(s); menu = false },
                            leadingIcon = { CheckSlot(f.sort == s) }
                        )
                    }
                    HorizontalDivider()
                    MenuLabel("گروه‌بندی")
                    GroupMode.values().forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.label) },
                            onClick = { vm.setGroup(g); menu = false },
                            leadingIcon = { CheckSlot(f.group == g) }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (f.grid) "نمایش فهرستی" else "نمایش شبکه‌ای") },
                        onClick = { vm.setGrid(!f.grid); menu = false },
                        leadingIcon = {
                            Icon(if (f.grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun CheckSlot(selected: Boolean) {
    if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    else Spacer(Modifier.size(24.dp))
}

@Composable
private fun FilterRow(f: Filters, vm: NotesViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        if (f.topic != null) {
            item {
                FilterChip(
                    selected = true, onClick = { vm.setTopic(null) },
                    label = { Text(if (f.topic == NO_TOPIC) "بدون موضوع" else (f.topic ?: "")) },
                    trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
                )
            }
        }
        if (f.tag != null) {
            item {
                FilterChip(
                    selected = true, onClick = { vm.setTag(null) },
                    label = { Text("#" + (f.tag ?: "")) },
                    trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }
                )
            }
        }
        item {
            FilterChip(
                selected = f.reminder,
                onClick = { vm.toggleReminder() },
                label = { Text("یادآوری‌دار") },
                leadingIcon = { Icon(Icons.Rounded.Alarm, null, Modifier.size(16.dp)) }
            )
        }
        items(FileFilter.values().toList()) { ff ->
            FilterChip(
                selected = f.file == ff,
                onClick = { vm.toggleFile(ff) },
                label = { Text(ff.label) }
            )
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean, view: ViewMode, hasAny: Boolean, onNew: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (searching) Icons.Rounded.Search else if (view == ViewMode.TRASH) Icons.Rounded.DeleteOutline
                else if (view == ViewMode.ARCHIVE) Icons.Rounded.Archive
                else if (view == ViewMode.PINNED) Icons.Rounded.PushPin
                else Icons.Rounded.Inbox,
                null, Modifier.size(44.dp), tint = cs.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(20.dp))
        val title = when {
            searching -> "نتیجه‌ای پیدا نشد"
            view == ViewMode.TRASH -> "سطل زباله خالی است"
            view == ViewMode.ARCHIVE -> "بایگانی خالی است"
            view == ViewMode.PINNED -> "هنوز چیزی سنجاق نکرده‌اید"
            else -> "اولین یادداشتت را بنویس"
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                searching -> "عبارت دیگری امتحان کنید یا فیلترها را بردارید."
                view == ViewMode.ALL && !hasAny -> "متن، چک‌لیست و هر نوع فایلی را کنار هم نگه دارید."
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
        if (!searching && view == ViewMode.ALL) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNew) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("یادداشت جدید")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawerContent(
    st: HomeState,
    f: Filters,
    vm: NotesViewModel,
    closeDrawer: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val itemPad = NavigationDrawerItemDefaults.ItemPadding
    ModalDrawerSheet(drawerContainerColor = cs.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().height(150.dp)
                    .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary.copy(alpha = 0.9f))))
                    .padding(24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Description, null, tint = Color.White) }
                    Spacer(Modifier.height(10.dp))
                    Text("یادداشت‌یار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("همه‌چیز، یک‌جا", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(8.dp))
            val noFilter = f.topic == null && f.tag == null
            DrawerItem("همه یادداشت‌ها", Icons.Rounded.Description, st.counts.all, f.view == ViewMode.ALL && noFilter) {
                vm.setView(ViewMode.ALL); closeDrawer()
            }
            DrawerItem("سنجاق‌شده‌ها", Icons.Rounded.PushPin, st.counts.pinned, f.view == ViewMode.PINNED && noFilter) {
                vm.setView(ViewMode.PINNED); closeDrawer()
            }
            DrawerItem("بایگانی", Icons.Rounded.Archive, st.counts.archive, f.view == ViewMode.ARCHIVE && noFilter) {
                vm.setView(ViewMode.ARCHIVE); closeDrawer()
            }
            DrawerItem("سطل زباله", Icons.Rounded.DeleteOutline, st.counts.trash, f.view == ViewMode.TRASH && noFilter) {
                vm.setView(ViewMode.TRASH); closeDrawer()
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            SectionLabel("موضوع‌ها")
            if (st.topics.isEmpty()) {
                Text(
                    "هنوز موضوعی ثبت نشده",
                    Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = cs.outline
                )
            }
            st.topics.forEach { (name, count) ->
                DrawerItem(name, Icons.Rounded.Folder, count, f.topic != null && f.topic != NO_TOPIC &&
                    ir.kaveh.yaddashtyar.util.norm(f.topic ?: "") == ir.kaveh.yaddashtyar.util.norm(name)) {
                    vm.setTopic(name); closeDrawer()
                }
            }
            if (st.topics.isNotEmpty() && st.counts.all - st.topics.sumOf { it.second } > 0) {
                DrawerItem("بدون موضوع", Icons.Rounded.Folder, st.counts.all - st.topics.sumOf { it.second }, f.topic == NO_TOPIC) {
                    vm.setTopic(NO_TOPIC); closeDrawer()
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            SectionLabel("برچسب‌ها")
            if (st.tags.isEmpty()) {
                Text(
                    "هنوز برچسبی ثبت نشده",
                    Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = cs.outline
                )
            } else {
                FlowRow(
                    Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    st.tags.forEach { (t, c) ->
                        FilterChip(
                            selected = f.tag == t,
                            onClick = { vm.setTag(t); closeDrawer() },
                            label = { Text("#$t  ${c.fa()}") }
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            NavigationDrawerItem(
                label = { Text("پشتیبان‌گیری (ZIP)") },
                icon = { Icon(Icons.Rounded.FileDownload, null) },
                selected = false, onClick = onExport, modifier = Modifier.padding(itemPad)
            )
            NavigationDrawerItem(
                label = { Text("بازیابی از پشتیبان") },
                icon = { Icon(Icons.Rounded.FileUpload, null) },
                selected = false, onClick = onImport, modifier = Modifier.padding(itemPad)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1) },
        icon = { Icon(icon, null) },
        badge = { Text(count.fa(), style = MaterialTheme.typography.labelMedium) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
