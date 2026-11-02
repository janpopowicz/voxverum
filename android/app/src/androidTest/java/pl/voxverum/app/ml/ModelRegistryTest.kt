package pl.voxverum.app.ml

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/** Na urządzeniu: modele z assets ładują się przez ONNX Runtime i zwracają wynik 0…1. */
@RunWith(AndroidJUnit4::class)
class ModelRegistryTest {
    @Test
    fun loadsModelsAndClassifies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val models = ModelRegistry.load(context)

        assertEquals(Task.entries.toSet(), models.keys)
        assertTrue("voice_clone powinien być modelem ONNX", models.getValue(Task.VOICE_CLONE) is OnnxClassifier)
        for ((task, model) in models) {
            if (model !is OnnxClassifier) continue
            val quiet = model.classify(ModelRegistry.probeWindow(64_000))
            val loud = model.classify(FloatArray(64_000) { if (it % 2 == 0) 0.3f else -0.3f })
            assertTrue("$task: $quiet, $loud", quiet in 0f..1f && loud in 0f..1f)
        }
    }

    @Test
    fun motherVoiceMatchesPyTorch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = ModelRegistry.load(context).getValue(Task.VOICE_CLONE)
        assertEquals("mother-voice-v1", model.version)
        // Oczekiwane wyniki policzył oryginalny MotherVoice w PyTorch (scripts/mother_voice.py) dla
        // tego samego sygnału. Rozjazd oznacza, że eksport albo ONNX Runtime na Androidzie coś psuje.
        assertEquals(0.821349f, model.classify(toneWithNoise(tone = 0.1, noise = 0.1)), 1e-3f)
        assertEquals(0.051688f, model.classify(toneWithNoise(tone = 0.2, noise = 0.3)), 1e-3f)
    }

    /** Ton 220 Hz + szum z prostego LCG, żeby ten sam sygnał dało się odtworzyć w Pythonie. */
    private fun toneWithNoise(tone: Double, noise: Double): FloatArray {
        var state = 1L
        return FloatArray(48_000) { i ->
            state = (state * 1103515245L + 12345L) % (1L shl 31)
            val n = state.toDouble() / (1L shl 31) - 0.5
            (tone * sin(2 * PI * 220 * i / 16_000) + noise * n).toFloat()
        }
    }
}
