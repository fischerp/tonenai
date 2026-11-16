package com.tonenai.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tonenai.app.AppViewModel
import com.tonenai.app.data.RequestFiles
import com.tonenai.app.data.RequestItem
import com.tonenai.app.data.SongHit
import com.tonenai.app.data.SongSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private val OPEN = setOf("new", "awaiting-approval", "working")

private fun statusLabel(r: RequestItem) = when (r.status) {
    "new" -> "Waiting for your computer"
    "awaiting-approval" -> "Waiting for you to approve it on the computer"
    "working" -> "Being created on your computer"
    "done" -> "Done"
    "declined" -> "Dismissed on the computer"
    else -> "Failed"
}

/** Ask your computer to create a song: the request goes through your Google Drive, the song comes back the same way. */
@Composable
fun RequestsScreen(vm: AppViewModel, initialText: String, onBack: () -> Unit, onOpenSong: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    var searched by remember { mutableStateOf<List<SongHit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun search() {
        val term = text.trim()
        if (term.isEmpty() || searching) return
        searching = true
        searchError = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { SongSearch.search(term) } }
                .onSuccess { searched = it }
                .onFailure { searchError = "Could not search: ${it.message ?: "no connection"}" }
            searching = false
        }
    }
    fun send(line: String) {
        if (vm.requestSong(line)) {
            text = ""
            searched = null
        }
    }
    // While something is waiting, look for the answer once in a while (the computer checks Drive every few minutes too).
    LaunchedEffect(Unit) {
        while (true) {
            delay(45_000)
            if (vm.requests.any { it.status in OPEN }) vm.syncNow(silent = true)
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tone.text) }
            Text("Request a song", style = MaterialTheme.typography.titleLarge, color = Tone.text)
        }
        Text(
            "Search for the song, tap the right one, and your computer creates it with your Claude sign-in; Google Drive brings it back here. " +
                "TonenAI has to be open on the computer; it checks Drive every few minutes and may ask you to approve the request first.",
            style = MaterialTheme.typography.bodyMedium, color = Tone.muted,
        )
        Panel {
            OutlinedTextField(
                text, { text = it.take(RequestFiles.MAX_TEXT); if (searched != null) searched = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Song or artist") }, placeholder = { Text("Under the Bridge, Red Hot Chili Peppers") }, colors = textFieldColors(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
            )
            if (!vm.driveConnected) Text("Connect Google Drive in Settings first: that is how the request reaches your computer.",
                style = MaterialTheme.typography.bodySmall, color = Tone.muted)
            Button(
                onClick = { search() },
                enabled = text.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.accent, contentColor = Color.White, disabledContainerColor = Tone.raised, disabledContentColor = Tone.muted),
            ) { Text(if (searching) "Searching…" else "Search") }
            searchError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.muted) }
        }
        searched?.let { hits ->
            SectionLabel(if (hits.isEmpty()) "No songs found" else "Which song? Tap the one you mean", Modifier.padding(start = 4.dp))
            for (hit in hits) Panel(Modifier.clickable(enabled = vm.driveConnected) { send(hit.request) }) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium, color = Tone.text)
                Text(listOf(hit.artist, hit.album).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Tone.muted)
            }
            TextButton(onClick = { send(text) }, enabled = vm.driveConnected) { Text("None of these: send “${text.trim()}” as typed", color = Tone.muted) }
        }
        if (vm.requests.isNotEmpty()) SectionLabel("Your requests", Modifier.padding(start = 4.dp))
        for (r in vm.requests) {
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.text, style = MaterialTheme.typography.titleMedium, color = Tone.text)
                        Chip(statusLabel(r), strong = r.status == "done")
                        if (r.note.isNotEmpty() && r.status != "done") Text(r.note, style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                    }
                    IconButton(onClick = { vm.removeRequest(r.id) }) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Tone.muted) }
                }
                if (r.status == "done" && r.song.isNotEmpty()) {
                    val there = vm.song(r.song) != null
                    OutlinedButton(onClick = { onOpenSong(r.song) }, enabled = there) { Text(if (there) "Open the song" else "Syncing the song…", color = Tone.text) }
                }
            }
        }
    }
}
