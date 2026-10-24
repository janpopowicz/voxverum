package pl.voxverum.app.ml

import org.json.JSONObject

/** Do czego służy model. Każde zadanie ma w aplikacji dokładnie jeden aktywny model. */
enum class Task(val id: String) {
    VOICE_CLONE("voice_clone"),
    SCAM("scam");

    companion object {
        fun fromId(id: String): Task = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Nieznane zadanie modelu: $id")
    }
}

enum class OutputType(val id: String) {
    /** Wyjście to już prawdopodobieństwo klasy pozytywnej (0…1). */
    PROBABILITY("probability"),
    /** Wyjście to logity; aplikacja liczy softmax (albo sigmoid dla jednego logitu). */
    LOGITS("logits");

    companion object {
        fun fromId(id: String): OutputType = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Nieznany output_type: $id")
    }
}

/**
 * Opis modelu z pliku `assets/models/<nazwa>.json`. Format opisuje `models/README.md`
 * w katalogu głównym repo, a generuje go `tools/model_export/export_onnx.py`.
 */
data class ModelSpec(
    val task: Task,
    val file: String,
    val version: String,
    val sampleRate: Int,
    val windowSamples: Int,
    val inputName: String,
    val outputName: String,
    val outputType: OutputType,
    val positiveIndex: Int,
    val threshold: Float,
) {
    companion object {
        fun parse(json: String): ModelSpec {
            val o = JSONObject(json)
            val spec = ModelSpec(
                task = Task.fromId(o.getString("task")),
                file = o.getString("file"),
                version = o.optString("version", "unknown"),
                sampleRate = o.optInt("sample_rate", 16_000),
                windowSamples = o.getInt("window_samples"),
                inputName = o.optString("input_name", "audio"),
                outputName = o.optString("output_name", "score"),
                outputType = OutputType.fromId(o.optString("output_type", "probability")),
                positiveIndex = o.optInt("positive_index", 1),
                threshold = o.optDouble("threshold", 0.5).toFloat(),
            )
            require(spec.sampleRate == 16_000) { "Aplikacja nagrywa 16 kHz, a model chce ${spec.sampleRate} Hz" }
            require(spec.windowSamples > 0) { "window_samples musi być dodatnie" }
            require(spec.threshold in 0f..1f) { "threshold musi być w zakresie 0…1" }
            return spec
        }
    }
}
