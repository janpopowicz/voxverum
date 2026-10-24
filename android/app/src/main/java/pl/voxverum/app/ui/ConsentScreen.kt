package pl.voxverum.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("VoxVerum", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Głos to nie dowód tożsamości. Sprawdź, zanim przelejesz.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Bullet("W trakcie rozmowy WhatsApp aplikacja słucha dźwięku z mikrofonu i ocenia, czy głos może być sklonowany oraz czy rozmowa wygląda na próbę wyłudzenia.")
        Bullet("Analiza działa w całości na tym telefonie. Dźwięk nie jest zapisywany ani wysyłany poza urządzenie. W historii zostaje tylko czas rozmowy i wynik.")
        Bullet("Żeby aplikacja słyszała rozmówcę, prowadź rozmowę na głośniku.")
        Bullet("Detektor to sygnał pomocniczy, a nie dowód. Przy prośbie o pieniądze zawsze zweryfikuj rozmówcę innym kanałem: Kod Rozmowy, hasło rodzinne, oddzwonienie na znany numer.")
        Bullet("Rozmówca ma prawo wiedzieć, że korzystasz z ochrony rozmów. Jeśli możesz, poinformuj go o tym.")
        Bullet("To wersja testowa (prototyp). Aplikacja korzysta z usługi ułatwień dostępu tylko do wykrywania rozmów WhatsApp i wyświetlania ostrzeżeń.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("Rozumiem i akceptuję")
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium)
}
