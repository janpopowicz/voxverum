package pl.voxverum.app.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Prosty detektor mowy oparty na energii. Wystarcza, żeby nie puszczać ciszy przez modele;
 * gdy zespół będzie miał lepszy VAD (np. Silero w ONNX), podmieniamy tę klasę.
 */
object Vad {
    const val SILENCE_DB = -100f

    fun rmsDb(samples: FloatArray, from: Int = 0, to: Int = samples.size): Float {
        if (to <= from) return SILENCE_DB
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        val rms = sqrt(sum / (to - from))
        return max(SILENCE_DB, (20 * log10(max(rms, 1e-10))).toFloat())
    }

    /** Odsetek ramek 20 ms, których głośność przekracza [thresholdDb]. */
    fun speechRatio(
        window: FloatArray,
        sampleRate: Int,
        frameMs: Int = 20,
        thresholdDb: Float = -45f,
    ): Float {
        val frame = sampleRate * frameMs / 1000
        if (frame <= 0 || window.size < frame) return 0f
        var frames = 0
        var loud = 0
        var start = 0
        while (start + frame <= window.size) {
            if (rmsDb(window, start, start + frame) > thresholdDb) loud++
            frames++
            start += frame
        }
        return loud.toFloat() / frames
    }

    fun isSpeech(window: FloatArray, sampleRate: Int, minRatio: Float = 0.2f): Boolean =
        speechRatio(window, sampleRate) >= minRatio
}
