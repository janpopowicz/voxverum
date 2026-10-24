package pl.voxverum.app.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSpecTest {
    @Test
    fun `parses full spec`() {
        val spec = ModelSpec.parse(
            """
            {"task": "voice_clone", "file": "vc.onnx", "version": "2026.11.1", "sample_rate": 16000,
             "window_samples": 48000, "input_name": "wave", "output_name": "logits",
             "output_type": "logits", "positive_index": 1, "threshold": 0.7}
            """,
        )
        assertEquals(Task.VOICE_CLONE, spec.task)
        assertEquals("vc.onnx", spec.file)
        assertEquals(48_000, spec.windowSamples)
        assertEquals("wave", spec.inputName)
        assertEquals(OutputType.LOGITS, spec.outputType)
        assertEquals(0.7f, spec.threshold, 1e-6f)
    }

    @Test
    fun `fills defaults`() {
        val spec = ModelSpec.parse("""{"task": "scam", "file": "s.onnx", "window_samples": 64000}""")
        assertEquals("audio", spec.inputName)
        assertEquals("score", spec.outputName)
        assertEquals(OutputType.PROBABILITY, spec.outputType)
        assertEquals(0.5f, spec.threshold, 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects other sample rates`() {
        ModelSpec.parse("""{"task": "scam", "file": "s.onnx", "window_samples": 1, "sample_rate": 8000}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown task`() {
        ModelSpec.parse("""{"task": "music", "file": "s.onnx", "window_samples": 1}""")
    }
}
