package com.tonenai.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.tonenai.app.AppViewModel
import com.tonenai.app.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onDiagnostics: () -> Unit, onGuitars: () -> Unit, onGear: () -> Unit, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tone.text) }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Tone.text)
        }

        SectionLabel("Amp", Modifier.padding(start = 4.dp))
        Panel {
            Text(if (vm.amp.connected) "Katana connected" else "Amp not connected", style = MaterialTheme.typography.titleMedium, color = Tone.text)
            Text(
                if (vm.amp.connected) "Playing “${vm.amp.patch}”. Patches go to the amp's temporary patch only; its memory is not changed."
                else "Switch the amp on and plug its USB cable into the phone (a USB-C adapter works). Allow USB access when Android asks.",
                style = MaterialTheme.typography.bodyMedium, color = Tone.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!vm.amp.connected) Button(onClick = { vm.connectAmp() }, colors = ButtonDefaults.buttonColors(containerColor = Tone.accent)) { Text("Connect") }
                OutlinedButton(onClick = onDiagnostics) { Text("USB diagnostics", color = Tone.text) }
            }
        }

        SectionLabel("Sound", Modifier.padding(start = 4.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sound effects", style = MaterialTheme.typography.titleMedium, color = Tone.text)
                    Text("Every sound the app makes: the opening sting and the Trio+ click track. Switch off to keep the app silent.", style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                }
                AccentSwitch(vm.soundEffects, true) { vm.changeSoundEffects(it) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Opening sound", style = MaterialTheme.typography.titleMedium, color = if (vm.soundEffects) Tone.text else Tone.muted)
                    Text("Plays the TonenAI sting with the opening animation (about 4 seconds). Off by default.", style = MaterialTheme.typography.bodySmall, color = Tone.muted)
                }
                AccentSwitch(vm.introSound, vm.soundEffects) { vm.changeIntroSound(it) }
            }
        }

        SectionLabel("My setup", Modifier.padding(start = 4.dp))
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onGuitars) { Text("My guitars (${vm.guitars.size})", color = Tone.text) }
                OutlinedButton(onClick = onGear) { Text("My gear", color = Tone.text) }
            }
            OutlinedButton(onClick = onRequest) { Text("Request a song from my computer", color = Tone.text) }
        }

        SectionLabel("Google Drive", Modifier.padding(start = 4.dp))
        DrivePanel(vm)

        SectionLabel("About", Modifier.padding(start = 4.dp))
        Panel {
            Text("TonenAI ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = Tone.text)
            Text(
                "Unofficial. Not affiliated with BOSS or Roland. Tones are informed approximations of how a song sounds, not copies. " +
                    "Album art comes from Apple's iTunes Search API and belongs to its owners.",
                style = MaterialTheme.typography.bodySmall, color = Tone.muted,
            )
        }
    }
}

private fun whenText(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault(iso)

/** Optional sync of the song files with the user's own Google Drive (see docs/google-drive-sync.md). */
@Composable
private fun DrivePanel(vm: AppViewModel) {
    val context = LocalContext.current
    // Silent when access was already granted, Google's consent screen otherwise.
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }
            .onSuccess { if (it.accessToken != null) vm.onDriveAuthorized() else vm.onDriveAuthorizationFailed("Google did not grant access.") }
            .onFailure { vm.onDriveAuthorizationFailed("Google sign-in was cancelled or is not set up for this app yet.") }
    }
    fun connect() {
        Identity.getAuthorizationClient(context).authorize(vm.driveAuthorizationRequest())
            .addOnSuccessListener { r ->
                val intent = r.pendingIntent
                if (r.hasResolution() && intent != null) authLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                else vm.onDriveAuthorized()
            }
            .addOnFailureListener { vm.onDriveAuthorizationFailed("Could not start Google sign-in: " + (it.message ?: "unknown error")) }
    }

    val connected = vm.driveConnected
    val synced = vm.driveLastSyncedAt
    val error = vm.driveLastError
    Panel {
        Text(
            when {
                vm.syncing -> "Syncing…"
                !connected -> "Not connected"
                synced != null -> "Connected · last sync ${whenText(synced)}"
                else -> "Connected · not synced yet"
            },
            style = MaterialTheme.typography.titleMedium, color = Tone.text,
        )
        Text(
            "Your songs are copied to a private folder of your own Google Drive, so this phone and the desktop app show the same library. Nothing is shared with anyone.",
            style = MaterialTheme.typography.bodyMedium, color = Tone.muted,
        )
        if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        else if (connected) vm.driveLastResult?.let { Text("Last sync: $it", style = MaterialTheme.typography.bodySmall, color = Tone.muted) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (connected && error == null) {
                Button(onClick = { vm.syncNow() }, enabled = !vm.syncing, colors = ButtonDefaults.buttonColors(containerColor = Tone.accent)) { Text("Sync now") }
            } else {
                Button(onClick = { connect() }, enabled = !vm.syncing, colors = ButtonDefaults.buttonColors(containerColor = Tone.accent)) {
                    Text(if (connected) "Connect again" else "Connect")
                }
            }
            if (connected) OutlinedButton(onClick = { vm.disconnectDrive() }, enabled = !vm.syncing) { Text("Disconnect", color = Tone.text) }
        }
    }
}


@androidx.compose.runtime.Composable
private fun AccentSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(checked, onChange, enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Tone.accent, checkedThumbColor = Color.White))
}
