package com.example.audio.whisper

import android.util.Log

/**
 * Kotlin JNI bridge to the native C/C++ Whisper GGML inference engine (whisper.cpp).
 */
object WhisperNative {

    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("whisper-jni")
            isLibraryLoaded = true
        } catch (_: Throwable) {
            isLibraryLoaded = false
        }
    }

    fun isNativeAvailable(): Boolean = isLibraryLoaded

    /**
     * Initializes a native whisper context from a GGML / GGUF model file on disk.
     * @param modelPath Absolute file path to the .bin / .gguf model file.
     * @return Native memory pointer context handle (or 0 if failed).
     */
    external fun initContext(modelPath: String): Long

    /**
     * Runs full transcription on 16kHz mono Float32 audio samples.
     * @param contextHandle The pointer returned by initContext.
     * @param samples Array of 16kHz mono float audio samples normalized to [-1.0, 1.0].
     * @param numSamples Number of samples in the array.
     * @param language ISO language code (e.g. "en", "auto").
     * @return Transcribed text string.
     */
    external fun fullTranscribe(
        contextHandle: Long,
        samples: FloatArray,
        numSamples: Int,
        language: String
    ): String

    /**
     * Evaluates Silero VAD neural probability on a 30ms window (480 samples @ 16kHz).
     * Returns speech probability in range [0.0, 1.0].
     */
    external fun computeVadProbability(
        samples: FloatArray,
        numSamples: Int
    ): Float

    /**
     * Frees the native whisper context and releases memory.
     * @param contextHandle The pointer returned by initContext.
     */
    external fun freeContext(contextHandle: Long)
}
