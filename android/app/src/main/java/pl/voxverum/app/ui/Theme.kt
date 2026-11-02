package pl.voxverum.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import pl.voxverum.app.risk.RiskLevel

// Identyfikacja z biznesplanu: granat = zaufanie, zieleń = „potwierdzone”.
val Navy = Color(0xFF1F3A5F)
val Confirmed = Color(0xFF2E7D32)
val Warning = Color(0xFFF9A825)
val Danger = Color(0xFFC62828)

private val Light = lightColorScheme(primary = Navy, secondary = Confirmed, error = Danger)
private val Dark = darkColorScheme(primary = Color(0xFF9EC1E8), secondary = Color(0xFF81C784), error = Color(0xFFEF9A9A))

@Composable
fun VoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

fun RiskLevel.color(): Color = when (this) {
    RiskLevel.UNKNOWN -> Navy
    RiskLevel.OK -> Confirmed
    RiskLevel.WARN -> Warning
    RiskLevel.DANGER -> Danger
}

fun RiskLevel.label(): String = when (this) {
    RiskLevel.UNKNOWN -> "brak danych"
    RiskLevel.OK -> "OK"
    RiskLevel.WARN -> "uwaga"
    RiskLevel.DANGER -> "zagrożenie"
}
