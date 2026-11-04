package pl.voxverum.app.ml

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Filtr dolnoprzepustowy Butterwortha 4. rzędu: dwie sekcje bikwadratowe z transformacji biliniowej,
 * tak jak `scipy.signal.butter(4, cutoffHz, fs = sampleRate)`. Każde okno filtrujemy od zera,
 * tak samo jak `tools/clone_tests/ocena.py --dolno`.
 *
 * Po co: Mother Voice v1 reaguje na pasmo powyżej ok. 7 kHz. Na prawdziwych głosach z FLEURS dawał
 * wynik ≥ progu w prawie każdym oknie, a po odcięciu 7 kHz w ok. 1/5 okien, przy tym samym wykrywaniu
 * klonów Chatterbox (docs/testy-klonu.md).
 */
class LowPass(cutoffHz: Float, sampleRate: Int) {
    /** Współczynniki sekcji: b0, b1, b2, a1, a2 (a0 = 1). */
    private val sections: List<DoubleArray>

    init {
        require(cutoffHz > 0f && cutoffHz < sampleRate / 2f) { "lowpass_hz musi być w zakresie 0…${sampleRate / 2} Hz" }
        val w0 = 2 * PI * cutoffHz / sampleRate
        val c = cos(w0)
        // Dobroć sekcji Butterwortha 4. rzędu: 1 / (2·cos(π/8)) i 1 / (2·cos(3π/8)).
        sections = listOf(PI / 8, 3 * PI / 8).map { angle ->
            val alpha = sin(w0) * cos(angle)
            val a0 = 1 + alpha
            doubleArrayOf((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }
    }

    fun apply(input: FloatArray): FloatArray {
        var x = DoubleArray(input.size) { input[it].toDouble() }
        for (s in sections) {
            val y = DoubleArray(x.size)
            var z1 = 0.0
            var z2 = 0.0
            // Postać kanoniczna transponowana (jak scipy.signal.sosfilt).
            for (i in x.indices) {
                val out = s[0] * x[i] + z1
                z1 = s[1] * x[i] - s[3] * out + z2
                z2 = s[2] * x[i] - s[4] * out
                y[i] = out
            }
            x = y
        }
        return FloatArray(x.size) { x[it].toFloat() }
    }
}
