package pl.voxverum.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.voxverum.app.core.CallState
import pl.voxverum.app.ui.ConsentScreen
import pl.voxverum.app.ui.DebugScreen
import pl.voxverum.app.ui.HistoryScreen
import pl.voxverum.app.ui.HomeScreen
import pl.voxverum.app.ui.VoxTheme
import pl.voxverum.app.ui.VoxViewModel

class MainActivity : ComponentActivity() {
    private val vm: VoxViewModel by viewModels()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }

    private val pickWav =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::startSimulation) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoxTheme {
                val consent by vm.consent.collectAsStateWithLifecycle()
                if (!consent) ConsentScreen(onAccept = vm::acceptConsent) else MainContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Po powrocie z ustawień ułatwień dostępu odświeżamy checklistę.
        vm.refreshPermissions()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainContent() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val permissions by vm.permissions.collectAsStateWithLifecycle()
        val protection by vm.protection.collectAsStateWithLifecycle()
        val callState by vm.callState.collectAsStateWithLifecycle()
        val audio by vm.audio.collectAsStateWithLifecycle()
        val scores by vm.scores.collectAsStateWithLifecycle()
        val risk by vm.risk.collectAsStateWithLifecycle()
        val models by vm.models.collectAsStateWithLifecycle()
        val history by vm.history.collectAsStateWithLifecycle()
        val scoreHistory by vm.scoreHistory.collectAsStateWithLifecycle()
        val audioSource by vm.audioSource.collectAsStateWithLifecycle()
        val debugDump by vm.debugDump.collectAsStateWithLifecycle()
        val simulation by vm.simulation.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { TopAppBar(title = { Text("VoxVerum") }) },
            bottomBar = {
                NavigationBar {
                    TABS.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icon) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(
                        permissions = permissions,
                        protection = protection,
                        callState = callState,
                        risk = risk,
                        models = models,
                        onProtectionChange = vm::setProtection,
                        onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onOpenAppInfo = {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                            )
                        },
                        onRequestMic = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    1 -> HistoryScreen(history, onClear = vm::clearHistory)
                    else -> DebugScreen(
                        audio = audio,
                        scores = scores,
                        risk = risk,
                        history = scoreHistory,
                        models = models,
                        audioSource = audioSource,
                        debugBuild = BuildConfig.DEBUG,
                        debugDump = debugDump,
                        simulation = simulation,
                        canSimulate = callState is CallState.Idle,
                        onAudioSource = vm::setAudioSource,
                        onDebugDump = vm::setDebugDump,
                        onPickWav = { pickWav.launch(arrayOf("audio/*")) },
                        onStopSimulation = vm::stopSimulation,
                    )
                }
            }
        }
    }

    private companion object {
        val TABS = listOf("🛡" to "Ochrona", "🕘" to "Historia", "🔧" to "Debug")
    }
}
