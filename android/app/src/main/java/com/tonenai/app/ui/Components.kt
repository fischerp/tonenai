package com.tonenai.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonenai.app.AmpState
import com.tonenai.app.R
import com.tonenai.app.data.Covers
import com.tonenai.app.data.Song

/** A colour pair that is always the same for one song, for covers we have no artwork for. */
private fun coverColors(id: String): List<Color> {
    val hue = ((id.hashCode() % 360) + 360) % 360
    return listOf(Color.hsv(hue.toFloat(), 0.62f, 0.72f), Color.hsv(((hue + 42) % 360).toFloat(), 0.78f, 0.22f))
}

/** Album art when we have it; until then (or when there is none) a generated cover with the song's initials. */
@Composable
fun CoverArt(song: Song, modifier: Modifier = Modifier, initialsSize: Int = 30) {
    val context = LocalContext.current.applicationContext
    val art by produceState<ImageBitmap?>(Covers.cached(song.id), song.id) { value = Covers.load(context, song) }
    Box(modifier.background(Brush.linearGradient(coverColors(song.id))), contentAlignment = Alignment.Center) {
        Text(
            (song.artist.ifEmpty { song.title }).split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() },
            color = Color.White.copy(alpha = 0.92f), fontSize = initialsSize.sp, fontWeight = FontWeight.Black,
        )
        Crossfade(art, label = "cover") { bitmap ->
            if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun Wordmark(markSize: Dp = 34.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(markSize * 1.5f))
        Row(Modifier.padding(start = 0.dp)) {
            Text("Tonen", style = MaterialTheme.typography.headlineSmall, color = Tone.text)
            Text("AI", style = MaterialTheme.typography.headlineSmall, color = Tone.accentBright)
        }
    }
}

/** The amp's state in one pill: a green dot and the patch that is playing, or a nudge to plug it in. */
@Composable
fun AmpPill(amp: AmpState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Tone.panel).border(1.dp, Tone.line, CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (amp.connected) Tone.ok else Tone.muted))
        Spacer(Modifier.width(8.dp))
        Text(
            if (amp.connected) amp.patch.ifEmpty { "Katana" } else "Amp not connected",
            style = MaterialTheme.typography.labelMedium, color = if (amp.connected) Tone.text else Tone.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = if (strong) Color.White else Tone.muted, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (strong) Tone.accent else Tone.raised)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tone.muted, modifier = modifier)
}

/** A panel with the app's standard padding, corner and hairline. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tone.panel)
            .border(1.dp, Tone.line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}
