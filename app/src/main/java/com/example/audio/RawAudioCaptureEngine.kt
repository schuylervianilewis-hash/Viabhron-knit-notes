package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.audio.buffer.CircularAudioBuffer
import com.example.audio.vad.SileroVadDetector
import com.example.audio.vad.VadTransition
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

sealed interface AudioCaptureState {
    data object Idle : AudioCaptureState
    data class Recording(
        val durationMs: Long = 0L,
        val currentRms: Float = 0.0f,
        val totalChunksEmitted: Int = 0
    ) : AudioCaptureState
    data class Error(val message: String) : AudioCaptureState
}

/**
 * Raw audio capture engine utilizing:
 * 1. Thread-safe Circular Ring Buffer & 200ms Pre-Roll Cache (FUTO Voice Input method)
 * 2. Silero Neural VAD analyzing 32ms frames (512 samples @ 16kHz) with dual hysteresis
 * 3. Dedicated urgent audio thread priority (Process.THREAD_PRIORITY_URGENT_AUDIO)
 * 4. Real-time amplitude stream for smooth glowing UI pulse
 */
class RawAudioCaptureEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val SAMPLE_RATE = 16000 // 16kHz required for Whisper & Silero VAD
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_DURATION_SECONDS = 3 // Maximum 3-second speech window
        const val SAMPLES_PER_CHUNK = SAMPLE_RATE * CHUNK_DURATION_SECONDS // 48,000 samples
        const val VAD_FRAME_SIZE = SileroVadDetector.FRAME_SIZE_SAMPLES // 512 samples = 32ms
        const val PRE_ROLL_SAMPLES = (SAMPLE_RATE * 0.200).toInt() // 200ms pre-roll = 3,200 samples
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Circular ring buffer (10s audio storage)
    private val circularBuffer = CircularAudioBuffer(SAMPLE_RATE * 10)
    // Silero Neural VAD engine
    private val sileroVad = SileroVadDetector()

    private val _captureState = MutableStateFlow<AudioCaptureState>(AudioCaptureState.Idle)
    val captureState: StateFlow<AudioCaptureState> = _captureState.asStateFlow()

    // Real-time amplitude for visualizer bars & glowing pulse (normalized 0.0f to 1.0f)
    private val _currentAmplitude = MutableStateFlow(0.0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    // Shared flow emitting normalized Float32 audio chunks (ready for Whisper/Sherpa inference)
    private val _audioChunks = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 16)
    val audioChunks: SharedFlow<AudioChunk> = _audioChunks.asSharedFlow()

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (!hasRecordPermission()) {
            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Cannot start capture: RECORD_AUDIO permission missing"
            )
            _captureState.value = AudioCaptureState.Error("Microphone permission required")
            return false
        }

        if (_captureState.value is AudioCaptureState.Recording) {
            LogKeeperManager.log(LogTag.VoiceEngine, "Audio capture is already active")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            val err = "Invalid AudioRecord buffer configuration"
            LogKeeperManager.log(LogTag.VoiceEngine, err)
            _captureState.value = AudioCaptureState.Error(err)
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val err = "AudioRecord failed to initialize hardware"
                LogKeeperManager.log(LogTag.VoiceEngine, err)
                _captureState.value = AudioCaptureState.Error(err)
                return false
            }

            try {
                if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                    val agc = android.media.audiofx.AutomaticGainControl.create(audioRecord!!.audioSessionId)
                    agc?.enabled = true
                }
            } catch (ignored: Throwable) {}

            circularBuffer.clear()
            sileroVad.reset()

            audioRecord?.startRecording()
            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "AudioRecord started with Silero VAD (30ms frames @ >0.5 prob) + Circular Ring Buffer"
            )

            val startTime = System.currentTimeMillis()
            var chunkCount = 0

            recordingJob = scope.launch(Dispatchers.IO) {
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                } catch (ignored: Throwable) {}

                val shortBuffer = ShortArray(1024)
                val floatBuffer = FloatArray(1024)
                val vadFrameBuffer = FloatArray(VAD_FRAME_SIZE)
                val speechAccumulator = FloatArray(SAMPLES_PER_CHUNK)
                val preRollBuffer = FloatArray(PRE_ROLL_SAMPLES)
                var preRollWritePos = 0
                var preRollFilledCount = 0
                var speechSamplesCount = 0
                var chunkId = 1L
                var speechDetectedInUtterance = false

                _captureState.value = AudioCaptureState.Recording(
                    durationMs = 0L,
                    currentRms = 0f,
                    totalChunksEmitted = 0
                )

                val DIGITAL_PREAMP_GAIN = 3.5f

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (readCount > 0) {
                        // 1. Calculate RMS amplitude for glowing visual pulse
                        var sumOfSquares = 0.0
                        for (i in 0 until readCount) {
                            val amplified = (shortBuffer[i] * DIGITAL_PREAMP_GAIN).coerceIn(-32768f, 32767f)
                            sumOfSquares += (amplified * amplified).toDouble()
                            floatBuffer[i] = (amplified / 32768.0f).coerceIn(-1.0f, 1.0f)
                        }
                        val rms = sqrt(sumOfSquares / readCount).toFloat()
                        val db = if (rms > 0.1f) 20.0 * kotlin.math.log10(rms.toDouble() / 32767.0) else -80.0
                        val normalizedRms = ((db + 65.0) / 55.0).coerceIn(0.0, 1.0).toFloat()

                        _currentAmplitude.value = normalizedRms

                        // 2. Write to Circular Ring Buffer
                        circularBuffer.write(floatBuffer, readCount)

                        // 3. Consume 32ms frames from ring buffer for Silero Neural VAD analysis
                        while (circularBuffer.available() >= VAD_FRAME_SIZE) {
                            val samplesRead = circularBuffer.read(vadFrameBuffer, VAD_FRAME_SIZE)
                            if (samplesRead == VAD_FRAME_SIZE) {
                                val vadResult = sileroVad.processFrame(vadFrameBuffer)

                                if (vadResult.transition == VadTransition.SPEECH_START) {
                                    speechDetectedInUtterance = true
                                    // Prepend the 200ms pre-roll audio ring to preserve word-initial acoustic attacks
                                    speechSamplesCount = 0
                                    val availablePreRoll = kotlin.math.min(preRollFilledCount, PRE_ROLL_SAMPLES)
                                    val startIdx = (preRollWritePos - availablePreRoll + PRE_ROLL_SAMPLES) % PRE_ROLL_SAMPLES
                                    for (p in 0 until availablePreRoll) {
                                        val idx = (startIdx + p) % PRE_ROLL_SAMPLES
                                        if (speechSamplesCount < SAMPLES_PER_CHUNK) {
                                            speechAccumulator[speechSamplesCount++] = preRollBuffer[idx]
                                        }
                                    }
                                }

                                if (vadResult.isSpeech) {
                                    speechDetectedInUtterance = true
                                    // Accumulate speech frame
                                    for (s in 0 until VAD_FRAME_SIZE) {
                                        if (speechSamplesCount < SAMPLES_PER_CHUNK) {
                                            speechAccumulator[speechSamplesCount++] = vadFrameBuffer[s]
                                        }
                                    }

                                    // If window reached max 3 seconds (48,000 samples), emit chunk immediately
                                    if (speechSamplesCount >= SAMPLES_PER_CHUNK) {
                                        val chunkSamples = speechAccumulator.copyOf()
                                        val chunk = AudioChunk(
                                            id = chunkId++,
                                            samples = chunkSamples,
                                            sampleRate = SAMPLE_RATE,
                                            durationSeconds = CHUNK_DURATION_SECONDS.toFloat(),
                                            rmsAmplitude = normalizedRms
                                        )
                                        _audioChunks.tryEmit(chunk)
                                        chunkCount++
                                        speechSamplesCount = 0
                                        speechDetectedInUtterance = false
                                    }
                                } else {
                                    // In silence: feed 200ms pre-roll sliding buffer
                                    for (s in 0 until VAD_FRAME_SIZE) {
                                        preRollBuffer[preRollWritePos] = vadFrameBuffer[s]
                                        preRollWritePos = (preRollWritePos + 1) % PRE_ROLL_SAMPLES
                                        if (preRollFilledCount < PRE_ROLL_SAMPLES) {
                                            preRollFilledCount++
                                        }
                                    }

                                    if (vadResult.transition == VadTransition.SPEECH_END && speechDetectedInUtterance) {
                                        // Speech pause boundary detected by Silero VAD (>= 500ms silence)
                                        if (speechSamplesCount >= (SAMPLE_RATE / 4)) { // At least 250ms of speech
                                            val chunkSamples = speechAccumulator.copyOfRange(0, speechSamplesCount)
                                            val durationSec = speechSamplesCount.toFloat() / SAMPLE_RATE
                                            val chunk = AudioChunk(
                                                id = chunkId++,
                                                samples = chunkSamples,
                                                sampleRate = SAMPLE_RATE,
                                                durationSeconds = durationSec,
                                                rmsAmplitude = normalizedRms
                                            )
                                            _audioChunks.tryEmit(chunk)
                                            chunkCount++
                                            LogKeeperManager.log(
                                                LogTag.VoiceEngine,
                                                "Silero VAD emitted utterance chunk #$chunkCount (${speechSamplesCount} samples / ${String.format("%.2f", durationSec)}s)"
                                            )
                                        }
                                        speechSamplesCount = 0
                                        speechDetectedInUtterance = false
                                    }
                                }
                            }
                        }

                        val elapsed = System.currentTimeMillis() - startTime
                        _captureState.value = AudioCaptureState.Recording(
                            durationMs = elapsed,
                            currentRms = normalizedRms,
                            totalChunksEmitted = chunkCount
                        )
                    }
                }

                // If stopped with remaining speech audio, emit final utterance chunk
                if (speechSamplesCount >= (SAMPLE_RATE / 3)) {
                    val partialSamples = speechAccumulator.copyOfRange(0, speechSamplesCount)
                    val durationSec = speechSamplesCount.toFloat() / SAMPLE_RATE
                    val finalChunk = AudioChunk(
                        id = chunkId++,
                        samples = partialSamples,
                        sampleRate = SAMPLE_RATE,
                        durationSeconds = durationSec,
                        rmsAmplitude = _currentAmplitude.value
                    )
                    _audioChunks.tryEmit(finalChunk)
                    chunkCount++
                }
            }

            return true
        } catch (e: Exception) {
            val err = "AudioRecord exception: ${e.localizedMessage}"
            LogKeeperManager.log(LogTag.VoiceEngine, err)
            _captureState.value = AudioCaptureState.Error(err)
            release()
            return false
        }
    }

    fun stopCapture() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
            circularBuffer.clear()
            sileroVad.reset()
            _currentAmplitude.value = 0.0f
            _captureState.value = AudioCaptureState.Idle
            LogKeeperManager.log(LogTag.VoiceEngine, "Audio capture stopped and resources released")
        } catch (e: Exception) {
            LogKeeperManager.log(LogTag.VoiceEngine, "Error stopping AudioRecord: ${e.message}")
        }
    }

    fun release() {
        stopCapture()
    }
}
