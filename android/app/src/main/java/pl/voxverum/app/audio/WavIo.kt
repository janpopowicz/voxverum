package pl.voxverum.app.audio

import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimalny zapis/odczyt WAV PCM 16-bit mono. Wystarcza do zrzutów debug i trybu symulacji. */
object WavIo {
    class WavData(val sampleRate: Int, val samples: FloatArray)

    /** Zapisuje próbki do pliku WAV 16-bit. */
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        val dataBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataBytes)
        for (s in samples) buffer.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        RandomAccessFile(file, "rw").use { it.setLength(0); it.write(buffer.array()) }
    }

    /**
     * Czyta WAV PCM 16-bit (mono albo stereo, wtedy uśrednia kanały).
     * Przeskakuje nieznane chunki (LIST itp.).
     */
    fun read(input: InputStream): WavData {
        val bytes = DataInputStream(input).use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "To nie jest plik WAV" }
        var pos = 12
        var channels = 1
        var sampleRate = 16_000
        var bits = 16
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    require(buf.getShort(body).toInt() == 1) { "Obsługiwany jest tylko PCM" }
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    require(bits == 16) { "Obsługiwany jest tylko PCM 16-bit (jest $bits)" }
                    val end = minOf(body + size, bytes.size)
                    val frames = (end - body) / (2 * channels)
                    val out = FloatArray(frames)
                    for (f in 0 until frames) {
                        var acc = 0f
                        for (c in 0 until channels) acc += buf.getShort(body + (f * channels + c) * 2)
                        out[f] = acc / channels / Short.MAX_VALUE
                    }
                    return WavData(sampleRate, out)
                }
            }
            pos = body + size + (size and 1)
        }
        error("Brak chunka data w pliku WAV")
    }
}
