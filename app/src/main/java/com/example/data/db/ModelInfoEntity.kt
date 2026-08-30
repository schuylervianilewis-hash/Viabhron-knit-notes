package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelInfoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val format: String, // "GGUF", "GGML_BIN", "TFLITE", or "UNKNOWN"
    val modelTier: String, // "Tiny (~39MB)", "Base (~75MB)", "Small (~240MB)", "Custom"
    val isActive: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)
