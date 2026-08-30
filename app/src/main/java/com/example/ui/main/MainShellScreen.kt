package com.example.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.NoteEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.NoteColor
import com.example.ui.main.views.ArchiveNotesView
import com.example.ui.main.views.CalendarNotesView
import com.example.ui.main.views.FoldersView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    onOpenLogKeeper: () -> Unit,
    onOpenNoteEditor: (noteId: Long?, initialColor: NoteColor) -> Unit,
    onOpenModelManager: () -> Unit,
    onOpenWordReplacements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = viewModel()
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val allActiveNotes by viewModel.allActiveNotes.collectAsStateWithLifecycle()
    val archivedNotes by viewModel.archivedNotes.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedColorFilter by viewModel.selectedColorFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val isSelectionMode = selectedNoteIds.isNotEmpty()

    var selectedTab by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var noteToEditColor by remember { mutableStateOf<NoteEntity?>(null) }
    var showBatchColorDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var isCreatingNewFolderInBatch by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            if (isSelectionMode) {
                // Multi-Select Top Bar (Showing X / Total selected)
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("btn_close_selection")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection Mode")
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedNoteIds.size} / ${notes.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedNoteIds.size == notes.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll(notes.map { it.id })
                                }
                            },
                            modifier = Modifier.testTag("btn_select_all_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select / Deselect All",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search title or content...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_input")
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                viewModel.onSearchQueryChanged("")
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Close Search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Color",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Note",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (selectedTab != 0) {
                                Text(
                                    text = " • " + when (selectedTab) {
                                        1 -> "Calendar"
                                        2 -> "Archive"
                                        3 -> "Folders"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectedTab == 0) {
                            IconButton(
                                onClick = {
                                    isSearchActive = true
                                    LogKeeperManager.log(LogTag.UI_Editor, "Search mode opened")
                                },
                                modifier = Modifier.testTag("main_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Notes"
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.cycleSortOrder()
                                },
                                modifier = Modifier.testTag("main_view_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Toggle Grid or Sort View"
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("main_overflow_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options"
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Word Replacements") },
                                    leadingIcon = {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF6366F1))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        LogKeeperManager.log(LogTag.Navigation, "Opened Word Replacements via top menu")
                                        onOpenWordReplacements()
                                    },
                                    modifier = Modifier.testTag("menu_item_word_replacements")
                                )
                                DropdownMenuItem(
                                    text = { Text("Log Keeper") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        LogKeeperManager.log(LogTag.Navigation, "Opened LogKeeper via top menu")
                                        onOpenLogKeeper()
                                    },
                                    modifier = Modifier.testTag("menu_item_logkeeper")
                                )
                                DropdownMenuItem(
                                    text = { Text("Import Whisper Model") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tune, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        LogKeeperManager.log(LogTag.Navigation, "Opened Model Manager via top menu")
                                        onOpenModelManager()
                                    },
                                    modifier = Modifier.testTag("menu_item_import_model")
                                )
                                DropdownMenuItem(
                                    text = { Text("Backup & Restore") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Security, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        LogKeeperManager.log(LogTag.Navigation, "Opened Backup & Restore")
                                    },
                                    modifier = Modifier.testTag("menu_item_backup")
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                // Multi-Select Bottom Action Bar: Color, Pin, Archive, Folder, Delete
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Change Color
                        IconButton(
                            onClick = { showBatchColorDialog = true },
                            modifier = Modifier.testTag("btn_batch_color")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ColorLens, contentDescription = "Color", tint = MaterialTheme.colorScheme.primary)
                                Text("Color", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 2. Pin / Unpin
                        IconButton(
                            onClick = { viewModel.batchSetPin(true) },
                            modifier = Modifier.testTag("btn_batch_pin")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = Color(0xFF0284C7))
                                Text("Pin", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 3. Archive
                        IconButton(
                            onClick = { viewModel.batchArchive() },
                            modifier = Modifier.testTag("btn_batch_archive")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Archive, contentDescription = "Archive", tint = Color(0xFFD97706))
                                Text("Archive", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 4. Add to Folder
                        IconButton(
                            onClick = {
                                isCreatingNewFolderInBatch = false
                                newFolderNameInput = ""
                                showBatchFolderDialog = true
                            },
                            modifier = Modifier.testTag("btn_batch_folder")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Folder", tint = Color(0xFF7C3AED))
                                Text("Folder", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 5. Delete
                        IconButton(
                            onClick = { showBatchDeleteDialog = true },
                            modifier = Modifier.testTag("btn_batch_delete")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                                Text("Delete", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            } else {
                // Standard Bottom Navigation Bar
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            LogKeeperManager.log(LogTag.Navigation, "Switched tab: Notes List")
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Notes") },
                        label = { Text("Notes") },
                        modifier = Modifier.testTag("bottom_tab_notes")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            LogKeeperManager.log(LogTag.Navigation, "Switched tab: Calendar")
                        },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                        label = { Text("Calendar") },
                        modifier = Modifier.testTag("bottom_tab_calendar")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            LogKeeperManager.log(LogTag.Navigation, "Switched tab: Archive")
                        },
                        icon = { Icon(Icons.Default.Archive, contentDescription = "Archive") },
                        label = { Text("Archive") },
                        modifier = Modifier.testTag("bottom_tab_archive")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                            LogKeeperManager.log(LogTag.Navigation, "Switched tab: Folders")
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                        label = { Text("Folders") },
                        modifier = Modifier.testTag("bottom_tab_folders")
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Floating LogKeeper FAB
                    FloatingActionButton(
                        onClick = {
                            LogKeeperManager.log(LogTag.Navigation, "Opened LogKeeper via global FAB")
                            onOpenLogKeeper()
                        },
                        shape = CircleShape,
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("fab_open_logkeeper")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = "Open Log Keeper Console",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Primary Add Note FAB
                    FloatingActionButton(
                        onClick = {
                            LogKeeperManager.log(LogTag.UI_Editor, "Creating new note")
                            onOpenNoteEditor(null, selectedColorFilter ?: NoteColor.YELLOW)
                        },
                        shape = CircleShape,
                        containerColor = Color(0xFF00897B),
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("fab_add_note")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create New Note",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab_switch_animation",
            modifier = Modifier.padding(innerPadding)
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    // TAB 0: Main Notes List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sort & Filter Header Bar
                        item(key = "header_sort", contentType = "header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.cycleSortOrder() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${sortOrder.displayName} ▼",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    )
                                }

                                // Color Filter Horizontal Selector
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 6.dp)
                                ) {
                                    item(key = "color_all") {
                                        FilterChip(
                                            selected = selectedColorFilter == null,
                                            onClick = { viewModel.onColorFilterSelected(null) },
                                            label = { Text("All (${notes.size})") },
                                            colors = FilterChipDefaults.filterChipColors()
                                        )
                                    }

                                    items(NoteColor.entries.toTypedArray(), key = { it.name }) { color ->
                                        FilterChip(
                                            selected = selectedColorFilter == color,
                                            onClick = {
                                                if (selectedColorFilter == color) {
                                                    viewModel.onColorFilterSelected(null)
                                                } else {
                                                    viewModel.onColorFilterSelected(color)
                                                }
                                            },
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .background(color.stripeColor, CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(color.displayName)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (notes.isEmpty()) {
                            item(key = "empty_notes_placeholder") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp, bottom = 48.dp, start = 24.dp, end = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Notes,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(54.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (selectedColorFilter != null) "No notes in this color" else "No notes yet",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap the + button to create a new voice or text note",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            // Live Room Database Note Cards with Selection & Long-Press
                            items(
                                items = notes,
                                key = { it.id },
                                contentType = { "note_card" }
                            ) { note ->
                                val noteColor = remember(note.colorTheme) { NoteColor.fromName(note.colorTheme) }
                                val isSelected = selectedNoteIds.contains(note.id)
                                val formattedTime = remember(note.updatedAt) {
                                    val date = Date(note.updatedAt)
                                    val now = System.currentTimeMillis()
                                    if (now - note.updatedAt < 86400000) {
                                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                                    } else {
                                        SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
                                    }
                                }

                                SelectableColorNoteCardItem(
                                    note = note,
                                    noteColor = noteColor,
                                    formattedTime = formattedTime,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleNoteSelection(note.id)
                                        } else {
                                            onOpenNoteEditor(note.id, noteColor)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleNoteSelection(note.id)
                                    },
                                    onTogglePin = { viewModel.togglePin(note) },
                                    onArchive = {
                                        LogKeeperManager.log(LogTag.Storage, "Archiving note #${note.id}")
                                        viewModel.toggleArchive(note)
                                    },
                                    onDelete = { viewModel.deleteNote(note) },
                                    onChangeColor = { noteToEditColor = note }
                                )
                            }
                        }

                        item(key = "footer_spacer", contentType = "spacer") {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }

                1 -> {
                    // TAB 1: Calendar View
                    CalendarNotesView(
                        notes = allActiveNotes,
                        onOpenNoteEditor = onOpenNoteEditor,
                        onTogglePin = { viewModel.togglePin(it) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        onChangeColor = { noteToEditColor = it }
                    )
                }

                2 -> {
                    // TAB 2: Archive View
                    ArchiveNotesView(
                        archivedNotes = archivedNotes,
                        onOpenNoteEditor = onOpenNoteEditor,
                        onRestoreNote = { viewModel.toggleArchive(it) },
                        onPermanentDelete = { viewModel.deleteNote(it) }
                    )
                }

                3 -> {
                    // TAB 3: Folders View (Chapters, Reorder, PDF Export/Import)
                    FoldersView(
                        notes = allActiveNotes,
                        customFolders = allFolders,
                        onOpenNoteEditor = onOpenNoteEditor,
                        onTogglePin = { viewModel.togglePin(it) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        onChangeColor = { noteToEditColor = it },
                        onReorderNotes = { reorderedList ->
                            for ((idx, item) in reorderedList.withIndex()) {
                                viewModel.reorderNoteInFolder(item.id, idx)
                            }
                        },
                        onImportPdfNotes = { folderName, imported ->
                            viewModel.importNotesFromPdf(folderName, imported)
                        }
                    )
                }
            }
        }
    }

    // Single Note Color Picker Dialog
    noteToEditColor?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToEditColor = null },
            title = { Text("Change Note Color") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NoteColor.entries.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color.bgColor, CircleShape)
                                .clickable {
                                    viewModel.updateNoteColor(note, color)
                                    noteToEditColor = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color.stripeColor, CircleShape)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { noteToEditColor = null }) {
                    Text("Done")
                }
            }
        )
    }

    // Batch Color Palette Picker Dialog
    if (showBatchColorDialog) {
        AlertDialog(
            onDismissRequest = { showBatchColorDialog = false },
            title = { Text("Color for ${selectedNoteIds.size} Selected Notes") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NoteColor.entries.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color.bgColor, CircleShape)
                                .clickable {
                                    viewModel.batchSetColor(color)
                                    showBatchColorDialog = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color.stripeColor, CircleShape)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchColorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete ${selectedNoteIds.size} Notes?") },
            text = {
                Text("Are you sure you want to permanently delete these ${selectedNoteIds.size} notes? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchDelete()
                        showBatchDeleteDialog = false
                    }
                ) {
                    Text("Delete All", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Add to Folder Dialog
    if (showBatchFolderDialog) {
        val namedFolders = remember(allActiveNotes, allFolders) {
            val fromNotes = allActiveNotes.mapNotNull { it.folderName }.filter { it.isNotBlank() }
            (allFolders + fromNotes).distinct()
        }

        AlertDialog(
            onDismissRequest = { showBatchFolderDialog = false },
            title = { Text("Add ${selectedNoteIds.size} Notes to Folder") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isCreatingNewFolderInBatch) {
                        Text("Create a new folder for these notes:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            placeholder = { Text("Folder name (e.g. Chapter Set 1)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Option 1: Create New Folder Action
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCreatingNewFolderInBatch = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("+ Create New Folder", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (namedFolders.isNotEmpty()) {
                            Text("Or select an existing folder:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(4.dp))
                            namedFolders.forEach { folder ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            viewModel.batchSetFolder(folder)
                                            showBatchFolderDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(folder, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        // Remove from folder option
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    viewModel.batchSetFolder(null)
                                    showBatchFolderDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remove from Folder", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isCreatingNewFolderInBatch) {
                    TextButton(
                        onClick = {
                            val name = newFolderNameInput.trim()
                            if (name.isNotBlank()) {
                                viewModel.batchSetFolder(name)
                                showBatchFolderDialog = false
                            }
                        }
                    ) {
                        Text("Add to Folder")
                    }
                } else {
                    TextButton(onClick = { showBatchFolderDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            dismissButton = {
                if (isCreatingNewFolderInBatch) {
                    TextButton(onClick = { isCreatingNewFolderInBatch = false }) {
                        Text("Back")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableColorNoteCardItem(
    note: NoteEntity,
    noteColor: NoteColor,
    formattedTime: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onChangeColor: () -> Unit,
    modifier: Modifier = Modifier,
    onArchive: (() -> Unit)? = null
) {
    var cardMenuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) noteColor.stripeColor.copy(alpha = 0.25f) else noteColor.bgColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, noteColor.stripeColor, RoundedCornerShape(6.dp))
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Selection Checkbox Indicator or Colored Stripe
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) noteColor.stripeColor else Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxSize()
                        .background(noteColor.stripeColor)
                        .clickable { onChangeColor() }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title, Content preview, and Folder badge
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title.ifBlank { "Untitled Note" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!note.folderName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE2E8F0)
                        ) {
                            Text(
                                text = note.folderName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF475569),
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            // Pin Button
            if (!isSelectionMode) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin Note" else "Pin Note",
                        tint = if (note.isPinned) noteColor.stripeColor else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Timestamp
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF334155),
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(end = 4.dp)
            )

            // Card Options Menu (Archive, Change Color, Delete)
            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { cardMenuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Note Actions",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = cardMenuExpanded,
                        onDismissRequest = { cardMenuExpanded = false }
                    ) {
                        if (onArchive != null) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    cardMenuExpanded = false
                                    onArchive()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Change Color") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(noteColor.stripeColor, CircleShape)
                                )
                            },
                            onClick = {
                                cardMenuExpanded = false
                                onChangeColor()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFE53935)) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935))
                            },
                            onClick = {
                                cardMenuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
