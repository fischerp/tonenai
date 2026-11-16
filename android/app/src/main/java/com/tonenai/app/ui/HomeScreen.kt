package com.tonenai.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonenai.app.AppViewModel
import com.tonenai.app.data.Song

@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (String) -> Unit, onLivesets: () -> Unit, onSettings: () -> Unit, onRequest: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val songs = vm.songs
    val shown = remember(songs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) songs else songs.filter { q in it.title.lowercase() || q in it.artist.lowercase() }
    }
    val browsing = query.isBlank()
    val favourites = remember(songs, vm.favorites) { songs.filter { it.id in vm.favorites } }
    val learning = remember(songs, vm.meta) { songs.filter { vm.meta[it.id]?.status == "learning" } }
    val live = vm.playing?.let { p -> vm.song(p.songId)?.let { it to p.index } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importTsl(uri) }
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "top", span = full) {
            // The logo gets its own row of icons; the amp pill sits below so nothing is squeezed on a narrow phone.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Wordmark() }
                    IconButton(onClick = onLivesets) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Livesets", tint = Tone.muted) }
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.Add, contentDescription = "Import a .tsl", tint = Tone.muted) }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Tone.muted) }
                }
                AmpPill(vm.amp, onClick = { vm.connectAmp() }, modifier = Modifier.padding(start = 4.dp))
            }
        }
        item(key = "search", span = full) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search songs or artists", color = Tone.muted) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Tone.muted) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Tone.muted)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Tone.panel, unfocusedContainerColor = Tone.panel,
                    focusedBorderColor = Tone.accent, unfocusedBorderColor = Tone.line, cursorColor = Tone.accentBright,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (browsing && live != null) item(key = "live", span = full) {
            NowPlaying(live.first, live.first.patches.getOrNull(live.second)?.name.orEmpty()) { onOpen(live.first.id) }
        }
        if (browsing && favourites.isNotEmpty()) item(key = "favs", span = full) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Tone.accentBright, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    SectionLabel("Favourites")
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favourites, key = { it.id }) { song -> SongCard(song, vm, Modifier.width(132.dp)) { onOpen(song.id) } }
                }
            }
        }
        if (browsing && learning.isNotEmpty()) item(key = "learning", span = full) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("🎯 Learning")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(learning, key = { it.id }) { song -> SongCard(song, vm, Modifier.width(132.dp)) { onOpen(song.id) } }
                }
            }
        }
        item(key = "all", span = full) {
            SectionLabel(if (browsing) "All songs · ${songs.size}" else "${shown.size} found", Modifier.padding(top = 4.dp))
        }
        items(shown, key = { it.id }) { song -> SongCard(song, vm) { onOpen(song.id) } }
        if (shown.isEmpty() && vm.loaded) item(key = "empty", span = full) { Column {
            Text(
                if (browsing) "Your library is empty. Tap + to import a Tone Studio .tsl, or connect Google Drive in Settings to bring your songs here."
                else "Nothing in your library matches “${query.trim()}”.",
                style = MaterialTheme.typography.bodyMedium, color = Tone.muted, modifier = Modifier.padding(vertical = 24.dp),
            )
            if (!browsing) androidx.compose.material3.OutlinedButton(onClick = { onRequest(query.trim()) }) {
                Text("Ask my computer to create it", color = Tone.text)
            }
        } }
        item(key = "bottom", span = full) { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun SongCard(song: Song, vm: AppViewModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val onAmp = vm.playing?.songId == song.id && vm.amp.connected
    // The rounded corner is only on the cover: clipping the whole card cut the first letter of the text in its bottom-left corner.
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            CoverArt(
                song,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, if (onAmp) Tone.accentBright else Tone.line, RoundedCornerShape(16.dp)),
            )
            if (onAmp) Chip("ON THE AMP", Modifier.align(Alignment.TopStart).padding(8.dp), strong = true)
            when (vm.meta[song.id]?.status) {
                "learning" -> Chip("🎯 LEARNING", Modifier.align(Alignment.BottomStart).padding(8.dp))
                "played" -> Chip("✓ PLAYED", Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.titleSmall, color = Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp))
        Text(
            listOf(song.artist, "${song.patches.size} " + if (song.patches.size == 1) "patch" else "patches").filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = Tone.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun NowPlaying(song: Song, patch: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Tone.accentDeep.copy(alpha = 0.85f), Tone.panel)))
            .border(1.dp, Tone.accent.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(song, Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)), initialsSize = 24)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Tone.ok))
                Spacer(Modifier.width(6.dp))
                Text("ON THE AMP", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
            Text(song.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(song.artist, patch).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
