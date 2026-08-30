package com.example.data.logkeeper

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class TimeFilter(val label: String, val hours: Long) {
    SIX_HOURS("6h", 6L),
    TWELVE_HOURS("12h", 12L),
    TWENTY_FOUR_HOURS("24h", 24L),
    ALL("All", Long.MAX_VALUE)
}

object LogKeeperManager {
    private val idCounter = AtomicLong(1L)
    private const val MAX_LOG_ENTRIES = 2000

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private var persistentLogFile: File? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    init {
        log(LogTag.System, "LogKeeper initialized")
        log(LogTag.System, "WindowInsets configured (Edge-to-Edge active)")
        log(LogTag.Navigation, "Navigated to: main")
    }

    /**
     * Initializes disk persistence so logs survive process restarts and exits.
     */
    fun initPersistence(context: Context) {
        try {
            val logsDir = File(context.filesDir, "logs").apply { mkdirs() }
            val file = File(logsDir, "logkeeper_audit.log")
            persistentLogFile = file

            if (file.exists()) {
                val loadedEntries = mutableListOf<LogEntry>()
                file.forEachLine { line ->
                    val parts = line.split("\t", limit = 5)
                    if (parts.size == 5) {
                        try {
                            val ts = parts[0].toLong()
                            val tagName = parts[1]
                            val lvlName = parts[2]
                            val timeStr = parts[3]
                            val msg = parts[4]

                            val tag = try { LogTag.valueOf(tagName) } catch (_: Throwable) { LogTag.System }
                            val lvl = try { LogLevel.valueOf(lvlName) } catch (_: Throwable) { LogLevel.INFO }

                            loadedEntries.add(
                                LogEntry(
                                    id = idCounter.getAndIncrement(),
                                    timestamp = ts,
                                    formattedTime = timeStr,
                                    tag = tag,
                                    message = msg,
                                    level = lvl
                                )
                            )
                        } catch (_: Throwable) {
                            // ignore malformed line
                        }
                    }
                }

                if (loadedEntries.isNotEmpty()) {
                    // Combine loaded persistent entries with initial in-memory logs
                    val combined = (loadedEntries.reversed() + _logs.value).distinctBy { "${it.timestamp}_${it.message}" }
                    _logs.value = combined.take(MAX_LOG_ENTRIES)
                }
            }
        } catch (_: Throwable) {
            // fallback gracefully
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        if (enabled) {
            log(LogTag.System, "Logging resumed by user")
        }
    }

    fun log(tag: LogTag, message: String, level: LogLevel = LogLevel.INFO) {
        if (!_isLoggingEnabled.value) return

        val now = System.currentTimeMillis()
        val formattedTimeStr = timeFormat.format(Date(now))
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = now,
            formattedTime = formattedTimeStr,
            tag = tag,
            message = message,
            level = level
        )

        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Newest logs first
        if (currentList.size > MAX_LOG_ENTRIES) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList

        // Asynchronously persist to disk
        persistentLogFile?.let { file ->
            ioScope.launch {
                try {
                    val sanitizedMsg = message.replace("\n", " ").replace("\t", " ")
                    file.appendText("${now}\t${tag.name}\t${level.name}\t${formattedTimeStr}\t${sanitizedMsg}\n")
                } catch (_: Throwable) {
                    // ignore disk write errors
                }
            }
        }
    }

    fun getFilteredLogs(filter: TimeFilter): List<LogEntry> {
        val allLogs = _logs.value
        if (filter == TimeFilter.ALL) return allLogs

        val cutoff = System.currentTimeMillis() - (filter.hours * 60 * 60 * 1000L)
        return allLogs.filter { it.timestamp >= cutoff }
    }

    fun formatLogsForExport(filter: TimeFilter): String {
        val filtered = getFilteredLogs(filter)
        val sb = StringBuilder()
        sb.append("========================================\n")
        sb.append("OFFLINE VOICE NOTES - LOG KEEPER AUDIT\n")
        sb.append("Exported: ").append(fullDateFormat.format(Date())).append("\n")
        sb.append("Filter Range: ").append(filter.label).append("\n")
        sb.append("Total Entries: ").append(filtered.size).append("\n")
        sb.append("========================================\n\n")

        for (entry in filtered.asReversed()) { // Chronological in export
            sb.append(fullDateFormat.format(Date(entry.timestamp)))
                .append(" [").append(entry.tag.displayName).append("] ")
                .append("[").append(entry.level.name).append("] ")
                .append(entry.message)
                .append("\n")
        }
        return sb.toString()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        persistentLogFile?.let { file ->
            ioScope.launch {
                try {
                    file.writeText("")
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
        log(LogTag.System, "Log buffer cleared")
    }
}

