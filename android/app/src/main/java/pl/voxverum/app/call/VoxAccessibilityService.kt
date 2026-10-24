package pl.voxverum.app.call

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.voxverum.app.BuildConfig
import pl.voxverum.app.alert.AlertTexts
import pl.voxverum.app.alert.Alerter
import pl.voxverum.app.audio.MicAudioSource
import pl.voxverum.app.core.CallState
import pl.voxverum.app.core.VoxSettings
import pl.voxverum.app.core.VoxState
import pl.voxverum.app.history.CallHistory
import pl.voxverum.app.ml.ModelRegistry
import pl.voxverum.app.ml.WindowAnalyzer
import pl.voxverum.app.overlay.OverlayController
import pl.voxverum.app.session.CaptureForegroundService
import pl.voxverum.app.session.DebugDumper
import pl.voxverum.app.session.ProtectionSession

/**
 * Serce aplikacji: system trzyma tę usługę przy życiu, ona wykrywa rozmowy WhatsApp,
 * pokazuje nakładkę i uruchamia nasłuch w swoim procesie.
 */
class VoxAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private lateinit var settings: VoxSettings
    private lateinit var overlay: OverlayController
    private lateinit var detector: CallDetector
    private lateinit var alerter: Alerter
    private var session: ProtectionSession? = null
    private var sessionJob: Job? = null

    override fun onServiceConnected() {
        settings = VoxSettings(this)
        overlay = OverlayController(this)
        alerter = Alerter(this)
        detector = CallDetector(this, scope, ::onCallStateChanged)
        detector.start()
        VoxState.setServiceConnected(true)
        // Wczytujemy modele od razu, żeby start rozmowy nie czekał na ONNX Runtime.
        scope.launch(Dispatchers.Default) { ModelRegistry.load(applicationContext) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        detector.onVoipAppEvent(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopSession()
        if (::detector.isInitialized) detector.stop()
        scope.cancel()
        VoxState.setServiceConnected(false)
        super.onDestroy()
    }

    private fun onCallStateChanged(state: CallState) {
        VoxState.setCallState(state)
        when (state) {
            is CallState.Active -> if (settings.protectionEnabled) startSession(state)
            CallState.Idle -> stopSession()
        }
    }

    private fun startSession(call: CallState.Active) {
        // Kolejność ma znaczenie: nakładka musi być na wierzchu, zanim otworzymy mikrofon.
        overlay.show(AlertTexts.overlay(risk = null, silenced = false, mockModels = false))
        CaptureForegroundService.start(this)
        alerter.reset()

        val start = scope.launch {
            val models = withContext(Dispatchers.Default) { ModelRegistry.load(applicationContext) }
            val analyzer = WindowAnalyzer(models)
            val mock = models.values.any { it.isMock }
            val source = MicAudioSource(this@VoxAccessibilityService, settings.audioSource)
            val dumper = if (BuildConfig.DEBUG && settings.debugDumpEnabled) {
                DebugDumper(this@VoxAccessibilityService, source.sampleRate, source.name)
            } else {
                null
            }
            session = ProtectionSession(
                scope = scope,
                source = source,
                analyzer = analyzer,
                debugDumper = dumper,
                onAssessment = { risk, _ -> alerter.onAssessment(risk) },
                onFinished = { risk -> CallHistory.record(applicationContext, call, risk, mock) },
            ).also { it.start() }

            // Baner odświeża się przy każdej zmianie ryzyka albo wyciszenia mikrofonu.
            combine(VoxState.risk, VoxState.audio) { risk, audio -> AlertTexts.overlay(risk, audio.silenced, mock) }
                .collect { overlay.render(it) }
        }
        sessionJob = start
    }

    private fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        session?.stop()
        session = null
        VoxState.setScores(null)
        VoxState.setRisk(null)
        if (::overlay.isInitialized) overlay.hide()
        CaptureForegroundService.stop(this)
    }
}
