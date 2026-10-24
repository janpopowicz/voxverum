package pl.voxverum.app.call

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import pl.voxverum.app.BuildConfig
import pl.voxverum.app.audio.MicAudioSource
import pl.voxverum.app.core.CallState
import pl.voxverum.app.core.VoxSettings
import pl.voxverum.app.core.VoxState
import pl.voxverum.app.overlay.OverlayController
import pl.voxverum.app.overlay.OverlayModel
import pl.voxverum.app.overlay.Tone
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
    private var session: ProtectionSession? = null

    override fun onServiceConnected() {
        settings = VoxSettings(this)
        overlay = OverlayController(this)
        detector = CallDetector(this, scope, ::onCallStateChanged)
        detector.start()
        VoxState.setServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        detector.onVoipAppEvent(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopSession()
        if (::detector.isInitialized) detector.stop()
        if (::overlay.isInitialized) overlay.hide()
        scope.cancel()
        VoxState.setServiceConnected(false)
        super.onDestroy()
    }

    private fun onCallStateChanged(state: CallState) {
        VoxState.setCallState(state)
        when (state) {
            is CallState.Active -> if (settings.protectionEnabled) startSession()
            CallState.Idle -> stopSession()
        }
    }

    private fun startSession() {
        // Kolejność ma znaczenie: nakładka musi być na wierzchu, zanim otworzymy mikrofon.
        overlay.show(
            OverlayModel(Tone.NEUTRAL, "VoxVerum słucha rozmowy", "Włącz głośnik, żeby analiza słyszała rozmówcę."),
        )
        CaptureForegroundService.start(this)
        val source = MicAudioSource(this, settings.audioSource)
        val dumper = if (BuildConfig.DEBUG && settings.debugDumpEnabled) {
            DebugDumper(this, source.sampleRate, source.name)
        } else {
            null
        }
        session = ProtectionSession(scope, source, dumper).also { it.start() }
    }

    private fun stopSession() {
        session?.stop()
        session = null
        if (::overlay.isInitialized) overlay.hide()
        CaptureForegroundService.stop(this)
    }
}
