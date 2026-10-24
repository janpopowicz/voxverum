package pl.voxverum.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Model z pliku `.onnx` uruchamiany przez ONNX Runtime na CPU telefonu. */
class OnnxClassifier(
    private val env: OrtEnvironment,
    modelBytes: ByteArray,
    private val spec: ModelSpec,
) : AudioClassifier {
    private val session: OrtSession = env.createSession(
        modelBytes,
        OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(2)
        },
    )

    override val task = spec.task
    override val version = spec.version
    override val threshold = spec.threshold
    override val isMock = spec.version.startsWith("mock")

    init {
        require(spec.inputName in session.inputNames) {
            "Model ${spec.file} nie ma wejścia '${spec.inputName}' (ma: ${session.inputNames})"
        }
        require(spec.outputName in session.outputNames) {
            "Model ${spec.file} nie ma wyjścia '${spec.outputName}' (ma: ${session.outputNames})"
        }
    }

    override fun classify(window: FloatArray): Float {
        val input = ScoreMath.tail(window, spec.windowSamples)
        val shape = longArrayOf(1, spec.windowSamples.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(spec.inputName to tensor), setOf(spec.outputName)).use { result ->
                val raw = flatten(result.get(0).value)
                return ScoreMath.toProbability(raw, spec.outputType, spec.positiveIndex)
            }
        }
    }

    override fun close() = session.close()

    private fun flatten(value: Any): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { flatten(it!!).asList() }.toFloatArray()
        is Float -> floatArrayOf(value)
        else -> error("Nieobsługiwany typ wyjścia modelu: ${value::class.java}")
    }
}

/** Używany, gdy w assets nie ma modelu dla zadania: zawsze zwraca 0, więc nigdy nie alarmuje. */
class MissingModelClassifier(override val task: Task) : AudioClassifier {
    override val version = "missing"
    override val threshold = 1f
    override val isMock = true
    override fun classify(window: FloatArray) = 0f
}
