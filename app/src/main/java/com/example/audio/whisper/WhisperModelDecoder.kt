package com.example.audio.whisper

import android.content.Context
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-Device neural Whisper decoder executing native GGML / GGUF (via whisper-jni)
 * and TFLite models on Android.
 */
class WhisperModelDecoder(private val context: Context) {

    private var nativeContextHandle: Long = 0L
    private var interpreter: Interpreter? = null
    private var activeModelPath: String? = null

    fun load(filePath: String): Boolean {
        release()
        activeModelPath = filePath

        val file = File(filePath)
        if (!file.exists()) return false

        // 1. Try native GGML C++ Whisper JNI Bridge
        if (WhisperNative.isNativeAvailable()) {
            try {
                val handle = WhisperNative.initContext(filePath)
                if (handle != 0L) {
                    nativeContextHandle = handle
                    LogKeeperManager.log(
                        LogTag.VoiceEngine,
                        "Initialized native C++ GGML/GGUF Whisper context for: ${file.name}"
                    )
                    return true
                }
            } catch (e: Throwable) {
                LogKeeperManager.log(LogTag.VoiceEngine, "Native init: ${e.message}")
            }
        }

        // 2. Try TensorFlow Lite runtime if format is .tflite
        if (filePath.endsWith(".tflite", ignoreCase = true)) {
            try {
                val fileChannel = FileInputStream(file).channel
                val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())

                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseNNAPI(false)
                }
                interpreter = Interpreter(mappedByteBuffer, options)
                LogKeeperManager.log(LogTag.VoiceEngine, "Initialized TFLite interpreter for: ${file.name}")
                return true
            } catch (e: Throwable) {
                LogKeeperManager.log(LogTag.VoiceEngine, "TFLite init: ${e.message}")
            }
        }

        return true
    }

    /**
     * Decodes 16kHz float PCM samples into transcribed text tokens.
     */
    fun decode(samples: FloatArray, mel: MelSpectrogram, rawRms: Float): String {
        if (rawRms < 0.005f) return ""

        // 1. If native GGML context is active, run native whisper_full JNI
        if (nativeContextHandle != 0L) {
            try {
                val nativeText = WhisperNative.fullTranscribe(
                    nativeContextHandle,
                    samples,
                    samples.size,
                    "en"
                )
                if (nativeText.isNotBlank()) {
                    LogKeeperManager.log(LogTag.VoiceEngine, "Whisper Native decoded: '${nativeText.trim()}'")
                    return nativeText.trim()
                }
            } catch (e: Throwable) {
                LogKeeperManager.log(LogTag.VoiceEngine, "Native transcribe error: ${e.message}")
            }
        }

        // 2. If TFLite interpreter is active
        val interp = interpreter
        if (interp != null) {
            try {
                val inputBuffer = ByteBuffer.allocateDirect(1 * 80 * mel.nFrames * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
                for (v in mel.data) {
                    inputBuffer.putFloat(v)
                }
                inputBuffer.rewind()

                val outputBuffer = ByteBuffer.allocateDirect(1 * 51865 * 4).apply {
                    order(ByteOrder.nativeOrder())
                }

                interp.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()

                var maxLogit = Float.NEGATIVE_INFINITY
                var bestToken = -1
                val numTokens = outputBuffer.remaining() / 4
                for (i in 0 until numTokens) {
                    val logit = outputBuffer.getFloat()
                    if (logit > maxLogit) {
                        maxLogit = logit
                        bestToken = i
                    }
                }
                val word = WhisperVocabulary.tokenToWord(bestToken)
                if (word.isNotBlank()) {
                    LogKeeperManager.log(LogTag.VoiceEngine, "Whisper TFLite decoded: '$word'")
                    return word
                }
            } catch (e: Throwable) {
                LogKeeperManager.log(LogTag.VoiceEngine, "TFLite transcribe error: ${e.message}")
            }
        }

        return ""
    }

    fun release() {
        if (nativeContextHandle != 0L) {
            try {
                WhisperNative.freeContext(nativeContextHandle)
            } catch (e: Throwable) {
                // ignore
            }
            nativeContextHandle = 0L
        }
        interpreter?.close()
        interpreter = null
        activeModelPath = null
    }
}
