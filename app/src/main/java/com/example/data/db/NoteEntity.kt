package com.example.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.NoteColor

@Immutable
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String = "",
    val colorTheme: String = NoteColor.YELLOW.name,
    val isPinned: Boolean = false,
    val isChecklist: Boolean = false,
    val isArchived: Boolean = false,
    val folderName: String? = null,
    val orderIndex: Int = 0,
    val hasAudio: Boolean = false,
    val audioPath: String? = null,
    val audioDurationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
