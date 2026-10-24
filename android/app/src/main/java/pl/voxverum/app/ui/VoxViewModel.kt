package pl.voxverum.app.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.voxverum.app.call.VoxAccessibilityService
import pl.voxverum.app.core.CallState
import pl.voxverum.app.core.VoxSettings
import pl.voxverum.app.core.VoxState
import pl.voxverum.app.history.CallHistory
import pl.voxverum.app.ml.AudioClassifier
import pl.voxverum.app.ml.ModelRegistry
import pl.voxverum.app.ml.Task
import pl.voxverum.app.ml.WindowAnalyzer
import pl.voxverum.app.session.ProtectionSession
import pl.voxverum.app.sim.WavAudioSource

data class Permissions(
    val accessibility: Boolean = false,
    val microphone: Boolean = false,
    val notifications: Boolean = false,
) {
    val allGranted get() = accessibility && microphone && notifications
}

data class ModelInfo(val task: Task, val version: String, val threshold: Float, val mock: Boolean)

class VoxViewModel(app: Application) : AndroidViewModel(app) {
    val settings = VoxSettings(app)

    val callState = VoxState.callState
    val audio = VoxState.audio
    val scores = VoxState.scores
    val risk = VoxState.risk
    val history = CallHistory.records

    private val _permissions = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    private val _consent = MutableStateFlow(settings.consentAccepted)
    val consent: StateFlow<Boolean> = _consent.asStateFlow()

    private val _protection = MutableStateFlow(settings.protectionEnabled)
    val protection: StateFlow<Boolean> = _protection.asStateFlow()

    private val _audioSource = MutableStateFlow(settings.audioSource)
    val audioSource: StateFlow<Int> = _audioSource.asStateFlow()

    private val _debugDump = MutableStateFlow(settings.debugDumpEnabled)
    val debugDump: StateFlow<Boolean> = _debugDump.asStateFlow()

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    /** Ostatnie wyniki (klon, wyłudzenie) do wykresu na ekranie Debug. */
    private val _scoreHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val scoreHistory: StateFlow<List<Pair<Float, Float>>> = _scoreHistory.asStateFlow()

    private val _simulation = MutableStateFlow<String?>(null)
    /** Nazwa pliku, który właśnie jest symulowany, albo null. */
    val simulation: StateFlow<String?> = _simulation.asStateFlow()
    private var simulationSession: ProtectionSession? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { CallHistory.load(app) }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) { ModelRegistry.load(app) }
            _models.value = loaded.values.map(::info).sortedBy { it.task }
        }
        viewModelScope.launch {
            VoxState.scores.filterNotNull().collect { s ->
                _scoreHistory.value = (_scoreHistory.value + (s.clone to s.scam)).takeLast(HISTORY_POINTS)
            }
        }
        refreshPermissions()
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        _permissions.value = Permissions(
            accessibility = isAccessibilityEnabled(),
            microphone = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            notifications = notifications,
        )
    }

    fun acceptConsent() {
        settings.consentAccepted = true
        _consent.value = true
    }

    fun setProtection(enabled: Boolean) {
        settings.protectionEnabled = enabled
        _protection.value = enabled
    }

    fun setAudioSource(source: Int) {
        settings.audioSource = source
        _audioSource.value = source
    }

    fun setDebugDump(enabled: Boolean) {
        settings.debugDumpEnabled = enabled
        _debugDump.value = enabled
    }

    fun clearHistory() {
        CallHistory.clear(getApplication())
    }

    /** Puszcza wybrany plik WAV przez ten sam pipeline co rozmowę (tempo rzeczywiste). */
    fun startSimulation(uri: Uri) {
        if (simulationSession != null || callState.value is CallState.Active) return
        val app = getApplication<Application>()
        val name = displayName(uri)
        _scoreHistory.value = emptyList()
        viewModelScope.launch {
            val analyzer = WindowAnalyzer(withContext(Dispatchers.Default) { ModelRegistry.load(app) })
            val source = WavAudioSource({ app.contentResolver.openInputStream(uri)!! }, name = "WAV")
            _simulation.value = name
            simulationSession = ProtectionSession(
                scope = viewModelScope,
                source = source,
                analyzer = analyzer,
                onFinished = {
                    simulationSession = null
                    _simulation.value = null
                },
            ).also { it.start() }
        }
    }

    fun stopSimulation() {
        simulationSession?.stop()
    }

    private fun info(c: AudioClassifier) = ModelInfo(c.task, c.version, c.threshold, c.isMock)

    private fun isAccessibilityEnabled(): Boolean {
        val app = getApplication<Application>()
        val enabled = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(app, VoxAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun displayName(uri: Uri): String {
        val app = getApplication<Application>()
        return runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "plik.wav"
    }

    override fun onCleared() {
        simulationSession?.stop()
    }

    private companion object {
        const val HISTORY_POINTS = 60
    }
}
