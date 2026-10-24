package pl.voxverum.app.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pl.voxverum.app.core.CallState
import pl.voxverum.app.risk.RiskAssessment
import pl.voxverum.app.risk.RiskLevel
import java.io.File

/** Podsumowanie jednej rozmowy. Bez audio i bez transkrypcji: tylko czas i wyniki. */
data class CallRecord(
    val startedAtMs: Long,
    val durationSec: Long,
    val app: String,
    val level: RiskLevel,
    val clonePeak: Float,
    val scamPeak: Float,
    val windows: Int,
    val mockModels: Boolean,
)

/** Historia ostatnich [MAX] rozmów w pliku JSON w prywatnym katalogu aplikacji. */
object CallHistory {
    private const val MAX = 50
    private const val FILE = "call_history.json"

    private val _records = MutableStateFlow<List<CallRecord>>(emptyList())
    val records: StateFlow<List<CallRecord>> = _records.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context): List<CallRecord> {
        if (!loaded) {
            _records.value = readFile(file(context))
            loaded = true
        }
        return _records.value
    }

    @Synchronized
    fun record(context: Context, call: CallState.Active, risk: RiskAssessment, mockModels: Boolean) {
        val entry = CallRecord(
            startedAtMs = call.startedAtMs,
            durationSec = (System.currentTimeMillis() - call.startedAtMs) / 1000,
            app = call.packageName,
            level = risk.overall,
            clonePeak = risk.clone.peak,
            scamPeak = risk.scam.peak,
            windows = risk.windows,
            mockModels = mockModels,
        )
        val updated = (listOf(entry) + load(context)).take(MAX)
        _records.value = updated
        file(context).writeText(toJson(updated).toString())
    }

    @Synchronized
    fun clear(context: Context) {
        _records.value = emptyList()
        loaded = true
        file(context).delete()
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun toJson(records: List<CallRecord>) = JSONArray().apply {
        records.forEach { r ->
            put(
                JSONObject()
                    .put("startedAtMs", r.startedAtMs)
                    .put("durationSec", r.durationSec)
                    .put("app", r.app)
                    .put("level", r.level.name)
                    .put("clonePeak", r.clonePeak.toDouble())
                    .put("scamPeak", r.scamPeak.toDouble())
                    .put("windows", r.windows)
                    .put("mockModels", r.mockModels),
            )
        }
    }

    private fun readFile(file: File): List<CallRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                CallRecord(
                    startedAtMs = o.getLong("startedAtMs"),
                    durationSec = o.getLong("durationSec"),
                    app = o.getString("app"),
                    level = RiskLevel.valueOf(o.getString("level")),
                    clonePeak = o.getDouble("clonePeak").toFloat(),
                    scamPeak = o.getDouble("scamPeak").toFloat(),
                    windows = o.getInt("windows"),
                    mockModels = o.getBoolean("mockModels"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
