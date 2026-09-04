package com.example.audio.whisper

import android.content.Context
import com.example.audio.AudioChunk
import com.example.data.db.ModelInfoEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface WhisperEngineState {
    data object Unloaded : WhisperEngineState
    data class Loading(val modelName: String) : WhisperEngineState
    data class Ready(val modelName: String, val tier: String) : WhisperEngineState
    data class Transcribing(val modelName: String, val chunkId: Long) : WhisperEngineState
    data class Error(val message: String) : WhisperEngineState
}

class WhisperInferenceEngine(private val context: Context) {

    private var activeModel: ModelInfoEntity? = null
    @Volatile
    private var isLoaded: Boolean = false
    @Volatile
    private var isLoading: Boolean = false
    private val modelDecoder = WhisperModelDecoder(context)

    suspend fun loadModel(model: ModelInfoEntity): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded && activeModel?.filePath == model.filePath) {
            return@withContext true
        }
        if (isLoading) {
            return@withContext false
        }

        try {
            isLoading = true
            val file = File(model.filePath)
            if (!file.exists() || file.length() == 0L) {
                val err = "Model file does not exist on disk: ${model.filePath}"
                LogKeeperManager.log(LogTag.VoiceEngine, err)
                isLoading = false
                return@withContext false
            }

            val startTime = System.currentTimeMillis()
            activeModel = model
            modelDecoder.load(model.filePath)
            isLoaded = true
            isLoading = false
            val loadDuration = System.currentTimeMillis() - startTime

            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Whisper inference engine loaded model '${model.fileName}' (${model.modelTier}) in ${loadDuration}ms"
            )
            true
        } catch (e: Exception) {
            val err = "Failed to load Whisper model: ${e.message}"
            LogKeeperManager.log(LogTag.VoiceEngine, err)
            isLoaded = false
            isLoading = false
            false
        }
    }

    suspend fun transcribeChunk(chunk: AudioChunk): TranscriptionResult = withContext(Dispatchers.Default) {
        if (!isLoaded || isLoading) {
            return@withContext TranscriptionResult(
                chunkId = chunk.id,
                text = "",
                processingDurationMs = 0L,
                confidence = 0f,
                isFinal = true
            )
        }
        val overallStart = System.currentTimeMillis()

        // 1. Phase 1: Mel-Spectrogram Extraction & FFT
        val melStart = System.currentTimeMillis()
        val mel = WhisperAudioPreprocessor.computeLogMelSpectrogram(chunk.samples)
        val melDuration = System.currentTimeMillis() - melStart

        // 2. Phase 2: Offline Inference & Token Decoding
        val inferStart = System.currentTimeMillis()
        val recognizedText = if (chunk.rmsAmplitude < 0.005f) {
            // Audio below silence/ambient room noise threshold
            ""
        } else {
            modelDecoder.decode(chunk.samples, mel, chunk.rmsAmplitude)
        }
        val inferDuration = System.currentTimeMillis() - inferStart

        val totalDuration = System.currentTimeMillis() - overallStart
        val audioDuration = chunk.durationSeconds.coerceAtLeast(0.1f)
        val rtf = totalDuration.toFloat() / (audioDuration * 1000f)
        val speedup = if (totalDuration > 0) (audioDuration * 1000f) / totalDuration.toFloat() else 0f

        val benchmark = InferenceBenchmark(
            chunkId = chunk.id,
            modelName = activeModel?.fileName ?: "Offline Whisper",
            modelTier = activeModel?.modelTier ?: "Tiny (~39MB)",
            audioDurationSeconds = audioDuration,
            melDurationMs = melDuration,
            inferenceDurationMs = inferDuration,
            totalDurationMs = totalDuration,
            realTimeFactor = rtf,
            speedupMultiplier = speedup,
            memoryUsedMb = 0.0f
        )

        InferenceBenchmarkTracker.recordBenchmark(benchmark)

        TranscriptionResult(
            chunkId = chunk.id,
            text = recognizedText,
            processingDurationMs = totalDuration,
            confidence = if (recognizedText.isNotBlank()) 0.95f else 0.0f,
            isFinal = true
        )
    }

    fun release() {
        isLoaded = false
        activeModel = null
        modelDecoder.release()
        LogKeeperManager.log(LogTag.VoiceEngine, "Whisper inference engine released")
    }
}
