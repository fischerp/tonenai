package com.tonenai.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.tonenai.app.ui.GearScreen
import com.tonenai.app.ui.GuitarsScreen
import com.tonenai.app.ui.HomeScreen
import com.tonenai.app.ui.Splash
import com.tonenai.app.ui.RequestsScreen
import com.tonenai.app.ui.LivesetScreen
import com.tonenai.app.ui.LivesetsScreen
import com.tonenai.app.ui.SettingsScreen
import com.tonenai.app.ui.SongScreen
import com.tonenai.app.ui.Tone
import com.tonenai.app.ui.TonenTheme
import com.tonenai.app.ui.UsbTestScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TonenTheme { App(vm) } }
        if (savedInstanceState == null) importFrom(intent)      // not again after a rotation
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFrom(intent)
    }

    /** A .tsl opened with TonenAI ("Open with") or shared to it. */
    private fun importFrom(incoming: Intent?) {
        val i = incoming ?: return
        val uri: Uri? = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) vm.importTsl(uri)
    }

    override fun onStart() {
        super.onStart()
        vm.foreground = true
    }

    override fun onStop() {
        vm.foreground = false
        super.onStop()
    }
}

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val USB = "usb"
private const val SONG = "song:"
private const val REQUESTS = "requests"
private const val GUITARS = "guitars"
private const val GEAR = "gear"
private const val LIVESETS = "livesets"
private const val LIVESET = "liveset:"

@Composable
private fun App(vm: AppViewModel) {
    var screen by rememberSaveable { mutableStateOf(HOME) }
    var requestText by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        // A newer note replaces the one on screen; clearing it right away lets the same text be shown again later.
        snapshotFlow { vm.notice }.filterNotNull().collectLatest { text ->
            vm.notice = null
            snackbar.showSnackbar(text)
        }
    }
    BackHandler(enabled = screen != HOME) {
        screen = when {
            screen == USB || screen == GUITARS || screen == GEAR -> SETTINGS
            screen.startsWith(LIVESET) -> LIVESETS
            else -> HOME
        }
    }

    var splash by rememberSaveable { mutableStateOf(true) }     // the opening animation, once per launch
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Tone.bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, containerColor = Tone.raised, contentColor = Tone.text) } },
        ) { inner ->
            AnimatedContent(screen, Modifier.padding(inner).fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { current ->
                when {
                    current == SETTINGS -> SettingsScreen(vm, onBack = { screen = HOME }, onDiagnostics = { screen = USB },
                        onGuitars = { screen = GUITARS }, onGear = { screen = GEAR }, onRequest = { requestText = ""; screen = REQUESTS })
                    current == REQUESTS -> RequestsScreen(vm, requestText, onBack = { screen = HOME }, onOpenSong = { screen = SONG + it })
                    current == GUITARS -> GuitarsScreen(vm, onBack = { screen = SETTINGS })
                    current == GEAR -> GearScreen(vm, onBack = { screen = SETTINGS })
                    current == USB -> {
                        DisposableEffect(Unit) {
                            vm.diagnostics = true
                            onDispose { vm.diagnostics = false }
                        }
                        UsbTestScreen(onBack = { screen = SETTINGS })
                    }
                    current == LIVESETS -> LivesetsScreen(vm, onBack = { screen = HOME }, onOpen = { screen = LIVESET + it })
                    current.startsWith(LIVESET) -> LivesetScreen(vm, current.removePrefix(LIVESET), onBack = { screen = LIVESETS })
                    current.startsWith(SONG) -> {
                        val song = vm.song(current.removePrefix(SONG))
                        if (song != null) SongScreen(vm, song, onBack = { screen = HOME })
                        else Box(Modifier.fillMaxSize()) { LaunchedEffect(Unit) { if (vm.loaded) screen = HOME } }
                    }
                    else -> HomeScreen(vm, onOpen = { screen = SONG + it }, onLivesets = { screen = LIVESETS }, onSettings = { screen = SETTINGS },
                        onRequest = { requestText = it; screen = REQUESTS })
                }
            }
        }
        if (splash) Splash(sound = vm.introSound && vm.soundEffects, onDone = { splash = false })
    }
}
