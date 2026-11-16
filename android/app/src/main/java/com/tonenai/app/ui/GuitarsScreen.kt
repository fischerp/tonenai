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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonenai.app.AppViewModel
import com.tonenai.app.data.Guitar
import com.tonenai.app.data.GuitarFiles

private fun pickupLabel(p: String) = when (p) {
    "humbucker" -> "Humbucker"; "single-coil" -> "Single coil"; "p90" -> "P-90"; "sss" -> "SSS (3 single coils)"
    "hh" -> "HH"; "hs" -> "HS"; "hss" -> "HSS"; "hsh" -> "HSH"; "acoustic" -> "Acoustic"; else -> "Other"
}

/** The finish of a guitar as a round swatch. */
@Composable
private fun FinishDot(finish: String, size: androidx.compose.ui.unit.Dp) {
    val f = GuitarFiles.finish(finish)
    val brush = if (f == null) Brush.linearGradient(listOf(Tone.raised, Tone.line)) else Brush.radialGradient(listOf(Color(f.top), Color(f.bottom)))
    Box(Modifier.size(size).clip(CircleShape).background(brush).border(1.dp, Tone.line, CircleShape))
}

/** My guitars: the guitars you own; the song page can then say which one suits a song. */
@Composable
fun GuitarsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Guitar?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tone.text) }
            Text("My guitars", style = MaterialTheme.typography.titleLarge, color = Tone.text, modifier = Modifier.weight(1f))
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add a guitar", tint = Tone.text) }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.guitars.isEmpty()) item(key = "empty") {
                Panel { Text("No guitars yet. Tap + to add the ones you play.", style = MaterialTheme.typography.bodyMedium, color = Tone.muted) }
            }
            items(vm.guitars, key = { it.id }) { g ->
                Panel(Modifier.clickable { editing = g }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GuitarPic(g.kind, g.name, g.finish, Modifier.size(width = 52.dp, height = 84.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium, color = Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(g.brand, if (g.pickups == "other") g.pickupsOther.ifEmpty { "Other pickups" } else pickupLabel(g.pickups), GuitarFiles.finish(g.finish)?.label.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = Tone.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (g.notes.isNotBlank()) Text(g.notes, style = MaterialTheme.typography.bodySmall, color = Tone.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (adding) GuitarEditor(vm, null) { adding = false }
    editing?.let { g -> GuitarEditor(vm, g) { editing = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuitarEditor(vm: AppViewModel, initial: Guitar?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var brand by remember { mutableStateOf(initial?.brand.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind.orEmpty()) }
    var pickups by remember { mutableStateOf(initial?.pickups ?: "humbucker") }
    var other by remember { mutableStateOf(initial?.pickupsOther.orEmpty()) }
    var finish by remember { mutableStateOf(initial?.finish.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tone.bg, contentColor = Tone.text,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "Add a guitar" else "Edit guitar", style = MaterialTheme.typography.titleLarge, color = Tone.text)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GuitarPic(kind.ifEmpty { "Other" }, name, finish, Modifier.size(width = 130.dp, height = 240.dp))
                Text(name.ifBlank { "New guitar" }, style = MaterialTheme.typography.titleMedium, color = Tone.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(kind, GuitarFiles.finish(finish)?.label.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Tone.muted)
            }
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
            OutlinedTextField(brand, { brand = it.take(40) }, label = { Text("Brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
            SectionLabel("Model")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in GuitarFiles.KINDS) Pick(k, kind == k) { kind = if (kind == k) "" else k }
            }
            SectionLabel("Pickups")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in GuitarFiles.PICKUPS) Pick(pickupLabel(p), pickups == p) { pickups = p }
            }
            if (pickups == "other") OutlinedTextField(other, { other = it.take(40) }, label = { Text("Which pickups?") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
            SectionLabel("Finish")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                for (f in GuitarFiles.FINISHES) {
                    Box(Modifier.clip(CircleShape).border(2.dp, if (finish == f.key) Tone.accentBright else Color.Transparent, CircleShape).padding(3.dp)
                        .clickable { finish = if (finish == f.key) "" else f.key }) { FinishDot(f.key, 38.dp) }
                }
            }
            GuitarFiles.finish(finish)?.let { Text(it.label, style = MaterialTheme.typography.bodySmall, color = Tone.muted) }
            OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("Notes (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
            Button(
                onClick = {
                    val id = initial?.id ?: GuitarFiles.newId(name, vm.guitars.map { it.id }.toSet())
                    vm.saveGuitar(Guitar(id, name.trim(), brand.trim(), kind, pickups, if (pickups == "other") other.trim() else "", finish, notes))
                    onDismiss()
                },
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.accent, contentColor = Color.White, disabledContainerColor = Tone.raised, disabledContentColor = Tone.muted),
            ) { Text("Save") }
            if (initial != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete this guitar", color = Tone.muted) }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmDelete && initial != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${initial.name}”?") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteGuitar(initial.id); onDismiss() }) { Text("Delete", color = Tone.accentBright) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Tone.text) } },
        containerColor = Tone.raised,
    )
}
