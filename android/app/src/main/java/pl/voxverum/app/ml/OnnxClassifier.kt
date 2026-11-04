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
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        options.setIntraOpNumThreads(2)
        env.createSession(modelBytes, options)
    }

    override val task = spec.task
    override val version = spec.version
    override val threshold = spec.threshold
    override val isMock = spec.version.startsWith("mock")
    override val windowSamples = spec.windowSamples
    private val lowPass = spec.lowpassHz?.let { LowPass(it, spec.sampleRate) }

    init {
        // Przy złym modelu zamykamy sesję od razu: konstruktor rzuci wyjątek, więc nikt inny jej nie zamknie.
        try {
            require(spec.inputName in session.inputNames) {
                "Model ${spec.file} nie ma wejścia '${spec.inputName}' (ma: ${session.inputNames})"
            }
            require(spec.outputName in session.outputNames) {
                "Model ${spec.file} nie ma wyjścia '${spec.outputName}' (ma: ${session.outputNames})"
            }
        } catch (e: IllegalArgumentException) {
            session.close()
            throw e
        }
    }

    override fun classify(window: FloatArray): Float {
        val tail = ScoreMath.tail(window, spec.windowSamples)
        val input = lowPass?.apply(tail) ?: tail
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

/**
 * Używany, gdy w assets nie ma modelu dla zadania: zawsze zwraca 0, więc nigdy nie alarmuje.
 * To nie atrapa (nie udaje wyników), dlatego [isMock] = false. UI rozpoznaje go po [VERSION].
 */
class MissingModelClassifier(override val task: Task) : AudioClassifier {
    override val version = VERSION
    override val threshold = 1f
    override val isMock = false
    override val windowSamples = 0
    override fun classify(window: FloatArray) = 0f

    companion object {
        const val VERSION = "missing"
    }
}
