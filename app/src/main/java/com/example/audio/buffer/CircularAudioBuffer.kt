package com.example.audio.buffer

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * Thread-safe Circular Ring Buffer for 16kHz mono 16-bit PCM / Float32 audio samples.
 * Modeled after FUTO Voice Input audio capture architecture to prevent dropouts,
 * audio glitches, and decouple mic ingestion from VAD & transformer inference.
 */
class CircularAudioBuffer(val capacity: Int = 16000 * 10) { // 10 seconds of 16kHz audio

    private val buffer = FloatArray(capacity)
    private var writeHead = 0
    private var readHead = 0
    private var availableSamples = 0
    private val lock = ReentrantLock()

    /**
     * Write float samples into the circular buffer.
     * If capacity is exceeded, oldest unread samples are safely overwritten.
     */
    fun write(samples: FloatArray, count: Int = samples.size) {
        lock.withLock {
            for (i in 0 until count) {
                buffer[writeHead] = samples[i]
                writeHead = (writeHead + 1) % capacity
                if (availableSamples < capacity) {
                    availableSamples++
                } else {
                    // Overwriting oldest sample: advance read head
                    readHead = (readHead + 1) % capacity
                }
            }
        }
    }

    /**
     * Read up to [count] samples from the circular buffer without blocking.
     * Returns the actual number of samples read.
     */
    fun read(outBuffer: FloatArray, count: Int): Int {
        lock.withLock {
            val toRead = min(count, availableSamples)
            for (i in 0 until toRead) {
                outBuffer[i] = buffer[readHead]
                readHead = (readHead + 1) % capacity
            }
            availableSamples -= toRead
            return toRead
        }
    }

    /**
     * Peek [count] samples without advancing the read head (useful for sliding VAD frames).
     */
    fun peek(outBuffer: FloatArray, count: Int): Int {
        lock.withLock {
            val toPeek = min(count, availableSamples)
            var current = readHead
            for (i in 0 until toPeek) {
                outBuffer[i] = buffer[current]
                current = (current + 1) % capacity
            }
            return toPeek
        }
    }

    /**
     * Returns the number of unread samples currently stored in the buffer.
     */
    fun available(): Int {
        lock.withLock {
            return availableSamples
        }
    }

    /**
     * Clears all samples in the buffer.
     */
    fun clear() {
        lock.withLock {
            writeHead = 0
            readHead = 0
            availableSamples = 0
        }
    }
}
