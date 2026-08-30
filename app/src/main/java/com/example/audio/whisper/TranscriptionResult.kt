package com.example.audio.whisper

data class MelSpectrogram(
    val nMel: Int = 80,
    val nFrames: Int,
    val data: FloatArray // Flattened [80, nFrames]
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MelSpectrogram
        return nMel == other.nMel && nFrames == other.nFrames && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = nMel
        result = 31 * result + nFrames
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class TranscriptionResult(
    val chunkId: Long,
    val text: String,
    val processingDurationMs: Long,
    val confidence: Float = 1.0f,
    val isFinal: Boolean = false
)
