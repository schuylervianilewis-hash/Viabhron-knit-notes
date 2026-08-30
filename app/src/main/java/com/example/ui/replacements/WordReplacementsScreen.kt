package com.example.ui.replacements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.VoiceCommandEntity
import com.example.data.db.WordReplacementEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordReplacementsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WordReplacementsViewModel = viewModel()
) {
    val allReplacements by viewModel.replacements.collectAsStateWithLifecycle()
    val allCommands by viewModel.voiceCommands.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    val isSelectionMode = uiState.selectedItemIds.isNotEmpty()

    // Filter items based on active tab, search, and category
    val filteredReplacements = remember(allReplacements, uiState.searchQuery, uiState.selectedCategory) {
        allReplacements.filter { item ->
            val matchesQuery = if (uiState.searchQuery.isBlank()) {
                true
            } else {
                item.targetPhrase.contains(uiState.searchQuery, ignoreCase = true) ||
                item.replacementPhrase.contains(uiState.searchQuery, ignoreCase = true)
            }
            val matchesCategory = if (uiState.selectedCategory == null) {
                true
            } else {
                item.category.equals(uiState.selectedCategory, ignoreCase = true)
            }
            matchesQuery && matchesCategory
        }
    }

    val filteredCommands = remember(allCommands, uiState.searchQuery, uiState.selectedCategory) {
        allCommands.filter { item ->
            val matchesQuery = if (uiState.searchQuery.isBlank()) {
                true
            } else {
                item.displayName.contains(uiState.searchQuery, ignoreCase = true) ||
                item.triggerPhrase.contains(uiState.searchQuery, ignoreCase = true) ||
                item.description.contains(uiState.searchQuery, ignoreCase = true)
            }
            val matchesCategory = if (uiState.selectedCategory == null) {
                true
            } else {
                item.category.equals(uiState.selectedCategory, ignoreCase = true)
            }
            matchesQuery && matchesCategory
        }
    }

    val currentListSize = if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
        filteredReplacements.size
    } else {
        filteredCommands.size
    }

    val categories = remember(uiState.selectedTab, allReplacements, allCommands) {
        if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
            allReplacements.map { it.category.ifBlank { "General" } }.distinct().sorted()
        } else {
            allCommands.map { it.category.ifBlank { "Navigation" } }.distinct().sorted()
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                // Contextual Action Top Bar for Multi-Select Mode
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("btn_close_replacements_selection")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                        }
                    },
                    title = {
                        Text(
                            text = "${uiState.selectedItemIds.size} / $currentListSize selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
                                    if (uiState.selectedItemIds.size == filteredReplacements.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll(filteredReplacements.map { it.id })
                                    }
                                } else {
                                    if (uiState.selectedItemIds.size == filteredCommands.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll(filteredCommands.map { it.id })
                                    }
                                }
                            },
                            modifier = Modifier.testTag("btn_replacements_select_all")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select / Deselect All",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.enableSelected(true) },
                            modifier = Modifier.testTag("btn_replacements_bulk_enable")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ToggleOn,
                                contentDescription = "Enable Selected",
                                tint = Color(0xFF10B981)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.enableSelected(false) },
                            modifier = Modifier.testTag("btn_replacements_bulk_disable")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ToggleOff,
                                contentDescription = "Disable Selected",
                                tint = Color(0xFF64748B)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.setDeleteConfirmDialogOpen(true) },
                            modifier = Modifier.testTag("btn_replacements_bulk_delete")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
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
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search word or voice command...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("replacements_search_input")
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
                        if (uiState.searchQuery.isNotEmpty()) {
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Voice Rules & Commands",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Text(
                                text = "Auto-convert shorthand & execute knitting macros",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("btn_back_from_replacements")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag("btn_replacements_search")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search Rules")
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("btn_replacements_menu")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Restore Default Knitting Presets") },
                                    leadingIcon = {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF6366F1))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.resetToDefaultKnittingPreset()
                                    },
                                    modifier = Modifier.testTag("menu_item_load_knitting_preset")
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
                        viewModel.openAddReplacementDialog()
                    } else {
                        viewModel.openAddCommandDialog()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_rule_or_command")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Row: Word Replacements vs Voice Commands
            TabRow(
                selectedTabIndex = if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) 0 else 1]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS,
                    onClick = { viewModel.selectTab(ReplacementTab.WORD_REPLACEMENTS) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Word Replacements (${allReplacements.size})", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_word_replacements")
                )
                Tab(
                    selected = uiState.selectedTab == ReplacementTab.VOICE_COMMANDS,
                    onClick = { viewModel.selectTab(ReplacementTab.VOICE_COMMANDS) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Voice Commands (${allCommands.size})", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_voice_commands")
                )
            }

            // Category Chips Row
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val totalCount = if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) allReplacements.size else allCommands.size
                        FilterChip(
                            selected = uiState.selectedCategory == null,
                            onClick = { viewModel.onCategoryFilterChanged(null) },
                            label = { Text("All ($totalCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    items(categories) { cat ->
                        val count = if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
                            allReplacements.count { it.category.equals(cat, ignoreCase = true) }
                        } else {
                            allCommands.count { it.category.equals(cat, ignoreCase = true) }
                        }
                        FilterChip(
                            selected = uiState.selectedCategory.equals(cat, ignoreCase = true),
                            onClick = {
                                if (uiState.selectedCategory.equals(cat, ignoreCase = true)) {
                                    viewModel.onCategoryFilterChanged(null)
                                } else {
                                    viewModel.onCategoryFilterChanged(cat)
                                }
                            },
                            label = { Text("$cat ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Main Content Area
            if (uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
                if (filteredReplacements.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "No matching replacement rules" else "No Word Replacement Rules",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "Try a different search keyword" else "Add custom shorthand dictionary rules (like 'yarn over' -> 'yo' or 'knit 1' -> 'k1') or tap below to load presets.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (allReplacements.isEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                TextButton(
                                    onClick = { viewModel.resetToDefaultKnittingPreset() },
                                    modifier = Modifier.testTag("btn_empty_load_preset")
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Load Knitting Shorthand Preset")
                                }
                            }
                        }
                    }
                } else {
                    val sequenceRules = remember(allReplacements) {
                        allReplacements.filter { it.category.equals("Sequence", ignoreCase = true) }
                    }
                    val areAllSequenceRulesEnabled = remember(sequenceRules) {
                        sequenceRules.isNotEmpty() && sequenceRules.all { it.isEnabled }
                    }
                    val isAnySequenceRuleEnabled = remember(sequenceRules) {
                        sequenceRules.any { it.isEnabled }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Master Feature Card for Sequence Counting & Aggregation
                        if (uiState.selectedCategory == null || uiState.selectedCategory.equals("Sequence", ignoreCase = true)) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                        .testTag("card_sequence_aggregation_master"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAnySequenceRuleEnabled) {
                                            Color(0xFFF5F3FF) // Light violet when active
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isAnySequenceRuleEnabled) Color(0xFFC4B5FD) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Sequence Counting & Aggregation",
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isAnySequenceRuleEnabled) Color(0xFF5B21B6) else MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isAnySequenceRuleEnabled) Color(0xFFDDD6FE) else MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = if (areAllSequenceRulesEnabled) "ACTIVE" else if (isAnySequenceRuleEnabled) "PARTIAL" else "OFF BY DEFAULT",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isAnySequenceRuleEnabled) Color(0xFF4C1D95) else MaterialTheme.colorScheme.outline
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Auto-collapses spoken counts (e.g. \"knit 1 knit 2 ... knit 5\" ➔ \"k5\", \"make 1 make 2\" ➔ \"m2\").",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Switch(
                                            checked = areAllSequenceRulesEnabled,
                                            onCheckedChange = { enable ->
                                                viewModel.toggleCategory("Sequence", enable)
                                            },
                                            modifier = Modifier.testTag("switch_sequence_master_toggle")
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            items = filteredReplacements,
                            key = { it.id }
                        ) { rule ->
                            WordReplacementCard(
                                rule = rule,
                                isSelected = uiState.selectedItemIds.contains(rule.id),
                                isSelectionMode = isSelectionMode,
                                onToggleSelect = { viewModel.toggleSelection(rule.id) },
                                onToggleEnabled = { viewModel.toggleRuleEnabled(rule) },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(rule.id)
                                    } else {
                                        viewModel.openEditReplacementDialog(rule)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(rule.id)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            } else {
                // Voice Commands Tab
                if (filteredCommands.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "No matching voice commands" else "No Voice Commands",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Commands execute hands-free editor actions when spoken (e.g. 'next row', 'repeat last stitch 3 times', 'undo').",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (allCommands.isEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                TextButton(
                                    onClick = { viewModel.resetToDefaultKnittingPreset() },
                                    modifier = Modifier.testTag("btn_empty_load_commands")
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Load Default Voice Commands")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filteredCommands,
                            key = { it.id }
                        ) { cmd ->
                            VoiceCommandCard(
                                command = cmd,
                                isSelected = uiState.selectedItemIds.contains(cmd.id),
                                isSelectionMode = isSelectionMode,
                                onToggleSelect = { viewModel.toggleSelection(cmd.id) },
                                onToggleEnabled = { viewModel.toggleCommandEnabled(cmd) },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(cmd.id)
                                    } else {
                                        viewModel.openEditCommandDialog(cmd)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(cmd.id)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Replacement Dialog
    if (uiState.isAddDialogOpen && uiState.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
        AddEditReplacementDialog(
            editingRule = uiState.editingReplacement,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { target, replacement, category, matchCase, enabled ->
                viewModel.saveReplacement(target, replacement, category, matchCase, enabled)
            },
            onDelete = if (uiState.editingReplacement != null) {
                { viewModel.deleteReplacement(uiState.editingReplacement!!) }
            } else null
        )
    }

    // Add / Edit Voice Command Dialog
    if (uiState.isAddDialogOpen && uiState.selectedTab == ReplacementTab.VOICE_COMMANDS) {
        AddEditVoiceCommandDialog(
            editingCommand = uiState.editingCommand,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { trigger, displayName, desc, type, category, enabled ->
                viewModel.saveVoiceCommand(trigger, displayName, desc, type, category, enabled)
            },
            onDelete = if (uiState.editingCommand != null) {
                { viewModel.deleteCommand(uiState.editingCommand!!) }
            } else null
        )
    }

    // Bulk Delete Confirmation Dialog
    if (uiState.isDeleteConfirmDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setDeleteConfirmDialogOpen(false) },
            title = { Text("Delete Selected Items?") },
            text = { Text("Are you sure you want to delete ${uiState.selectedItemIds.size} selected items? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSelected() },
                    modifier = Modifier.testTag("btn_confirm_bulk_delete")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setDeleteConfirmDialogOpen(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordReplacementCard(
    rule: WordReplacementEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("replacement_card_${rule.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                IconButton(
                    onClick = onToggleSelect,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                val isSequenceRule = rule.category.equals("Sequence", ignoreCase = true)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Spoken voice phrase
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSequenceRule) Color(0xFFF5F3FF) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSequenceRule) Color(0xFFDDD6FE) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = if (isSequenceRule) "\"${rule.targetPhrase} 1...N\"" else "\"${rule.targetPhrase}\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (rule.isEnabled) {
                                    if (isSequenceRule) Color(0xFF5B21B6) else MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Output shorthand replacement
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSequenceRule) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = if (isSequenceRule) "${rule.replacementPhrase}N" else rule.replacementPhrase,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSequenceRule) Color(0xFF4C1D95) else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSequenceRule) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isSequenceRule) "Sequence Counter" else rule.category.ifBlank { "General" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isSequenceRule) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSequenceRule) Color(0xFF5B21B6) else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (rule.isMatchCase) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFDE68A)
                        ) {
                            Text(
                                text = "Match Case",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = Color(0xFF92400E)
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Enable / Disable switch toggle
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                modifier = Modifier.testTag("switch_rule_${rule.id}")
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceCommandCard(
    command: VoiceCommandEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("command_card_${command.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                IconButton(
                    onClick = onToggleSelect,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = command.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (command.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE0E7FF),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFC7D2FE))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF4338CA))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "\"${command.triggerPhrase}\"",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF312E81)
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = command.category.ifBlank { "Navigation" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = command.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = command.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                modifier = Modifier.testTag("switch_command_${command.id}")
            )
        }
    }
}

