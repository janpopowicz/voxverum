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
 * - DANGER: co najmniej [dangerHits] z ostatnich [historySize] okien ≥ t. Po wejściu
 *   w DANGER poziom zostaje do końca rozmowy: raz ostrzeżony użytkownik ma widzieć ostrzeżenie.
 * - WARN: jedno okno ≥ t albo EMA ≥ [warnRatio]·t. Schodzi do OK dopiero, gdy EMA spadnie
 *   poniżej [releaseRatio]·t (histereza, bez migania baneru).
 * - OK: w pozostałych przypadkach.
 */
class RiskEngine(
    private val cloneThreshold: Float,
    private val scamThreshold: Float,
    private val alpha: Float = 0.4f,
    private val historySize: Int = 3,
    private val dangerHits: Int = 2,
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
            val hits = history.count { it }

            level = when {
                level == RiskLevel.DANGER || hits >= dangerHits -> RiskLevel.DANGER
                hits >= 1 || smoothed >= warnRatio * threshold -> RiskLevel.WARN
                level == RiskLevel.WARN && smoothed >= releaseRatio * threshold -> RiskLevel.WARN
                else -> RiskLevel.OK
            }
            return ModelRisk(level, smoothed, score, peak)
        }
    }
}
