package pl.voxverum.app.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Tnie strumień próbek na nakładające się okna: długość [windowSize], krok [hopSize].
 * Bufor kołowy, więc koszt to O(1) na próbkę + kopia okna przy emisji.
 */
class AudioWindower(private val windowSize: Int, private val hopSize: Int) {
    init {
        require(windowSize > 0 && hopSize > 0) { "windowSize i hopSize muszą być dodatnie" }
    }

    private val ring = FloatArray(windowSize)
    private var writePos = 0
    private var total = 0L
    private var sinceEmit = 0

    inline fun push(chunk: FloatArray, emit: (FloatArray) -> Unit) {
        for (sample in chunk) {
            if (pushSample(sample)) emit(snapshot())
        }
    }

    /** Zwraca true, gdy po tej próbce należy wyemitować okno. */
    fun pushSample(sample: Float): Boolean {
        ring[writePos] = sample
        writePos = (writePos + 1) % windowSize
        total++
        sinceEmit++
        if (total >= windowSize && sinceEmit >= hopSize) {
            sinceEmit = 0
            return true
        }
        return false
    }

    fun snapshot(): FloatArray {
        val out = FloatArray(windowSize)
        val tail = windowSize - writePos
        System.arraycopy(ring, writePos, out, 0, tail)
        System.arraycopy(ring, 0, out, tail, writePos)
        return out
    }

    fun clear() {
        ring.fill(0f)
        writePos = 0
        total = 0
        sinceEmit = 0
    }
}

fun Flow<FloatArray>.windowed(windowSize: Int, hopSize: Int): Flow<FloatArray> = flow {
    val windower = AudioWindower(windowSize, hopSize)
    try {
        collect { chunk -> windower.push(chunk) { emit(it) } }
    } finally {
        windower.clear()
    }
}
