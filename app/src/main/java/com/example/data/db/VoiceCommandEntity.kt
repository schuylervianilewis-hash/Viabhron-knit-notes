package com.example.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an executable voice command or knitting macro.
 * E.g. "next row" -> starts a new row line,
 * "repeat last stitch 3 times" -> repeats last token 3 times,
 * "repeat last group" -> repeats stitch cluster.
 */
@Entity(tableName = "voice_commands")
data class VoiceCommandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "command_type")
    val commandType: String, // NEXT_ROW, NEXT_LINE, REPEAT_LAST_STITCH, REPEAT_LAST_GROUP, UNDO_LAST, INSERT_STAR, INSERT_COMMA, INSERT_PERIOD

    @ColumnInfo(name = "trigger_phrase")
    val triggerPhrase: String, // e.g. "next row", "repeat last", "new line"

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "category")
    val category: String = "Navigation", // Navigation, Repetition, Editing, Punctuation

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
