package pl.voxverum.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.voxverum.app.core.CallState
import pl.voxverum.app.risk.RiskAssessment
import pl.voxverum.app.risk.RiskLevel

@Composable
fun HomeScreen(
    permissions: Permissions,
    protection: Boolean,
    callState: CallState,
    risk: RiskAssessment?,
    models: List<ModelInfo>,
    onProtectionChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(permissions, protection, callState, risk)

        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ochrona rozmów WhatsApp", fontWeight = FontWeight.SemiBold)
                    Text("Analiza startuje sama, gdy zaczyna się rozmowa.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = protection, onCheckedChange = onProtectionChange)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Konfiguracja", fontWeight = FontWeight.SemiBold)
                Requirement(
                    ok = permissions.microphone,
                    label = "Dostęp do mikrofonu",
                    action = "Zezwól",
                    onAction = onRequestMic,
                )
                Requirement(
                    ok = permissions.notifications,
                    label = "Powiadomienia z ostrzeżeniami",
                    action = "Zezwól",
                    onAction = onRequestNotifications,
                )
                Requirement(
                    ok = permissions.accessibility,
                    label = "Usługa ułatwień dostępu VoxVerum",
                    action = "Włącz",
                    onAction = onOpenAccessibility,
                )
                if (!permissions.accessibility && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        "Jeśli przełącznik usługi jest wyszarzony („ustawienie ograniczone”): otwórz informacje " +
                            "o aplikacji, menu ⋮ w prawym górnym rogu i wybierz „Zezwól na ustawienia ograniczone”.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenAppInfo) { Text("Otwórz informacje o aplikacji") }
                }
            }
        }

        if (models.any { it.mock }) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.2f)),
            ) {
                Text(
                    "Aplikacja używa modeli testowych (atrap), które reagują tylko na głośność. " +
                        "Wyniki nie oznaczają prawdziwego wykrycia.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Zasada STOP, ODDZWOŃ, ZAPYTAJ O HASŁO", fontWeight = FontWeight.SemiBold)
                Text("Gdy ktoś prosi o pieniądze, kod BLIK albo zmianę numeru konta, zatrzymaj rozmowę, " +
                    "oddzwoń na znany numer i zapytaj o Kod Rozmowy albo hasło rodzinne.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(permissions: Permissions, protection: Boolean, callState: CallState, risk: RiskAssessment?) {
    val (color, title, detail) = when {
        !permissions.allGranted -> Triple(Warning, "Dokończ konfigurację", "Bez uprawnień poniżej ochrona nie zadziała.")
        !protection -> Triple(Color.Gray, "Ochrona wyłączona", "Włącz przełącznik, żeby analizować rozmowy.")
        callState is CallState.Active -> {
            val level = risk?.overall ?: RiskLevel.UNKNOWN
            Triple(level.color(), "Trwa rozmowa: ${level.label()}", "Szczegóły na banerze nad rozmową.")
        }
        else -> Triple(Confirmed, "Ochrona aktywna", "Czekam na rozmowę WhatsApp.")
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = if (color == Warning) Color.Black else Color.White),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail)
        }
    }
}

@Composable
private fun Requirement(ok: Boolean, label: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✅" else "⚠️")
        Text(label, Modifier.weight(1f).padding(start = 8.dp))
        if (!ok) OutlinedButton(onClick = onAction) { Text(action) }
    }
}
