package pl.voxverum.app.session

import android.content.Context
import android.util.Log
import pl.voxverum.app.audio.WavIo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tylko do testu wykonalności w wersji debug: zbiera pierwsze [seconds] s rozmowy
 * i zapisuje je jako WAV w katalogu aplikacji (`Android/data/pl.voxverum.app/files/debug`).
 * Wyłączone domyślnie, a w wersji release nigdy się nie uruchamia.
 */
class DebugDumper(
    private val context: Context,
    private val sampleRate: Int,
    private val sourceName: String,
    seconds: Int = 10,
) {
    private val buffer = FloatArray(sampleRate * seconds)
    private var filled = 0
    private var written = false

    fun add(chunk: FloatArray) {
        if (written) return
        val n = minOf(chunk.size, buffer.size - filled)
        System.arraycopy(chunk, 0, buffer, filled, n)
        filled += n
        if (filled == buffer.size) flush()
    }

    fun flush() {
        if (written || filled == 0) return
        written = true
        val dir = File(context.getExternalFilesDir(null), "debug").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "call_${stamp}_$sourceName.wav")
        WavIo.write(file, buffer.copyOf(filled), sampleRate)
        Log.i("VoxVerum", "Debug dump saved: ${file.absolutePath}")
    }
}
