package pl.voxverum.app.ml

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Na urządzeniu: modele z assets ładują się przez ONNX Runtime i zwracają wynik 0…1. */
@RunWith(AndroidJUnit4::class)
class ModelRegistryTest {
    @Test
    fun loadsBothModelsAndClassifies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val models = ModelRegistry.load(context)

        assertEquals(Task.entries.toSet(), models.keys)
        for ((task, model) in models) {
            assertTrue("$task powinien być modelem ONNX", model is OnnxClassifier)
            val quiet = model.classify(FloatArray(64_000))
            val loud = model.classify(FloatArray(64_000) { if (it % 2 == 0) 0.3f else -0.3f })
            assertTrue(quiet in 0f..1f && loud in 0f..1f)
        }
    }
}
