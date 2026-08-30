package com.example.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a text replacement rule applied to transcribed speech output.
 * E.g. "yarn over" -> "yo", "knit 1" -> "k1", "make 1" -> "m1"
 */
@Entity(tableName = "word_replacements")
data class WordReplacementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "target_phrase")
    val targetPhrase: String,

    @ColumnInfo(name = "replacement_phrase")
    val replacementPhrase: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = false,

    @ColumnInfo(name = "is_match_case")
    val isMatchCase: Boolean = false,

    @ColumnInfo(name = "category")
    val category: String = "Knitting",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
