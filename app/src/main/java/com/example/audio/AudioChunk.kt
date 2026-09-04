package com.example.audio

data class AudioChunk(
    val id: Long,
    val samples: FloatArray,
    val sampleRate: Int = 16000,
    val durationSeconds: Float,
    val rmsAmplitude: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioChunk
        return id == other.id && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + samples.contentHashCode()
        return result
    }
}