@Composable
fun AddEditReplacementDialog(
    editingRule: WordReplacementEntity?,
    onDismiss: () -> Unit,
    onSave: (targetPhrase: String, replacementPhrase: String, category: String, isMatchCase: Boolean, isEnabled: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var targetPhrase by remember { mutableStateOf(editingRule?.targetPhrase ?: "") }
    var replacementPhrase by remember { mutableStateOf(editingRule?.replacementPhrase ?: "") }
    var category by remember { mutableStateOf(editingRule?.category ?: "Knitting") }
    var isMatchCase by remember { mutableStateOf(editingRule?.isMatchCase ?: false) }
    var isEnabled by remember { mutableStateOf(editingRule?.isEnabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingRule != null) "Edit Replacement Rule" else "Add Replacement Rule",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = targetPhrase,
                    onValueChange = { targetPhrase = it },
                    label = { Text("Spoken Phrase (e.g. yarn over, knit 1)") },
                    placeholder = { Text("e.g. yarn over") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_target_phrase")
                )

                OutlinedTextField(
                    value = replacementPhrase,
                    onValueChange = { replacementPhrase = it },
                    label = { Text("Output Shorthand (e.g. yo, k1)") },
                    placeholder = { Text("e.g. yo") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_replacement_phrase")
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. Knitting, Crochet, Coding)") },
                    placeholder = { Text("Knitting") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_category")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Match exact casing",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isMatchCase,
                        onCheckedChange = { isMatchCase = it },
                        modifier = Modifier.testTag("switch_match_case")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active rule",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("switch_dialog_enabled")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(targetPhrase, replacementPhrase, category, isMatchCase, isEnabled)
                },
                enabled = targetPhrase.isNotBlank() && replacementPhrase.isNotBlank(),
                modifier = Modifier.testTag("btn_save_replacement_rule")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("btn_delete_replacement_rule")
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun AddEditVoiceCommandDialog(
    editingCommand: VoiceCommandEntity?,
    onDismiss: () -> Unit,
    onSave: (triggerPhrase: String, displayName: String, description: String, commandType: String, category: String, isEnabled: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var triggerPhrase by remember { mutableStateOf(editingCommand?.triggerPhrase ?: "") }
    var displayName by remember { mutableStateOf(editingCommand?.displayName ?: "") }
    var description by remember { mutableStateOf(editingCommand?.description ?: "") }
    var commandType by remember { mutableStateOf(editingCommand?.commandType ?: "NEXT_ROW") }
    var category by remember { mutableStateOf(editingCommand?.category ?: "Navigation") }
    var isEnabled by remember { mutableStateOf(editingCommand?.isEnabled ?: true) }

    val commandTypes = listOf(
        "NEXT_ROW" to "Next Row (Auto-increment Row)",
        "NEXT_LINE" to "Next Line (Newline Break)",
        "REPEAT_LAST_STITCH" to "Repeat Last Stitch (xN)",
        "REPEAT_LAST_GROUP" to "Repeat Last Group (xN)",
        "UNDO_LAST" to "Undo Last Stitch",
        "INSERT_STAR" to "Insert Repeat Asterisk (*)",
        "INSERT_COMMA" to "Insert Comma (,)",
        "INSERT_PERIOD" to "Insert Period (.)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingCommand != null) "Edit Voice Command" else "Add Voice Command",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = triggerPhrase,
                    onValueChange = { triggerPhrase = it },
                    label = { Text("Spoken Trigger Phrase") },
                    placeholder = { Text("e.g. next row, repeat last") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_command_trigger")
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Command Name") },
                    placeholder = { Text("e.g. Next Row") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_command_name")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Action Description") },
                    placeholder = { Text("e.g. Inserts new row with auto counter") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_command_description")
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("Navigation / Repetition / Editing") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_command_category")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active command",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("switch_command_dialog_enabled")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(triggerPhrase, displayName, description, commandType, category, isEnabled)
                },
                enabled = triggerPhrase.isNotBlank() && displayName.isNotBlank(),
                modifier = Modifier.testTag("btn_save_voice_command")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("btn_delete_voice_command")
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
