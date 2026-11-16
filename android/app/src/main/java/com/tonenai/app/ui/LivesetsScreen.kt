package com.tonenai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonenai.app.AppViewModel
import com.tonenai.app.data.LivesetFiles
import com.tonenai.app.data.Song
import com.tonenai.app.data.SlotRef
import com.tonenai.app.katana.Slots
import kotlinx.coroutines.delay

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tone.accentBright, unfocusedBorderColor = Tone.line,
    focusedTextColor = Tone.text, unfocusedTextColor = Tone.text, cursorColor = Tone.accentBright,
    focusedLabelColor = Tone.muted, unfocusedLabelColor = Tone.muted,
    focusedPlaceholderColor = Tone.muted, unfocusedPlaceholderColor = Tone.muted,
)

private fun isInit(name: String?) = name.isNullOrEmpty() || name.uppercase().contains("INIT TONE")

/** All livesets: set lists for the amp's eight bank slots. */
@Composable
fun LivesetsScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tone.text) }
            Text("Livesets", style = MaterialTheme.typography.titleLarge, color = Tone.text, modifier = Modifier.weight(1f))
            IconButton(onClick = { onOpen(vm.newLiveset()) }) { Icon(Icons.Filled.Add, contentDescription = "New liveset", tint = Tone.text) }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    "Plan the amp's eight memory slots (A: CH-1 to CH-4, B: CH-1 to CH-4) as a set list, then send it to the amp over USB.",
                    style = MaterialTheme.typography.bodyMedium, color = Tone.muted,
                )
            }
            if (vm.livesets.isEmpty()) item(key = "empty") {
                Panel { Text("No livesets yet. Tap + to plan one.", style = MaterialTheme.typography.bodyMedium, color = Tone.muted) }
            }
            items(vm.livesets, key = { it.id }) { ls ->
                Panel(Modifier.clickable { onOpen(ls.id) }) {
                    Text(ls.name, style = MaterialTheme.typography.titleMedium, color = Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${ls.slots.size} of 8 slots filled", style = MaterialTheme.typography.bodyMedium, color = Tone.muted)
                    if (ls.notes.isNotBlank()) Text(ls.notes, style = MaterialTheme.typography.bodySmall, color = Tone.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One liveset: its eight slots, and sending it to the amp. */
@Composable
fun LivesetScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val ls = vm.liveset(id)
    if (ls == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var name by remember(id) { mutableStateOf(ls.name) }
    var notes by remember(id) { mutableStateOf(ls.notes) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmSend by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(Triple(id, name, notes))

    fun flush() {
        val (lid, n, no) = latest
        val current = vm.liveset(lid) ?: return
        if (n.isNotBlank() && (current.name != n.trim().take(LivesetFiles.MAX_NAME) || current.notes != no.take(LivesetFiles.MAX_NOTES))) {
            vm.saveLiveset(current.copy(name = n, notes = no))
        }
    }
    LaunchedEffect(name, notes) {
        delay(600)
        flush()
    }
    DisposableEffect(id) { onDispose { flush() } }
    LaunchedEffect(vm.amp.connected) { if (vm.amp.connected) vm.readAmpSlots() }

    val filled = ls.slots.keys.toList()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "top") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tone.text) }
                Text("Liveset", style = MaterialTheme.typography.titleLarge, color = Tone.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete liveset", tint = Tone.muted) }
            }
        }
        item(key = "fields") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(LivesetFiles.MAX_NAME) }, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(notes, { notes = it.take(LivesetFiles.MAX_NOTES) }, label = { Text("Notes") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            }
        }
        item(key = "label") {
            SectionLabel(if (vm.amp.connected) "Amp slots" else "Amp slots · connect the amp to see what is on it", Modifier.padding(top = 4.dp))
        }
        items(LivesetFiles.KEYS, key = { it }) { key ->
            val ref = ls.slots[key]
            val song = ref?.let { vm.song(it.song) }
            val patch = song?.patches?.firstOrNull { it.index == ref?.index }
            val ampName = vm.ampSlots?.getOrNull(Slots.KEYS.indexOf(key))
            SlotCard(
                key = key, ref = ref, song = song, patchName = patch?.name,
                ampLine = if (vm.ampSlots == null) null else when {
                    ref != null && patch != null && ampName == patch.name.take(16).trim() -> "✓ On your amp" to true
                    ref != null && !isInit(ampName) -> "Amp has “$ampName”: will be replaced" to false
                    ref != null -> "Amp slot is empty" to false
                    !isInit(ampName) -> "Amp has “$ampName”" to false
                    else -> null
                },
                onPick = { picking = key },
                onClear = { vm.saveLiveset(ls.assign(key, null)) },
            )
        }
        item(key = "actions") {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { confirmSend = true }, enabled = filled.isNotEmpty() && !vm.sendingLiveset && !vm.amp.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.accent, contentColor = Color.White,
                        disabledContainerColor = Tone.raised, disabledContentColor = Tone.muted),
                ) {
                    if (vm.sendingLiveset) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (vm.amp.connected) "Send to amp" else "Connect the amp to send")
                }
                if (vm.hasSlotBackup) TextButton(onClick = { confirmUndo = true }, enabled = !vm.sendingLiveset && !vm.amp.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Undo last send", color = Tone.text)
                }
                Text(
                    "The Panel slot cannot be written. Before anything is written, the patches now in those slots are saved on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = Tone.muted,
                )
            }
        }
    }

    picking?.let { key ->
        PatchPicker(vm, key, onDismiss = { picking = null }, onPick = { ref ->
            picking = null
            vm.saveLiveset(ls.assign(key, ref))
        })
    }
    if (confirmSend) AlertDialog(
        onDismissRequest = { confirmSend = false },
        title = { Text("Send to the amp?") },
        text = { Text("This writes ${filled.size} patch${if (filled.size == 1) "" else "es"} into slots ${LivesetFiles.KEYS.filter { it in ls.slots }.joinToString(", ")}. " +
            "What is in those slots now is saved first, and “Undo last send” puts it back.") },
        confirmButton = { TextButton(onClick = { confirmSend = false; flush(); vm.sendLiveset(id) }) { Text("Send", color = Tone.accentBright) } },
        dismissButton = { TextButton(onClick = { confirmSend = false }) { Text("Cancel", color = Tone.text) } },
        containerColor = Tone.raised,
    )
    if (confirmUndo) AlertDialog(
        onDismissRequest = { confirmUndo = false },
        title = { Text("Undo the last send?") },
        text = { Text("The amp slots get back what they held before the last send.") },
        confirmButton = { TextButton(onClick = { confirmUndo = false; vm.restoreSlotBackup() }) { Text("Undo", color = Tone.accentBright) } },
        dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel", color = Tone.text) } },
        containerColor = Tone.raised,
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${ls.name}”?") },
        text = { Text("Only the plan is deleted. Your songs and the amp are not touched.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); vm.deleteLiveset(id) }) { Text("Delete", color = Tone.accentBright) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Tone.text) } },
        containerColor = Tone.raised,
    )
}

@Composable
private fun SlotCard(
    key: String, ref: SlotRef?, song: Song?, patchName: String?, ampLine: Pair<String, Boolean>?,
    onPick: () -> Unit, onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tone.panel)
            .border(1.dp, Tone.line, RoundedCornerShape(16.dp)).clickable(onClick = onPick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (song != null) CoverArt(song, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), initialsSize = 16)
        else Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Tone.raised), contentAlignment = Alignment.Center) {
            if (ref == null) Icon(Icons.Filled.Add, contentDescription = null, tint = Tone.accentBright)
            else Text(key, style = MaterialTheme.typography.titleMedium, color = Tone.muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(LivesetFiles.label(key))
            when {
                song != null -> {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(patchName ?: "Patch ${(ref?.index ?: 0) + 1}", song.artist.ifEmpty { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = Tone.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ref != null -> Text("Song not in your library: ${ref.song}", style = MaterialTheme.typography.bodyMedium, color = Tone.muted, maxLines = 2)
                else -> Text("Add a patch", style = MaterialTheme.typography.titleMedium, color = Tone.accentBright)
            }
            ampLine?.let { (text, ok) -> Text(text, style = MaterialTheme.typography.labelMedium, color = if (ok) Tone.ok else Tone.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (ref != null) IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Remove from slot", tint = Tone.muted) }
    }
}

/** A bottom sheet in two steps: pick a song (with its cover), then one of its patches (a single-patch song is picked at once). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatchPicker(vm: AppViewModel, slot: String, onPick: (SlotRef) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var song by remember { mutableStateOf<Song?>(null) }
    val chosen = song
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tone.bg, contentColor = Tone.text,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (chosen != null) IconButton(onClick = { song = null }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the songs", tint = Tone.text)
                }
                Column(Modifier.weight(1f)) {
                    SectionLabel("Slot ${LivesetFiles.label(slot)}")
                    Text(chosen?.title ?: "Choose a song", style = MaterialTheme.typography.titleLarge, color = Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (chosen != null) CoverArt(chosen, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), initialsSize = 14)
            }
            Spacer(Modifier.height(12.dp))
            if (chosen == null) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search songs or artists", color = Tone.muted) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Tone.muted) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Tone.panel, unfocusedContainerColor = Tone.panel,
                        focusedBorderColor = Tone.accent, unfocusedBorderColor = Tone.line, cursorColor = Tone.accentBright,
                        focusedTextColor = Tone.text, unfocusedTextColor = Tone.text,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                val q = query.trim().lowercase()
                val shown = vm.songs.filter { q.isEmpty() || q in it.title.lowercase() || q in it.artist.lowercase() }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(shown, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                if (s.patches.size == 1) onPick(SlotRef(s.id, s.patches[0].index)) else song = s
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CoverArt(s, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), initialsSize = 16)
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium, color = Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist.ifEmpty { "Imported" }, style = MaterialTheme.typography.bodyMedium, color = Tone.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Chip("${s.patches.size} ${if (s.patches.size == 1) "patch" else "patches"}")
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Tone.muted)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chosen.patches, key = { it.index }) { p ->
                        val shape = RoundedCornerShape(18.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(Tone.panel).border(1.dp, Tone.line, shape)
                                .clickable { onPick(SlotRef(chosen.id, p.index)) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, color = Tone.text)
                                if (p.amp.isNotEmpty() || p.effects.isNotEmpty()) {
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (p.amp.isNotEmpty()) Chip(p.amp, strong = true)
                                        p.effects.forEach { Chip(it) }
                                    }
                                }
                                if (p.note.isNotEmpty()) Text(p.note, style = MaterialTheme.typography.bodySmall, color = Tone.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Tone.accentBrush), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = "Use ${p.name}", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
