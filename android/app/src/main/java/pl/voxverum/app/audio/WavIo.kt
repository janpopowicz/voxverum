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
     * Czyta WAV: PCM 16/24/32-bit albo float32, także w nagłówku WAVE_FORMAT_EXTENSIBLE
     * (zapisują go m.in. ffmpeg i Audacity). Wiele kanałów uśrednia do mono.
     * Przeskakuje nieznane chunki (LIST itp.).
     */
    fun read(input: InputStream): WavData {
        val bytes = DataInputStream(input).use { it.readBytes() }
        require(bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
            "To nie jest plik WAV"
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var fmt: Format? = null
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val body = pos + 8
            // Rozmiar to uint32; nagrania strumieniowe wpisują 0xFFFFFFFF, więc przycinamy do pliku.
            val size = minOf(buf.getInt(pos + 4).toLong() and 0xFFFFFFFFL, (bytes.size - body).toLong()).toInt()
            when (id) {
                "fmt " -> fmt = parseFormat(buf, body, size)
                "data" -> {
                    val f = requireNotNull(fmt) { "Brak chunka fmt przed danymi" }
                    return WavData(f.sampleRate, decode(buf, body, size, f))
                }
            }
            pos = body + size + (size and 1)
        }
        error("Brak chunka data w pliku WAV")
    }

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    private class Format(val code: Int, val channels: Int, val sampleRate: Int, val bits: Int)

    private fun parseFormat(buf: ByteBuffer, body: Int, size: Int): Format {
        require(size >= 16) { "Uszkodzony chunk fmt" }
        var code = buf.getShort(body).toInt() and 0xFFFF
        // W EXTENSIBLE właściwy format to pierwsze 2 bajty GUID-u podformatu (offset 24).
        if (code == FORMAT_EXTENSIBLE && size >= 26) code = buf.getShort(body + 24).toInt() and 0xFFFF
        val f = Format(code, buf.getShort(body + 2).toInt(), buf.getInt(body + 4), buf.getShort(body + 14).toInt())
        val supported = (f.code == FORMAT_PCM && f.bits in setOf(16, 24, 32)) ||
            (f.code == FORMAT_FLOAT && f.bits == 32)
        require(supported) { "Nieobsługiwany WAV (format ${f.code}, ${f.bits} bit). Użyj PCM 16-bit albo float32." }
        require(f.channels >= 1 && f.sampleRate > 0) { "Uszkodzony chunk fmt" }
        return f
    }

    private fun decode(buf: ByteBuffer, body: Int, size: Int, f: Format): FloatArray {
        val bytesPerSample = f.bits / 8
        val frames = size / (bytesPerSample * f.channels)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var acc = 0f
            for (c in 0 until f.channels) {
                val at = body + (i * f.channels + c) * bytesPerSample
                acc += when {
                    f.code == FORMAT_FLOAT -> buf.getFloat(at)
                    f.bits == 16 -> buf.getShort(at) / 32768f
                    f.bits == 24 -> ((buf.get(at).toInt() and 0xFF) or
                        ((buf.get(at + 1).toInt() and 0xFF) shl 8) or
                        (buf.get(at + 2).toInt() shl 16)) / 8_388_608f
                    else -> (buf.getInt(at) / 2_147_483_648.0).toFloat()
                }
            }
            out[i] = (acc / f.channels).coerceIn(-1f, 1f)
        }
        return out
    }
}
