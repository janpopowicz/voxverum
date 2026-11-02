package pl.voxverum.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Mikrofon przez `AudioRecord`, 16 kHz mono float.
 *
 * Domyślnie bierzemy `VOICE_RECOGNITION` (mniej przetwarzania niż `MIC` na wielu telefonach),
 * a gdy się nie uda, przechodzimy na drugie źródło. Przez `AudioRecordingCallback` śledzimy,
 * czy system nas nie wyciszył: tak się dzieje, gdy Android nie pozwala współdzielić mikrofonu
 * z rozmową VoIP (np. nakładka nie jest na wierzchu).
 */
class MicAudioSource(
    private val context: Context,
    private val preferredSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
) : AudioSource {
    override val sampleRate = SAMPLE_RATE
    override var name: String = sourceName(preferredSource)
        private set

    private val _silenced = MutableStateFlow(false)
    val silenced: StateFlow<Boolean> = _silenced.asStateFlow()

    override fun frames(): Flow<FloatArray> = flow {
        check(hasPermission()) { "Brak uprawnienia RECORD_AUDIO" }
        val record = open()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val mine = configs.firstOrNull { it.clientAudioSessionId == record.audioSessionId }
                    ?: return
                _silenced.value = mine.isClientSilenced
            }
        }
        record.registerAudioRecordingCallback(context.mainExecutor, callback)
        try {
            record.startRecording()
            val buffer = FloatArray(CHUNK_SAMPLES)
            while (currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> emit(buffer.copyOf(read))
                    read < 0 -> error("AudioRecord.read zwrócił błąd $read")
                }
            }
        } finally {
            record.unregisterAudioRecordingCallback(callback)
            runCatching { record.stop() }
            record.release()
            _silenced.value = false
            Log.i(TAG, "Mic released (audioManager mode=${audioManager.mode})")
        }
    }.flowOn(Dispatchers.IO)

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // sprawdzane w hasPermission()
    private fun open(): AudioRecord {
        val candidates = listOf(preferredSource) + FALLBACK_SOURCES.filter { it != preferredSource }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufferBytes = maxOf(minBuffer * 2, CHUNK_SAMPLES * Float.SIZE_BYTES * 4)
        for (source in candidates) {
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            }.getOrNull() ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                name = sourceName(source)
                Log.i(TAG, "Recording from $name")
                return record
            }
            record.release()
        }
        error("Nie udało się otworzyć mikrofonu (próbowane: ${candidates.map(::sourceName)})")
    }

    companion object {
        private const val TAG = "VoxVerum"
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10 // 100 ms
        private val FALLBACK_SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )

        fun sourceName(source: Int): String = when (source) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.MIC -> "MIC"
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            else -> "SOURCE_$source"
        }
    }
}
