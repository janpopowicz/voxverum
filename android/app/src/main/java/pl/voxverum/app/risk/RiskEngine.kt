package pl.voxverum.app.risk

enum class RiskLevel { UNKNOWN, OK, WARN, DANGER }

data class ModelRisk(
    val level: RiskLevel = RiskLevel.UNKNOWN,
    /** Wygładzony wynik (EMA). */
    val smoothed: Float = 0f,
    val last: Float = 0f,
    val peak: Float = 0f,
)

data class RiskAssessment(
    val clone: ModelRisk = ModelRisk(),
    val scam: ModelRisk = ModelRisk(),
    val windows: Int = 0,
) {
    val overall: RiskLevel get() = maxOf(clone.level, scam.level)
}

/**
 * Zamienia wyniki pojedynczych okien na stabilny poziom ryzyka, żeby jedno dziwne okno
 * nie odpalało alarmu.
 *
 * Reguły dla każdego modelu (próg `t` z pliku `.json` modelu):
 * - DANGER: wśród ostatnich [historySize] okien są dwa okna ≥ t odległe o co najmniej
 *   [independentGap] okien. Okna mają 4 s z krokiem 1 s, a model widzi ostatnie 3 s, więc trzy
 *   kolejne okna dzielą ten sam fragment dźwięku. Jeden krótki dziwny dźwięk (stuk, trzask)
 *   trafia w trzy okna z rzędu i sam nie odpali alarmu. Po wejściu w DANGER poziom zostaje
 *   do końca rozmowy: raz ostrzeżony użytkownik ma widzieć ostrzeżenie.
 * - WARN: okno ≥ t wśród ostatnich [independentGap] albo EMA ≥ [warnRatio]·t. Schodzi do OK dopiero, gdy EMA spadnie
 *   poniżej [releaseRatio]·t (histereza, bez migania baneru).
 * - OK: w pozostałych przypadkach.
 */
class RiskEngine(
    private val cloneThreshold: Float,
    private val scamThreshold: Float,
    private val alpha: Float = 0.4f,
    private val historySize: Int = 6,
    private val independentGap: Int = 3,
    private val warnRatio: Float = 0.7f,
    private val releaseRatio: Float = 0.5f,
) {
    private val clone = Tracker(cloneThreshold)
    private val scam = Tracker(scamThreshold)
    private var windows = 0

    fun update(cloneScore: Float, scamScore: Float): RiskAssessment {
        windows++
        return RiskAssessment(clone.update(cloneScore), scam.update(scamScore), windows)
    }

    private inner class Tracker(private val threshold: Float) {
        private val history = ArrayDeque<Boolean>()
        private var ema: Float? = null
        private var level = RiskLevel.UNKNOWN
        private var peak = 0f

        fun update(score: Float): ModelRisk {
            val smoothed = ema?.let { alpha * score + (1 - alpha) * it } ?: score
            ema = smoothed
            peak = maxOf(peak, score)
            history.addLast(score >= threshold)
            if (history.size > historySize) history.removeFirst()
            val first = history.indexOf(true)
            val last = history.lastIndexOf(true)
            // Dwa trafienia odległe o ≥ independentGap okien pochodzą z różnych fragmentów dźwięku.
            val independentHits = first >= 0 && last - first >= independentGap
            // Trafienie, którego fragment dźwięku model nadal widzi w bieżącym oknie.
            val recentHit = last >= 0 && history.size - 1 - last < independentGap

            level = when {
                level == RiskLevel.DANGER || independentHits -> RiskLevel.DANGER
                recentHit || smoothed >= warnRatio * threshold -> RiskLevel.WARN
                level == RiskLevel.WARN && smoothed >= releaseRatio * threshold -> RiskLevel.WARN
                else -> RiskLevel.OK
            }
            return ModelRisk(level, smoothed, score, peak)
        }
    }
}
