package pl.voxverum.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavIoTest {
    /** Buduje plik WAV z podanym chunkiem fmt i danymi. */
    private fun wav(fmt: ByteArray, data: ByteArray, dataSize: Int = data.size, extraChunk: Boolean = false): ByteArray {
        val list = if (extraChunk) "LIST".toByteArray() + le32(3) + byteArrayOf(1, 2, 3, 0) else ByteArray(0)
        val body = "WAVE".toByteArray() +
            "fmt ".toByteArray() + le32(fmt.size) + fmt +
            list +
            "data".toByteArray() + le32(dataSize) + data
        return "RIFF".toByteArray() + le32(body.size) + body
    }

    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun fmt(code: Int, channels: Int, rate: Int, bits: Int, extensibleSub: Int? = null): ByteArray {
        val size = if (extensibleSub != null) 40 else 16
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(code.toShort()).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * bits / 8).putShort((channels * bits / 8).toShort()).putShort(bits.toShort())
        if (extensibleSub != null) {
            b.putShort(22).putShort(bits.toShort()).putInt(0).putShort(extensibleSub.toShort())
        }
        return b.array()
    }

    private fun read(bytes: ByteArray) = WavIo.read(ByteArrayInputStream(bytes))

    @Test
    fun `pcm16 round trip through file`() {
        val file = File.createTempFile("vox", ".wav")
        try {
            val samples = floatArrayOf(0f, 0.5f, -0.5f, 0.25f)
            WavIo.write(file, samples, 16_000)
            val back = file.inputStream().use { WavIo.read(it) }
            assertEquals(16_000, back.sampleRate)
            assertArrayEquals(samples, back.samples, 1e-4f)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `float32 stereo in extensible header is averaged to mono`() {
        val data = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0.2f).putFloat(0.4f).putFloat(-1f).putFloat(0f).array()
        val w = read(wav(fmt(0xFFFE, 2, 48_000, 32, extensibleSub = 3), data, extraChunk = true))
        assertEquals(48_000, w.sampleRate)
        assertArrayEquals(floatArrayOf(0.3f, -0.5f), w.samples, 1e-6f)
    }

    @Test
    fun `pcm24 decodes sign correctly`() {
        // 0x400000 = +0.5, 0xC00000 = -0.5
        val data = byteArrayOf(0, 0, 0x40, 0, 0, 0xC0.toByte())
        val w = read(wav(fmt(1, 1, 16_000, 24), data))
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), w.samples, 1e-6f)
    }

    @Test
    fun `streaming data size is clamped to file length`() {
        val data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(16384).putShort(-16384).array()
        val w = read(wav(fmt(1, 1, 16_000, 16), data, dataSize = -1)) // 0xFFFFFFFF
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), w.samples, 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `8 bit pcm is rejected with a message`() {
        read(wav(fmt(1, 1, 8_000, 8), byteArrayOf(1, 2)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non wav is rejected`() {
        read("hello world, not a wav".toByteArray())
    }
}
