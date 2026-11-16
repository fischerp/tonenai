package com.tonenai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonenai.app.AppViewModel
import com.tonenai.app.data.Song
import com.tonenai.app.data.SongMetaFiles
import com.tonenai.app.data.TabFiles
import com.tonenai.app.data.TabsConfig
import kotlinx.coroutines.delay

@Composable
internal fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tone.accentBright, unfocusedBorderColor = Tone.line,
    focusedTextColor = Tone.text, unfocusedTextColor = Tone.text, cursorColor = Tone.accentBright,
    focusedLabelColor = Tone.muted, unfocusedLabelColor = Tone.muted,
    focusedPlaceholderColor = Tone.muted, unfocusedPlaceholderColor = Tone.muted,
)

/** A selectable pill, used for choices (status, pickups, kinds). */
@Composable
internal fun Pick(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(shape).background(if (selected) Tone.accent else Tone.raised).border(1.dp, if (selected) Tone.accentBright else Tone.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Learning / Played and your own notes for a song. */
@Composable
fun PracticeSection(vm: AppViewModel, song: Song, modifier: Modifier = Modifier) {
    val saved = vm.metaOf(song.id)
    var notes by remember(song.id) { mutableStateOf(saved.notes) }
    val latest by rememberUpdatedState(notes)
    LaunchedEffect(notes) {
        delay(600)
        vm.setNotes(song.id, latest)
    }
    DisposableEffect(song.id) { onDispose { vm.setNotes(song.id, latest) } }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("My practice", Modifier.padding(start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("" to "Not started", "learning" to "🎯 Learning", "played" to "✓ Played")) {
                Pick(label, saved.status == key) { vm.setStatus(song.id, key) }
            }
        }
        OutlinedTextField(
            notes, { notes = it.take(SongMetaFiles.MAX_NOTES) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
            placeholder = { Text("Notes for yourself: tricky parts, capo, what to practise next…") }, colors = textFieldColors(),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

/** Opens the song on your tab site and on YouTube lessons (search links: nothing is downloaded). */
@Composable
fun TabsSection(vm: AppViewModel, song: Song, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    var settings by remember { mutableStateOf(false) }
    fun open(url: String) {
        runCatching { uri.openUri(url) }.onFailure { vm.notice = "No app can open that link" }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Learn it", Modifier.padding(start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val url = TabFiles.url(vm.tabs, song.id, song.artist, song.title)
                if (url == null) settings = true else open(url)
            }) { Text("Guitar tabs", color = Tone.text) }
            OutlinedButton(onClick = { open(TabFiles.lessonsUrl(song.artist, song.title)) }) { Text("Lessons", color = Tone.text) }
            TextButton(onClick = { settings = true }) { Text("Tab site…", color = Tone.muted) }
        }
    }
    if (settings) TabsDialog(vm, song, onDismiss = { settings = false }, onOpen = ::open)
}

@Composable
private fun TabsDialog(vm: AppViewModel, song: Song, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var service by remember { mutableStateOf(vm.tabs.service) }
    var template by remember { mutableStateOf(vm.tabs.template) }
    var link by remember { mutableStateOf(vm.tabs.links[song.id].orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guitar tabs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick the site you use. “Guitar tabs” searches it for each song. You can also paste an exact link for this song.",
                    style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                for ((key, value) in TabFiles.SERVICES) {
                    Row(Modifier.fillMaxWidth().clickable { service = key }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(service == key, { service = key }, colors = RadioButtonDefaults.colors(selectedColor = Tone.accentBright, unselectedColor = Tone.muted))
                        Text(value.first, color = Tone.text)
                    }
                }
                if (service == "custom") OutlinedTextField(template, { template = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search URL with {q}") }, placeholder = { Text("https://example.com/search?q={q}") }, colors = textFieldColors())
                OutlinedTextField(link, { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exact link for “${song.title}” (optional)") }, placeholder = { Text("https://…") }, colors = textFieldColors())
                error?.let { Text(it, color = Color(0xFFFF8A85), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val exact = link.trim()
                when {
                    service.isEmpty() && exact.isEmpty() -> error = "Pick a site or paste a link"
                    exact.isNotEmpty() && !Regex("https?://\\S+").matches(exact) -> error = "The link must start with http:// or https://"
                    service == "custom" && !template.contains("{q}") && exact.isEmpty() -> error = "The search URL must contain {q}"
                    else -> {
                        val links = vm.tabs.links.toMutableMap().apply { if (exact.isEmpty()) remove(song.id) else put(song.id, exact) }
                        val cfg = TabsConfig(service.ifEmpty { vm.tabs.service }, template.trim(), links)
                        vm.saveTabs(cfg)
                        onDismiss()
                        TabFiles.url(cfg, song.id, song.artist, song.title)?.let(onOpen)
                    }
                }
            }) { Text("Save and open", color = Tone.accentBright) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Tone.text) } },
        containerColor = Tone.raised,
    )
}
