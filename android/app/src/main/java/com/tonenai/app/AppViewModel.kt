package com.tonenai.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.tonenai.app.data.DriveFiles
import com.tonenai.app.data.DriveSync
import com.tonenai.app.data.GearCatalog
import com.tonenai.app.data.GearFiles
import com.tonenai.app.data.Guitar
import com.tonenai.app.data.GuitarFiles
import com.tonenai.app.data.Liveset
import com.tonenai.app.data.LivesetFiles
import com.tonenai.app.data.LivesetStore
import com.tonenai.app.data.PersonalStore
import com.tonenai.app.data.RequestItem
import com.tonenai.app.data.RequestStore
import com.tonenai.app.data.Rig
import com.tonenai.app.data.Song
import com.tonenai.app.data.SongMeta
import com.tonenai.app.data.SongMetaFiles
import com.tonenai.app.data.TabFiles
import com.tonenai.app.data.TabsConfig
import com.tonenai.app.data.SongFiles
import com.tonenai.app.data.SongStore
import com.tonenai.app.katana.Katana
import com.tonenai.app.katana.Slots
import com.tonenai.app.katana.Tsl
import com.tonenai.app.katana.UsbRawLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What is on the amp right now, as far as the app knows. */
data class AmpState(val connected: Boolean = false, val patch: String = "", val busy: Boolean = false)

class Playing(val songId: String, val index: Int)

