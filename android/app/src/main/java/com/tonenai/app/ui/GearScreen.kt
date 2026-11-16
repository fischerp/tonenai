package com.tonenai.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tonenai.app.AppViewModel
import com.tonenai.app.data.GearFiles
import com.tonenai.app.data.Rig
import org.json.JSONObject

/** My gear: the amp and pedals you own. The song page shows the Whammy and Band sections only for pedals you have. */
@Composable
fun GearScreen(vm: AppViewModel, onBack: () -> Unit) {
    val rig = vm.rig
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    val allPedals = vm.catalog.pedals + rig.custom

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tone.text) }
            Text("My gear", style = MaterialTheme.typography.titleLarge, color = Tone.text)
        }
        Text("Tell TonenAI what you play through. The song page and the AI follow this list.", style = MaterialTheme.typography.bodyMedium, color = Tone.muted)

        SectionLabel("Amp", Modifier.padding(start = 4.dp))
        Panel {
            for (a in vm.catalog.amps) {
                Row(Modifier.fillMaxWidth().clickable { vm.saveRig(Rig(a.id, rig.pedals, rig.customRaw)) }, verticalAlignment = Alignment.Top) {
                    RadioButton(rig.amp == a.id, { vm.saveRig(Rig(a.id, rig.pedals, rig.customRaw)) },
                        colors = RadioButtonDefaults.colors(selectedColor = Tone.accentBright, unselectedColor = Tone.muted))
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium, color = Tone.text)
                        Text(a.about, style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                    }
                }
            }
        }

        SectionLabel("Pedals", Modifier.padding(start = 4.dp))
        Panel {
            if (allPedals.isEmpty()) Text("No pedals in the catalog.", color = Tone.muted)
            for (p in allPedals) {
                val owned = p.id in rig.pedals
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(p.icon.ifEmpty { "🎛" }, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, color = Tone.text)
                        if (p.about.isNotEmpty()) Text(p.about, style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                    }
                    if (p.custom) IconButton(onClick = {
                        vm.saveRig(Rig(rig.amp, rig.pedals - p.id, rig.customRaw.filter { it.optString("id") != p.id }))
                    }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${p.name}", tint = Tone.muted) }
                    Switch(owned, { on -> vm.saveRig(Rig(rig.amp, if (on) rig.pedals + p.id else rig.pedals - p.id, rig.customRaw)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Tone.accent, checkedThumbColor = Color.White))
                }
            }
        }

        SectionLabel("Add your own pedal", Modifier.padding(start = 4.dp))
        Panel {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Pedal name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
            OutlinedTextField(about, { about = it.take(200) }, label = { Text("What it does (optional)") }, maxLines = 2, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
            Button(
                onClick = {
                    val id = GearFiles.newCustomId(name, allPedals.map { it.id }.toSet())
                    val pedal = JSONObject().put("id", id).put("name", name.trim()).put("about", about.trim())
                    vm.saveRig(Rig(rig.amp, rig.pedals + id, rig.customRaw + pedal))
                    name = ""
                    about = ""
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.accent, contentColor = Color.White, disabledContainerColor = Tone.raised, disabledContentColor = Tone.muted),
            ) { Text("Add pedal") }
            Text("The AI works out each pedal's settings per song once you connect it in the AI settings.", style = MaterialTheme.typography.bodySmall, color = Tone.muted)
        }
    }
}
