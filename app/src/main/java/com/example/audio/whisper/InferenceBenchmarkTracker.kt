package com.example.audio.whisper

import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object InferenceBenchmarkTracker {

    private val _stats = MutableStateFlow(SessionBenchmarkStats())
    val stats: StateFlow<SessionBenchmarkStats> = _stats.asStateFlow()

    private val sessionLatencies = mutableListOf<Long>()

    fun recordBenchmark(benchmark: InferenceBenchmark) {
        val runtime = Runtime.getRuntime()
        val currentUsedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val benchmarkWithMem = benchmark.copy(
            memoryUsedMb = String.format("%.1f", currentUsedMemoryMb).toFloatOrNull() ?: currentUsedMemoryMb
        )

        sessionLatencies.add(benchmark.totalDurationMs)

        _stats.update { current ->
            val newTotalChunks = current.totalChunksProcessed + 1
            val newTotalAudio = current.totalAudioSeconds + benchmark.audioDurationSeconds
            val avgLatency = sessionLatencies.average().toLong()
            val minLat = sessionLatencies.minOrNull() ?: benchmark.totalDurationMs
            val maxLat = sessionLatencies.maxOrNull() ?: benchmark.totalDurationMs
            val avgRtf = if (newTotalAudio > 0) (sessionLatencies.sum().toFloat() / (newTotalAudio * 1000f)) else 0f
            val peakMem = maxOf(current.peakMemoryMb, benchmarkWithMem.memoryUsedMb)

            current.copy(
                totalChunksProcessed = newTotalChunks,
                totalAudioSeconds = newTotalAudio,
                averageLatencyMs = avgLatency,
                minLatencyMs = minLat,
                maxLatencyMs = maxLat,
                averageRtf = avgRtf,
                peakMemoryMb = peakMem,
                latestBenchmark = benchmarkWithMem
            )
        }

        val logMessage = buildString {
            append("⚡ INFERENCE BENCHMARK #${benchmarkWithMem.chunkId} [${benchmarkWithMem.modelName} - ${benchmarkWithMem.modelTier}]\n")
            append("  • Audio Chunk: ${String.format("%.1f", benchmarkWithMem.audioDurationSeconds)}s\n")
            append("  • Mel FFT: ${benchmarkWithMem.melDurationMs}ms\n")
            append("  • Inference: ${benchmarkWithMem.inferenceDurationMs}ms\n")
            append("  • Total Latency: ${benchmarkWithMem.totalDurationMs}ms\n")
            append("  • Real-Time Factor (RTF): ${String.format("%.2f", benchmarkWithMem.realTimeFactor)}x (${String.format("%.1f", benchmarkWithMem.speedupMultiplier)}x real-time speed)\n")
            append("  • App RAM: ${benchmarkWithMem.memoryUsedMb} MB")
        }

        LogKeeperManager.log(LogTag.VoiceEngine, logMessage)
    }

    fun resetSession() {
        sessionLatencies.clear()
        _stats.value = SessionBenchmarkStats()
        LogKeeperManager.log(LogTag.VoiceEngine, "Inference telemetry tracker reset")
    }
}
