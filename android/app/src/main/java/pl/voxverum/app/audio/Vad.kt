package pl.voxverum.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Wynik detektora dla okna: odsetek ramek z mową i odsetek ramek ze stałym tonem. */
data class VadResult(val speechRatio: Float, val toneRatio: Float) {
    /**
     * Okno idzie do modeli, gdy jest w nim dość mowy i prawie nie ma stałych tonów.
     * Ton (sygnał wołania, brum sieci, piszczenie) Mother Voice ocenia jako syntetyczny głos
     * z wynikiem 1,0, także zmieszany z mową, więc takie okno lepiej pominąć.
     */
    val isSpeech get() = speechRatio >= MIN_SPEECH && toneRatio <= MAX_TONE

    companion object {
        const val MIN_SPEECH = 0.2f
        const val MAX_TONE = 0.1f
    }
}

/**
 * Prosty detektor mowy: głośność ramek 20 ms plus wykrywanie stałych tonów.
 * Ton rozpoznajemy po tym, że jeden prążek widma dominuje w ramce i stoi w miejscu przez
 * co najmniej [TONE_RUN_FRAMES] ramek. W mowie wysokość i samogłoski zmieniają się szybciej.
 * Gdy zespół będzie miał lepszy VAD (np. Silero w ONNX), podmieniamy tę klasę.
 */
object Vad {
    const val SILENCE_DB = -100f
    private const val FRAME_MS = 20
    private const val LOUD_DB = -45f
    private const val FFT_SIZE = 512
    /** Pomijamy prążki poniżej ok. 90 Hz (stała składowa, najniższy brum). */
    private const val MIN_BIN = 3
    /** Udział szczytu (±1 prążek) w energii ramki, od którego ramka wygląda na ton. */
    private const val PEAK_SHARE = 0.5f
    /**
     * 25 ramek po 20 ms = 500 ms tego samego tonu. Przy 300 ms przeciągnięte samogłoski prawdziwej mowy
     * (zwłaszcza z pogłosem) odpadały w do 40% okien; sygnał wołania trwa 1 s, więc 500 ms go nadal łapie.
     */
    const val TONE_RUN_FRAMES = 25

    fun rmsDb(samples: FloatArray, from: Int = 0, to: Int = samples.size): Float {
        if (to <= from) return SILENCE_DB
        var sum = 0.0
        for (i in from until to) sum += samples[i] * samples[i]
        val rms = sqrt(sum / (to - from))
        return max(SILENCE_DB, (20 * log10(max(rms, 1e-10))).toFloat())
    }

    fun analyze(window: FloatArray, sampleRate: Int): VadResult {
        val frame = sampleRate * FRAME_MS / 1000
        val count = if (frame in 1..FFT_SIZE) window.size / frame else 0
        if (count == 0) return VadResult(0f, 0f)

        val loud = BooleanArray(count)
        val peakBin = IntArray(count)
        val peaky = BooleanArray(count)
        val spectrum = Spectrum(frame)
        for (f in 0 until count) {
            val start = f * frame
            loud[f] = rmsDb(window, start, start + frame) > LOUD_DB
            if (!loud[f]) continue
            val (bin, share) = spectrum.peak(window, start)
            peakBin[f] = bin
            peaky[f] = share > PEAK_SHARE
        }

        // Ciągi ramek z tym samym szczytem (±1 prążek) dłuższe niż TONE_RUN_FRAMES to ton.
        val tone = BooleanArray(count)
        var runStart = 0
        for (f in 1..count) {
            val continues = f < count && peaky[f] && peaky[f - 1] && abs(peakBin[f] - peakBin[f - 1]) <= 1
            if (continues) continue
            if (peaky[runStart] && f - runStart >= TONE_RUN_FRAMES) tone.fill(true, runStart, f)
            runStart = f
        }

        var speech = 0
        var tones = 0
        for (f in 0 until count) {
            if (!loud[f]) continue
            if (tone[f]) tones++ else speech++
        }
        return VadResult(speech.toFloat() / count, tones.toFloat() / count)
    }

    fun isSpeech(window: FloatArray, sampleRate: Int): Boolean = analyze(window, sampleRate).isSpeech

    /** Widmo mocy ramki (okno Hanna, FFT 512 z dopełnieniem zerami) i jego najsilniejszy prążek. */
    private class Spectrum(private val frame: Int) {
        private val hann = FloatArray(frame) { (0.5 - 0.5 * cos(2 * PI * it / (frame - 1))).toFloat() }
        private val re = DoubleArray(FFT_SIZE)
        private val im = DoubleArray(FFT_SIZE)
        private val twiddles = Twiddles(FFT_SIZE)

        /** Zwraca (prążek szczytu, udział szczytu ±1 prążek w energii pasma od [MIN_BIN]). */
        fun peak(samples: FloatArray, start: Int): Pair<Int, Float> {
            re.fill(0.0)
            im.fill(0.0)
            for (i in 0 until frame) re[i] = (samples[start + i] * hann[i]).toDouble()
            fft(re, im, twiddles)
            val power = DoubleArray(FFT_SIZE / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
            var total = 0.0
            var best = MIN_BIN
            for (k in MIN_BIN until power.size) {
                total += power[k]
                if (power[k] > power[best]) best = k
            }
            if (total <= 0.0) return best to 0f
            val lobe = power[best] + power.getOrElse(best + 1) { 0.0 } +
                (if (best - 1 >= MIN_BIN) power[best - 1] else 0.0)
            return best to (lobe / total).toFloat()
        }
    }

    /** cos/sin kątów -2πk/n, k < n/2, policzone raz zamiast w każdym motylku FFT. */
    internal class Twiddles(val n: Int) {
        val re = DoubleArray(n / 2) { cos(-2 * PI * it / n) }
        val im = DoubleArray(n / 2) { sin(-2 * PI * it / n) }
    }

    /** FFT radix-2 w miejscu; rozmiar musi być potęgą dwójki. */
    internal fun fft(re: DoubleArray, im: DoubleArray, tw: Twiddles = Twiddles(re.size)) {
        val n = re.size
        require(tw.n == n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val stride = n / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = tw.re[k * stride]
                    val wi = tw.im[k * stride]
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
            }
            len = len shl 1
        }
    }
}
