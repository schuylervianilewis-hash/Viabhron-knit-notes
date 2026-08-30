package com.example.ui.main.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.NoteEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.NoteColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchiveNotesView(
    archivedNotes: List<NoteEntity>,
    onOpenNoteEditor: (noteId: Long?, initialColor: NoteColor) -> Unit,
    onRestoreNote: (NoteEntity) -> Unit,
    onPermanentDelete: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var noteToDeleteForever by remember { mutableStateOf<NoteEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "archive_header_info") {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Archived Notes (${archivedNotes.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Archived notes are hidden from main list. Tap unarchive to restore.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                        )
                    }
                }
            }
        }

        if (archivedNotes.isEmpty()) {
            item(key = "empty_archive_view") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Archive is Empty",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Notes you archive will be safely stored here for long-term reference.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    )
                }
            }
        } else {
            items(
                items = archivedNotes,
                key = { "archived_${it.id}" }
            ) { note ->
                val noteColor = remember(note.colorTheme) { NoteColor.fromName(note.colorTheme) }
                val formattedTime = remember(note.updatedAt) {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(note.updatedAt))
                }

                ArchivedNoteCardItem(
                    note = note,
                    noteColor = noteColor,
                    formattedTime = formattedTime,
                    onClick = { onOpenNoteEditor(note.id, noteColor) },
                    onRestore = {
                        LogKeeperManager.log(LogTag.Storage, "Restored note #${note.id} from Archive")
                        onRestoreNote(note)
                    },
                    onPermanentDelete = {
                        noteToDeleteForever = note
                    }
                )
            }
        }
    }

    // Confirmation dialog for permanent deletion
    noteToDeleteForever?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDeleteForever = null },
            title = { Text("Permanently Delete Note?") },
            text = { Text("Note '${note.title}' will be permanently deleted from SQLite storage and cannot be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        LogKeeperManager.log(LogTag.Storage, "Permanently deleted archived note #${note.id}")
                        onPermanentDelete(note)
                        noteToDeleteForever = null
                    }
                ) {
                    Text("Delete Permanently", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDeleteForever = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ArchivedNoteCardItem(
    note: NoteEntity,
    noteColor: NoteColor,
    formattedTime: String,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = noteColor.bgColor.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left stripe
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxSize()
                    .background(noteColor.stripeColor.copy(alpha = 0.7f))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF1E293B)
                    ),
                    maxLines = 1
                )
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            // Timestamp
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(end = 4.dp)
            )

            // Restore / Unarchive Button
            IconButton(
                onClick = onRestore,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("btn_restore_${note.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Unarchive,
                    contentDescription = "Restore Note",
                    tint = Color(0xFF00897B),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Permanent Delete Button
            IconButton(
                onClick = onPermanentDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("btn_perm_delete_${note.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete Permanently",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
