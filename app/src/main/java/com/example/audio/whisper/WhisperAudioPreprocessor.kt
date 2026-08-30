package com.example.audio.whisper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

object WhisperAudioPreprocessor {
    const val SAMPLE_RATE = 16000
    const val N_FFT = 400
    const val HOP_LENGTH = 160
    const val N_MELS = 80
    const val NUM_FREQ_BINS = N_FFT / 2 + 1 // 201 bins

    private val hannWindow = FloatArray(N_FFT) { i ->
        (0.5f * (1.0f - cos(2.0f * PI.toFloat() * i / N_FFT))).toFloat()
    }

    private val melFilters: Array<FloatArray> = createMelFilters()

    // Precomputed DFT Cosine and Sine lookup tables: [NUM_FREQ_BINS][N_FFT]
    // Eliminates all runtime trigonometric Math calls, transforming O(N^2) Math.cos to pure flat array lookups
    private val dftCosTable = Array(NUM_FREQ_BINS) { k ->
        FloatArray(N_FFT) { t ->
            cos(-2.0 * PI * k * t / N_FFT).toFloat()
        }
    }

    private val dftSinTable = Array(NUM_FREQ_BINS) { k ->
        FloatArray(N_FFT) { t ->
            sin(-2.0 * PI * k * t / N_FFT).toFloat()
        }
    }

    // Sparse active filterbank range to skip multiplying 0s
    private val filterStartBins = IntArray(N_MELS)
    private val filterEndBins = IntArray(N_MELS)

    init {
        for (m in 0 until N_MELS) {
            val filter = melFilters[m]
            var start = 0
            while (start < NUM_FREQ_BINS && filter[start] == 0.0f) start++
            var end = NUM_FREQ_BINS - 1
            while (end >= 0 && filter[end] == 0.0f) end--
            filterStartBins[m] = start.coerceAtMost(NUM_FREQ_BINS - 1)
            filterEndBins[m] = (end + 1).coerceAtMost(NUM_FREQ_BINS)
        }
    }

    private fun hzToMel(hz: Float): Float {
        return 2595.0f * log10(1.0f + hz / 700.0f)
    }

    private fun melToHz(mel: Float): Float {
        return 700.0f * (Math.pow(10.0, (mel / 2595.0f).toDouble()).toFloat() - 1.0f)
    }

    private fun createMelFilters(): Array<FloatArray> {
        val fMin = 0.0f
        val fMax = 8000.0f // Nyquist frequency for 16kHz audio
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)

        val melPoints = FloatArray(N_MELS + 2) { i ->
            melMin + (melMax - melMin) * i / (N_MELS + 1)
        }

        val hzPoints = FloatArray(N_MELS + 2) { i ->
            melToHz(melPoints[i])
        }

        val binPoints = IntArray(N_MELS + 2) { i ->
            ((N_FFT + 1) * hzPoints[i] / SAMPLE_RATE).toInt().coerceIn(0, N_FFT / 2)
        }

        val filters = Array(N_MELS) { FloatArray(NUM_FREQ_BINS) }

        for (m in 1..N_MELS) {
            val fMinus = binPoints[m - 1]
            val fCenter = binPoints[m]
            val fPlus = binPoints[m + 1]

            for (k in fMinus until fCenter) {
                if (fCenter > fMinus) {
                    filters[m - 1][k] = (k - fMinus).toFloat() / (fCenter - fMinus)
                }
            }
            for (k in fCenter until fPlus) {
                if (fPlus > fCenter) {
                    filters[m - 1][k] = (fPlus - k).toFloat() / (fPlus - fCenter)
                }
            }
        }

        return filters
    }

    /**
     * Converts raw 16kHz Float32 PCM audio into an 80-channel Log-Mel Spectrogram.
     */
    fun computeLogMelSpectrogram(samples: FloatArray): MelSpectrogram {
        val numFrames = max(1, (samples.size - N_FFT) / HOP_LENGTH + 1)
        val melData = FloatArray(N_MELS * numFrames)

        val frame = FloatArray(N_FFT)
        val powerSpectrum = FloatArray(NUM_FREQ_BINS)

        for (t in 0 until numFrames) {
            val sampleOffset = t * HOP_LENGTH

            // Apply Hann window
            for (i in 0 until N_FFT) {
                val idx = sampleOffset + i
                val s = if (idx < samples.size) samples[idx] else 0.0f
                frame[i] = s * hannWindow[i]
            }

            // High-speed table-driven DFT computation (< 1ms per frame)
            for (k in 0 until NUM_FREQ_BINS) {
                var sumReal = 0.0f
                var sumImag = 0.0f
                val cosRow = dftCosTable[k]
                val sinRow = dftSinTable[k]

                for (i in 0 until N_FFT) {
                    val s = frame[i]
                    sumReal += s * cosRow[i]
                    sumImag += s * sinRow[i]
                }
                powerSpectrum[k] = sumReal * sumReal + sumImag * sumImag
            }

            // Apply 80 Mel filterbanks (sparse multiplication)
            for (m in 0 until N_MELS) {
                var melSum = 0.0f
                val filter = melFilters[m]
                val start = filterStartBins[m]
                val end = filterEndBins[m]

                for (k in start until end) {
                    melSum += powerSpectrum[k] * filter[k]
                }
                // Log compression: log10(max(mel, 1e-5))
                val logMel = log10(max(melSum, 1e-5f))
                melData[m * numFrames + t] = logMel
            }
        }

        return MelSpectrogram(
            nMel = N_MELS,
            nFrames = numFrames,
            data = melData
        )
    }
}