/** A patch ready to be written into amp slot [key]. */
private class Prepared(val key: String, val blocks: Map<String, List<Int>>, val name: String)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private val store = SongStore(application)
    private val livesetStore = LivesetStore(application)
    private val drive = DriveSync(application, store.dir, application.filesDir)
    private val livesetLock = Mutex()
    private val personal = PersonalStore(application)
    private val settingsPrefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private val requestStore = RequestStore(java.io.File(application.filesDir, "requests"))

    var songs by mutableStateOf(emptyList<Song>())
        private set
    var favorites by mutableStateOf(store.favorites)
        private set
    var amp by mutableStateOf(AmpState())
        private set
    var playing by mutableStateOf<Playing?>(null)
        private set
    /** The patch being sent right now, for the spinner on its card. */
    var sending by mutableStateOf<Playing?>(null)
        private set
    /** A short note for the user (shown as a snackbar, then cleared). */
    var notice by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
        private set

    var livesets by mutableStateOf(emptyList<Liveset>())
        private set
    /** Names in the amp's memory slots (see [Slots.KEYS]), or null until the amp has been read. */
    var ampSlots by mutableStateOf<List<String?>?>(null)
        private set
    var sendingLiveset by mutableStateOf(false)
        private set

    /** Your practice status and notes per song, your tab site, your guitars and your gear (all synced through Drive). */
    var meta by mutableStateOf(emptyMap<String, SongMeta>())
        private set
    var tabs by mutableStateOf(TabsConfig())
        private set
    var guitars by mutableStateOf(emptyList<Guitar>())
        private set
    /** Play the desktop's opening sound with the opening animation (off until you turn it on). */
    var introSound by mutableStateOf(settingsPrefs.getBoolean("intro_sound", false))
        private set

    /** Master switch for every sound the app makes (opening sting, Trio+ click track). On by default. */
    var soundEffects by mutableStateOf(settingsPrefs.getBoolean("sound_effects", true))
        private set

    fun changeSoundEffects(on: Boolean) {
        soundEffects = on
        settingsPrefs.edit().putBoolean("sound_effects", on).apply()
    }

    fun changeIntroSound(on: Boolean) {
        introSound = on
        settingsPrefs.edit().putBoolean("intro_sound", on).apply()
    }

    /** Songs you asked your computer to create, newest first, with what it answered. */
    var requests by mutableStateOf(emptyList<RequestItem>())
        private set
    val catalog: GearCatalog = personal.gearCatalog()
    var rig by mutableStateOf(GearFiles.parseRig(null, catalog))
        private set
    var hasSlotBackup by mutableStateOf(false)
        private set

    var syncing by mutableStateOf(false)
        private set
    var driveRevision by mutableStateOf(0)       // bumped whenever the Drive status below may have changed
        private set
    val driveConnected: Boolean get() { driveRevision; return drive.connected }
    val driveLastSyncedAt: String? get() { driveRevision; return drive.lastSyncedAt }
    val driveLastResult: String? get() { driveRevision; return drive.lastResult }
    val driveLastError: String? get() { driveRevision; return drive.lastError }

    private var katana: Katana? = null
    private val ampLock = Mutex()
    private var askedUsbPermission = false
    var foreground = false
    /** True while the USB diagnostics screen talks to the amp on its own connection. */
    var diagnostics = false
        set(value) {
            field = value
            if (value) viewModelScope.launch { ampLock.withLock { dropAmp() } }
        }

    init {
        viewModelScope.launch {
            songs = withContext(Dispatchers.IO) { store.seed(); store.list() }
            livesets = withContext(Dispatchers.IO) { livesetStore.load() }
            loadPersonal()
            hasSlotBackup = livesetStore.lastBackup() != null
            loaded = true
            syncNow(silent = true)
        }
        viewModelScope.launch {
            while (isActive) {
                if (foreground && !diagnostics) refreshAmp()
                delay(4_000)
            }
        }
    }

    // ---------------------------------------------------------------- library
    private suspend fun reload() {
        songs = withContext(Dispatchers.IO) { store.list() }
    }

    fun song(id: String): Song? = songs.firstOrNull { it.id == id }

    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        store.favorites = favorites
    }

    /** Adds a Tone Studio .tsl chosen with the file picker or shared/opened from another app. */
    fun importTsl(uri: Uri) {
        viewModelScope.launch {
            while (!loaded) delay(50)                    // let the first load finish so its list cannot overwrite ours
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = app.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment ?: "imported"
                    val bytes = resolver.openInputStream(uri)?.use { SongFiles.readLimited(it) }
                        ?: throw java.io.IOException("Could not open that file")
                    store.importTsl(name, String(bytes, Charsets.UTF_8))
                }
            }
            result.onSuccess { id ->
                reload()
                notice = "Added “${song(id)?.title ?: id}” to your library"
                syncNow(silent = true)
            }.onFailure {
                notice = "Could not import that file: " + (it.message ?: "it is not a Tone Studio liveset")
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            if (id in favorites) toggleFavorite(id)
            reload()
            notice = "Song removed"
            syncNow(silent = true)
        }
    }

    // ---------------------------------------------------------------- practice, tabs, guitars, gear
    private suspend fun loadPersonal() {
        requests = withContext(Dispatchers.IO) { requestStore.items() }
        val all = withContext(Dispatchers.IO) {
            Triple(
                runCatching { SongMetaFiles.parse(personal.read("song_meta.json") ?: "{}") }.getOrDefault(emptyMap()),
                runCatching { TabFiles.parse(personal.read("tabs.json") ?: "{}") }.getOrDefault(TabsConfig()),
                runCatching { GuitarFiles.parse(personal.read("guitars.json") ?: "[]") }.getOrDefault(emptyList()),
            ) to GearFiles.parseRig(personal.read("rig.json"), catalog)
        }
        meta = all.first.first
        tabs = all.first.second
        guitars = all.first.third
        rig = all.second
    }

    private fun persistFile(name: String, text: String) {
        viewModelScope.launch {
            livesetLock.withLock {
                runCatching { withContext(Dispatchers.IO) { personal.write(name, text) } }
                    .onSuccess { syncNow(silent = true) }
                    .onFailure { notice = it.message ?: "Could not save" }
            }
        }
    }

    /** Asks your computer (through Drive) to create a song. It must be running TonenAI to answer. */
    fun requestSong(text: String): Boolean {
        if (!drive.connected) {
            notice = "Connect Google Drive in Settings first: that is how the request reaches your computer."
            return false
        }
        val made = runCatching { requestStore.create(text) }.getOrNull()
        if (made == null) {
            notice = "Write the song as one short line (up to 200 characters). Up to 10 requests can be open at once."
            return false
        }
        requests = requestStore.items()
        syncNow(silent = true)
        return true
    }

    fun removeRequest(id: String) {
        requestStore.remove(id)
        requests = requestStore.items()
        syncNow(silent = true)
    }

    fun metaOf(id: String): SongMeta = meta[id] ?: SongMeta()

    fun setStatus(id: String, status: String) = saveMeta(id, metaOf(id).copy(status = status))

    fun setNotes(id: String, notes: String) = saveMeta(id, metaOf(id).copy(notes = notes.take(SongMetaFiles.MAX_NOTES)))

    private fun saveMeta(id: String, m: SongMeta) {
        if (metaOf(id) == m) return
        meta = if (m.isEmpty) meta - id else meta + (id to m)
        persistFile("song_meta.json", SongMetaFiles.toJson(meta))
    }

    fun saveTabs(cfg: TabsConfig) {
        tabs = cfg
        persistFile("tabs.json", TabFiles.toJson(cfg))
    }

    fun saveGuitar(g: Guitar) {
        guitars = if (guitars.any { it.id == g.id }) guitars.map { if (it.id == g.id) g else it } else guitars + g
        persistFile("guitars.json", GuitarFiles.toJson(guitars))
    }

    fun deleteGuitar(id: String) {
        guitars = guitars.filter { it.id != id }
        persistFile("guitars.json", GuitarFiles.toJson(guitars))
    }

    fun saveRig(r: Rig) {
        rig = r
        persistFile("rig.json", GearFiles.toJson(r))
    }

    /** True when you own a pedal of this kind ("whammy", "trio"); the song page follows it. */
    fun owns(kind: String) = GearFiles.owns(rig, catalog, kind)

    // ---------------------------------------------------------------- livesets (set lists for the amp's A1..B4 slots)
    fun liveset(id: String): Liveset? = livesets.firstOrNull { it.id == id }

    private fun persistLivesets() {
        val snapshot = livesets
        viewModelScope.launch {
            livesetLock.withLock {
                runCatching { withContext(Dispatchers.IO) { livesetStore.save(snapshot) } }
                    .onSuccess { syncNow(silent = true) }
                    .onFailure { notice = it.message ?: "Could not save the livesets" }
            }
        }
    }

    fun saveLiveset(ls: Liveset) {
        val clean = ls.copy(name = ls.name.trim().take(LivesetFiles.MAX_NAME), notes = ls.notes.take(LivesetFiles.MAX_NOTES))
        if (clean.name.isEmpty()) return
        livesets = if (livesets.any { it.id == clean.id }) livesets.map { if (it.id == clean.id) clean else it } else livesets + clean
        persistLivesets()
    }

    fun newLiveset(): String {
        val name = "New liveset ${livesets.size + 1}"
        val ls = Liveset(LivesetFiles.newId(name, System.currentTimeMillis() / 1000), name, emptyMap(), "")
        saveLiveset(ls)
        return ls.id
    }

    fun deleteLiveset(id: String) {
        livesets = livesets.filter { it.id != id }
        persistLivesets()
    }

    /** Reads the names in the amp's memory slots; quiet when the amp is not connected. */
    fun readAmpSlots() {
        viewModelScope.launch {
            if (!amp.connected || sendingLiveset) return@launch
            try {
                ampLock.withLock<Unit> {
                    val names = (katana ?: return@withLock).slotNames()
                    if (names.all { it == null }) throw java.io.IOException("The amp did not answer")
                    ampSlots = names
                }
            } catch (e: Exception) {
                ampLock.withLock { dropAmp() }
            }
        }
    }

    /**
     * Writes a liveset into the amp's slots. What is in those slots now is saved first (as a .tsl on this phone),
     * and the names are read back afterwards to check that the amp took the patches.
     */
    fun sendLiveset(id: String, only: Set<String>? = null) {
        val ls = liveset(id) ?: return
        if (amp.busy || sendingLiveset) return
        viewModelScope.launch {
            if (!amp.connected) refreshAmp(askPermission = true)
            if (!amp.connected) {
                notice = "Connect the Katana to the phone by USB first"
                return@launch
            }
            val todo = LivesetFiles.KEYS.filter { it in ls.slots && (only == null || it in only) }
            if (todo.isEmpty()) {
                notice = "Nothing assigned to send"
                return@launch
            }
            sendingLiveset = true
            amp = amp.copy(busy = true)
            try {
                val prepared = withContext(Dispatchers.IO) {
                    todo.map { key ->
                        val ref = ls.slots.getValue(key)
                        val blocks = store.blocks(ref.song, ref.index)
                        Prepared(key, blocks, Tsl.patchName(blocks))
                    }
                }
                var backedUp = 0
                val bad = ampLock.withLock<List<String>> {
                    val k = katana ?: throw java.io.IOException("The amp was unplugged")
                    k.editorMode()
                    val backup = ArrayList<Pair<String, Map<String, List<Int>>>>()
                    for (p in prepared) {
                        val index = Slots.KEYS.indexOf(p.key)
                        val current = k.slotName(index).orEmpty()
                        if (current.isNotEmpty() && !current.uppercase().contains("INIT TONE")) backup += p.key to k.snapshotSlot(index, p.blocks)
                    }
                    if (backup.isNotEmpty()) {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                        withContext(Dispatchers.IO) { livesetStore.saveBackup(stamp, Tsl.build("Slot backup $stamp", backup)) }
                        backedUp = backup.size
                    }
                    for (p in prepared) k.writeSlot(Slots.KEYS.indexOf(p.key), p.blocks)
                    delay(300)
                    val after = k.slotNames()
                    ampSlots = after
                    prepared.filter { after[Slots.KEYS.indexOf(it.key)].orEmpty() != it.name.take(16).trim() }.map { it.key }
                }
                hasSlotBackup = livesetStore.lastBackup() != null
                notice = if (bad.isEmpty()) "Sent ${prepared.size} patch${if (prepared.size == 1) "" else "es"} to the amp" +
                    (if (backedUp > 0) " · previous slots saved, see Undo last send" else "")
                else "The amp did not take: " + bad.joinToString(", ")
            } catch (e: Exception) {
                ampLock.withLock { dropAmp() }
                notice = "Could not send the liveset: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                amp = amp.copy(busy = false)
                sendingLiveset = false
            }
        }
    }

    /** Puts back what the amp slots held before the last send, then forgets that backup. */
    fun restoreSlotBackup() {
        val file = livesetStore.lastBackup() ?: return
        if (amp.busy || sendingLiveset) return
        viewModelScope.launch {
            if (!amp.connected) refreshAmp(askPermission = true)
            if (!amp.connected) {
                notice = "Connect the Katana to the phone by USB first"
                return@launch
            }
            sendingLiveset = true
            amp = amp.copy(busy = true)
            try {
                val patches = withContext(Dispatchers.IO) { Tsl.parse(file.readText()) }
                ampLock.withLock<Unit> {
                    val k = katana ?: throw java.io.IOException("The amp was unplugged")
                    for (p in patches) {
                        val index = Slots.KEYS.indexOf(p.memo)
                        if (index > 0) k.writeSlot(index, p.blocks)
                    }
                    delay(300)
                    ampSlots = k.slotNames()
                }
                file.delete()
                hasSlotBackup = livesetStore.lastBackup() != null
                notice = "Put back ${patches.size} slot${if (patches.size == 1) "" else "s"} as they were"
            } catch (e: Exception) {
                ampLock.withLock { dropAmp() }
                notice = "Could not restore: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                amp = amp.copy(busy = false)
                sendingLiveset = false
            }
        }
    }

    // ---------------------------------------------------------------- amp (direct USB)
    private fun dropAmp() {
        katana?.close()
        katana = null
        amp = AmpState()
    }

    /** Keeps the connection alive, notices a pulled cable and picks the amp up again when it comes back. */
    suspend fun refreshAmp(askPermission: Boolean = false) = ampLock.withLock<Unit> {
        try {
            val device = UsbRawLink.katana(app)
            if (device == null) {
                if (katana != null) dropAmp()
                askedUsbPermission = false
                return@withLock
            }
            if (katana == null) {
                if (!UsbRawLink.hasPermission(app, device)) {
                    if (askedUsbPermission && !askPermission) return@withLock      // asked once; do not nag
                    askedUsbPermission = true
                    if (!UsbRawLink.requestPermission(app, device)) return@withLock
                }
                katana = UsbRawLink.open(app, device)?.let { Katana(it) } ?: return@withLock
            }
            val name = katana?.patchName()
            if (name == null) {
                dropAmp()
            } else {
                amp = amp.copy(connected = true, patch = name)
            }
        } catch (e: Exception) {
            dropAmp()
        }
    }

    fun connectAmp() {
        viewModelScope.launch {
            refreshAmp(askPermission = true)
            if (!amp.connected) notice = if (UsbRawLink.katana(app) == null)
                "No Katana found. Switch the amp on and plug it into the phone." else "The amp did not answer. Unplug it and plug it in again."
        }
    }

    fun play(song: Song, index: Int) {
        if (amp.busy || sending != null) return
        sending = Playing(song.id, index)
        viewModelScope.launch {
            if (!amp.connected) refreshAmp(askPermission = true)
            if (!amp.connected) {
                notice = "Connect the Katana to the phone by USB first"
                sending = null
                return@launch
            }
            amp = amp.copy(busy = true)
            try {
                val blocks = withContext(Dispatchers.IO) { store.blocks(song.id, index) }
                ampLock.withLock<Unit> {
                    val k = katana ?: throw java.io.IOException("The amp was unplugged")
                    k.applyLive(blocks)
                    amp = AmpState(true, k.patchName() ?: song.patches[index].name)
                }
                playing = Playing(song.id, index)
                notice = "Loaded “${song.patches[index].name}”"
            } catch (e: Exception) {
                ampLock.withLock { dropAmp() }
                notice = "Could not send the patch: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                amp = amp.copy(busy = false)
                sending = null
            }
        }
    }

    // ---------------------------------------------------------------- Google Drive (optional)
    fun driveAuthorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveFiles.SCOPE))).build()

    /** Called by the settings screen once the user granted access (directly or through Google's consent screen). */
    fun onDriveAuthorized() {
        drive.connected = true
        drive.lastError = null
        driveRevision++
        syncNow()
    }

    fun onDriveAuthorizationFailed(message: String) {
        drive.lastError = message
        driveRevision++
    }

    fun disconnectDrive() {
        if (syncing) return
        drive.disconnect()
        driveRevision++
        notice = "Disconnected. Your songs stay on this phone and in Drive."
    }

    /** [silent] is for syncs the user did not ask for: no messages unless the library changed. */
    fun syncNow(silent: Boolean = false) {
        if (syncing || !drive.connected) return
        syncing = true
        Identity.getAuthorizationClient(app).authorize(driveAuthorizationRequest())
            .addOnSuccessListener { result ->
                val token = result.accessToken
                if (result.hasResolution() || token == null) {
                    drive.lastError = "Google needs you to approve access again. Tap Connect."
                    syncing = false
                    driveRevision++
                } else viewModelScope.launch {
                    val counts = runCatching { withContext(Dispatchers.IO) { drive.sync(DriveFiles(token)) } }
                    counts.onSuccess {
                        if (it.changedHere) {
                            reload()
                            livesets = withContext(Dispatchers.IO) { livesetStore.load() }
                            loadPersonal()
                            notice = "Library updated from Google Drive"
                        } else if (!silent) {
                            notice = "Google Drive is up to date"
                        }
                    }.onFailure {
                        if (!silent) {
                            notice = it.message
                        }
                    }
                    syncing = false
                    driveRevision++
                }
            }
            .addOnFailureListener {
                drive.lastError = "Could not reach Google: " + (it.message ?: "unknown error")
                syncing = false
                driveRevision++
                if (!silent) notice = "Could not reach Google"
            }
    }

    override fun onCleared() {
        katana?.close()
    }
}
