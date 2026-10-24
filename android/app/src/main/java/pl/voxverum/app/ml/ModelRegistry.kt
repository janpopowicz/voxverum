package pl.voxverum.app.ml

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.util.Log

/**
 * Wczytuje modele z `assets/models/`. Każdy model to para `<nazwa>.json` + plik `.onnx`
 * wskazany w polu `file`. Żeby podmienić model, wystarczy podmienić te dwa pliki.
 */
object ModelRegistry {
    private const val TAG = "VoxVerum"
    private const val DIR = "models"

    @Volatile
    private var cached: Map<Task, AudioClassifier>? = null

    fun load(context: Context): Map<Task, AudioClassifier> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = loadFromAssets(context.applicationContext)
            cached = loaded
            return loaded
        }
    }

    private fun loadFromAssets(context: Context): Map<Task, AudioClassifier> {
        val env = OrtEnvironment.getEnvironment()
        val assets = context.assets
        val result = mutableMapOf<Task, AudioClassifier>()
        val specs = assets.list(DIR).orEmpty().filter { it.endsWith(".json") }
        for (specFile in specs) {
            runCatching {
                val spec = ModelSpec.parse(assets.open("$DIR/$specFile").bufferedReader().use { it.readText() })
                val bytes = assets.open("$DIR/${spec.file}").use { it.readBytes() }
                if (spec.task in result) {
                    Log.w(TAG, "Drugi model dla ${spec.task} ($specFile) zignorowany")
                    return@runCatching
                }
                val classifier = OnnxClassifier(env, bytes, spec)
                // Próbne uruchomienie na ciszy: błąd w .json (długość okna, positive_index, kształt
                // wyjścia) wychodzi teraz, a nie w połowie rozmowy. Przy okazji rozgrzewa ONNX Runtime.
                val probe = runCatching { classifier.classify(FloatArray(spec.windowSamples)) }
                    .onFailure { classifier.close() }
                    .getOrThrow()
                check(probe in 0f..1f) { "Model ${spec.file} zwrócił $probe zamiast wartości 0…1" }
                result[spec.task] = classifier
                Log.i(TAG, "Loaded model ${spec.task} ${spec.version} from ${spec.file}")
            }.onFailure { Log.e(TAG, "Failed to load model $specFile", it) }
        }
        for (task in Task.entries) {
            result.getOrPut(task) {
                Log.w(TAG, "No model for $task, using MissingModelClassifier")
                MissingModelClassifier(task)
            }
        }
        return result
    }
}
