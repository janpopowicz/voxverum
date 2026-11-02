package pl.voxverum.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.voxverum.app.history.CallRecord
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(records: List<CallRecord>, onClear: () -> Unit) {
    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Brak rozmów. Po każdej przeanalizowanej rozmowie pojawi się tu krótkie podsumowanie (bez nagrania).")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ostatnie rozmowy", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClear) { Text("Wyczyść") }
            }
        }
        items(records, key = { it.startedAtMs }) { r -> RecordRow(r) }
    }
}

@Composable
private fun RecordRow(r: CallRecord) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(r.level.color(), CircleShape))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.startedAtMs)),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${r.durationSec / 60} min ${r.durationSec % 60} s · ${r.level.label()}" +
                        if (r.mockModels) " · modele testowe" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Maks. klon: ${(r.clonePeak * 100).roundToInt()}% · maks. wyłudzenie: ${(r.scamPeak * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
