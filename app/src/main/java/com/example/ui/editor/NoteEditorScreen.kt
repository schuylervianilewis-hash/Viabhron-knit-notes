package com.example.ui.editor

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.AudioCaptureState
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.NoteColor
import com.example.ui.components.VoiceRecordingHud
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialColor: NoteColor = NoteColor.YELLOW,
    onOpenWordReplacements: (() -> Unit)? = null,
    viewModel: NoteEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(noteId) {
        viewModel.initialize(noteId, initialColor)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()

    // Automatically dismiss and suppress software keyboard when voice dictation is active
    LaunchedEffect(captureState) {
        if (captureState is AudioCaptureState.Recording) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }
    val currentAmplitude by viewModel.currentAmplitude.collectAsStateWithLifecycle()
    val benchmarkStats by viewModel.benchmarkStats.collectAsStateWithLifecycle()

    val animatedBgColor by animateColorAsState(
        targetValue = uiState.color.bgColor,
        animationSpec = tween(durationMillis = 200),
        label = "bg_color_anim"
    )

    var showPaletteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Text share helper
    val shareNoteText: () -> Unit = {
        val titleText = uiState.title.ifBlank { "ColorNote Note" }
        val shareBody = if (uiState.title.isNotBlank()) {
            "${uiState.title}\n\n${uiState.contentText}"
        } else {
            uiState.contentText
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, titleText)
            putExtra(Intent.EXTRA_TEXT, shareBody)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Note via")
        context.startActivity(shareIntent)
        LogKeeperManager.log(LogTag.UI_Editor, "Shared note text via Android ShareSheet")
    }

    // Text copy helper
    val copyNoteText: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Note Content", uiState.contentText)
        clipboard?.setPrimaryClip(clip)
        scope.launch {
            snackbarHostState.showSnackbar("Note copied to clipboard")
        }
        LogKeeperManager.log(LogTag.UI_Editor, "Copied note text to clipboard")
    }

    // Dynamic runtime microphone permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            LogKeeperManager.log(LogTag.VoiceEngine, "RECORD_AUDIO permission granted by user")
            viewModel.startVoiceRecording()
        } else {
            LogKeeperManager.log(LogTag.VoiceEngine, "RECORD_AUDIO permission denied by user")
            showPermissionRationaleDialog = true
        }
    }

    // Intercept hardware and system back gesture to auto-save and stop capture
    BackHandler {
        viewModel.stopVoiceRecording()
        viewModel.saveNote()
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.onTitleChanged(it) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        ),
                        cursorBrush = SolidColor(uiState.color.stripeColor),
                        decorationBox = { innerTextField ->
                            Box {
                                if (uiState.title.isEmpty()) {
                                    Text(
                                        text = "Note Title...",
                                        style = TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B).copy(alpha = 0.6f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopVoiceRecording()
                            viewModel.saveNote()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save and Back",
                            tint = Color(0xFF1E293B)
                        )
                    }
                },
                actions = {
                    // Voice Dictation Action Button (Triggers 16kHz PCM AudioRecord)
                    val micIconTint = if (captureState is AudioCaptureState.Recording) {
                        when (uiState.speechStatus) {
                            SpeechRecognitionStatus.WORDS_RECOGNIZED -> Color(0xFF10B981) // Green when converted
                            SpeechRecognitionStatus.HEARING_SOUND -> Color(0xFF0284C7)    // Blue when hearing sound
                            SpeechRecognitionStatus.NO_WORDS_DETECTED -> Color(0xFFEF4444)// Red if not recognized
                            SpeechRecognitionStatus.IDLE_SILENCE -> Color(0xFF64748B)     // Grey when silent
                        }
                    } else {
                        uiState.color.stripeColor
                    }

                    IconButton(
                        onClick = {
                            if (captureState is AudioCaptureState.Recording) {
                                viewModel.stopVoiceRecording()
                            } else {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    viewModel.startVoiceRecording()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier.testTag("editor_mic_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Dictate Note",
                            tint = micIconTint
                        )
                    }

                    // Share Action Button
                    IconButton(
                        onClick = { shareNoteText() },
                        modifier = Modifier.testTag("editor_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Note",
                            tint = Color(0xFF334155)
                        )
                    }

                    // Pinned status toggle
                    IconButton(
                        onClick = { viewModel.togglePinned() },
                        modifier = Modifier.testTag("editor_pin_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (uiState.isPinned) "Unpin Note" else "Pin Note",
                            tint = if (uiState.isPinned) uiState.color.stripeColor else Color(0xFF475569)
                        )
                    }

                    // Color palette trigger
                    IconButton(
                        onClick = { showPaletteDialog = true },
                        modifier = Modifier.testTag("editor_color_palette_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = "Change Color",
                            tint = uiState.color.stripeColor
                        )
                    }

                    // Explicit Save Checkmark
                    IconButton(
                        onClick = {
                            viewModel.stopVoiceRecording()
                            viewModel.saveNote()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save Note",
                            tint = Color(0xFF1E293B)
                        )
                    }

                    // More Options Dropdown Menu
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Editor Options",
                                tint = Color(0xFF334155)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (onOpenWordReplacements != null) {
                                DropdownMenuItem(
                                    text = { Text("Word Replacements") },
                                    leadingIcon = {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF6366F1))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.stopVoiceRecording()
                                        viewModel.saveNote()
                                        onOpenWordReplacements()
                                    },
                                    modifier = Modifier.testTag("menu_item_editor_word_replacements")
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Copy All Text") },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF334155))
                                },
                                onClick = {
                                    menuExpanded = false
                                    copyNoteText()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Note") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF334155))
                                },
                                onClick = {
                                    menuExpanded = false
                                    shareNoteText()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Text") },
                                leadingIcon = {
                                    Icon(Icons.Default.HorizontalRule, contentDescription = null, tint = Color(0xFFE53935))
                                },
                                onClick = {
                                    menuExpanded = false
                                    showClearConfirmDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Note") },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935))
                                },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = animatedBgColor
                )
            )
        },
        bottomBar = {
            Column {
                // Floating Real-Time Audio Recording HUD with Live Waveform
                AnimatedVisibility(
                    visible = captureState is AudioCaptureState.Recording,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    val recordingState = captureState as? AudioCaptureState.Recording
                    VoiceRecordingHud(
                        isRecording = true,
                        durationMs = recordingState?.durationMs ?: 0L,
                        amplitude = currentAmplitude,
                        chunkCount = recordingState?.totalChunksEmitted ?: 0,
                        speechStatus = uiState.speechStatus,
                        lastRecognizedSnippet = uiState.lastRecognizedSnippet,
                        latestBenchmark = benchmarkStats.latestBenchmark,
                        onStopAndSave = { viewModel.stopVoiceRecording() },
                        onCancel = { viewModel.stopVoiceRecording() }
                    )
                }

                // Quick Text Formatting Toolbar
                TextFormattingToolbar(
                    onInsertBullet = { viewModel.insertFormattedLine("• ") },
                    onInsertCheckbox = { viewModel.insertFormattedLine("[ ] ") },
                    onInsertTimestamp = { viewModel.insertTimestamp() },
                    onInsertDivider = { viewModel.insertTextAtCursor("\n-----------\n") },
                    onInsertBold = { viewModel.insertTextAtCursor("**", "**") },
                    onInsertQuote = { viewModel.insertFormattedLine("> ") },
                    accentColor = uiState.color.stripeColor,
                    backgroundColor = animatedBgColor
                )

                EditorBottomBar(
                    uiState = uiState,
                    backgroundColor = animatedBgColor
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(animatedBgColor)
        ) {
            TextEditorView(
                contentValue = uiState.contentValue,
                stripeColor = uiState.color.stripeColor,
                isRecording = captureState is AudioCaptureState.Recording,
                onContentValueChange = { viewModel.onContentValueChanged(it) }
            )
        }
    }

    // Permission Rationale Dialog
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("Microphone Access Required") },
            text = {
                Text(
                    "ColorNote uses the microphone strictly for on-device offline voice-to-text transcription. Audio is processed directly in memory (16kHz PCM) and never saved to storage or transmitted over the internet."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationaleDialog = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Color Palette Selection Dialog
    if (showPaletteDialog) {
        AlertDialog(
            onDismissRequest = { showPaletteDialog = false },
            title = { Text("Choose Note Color") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NoteColor.entries.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(color.bgColor, CircleShape)
                                .clickable {
                                    viewModel.onColorSelected(color)
                                    showPaletteDialog = false
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color.stripeColor, CircleShape)
                            )
                            if (uiState.color == color) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = color.stripeColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaletteDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Text Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Note Content?") },
            text = { Text("This will erase all typed and dictated text in this note.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearContent()
                    }
                ) {
                    Text("Clear All", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete this note?") },
            text = { Text("This will permanently remove the note from the Room SQLite database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteCurrentNote()
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TextFormattingToolbar(
    onInsertBullet: () -> Unit,
    onInsertCheckbox: () -> Unit,
    onInsertTimestamp: () -> Unit,
    onInsertDivider: () -> Unit,
    onInsertBold: () -> Unit,
    onInsertQuote: () -> Unit,
    accentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor.copy(alpha = 0.95f),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FormatChip(
                icon = Icons.Default.FormatListBulleted,
                label = "Bullet",
                onClick = onInsertBullet,
                tint = accentColor
            )
            FormatChip(
                icon = Icons.Default.CheckBox,
                label = "Todo",
                onClick = onInsertCheckbox,
                tint = accentColor
            )
            FormatChip(
                icon = Icons.Default.Schedule,
                label = "Time",
                onClick = onInsertTimestamp,
                tint = accentColor
            )
            FormatChip(
                icon = Icons.Default.FormatBold,
                label = "Bold",
                onClick = onInsertBold,
                tint = accentColor
            )
            FormatChip(
                icon = Icons.Default.FormatQuote,
                label = "Quote",
                onClick = onInsertQuote,
                tint = accentColor
            )
            FormatChip(
                icon = Icons.Default.HorizontalRule,
                label = "Line",
                onClick = onInsertDivider,
                tint = accentColor
            )
        }
    }
}

@Composable
fun FormatChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color(0x12000000),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
            )
        }
    }
}

@Composable
fun TextEditorView(
    contentValue: TextFieldValue,
    stripeColor: Color,
    isRecording: Boolean = false,
    onContentValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val content = contentValue.text

    val fontSize = 17.sp
    val lineHeight = 36.sp
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val topPaddingDp = 12.dp
    val topPaddingPx = with(density) { topPaddingDp.toPx() }
    val horizontalPaddingDp = 24.dp
    val redMarginRuleOffsetPx = with(density) { 18.dp.toPx() }

    // Notebook paper ruled lines and classic left red margin
    val lineColor = Color(0xFF000000).copy(alpha = 0.08f)
    val redMarginColor = Color(0xFFE53935).copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Draw classic left margin red rule line
                drawLine(
                    color = redMarginColor,
                    start = Offset(redMarginRuleOffsetPx, 0f),
                    end = Offset(redMarginRuleOffsetPx, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )

                val layout = textLayoutResult
                var lastLineY = topPaddingPx

                if (layout != null && content.isNotEmpty()) {
                    val lineCount = layout.lineCount
                    for (i in 0 until lineCount) {
                        // Position ruled line right beneath the text baseline so characters sit on top
                        val baselineY = topPaddingPx + layout.getLineBaseline(i) + 4.dp.toPx()
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, baselineY),
                            end = Offset(size.width, baselineY),
                            strokeWidth = 1.dp.toPx()
                        )
                        lastLineY = baselineY
                    }
                }

                // Continue drawing empty notebook lines down to the bottom of the screen
                var y = if (lastLineY > topPaddingPx) lastLineY + lineHeightPx else topPaddingPx + lineHeightPx
                while (y < size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineHeightPx
                }
            }
            .padding(start = horizontalPaddingDp, end = 16.dp, top = topPaddingDp, bottom = topPaddingDp)
    ) {
        BasicTextField(
            value = contentValue,
            onValueChange = onContentValueChange,
            readOnly = isRecording,
            onTextLayout = { layoutResult ->
                textLayoutResult = layoutResult
            },
            textStyle = TextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = Color(0xFF1E293B),
                platformStyle = PlatformTextStyle(
                    includeFontPadding = false
                ),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            cursorBrush = SolidColor(stripeColor),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (content.isEmpty()) {
                        Text(
                            text = "Tap here to start writing your note...",
                            style = TextStyle(
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                color = Color(0xFF64748B).copy(alpha = 0.6f),
                                platformStyle = PlatformTextStyle(
                                    includeFontPadding = false
                                ),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("editor_body_input")
        )
    }
}

@Composable
fun EditorBottomBar(
    uiState: NoteEditorUiState,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(uiState.updatedAt) {
        val date = Date(uiState.updatedAt)
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
    }

    val content = uiState.contentText
    val words = if (content.isBlank()) 0 else content.trim().split("\\s+".toRegex()).size
    val chars = content.length
    val statusText = "$chars chars  |  $words words"

    Surface(
        color = backgroundColor,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modified: $formattedTime",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
            )
        }
    }
}
