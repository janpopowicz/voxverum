package pl.voxverum.app.call

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import pl.voxverum.app.core.CallState
import pl.voxverum.app.core.VoxState
import pl.voxverum.app.overlay.OverlayController
import pl.voxverum.app.overlay.OverlayModel
import pl.voxverum.app.overlay.Tone

/**
 * Serce aplikacji: system trzyma tę usługę przy życiu, ona wykrywa rozmowy WhatsApp,
 * pokazuje nakładkę i (od kroku 3) uruchamia nasłuch w swoim procesie.
 */
class VoxAccessibilityService : AccessibilityService() {
    private val scope = MainScope()
    private lateinit var overlay: OverlayController
    private lateinit var detector: CallDetector

    override fun onServiceConnected() {
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
        if (::detector.isInitialized) detector.stop()
        if (::overlay.isInitialized) overlay.hide()
        scope.cancel()
        VoxState.setServiceConnected(false)
        super.onDestroy()
    }

    private fun onCallStateChanged(state: CallState) {
        VoxState.setCallState(state)
        when (state) {
            is CallState.Active -> overlay.show(
                OverlayModel(Tone.NEUTRAL, "VoxVerum słucha rozmowy", "Włącz głośnik, żeby analiza słyszała rozmówcę."),
            )
            CallState.Idle -> overlay.hide()
        }
    }
}
