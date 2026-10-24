package pl.voxverum.app.ui

import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.voxverum.app.core.AudioStatus
import pl.voxverum.app.ml.Scores
import pl.voxverum.app.risk.RiskAssessment
import java.util.Locale

/**
 * Ekran do testu wykonalności i wpinania modeli: czy mikrofon działa w trakcie rozmowy,
 * które źródło audio jest lepsze, co zwracają modele i jak szybko.
 */
@Composable
fun DebugScreen(
    audio: AudioStatus,
    scores: Scores?,
    risk: RiskAssessment?,
    history: List<Pair<Float, Float>>,
    models: List<ModelInfo>,
    audioSource: Int,
    debugBuild: Boolean,
    debugDump: Boolean,
    simulation: String?,
    canSimulate: Boolean,
    onAudioSource: (Int) -> Unit,
    onDebugDump: (Boolean) -> Unit,
    onPickWav: () -> Unit,
    onStopSimulation: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Audio") {
            Mono("działa: ${audio.running}   źródło: ${audio.sourceName.ifEmpty { "-" }}")
            Mono("poziom: ${fmt(audio.levelDb)} dBFS   mowa: ${audio.speech}   okna: ${audio.windows}")
            LinearProgressIndicator(
                progress = { ((audio.levelDb + 60f) / 60f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (audio.silenced) Text("System WYCISZYŁ mikrofon dla aplikacji", color = Danger, fontWeight = FontWeight.Bold)
            audio.error?.let { Text("Błąd: $it", color = Danger) }
        }

        Section("Modele") {
            models.forEach {
                val note = when {
                    it.version == "missing" -> "  (BRAK MODELU, szczegóły: adb logcat -s VoxVerum)"
                    it.mock -> "  (atrapa)"
                    else -> ""
                }
                Mono("${it.task.id}: ${it.version}  próg ${fmt(it.threshold)}$note")
            }
            if (scores != null) {
                Mono("klon: ${fmt(scores.clone)}   wyłudzenie: ${fmt(scores.scam)}   czas: ${scores.latencyMs} ms")
            }
            if (risk != null) {
                Mono("ryzyko klon: ${risk.clone.level.label()} (EMA ${fmt(risk.clone.smoothed)})")
                Mono("ryzyko wyłudzenie: ${risk.scam.level.label()} (EMA ${fmt(risk.scam.smoothed)})")
            }
            ScoreChart(history)
            Text("niebieski = klon, pomarańczowy = wyłudzenie", style = MaterialTheme.typography.bodySmall)
        }

        Section("Symulacja z pliku WAV") {
            Text(
                "Puszcza nagranie przez ten sam pipeline co rozmowę. Działa też na emulatorze. " +
                    "Obsługiwany jest WAV PCM 16/24/32-bit albo float32, dowolna liczba kanałów i częstotliwość.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (simulation == null) {
                Button(onClick = onPickWav, enabled = canSimulate) { Text("Wybierz plik WAV") }
            } else {
                Text("Analizuję: $simulation")
                OutlinedButton(onClick = onStopSimulation) { Text("Zatrzymaj") }
            }
        }

        Section("Źródło mikrofonu (test wykonalności)") {
            SourceOption("VOICE_RECOGNITION", MediaRecorder.AudioSource.VOICE_RECOGNITION, audioSource, onAudioSource)
            SourceOption("MIC", MediaRecorder.AudioSource.MIC, audioSource, onAudioSource)
            SourceOption("UNPROCESSED", MediaRecorder.AudioSource.UNPROCESSED, audioSource, onAudioSource)
            Text("Zmiana działa od następnej rozmowy.", style = MaterialTheme.typography.bodySmall)
            if (debugBuild) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Zapisuj 10 s rozmowy do WAV")
                        Text(
                            "Tylko wersja debug, tylko własne rozmowy testowe za zgodą obu stron. " +
                                "Pliki: Android/data/pl.voxverum.app/files/debug",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = debugDump, onCheckedChange = onDebugDump)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SourceOption(label: String, value: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = value == selected, onClick = { onSelect(value) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Text(label)
    }
}

@Composable
private fun ScoreChart(points: List<Pair<Float, Float>>) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        drawLine(grid, Offset(0f, size.height * 0.2f), Offset(size.width, size.height * 0.2f))
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
        if (points.size < 2) return@Canvas
        val step = size.width / (points.size - 1)
        fun line(select: (Pair<Float, Float>) -> Float, color: Color) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = i * step
                val y = size.height * (1f - select(p))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 4f))
        }
        line({ it.first }, Navy)
        line({ it.second }, Color(0xFFEF6C00))
    }
}

private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)
