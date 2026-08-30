package com.example.audio.whisper

data class InferenceBenchmark(
    val chunkId: Long,
    val modelName: String,
    val modelTier: String,
    val audioDurationSeconds: Float,
    val melDurationMs: Long,
    val inferenceDurationMs: Long,
    val totalDurationMs: Long,
    val realTimeFactor: Float, // e.g. 0.08 (Processing Time / Audio Time)
    val speedupMultiplier: Float, // e.g. 12.5x
    val memoryUsedMb: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class SessionBenchmarkStats(
    val totalChunksProcessed: Int = 0,
    val totalAudioSeconds: Float = 0.0f,
    val averageLatencyMs: Long = 0L,
    val minLatencyMs: Long = 0L,
    val maxLatencyMs: Long = 0L,
    val averageRtf: Float = 0.0f,
    val peakMemoryMb: Float = 0.0f,
    val latestBenchmark: InferenceBenchmark? = null
)
