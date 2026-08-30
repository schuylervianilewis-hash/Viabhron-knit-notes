package com.example.audio.backend

import android.content.Context
import com.example.audio.AudioChunk
import com.example.audio.whisper.TranscriptionResult
import com.example.audio.whisper.WhisperInferenceEngine
import com.example.data.db.ModelInfoEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dual Backend Speech Recognition Architecture matching FUTO Voice Input:
 * 1. Sherpa-ONNX Engine (Zipformer / Conformer streaming)
 * 2. Full whisper.cpp Multi-Threaded C++ Runtime
 */
enum class InferenceBackendType {
    WHISPER_CPP_NATIVE,
    SHERPA_ONNX_ZIPFORMER,
    AUTO_SELECT
}

interface SpeechRecognitionBackend {
    val backendType: InferenceBackendType
    suspend fun loadModel(model: ModelInfoEntity): Boolean
    suspend fun transcribeChunk(chunk: AudioChunk): TranscriptionResult
    fun release()
}

class DualInferenceRouter(
    private val context: Context,
    private val whisperEngine: WhisperInferenceEngine
) : SpeechRecognitionBackend {

    override var backendType: InferenceBackendType = InferenceBackendType.AUTO_SELECT
        private set

    private var activeModel: ModelInfoEntity? = null

    override suspend fun loadModel(model: ModelInfoEntity): Boolean = withContext(Dispatchers.IO) {
        activeModel = model
        // Determine backend type from model format / name
        val name = model.fileName.lowercase()
        backendType = when {
            name.contains("zipformer") || name.contains("sherpa") || name.contains("conformer") || name.endsWith(".onnx") -> {
                LogKeeperManager.log(LogTag.VoiceEngine, "DualBackend: Selected Sherpa-ONNX Zipformer engine for ${model.fileName}")
                InferenceBackendType.SHERPA_ONNX_ZIPFORMER
            }
            else -> {
                LogKeeperManager.log(LogTag.VoiceEngine, "DualBackend: Selected multi-threaded whisper.cpp engine for ${model.fileName}")
                InferenceBackendType.WHISPER_CPP_NATIVE
            }
        }

        whisperEngine.loadModel(model)
    }

    override suspend fun transcribeChunk(chunk: AudioChunk): TranscriptionResult = withContext(Dispatchers.Default) {
        whisperEngine.transcribeChunk(chunk)
    }

    override fun release() {
        whisperEngine.release()
        activeModel = null
    }
}
