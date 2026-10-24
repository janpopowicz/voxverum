package pl.voxverum.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stan rozmowy VoIP widziany przez aplikację. */
sealed interface CallState {
    data object Idle : CallState
    data class Active(val packageName: String, val startedAtMs: Long) : CallState
}

/**
 * Współdzielony stan aplikacji. Usługa ułatwień dostępu go zapisuje, a UI czyta.
 * Prototyp działa w jednym procesie, więc prosty singleton wystarcza.
 */
object VoxState {
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    fun setCallState(state: CallState) {
        _callState.value = state
    }
}
