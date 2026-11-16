package com.tonenai.app.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonenai.app.katana.Katana
import com.tonenai.app.katana.KatanaLink
import com.tonenai.app.katana.MidiApiLink
import com.tonenai.app.katana.Sysex
import com.tonenai.app.katana.Tsl
import com.tonenai.app.katana.TslPatch
import com.tonenai.app.katana.UsbRawLink
import kotlinx.coroutines.launch

/** Tries Android's MIDI API first, then direct USB. Returns the amp once it has answered. */
private suspend fun connect(context: Context, say: (String) -> Unit): Katana? {
    say("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

    suspend fun answers(link: KatanaLink): Katana? {
        val amp = Katana(link)
        val id = amp.identity()
        say(if (id != null) "  identity reply: ${Sysex.hex(id)}" else "  no identity reply")
        val name = amp.patchName()
        say(if (name != null) "  current patch: \"$name\"" else "  no reply to a patch read")
        if (id != null || name != null) return amp
        amp.close()
        return null
    }

    val midi = MidiApiLink.devices(context)
    say("Android MIDI devices: " + if (midi.isEmpty()) "none" else midi.size.toString())
    midi.forEach { say("  " + MidiApiLink.label(it)) }
    for (info in midi.filter(MidiApiLink::usable)) {
        say("Trying the MIDI API on ${MidiApiLink.label(info)}")
        val link = MidiApiLink.open(context, info)
        if (link == null) {
            say("  could not open it (in use by another app?)")
            continue
        }
        answers(link)?.let { return it }
    }

    val usb = UsbRawLink.devices(context)
    say("USB devices: " + if (usb.isEmpty()) "none (cable, OTG adapter or amp power?)" else usb.size.toString())
    usb.forEach { say("  " + UsbRawLink.describe(it)) }
    for (device in usb.filter { it.vendorId == UsbRawLink.ROLAND_VENDOR_ID }) {
        say("Trying direct USB on %04X:%04X".format(device.vendorId, device.productId))
        if (!UsbRawLink.requestPermission(context, device)) {
            say("  USB permission was not given")
            continue
        }
        val link = UsbRawLink.open(context, device)
        if (link == null) {
            say("  no usable interface (needs one with bulk in and bulk out)")
            continue
        }
        answers(link)?.let { return it }
    }
    return null
}

/**
 * USB diagnostics: talks to the amp on its own connection and shows everything the phone sees.
 * The log is what to send when the app cannot find an amp.
 */
@Composable
fun UsbTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val log = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    var amp by remember { mutableStateOf<Katana?>(null) }
    var busy by remember { mutableStateOf(false) }
    var patches by remember { mutableStateOf(emptyList<TslPatch>()) }

    fun say(line: String) {
        log.add(line)
    }

    fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                say("ERROR: $e")
            } finally {
                busy = false
            }
        }
    }

    fun load(text: String, from: String) {
        try {
            patches = Tsl.parse(text)
            say("Loaded ${patches.size} patches from $from")
        } catch (e: Exception) {
            say("Could not read $from: ${e.message}")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }.getOrNull()
            if (text == null) say("Could not open that file") else load(text, "the chosen file")
        }
    }

    LaunchedEffect(Unit) {
        load(context.assets.open("sample.tsl").bufferedReader().use { it.readText() }, "the built-in sample")
    }
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.scrollToItem(log.size - 1)
    }
    DisposableEffect(Unit) {
        onDispose { amp?.close() }
    }

    run {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("USB diagnostics", style = MaterialTheme.typography.headlineSmall)
            Text(
                amp?.let { "Connected through ${it.transport}" }
                    ?: "Amp on, USB cable plugged into the phone, then press Connect.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    task {
                        amp?.close()
                        amp = null
                        say("--- Connect ---")
                        amp = connect(context, ::say)
                        say(amp?.let { "RESULT: works through ${it.transport}" } ?: "RESULT: the amp did not answer")
                    }
                }) { Text("1. Connect") }
                OutlinedButton(enabled = !busy && amp != null, onClick = {
                    task { say("Current patch: " + (amp?.patchName()?.let { "\"$it\"" } ?: "no reply")) }
                }) { Text("Read patch") }
            }
            Text("2. Send a patch (temporary patch only, the amp's memory is not changed)",
                style = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(patches) { patch ->
                    Button(enabled = !busy && amp != null, onClick = {
                        task {
                            say("Sending \"${patch.name}\"...")
                            amp?.applyLive(patch.blocks)
                            val now = amp?.patchName()
                            say(if (now == patch.name) "RESULT: patch sent, the amp now reports \"$now\""
                                else "RESULT: sent, but the amp reports \"$now\"")
                        }
                    }) { Text(patch.name) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Open a .tsl") }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(log.joinToString("\n"))) }) {
                    Text("Copy log")
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                items(log.size) { i ->
                    Text(log[i], fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
