package com.tonenai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tonenai.app.AppViewModel
import com.tonenai.app.data.Patch
import com.tonenai.app.data.Song
import org.json.JSONObject

@Composable
fun SongScreen(vm: AppViewModel, song: Song, onBack: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sending = vm.sending?.takeIf { it.songId == song.id }?.index
    val live = vm.playing?.takeIf { it.songId == song.id && vm.amp.connected }?.index

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                CoverArt(song, Modifier.fillMaxSize(), initialsSize = 72)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f), 0.35f to Color.Transparent, 0.6f to Tone.bg.copy(alpha = 0.55f), 1f to Tone.bg)))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    val fav = song.id in vm.favorites
                    RoundIcon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (fav) "Remove from favourites" else "Add to favourites",
                        tint = if (fav) Tone.accentBright else Color.White) { vm.toggleFavorite(song.id) }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(song.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    if (song.artist.isNotEmpty()) Text(song.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        item(key = "patches-label") {
            SectionLabel(if (vm.amp.connected) "Tap a patch to play it" else "Patches · connect the amp to play",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp))
        }
        items(song.patches, key = { it.index }) { patch ->
            PatchCard(patch, onAmp = live == patch.index, sending = sending == patch.index, enabled = vm.sending == null) {
                vm.play(song, patch.index)
            }
        }
        item(key = "practice") { PracticeSection(vm, song, Modifier.padding(top = 18.dp)) }
        item(key = "learn") { TabsSection(vm, song, Modifier.padding(top = 18.dp)) }
        song.guitar?.let { g ->
            item(key = "guitar") {
                Details("Guitar") {
                    val originals = g.optJSONArray("originals")
                    for (i in 0 until (originals?.length() ?: 0)) {
                        val o = originals!!.getJSONObject(i)
                        Fact(o.optString("player").ifEmpty { "On the record" },
                            listOf(o.optString("guitar"), o.optString("pickups")).filter { it.isNotEmpty() }.joinToString(" · "))
                    }
                    Fact("Pickup to use", g.text("position"))
                    Fact("Notes", g.text("tone_notes"))
                }
            }
        }
        if (vm.owns("whammy")) song.whammy?.let { w ->
            item(key = "whammy") {
                Details("Tuning and Whammy") {
                    Fact("Tuning", w.text("tuning"))
                    Fact("Whammy", w.text("when").takeIf { w.text("use") != "no" } ?: "Not needed")
                    Steps(w)
                    Fact("Tip", w.text("tips"))
                }
            }
        }
        if (vm.owns("trio")) song.band?.let { b ->
            item(key = "band") {
                Details("Set up your Trio+") { TrioCard(b, sound = vm.soundEffects) }
            }
        }
        item(key = "delete") {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding()) {
                Text("Remove this song", color = Tone.muted)
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove “${song.title}”?") },
        text = { Text(if (vm.driveConnected) "It is removed from this phone and, at the next sync, from Google Drive and your other devices."
                      else "It is removed from this phone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); vm.delete(song.id) }) { Text("Remove", color = Tone.accentBright) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Tone.text) } },
        containerColor = Tone.raised,
    )
}

private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()

@Composable
private fun RoundIcon(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = tint) }
}

@Composable
private fun PatchCard(patch: Patch, onAmp: Boolean, sending: Boolean, enabled: Boolean, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(shape)
            .background(if (onAmp) Tone.raised else Tone.panel)
            .border(1.dp, if (onAmp) Tone.accentBright else Tone.line, shape)
            .clickable(enabled = enabled, onClick = onPlay).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onAmp) Text("ON THE AMP", style = MaterialTheme.typography.labelSmall, color = Tone.accentBright)
            Text(patch.name, style = MaterialTheme.typography.titleLarge, color = Tone.text)
            if (patch.amp.isNotEmpty() || patch.effects.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (patch.amp.isNotEmpty()) Chip(patch.amp, strong = true)
                    patch.effects.forEach { Chip(it) }
                }
            }
            if (patch.note.isNotEmpty()) Text(patch.note, style = MaterialTheme.typography.bodyMedium, color = Tone.muted)
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(54.dp).clip(CircleShape).background(Tone.accentBrush), contentAlignment = Alignment.Center) {
            if (sending) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
            else Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${patch.name} on the amp", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun Details(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
        SectionLabel(title, Modifier.padding(start = 4.dp, bottom = 10.dp))
        Panel { content() }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    if (value.isEmpty()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tone.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Tone.text)
    }
}

@Composable
private fun Steps(whammy: JSONObject) {
    val how = whammy.optJSONArray("how") ?: return
    for (i in 0 until how.length()) {
        Row {
            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Tone.accentBright, modifier = Modifier.width(20.dp))
            Text(how.optString(i), style = MaterialTheme.typography.bodyMedium, color = Tone.text)
        }
    }
}
