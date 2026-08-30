package com.example.data.logkeeper

import androidx.compose.runtime.Immutable

enum class LogTag(val displayName: String) {
    System("System"),
    Navigation("Navigation"),
    VoiceEngine("VoiceEngine"),
    UI_Editor("UI/Editor"),
    PdfExport("PdfExport"),
    Storage("Storage"),
    PlayerManager("PlayerManager")
}

enum class LogLevel {
    INFO,
    DEBUG,
    WARN,
    ERROR
}

@Immutable
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val formattedTime: String,
    val tag: LogTag,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)
