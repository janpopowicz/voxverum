package pl.voxverum.app.ml

import kotlin.math.exp

/** Klasyfikator okna audio. Zwraca prawdopodobieństwo klasy pozytywnej (klon / wyłudzenie). */
interface AudioClassifier : AutoCloseable {
    val task: Task
    val version: String
    val threshold: Float
    val isMock: Boolean

    /** [window] to 16 kHz mono float; klasyfikator sam bierze z niego tyle, ile potrzebuje. */
    fun classify(window: FloatArray): Float

    override fun close() = Unit
}

object ScoreMath {
    /** Zamienia surowe wyjście modelu na prawdopodobieństwo klasy [positiveIndex]. */
    fun toProbability(output: FloatArray, type: OutputType, positiveIndex: Int): Float {
        require(output.isNotEmpty()) { "Puste wyjście modelu" }
        return when (type) {
            OutputType.PROBABILITY ->
                (if (output.size == 1) output[0] else output[positiveIndex]).coerceIn(0f, 1f)
            OutputType.LOGITS ->
                if (output.size == 1) sigmoid(output[0]) else softmax(output)[positiveIndex]
        }
    }

    fun sigmoid(x: Float): Float = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()

    fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = DoubleArray(logits.size) { exp((logits[it] - max).toDouble()) }
        val sum = exps.sum()
        return FloatArray(logits.size) { (exps[it] / sum).toFloat() }
    }

    /**
     * Ostatnie [length] próbek okna. Jeśli okno jest krótsze, dopełnia zerami z przodu,
     * żeby model zawsze dostał wejście stałej długości.
     */
    fun tail(window: FloatArray, length: Int): FloatArray {
        if (window.size == length) return window
        val out = FloatArray(length)
        val n = minOf(length, window.size)
        System.arraycopy(window, window.size - n, out, length - n, n)
        return out
    }
}
