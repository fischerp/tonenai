package com.tonenai.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

private val GENRES = listOf("Blues", "R&B", "Rock", "Alt Rock", "Metal", "Pop", "E-Pop", "Hip-Hop", "Country", "Folk", "Latin", "Jazz")
private val GREEN = Color(0xFF3DDC84)

/** Which GENRE knob position a genre name belongs to (-1 when it matches none), same rules as the desktop app. */
internal fun genreIndex(genre: String): Int {
    val t = genre.lowercase()
    val map = listOf("alt" to 3, "electronic" to 6, "e-pop" to 6, "hip" to 7, "r&b" to 1, "metal" to 4, "blues" to 0, "rock" to 2, "pop" to 5, "country" to 8, "folk" to 9, "latin" to 10, "jazz" to 11)
    return map.firstOrNull { t.contains(it.first) }?.second ?: -1
}

/** The Trio+ GENRE knob: 12 positions from Blues (bottom left) clockwise to Jazz (bottom right), the chosen one lit. */
@Composable
private fun GenreDial(selected: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(224.dp, 170.dp)) {
        val s = 1.dp.toPx()
        fun p(x: Float, y: Float) = Offset((x + 28f) * s, (y + 2f) * s)
        val cx = 84f
        val cy = 84f
        val r = 62f
        drawCircle(Color(0xFF2A2A37), 24f * s, p(cx, cy))
        GENRES.forEachIndexed { i, g ->
            val a = Math.toRadians((-150.0 + i * (300.0 / 11)))
            val on = i == selected
            drawCircle(if (on) GREEN else Color(0xFF555566), (if (on) 6f else 3.5f) * s, p(cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat()))
            val label = measurer.measure(g, TextStyle(color = if (on) Color.White else Color(0xFF9A9AAE), fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal))
            val c = p(cx + (r + 21f) * sin(a).toFloat(), cy - (r + 21f) * cos(a).toFloat() + 3f)
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
        }
        if (selected >= 0) {
            val a = Math.toRadians(-150.0 + selected * (300.0 / 11))
            drawLine(Color.White, p(cx, cy), p(cx + 30f * sin(a).toFloat(), cy - 30f * cos(a).toFloat()), 3f * s, StrokeCap.Round)
        }
    }
}

@Composable
private fun Step(lead: String, rest: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", color = Tone.accentBright, style = MaterialTheme.typography.bodyMedium)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(lead) }
                append(" ")
                append(rest)
            },
            style = MaterialTheme.typography.bodyMedium, color = Tone.text,
        )
    }
}

/** The Trio+ setup for a song, laid out like the desktop app: tempo, genre knob, style knob, then the steps. */
@Composable
fun TrioCard(b: JSONObject, sound: Boolean = true) {
    val bpm = b.optInt("bpm", 0)
    val genre = b.optString("genre")
    val three = b.optString("time_signature").startsWith("3")
    val parts = b.optJSONArray("parts")?.let { a -> (0 until minOf(a.length(), 5)).map { a.optString(it) } }.orEmpty()
    var clicking by remember { mutableStateOf(false) }
    LaunchedEffect(clicking, bpm, sound) {
        if (!clicking || !sound || bpm <= 0) return@LaunchedEffect
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        try {
            var n = 0
            while (true) {
                tone.startTone(if (n++ % 4 == 0) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 60)
                delay(60_000L / bpm)
            }
        } finally {
            tone.release()
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (bpm > 0) "$bpm" else "?", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text("BPM", style = MaterialTheme.typography.labelMedium, color = Tone.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, key) in listOf("Genre" to "genre", "Time" to "time_signature", "Key" to "key")) {
                b.optString(key).takeIf { it.isNotEmpty() }?.let { Chip("$label · $it") }
            }
        }
    }
    if (bpm > 0 && sound) Button(
        onClick = { clicking = !clicking }, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (clicking) Tone.raised else Tone.accent, contentColor = Color.White),
    ) { Text(if (clicking) "■ Stop" else "▶ Click track") }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GenreDial(genreIndex(genre))
        Text("STYLE knob: green = ${if (three) "3/4" else "4/4"} styles", style = MaterialTheme.typography.bodySmall, color = Tone.muted)
        for (rowStart in listOf(1, 7)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in rowStart until rowStart + 6) {
                val ok = three == (n >= 10)
                val dash = n >= 10
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (ok) Color(0xFF1F4D36) else Color(0xFF2A2A37))
                        .drawBehind {
                            if (dash) drawRoundRect(Color(0xFF6D6D80), cornerRadius = CornerRadius(8.dp.toPx()),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("$n", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (ok) Color(0xFF7DFFB0) else Color(0xFF9A9AA8)) }
            }
        }
        b.optString("style_feel").takeIf { it.isNotEmpty() }?.let {
            Text(buildAnnotatedString { append("Look for: "); withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(it) } },
                style = MaterialTheme.typography.bodyMedium, color = Tone.muted)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Step("Pick an empty part.", "Press a PART button that isn't lit; the BAND light should blink red slowly. Set the BASS and DRUMS knobs to about 10 o'clock.")
        Step("Genre knob → ${genre.ifEmpty { "?" }}.", "")
        Step("Teach it at ${if (bpm > 0) "$bpm" else "the song's"} BPM.",
            "Start the click track, press BAND as you begin, strum clean, steady chords${if (parts.isNotEmpty()) " (see the parts below)" else ""}, then press BAND again on the same downbeat. Keep it under 32 bars. The pedal has no BPM display: it learns the tempo from your playing.")
        Step("Choose the style.", "The STYLE lights show the best matches. Pick one that fits ${if (three) "3/4 (positions 10–12)" else "4/4 (positions 1–9)"} and the feel above. If all lights flash red, play the part again, clearer.")
        Step("Tempo knob at 12 o'clock", "= exactly what you taught (the AUDIOLASTIC light is off). If it feels like half or double speed, press ALT TIME.")
        if (parts.size > 1) {
            Step("Repeat for the other parts", "(each on its own PART button, up to 5), then tap through them with BAND:")
            for (p in parts) Text("– $p", style = MaterialTheme.typography.bodyMedium, color = Tone.text, modifier = Modifier.padding(start = 22.dp))
        }
    }
    b.optString("tips").takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.muted) }
}
