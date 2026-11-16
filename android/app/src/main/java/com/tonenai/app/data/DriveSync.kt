package com.tonenai.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** A problem the user can read and act on. */
class SyncException(message: String) : Exception(message)

/** Your own lists and notes: one Drive file each, same names as the desktop app's data/ folder, never deleted by a sync. */
object DataFiles {
    /** name -> true when the file holds a JSON list of objects, false when it holds one JSON object. */
    val ALL = mapOf("livesets.json" to true, "guitars.json" to true, "song_meta.json" to false, "tabs.json" to false, "rig.json" to false)

    fun valid(name: String, text: String): Boolean = runCatching {
        if (ALL.getValue(name)) {
            val a = org.json.JSONArray(text)
            (0 until a.length()).all { a.optJSONObject(it) != null }
        } else {
            JSONObject(text)
            true
        }
    }.getOrDefault(false)
}

/** The same decision table as the desktop app (app/drive_sync.py, plan). */
object SyncPlan {
    const val UPLOAD = "upload"
    const val DOWNLOAD = "download"
    const val DELETE_LOCAL = "delete_local"
    const val DELETE_REMOTE = "delete_remote"
    const val CONFLICT = "conflict"

    /** Maps are file name -> md5; [base] is what both sides had at the last sync. Matching files are left out. */
    fun plan(local: Map<String, String>, remote: Map<String, String>, base: Map<String, String>): Map<String, String> {
        // A side that lost everything at once is treated as new, not as "the user deleted every song".
        val was = if (base.size >= 3 && (remote.isEmpty() || local.isEmpty())) emptyMap() else base
        val actions = sortedMapOf<String, String>()
        for (name in local.keys + remote.keys) {
            val here = local[name]
            val there = remote[name]
            if (here == there) continue
            actions[name] = when {
                there == null -> if (here == was[name]) DELETE_LOCAL else UPLOAD
                here == null -> if (there == was[name]) DELETE_REMOTE else DOWNLOAD
                here == was[name] -> DOWNLOAD
                there == was[name] -> UPLOAD
                else -> CONFLICT
            }
        }
        return actions
    }
}

/** The hidden appDataFolder of the user's Drive, through the REST API. */
class DriveFiles(private val accessToken: String) {
    class Remote(val id: String, val md5: String, val modified: Long)

    private fun open(method: String, url: String, contentType: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (contentType != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }

    private fun HttpURLConnection.body(action: String): ByteArray {
        val code = responseCode
        val bytes = (if (code >= 400) errorStream else inputStream)?.use { it.readBytes() } ?: ByteArray(0)
        disconnect()
        if (code == 401) throw SyncException("Google access expired. Tap Connect to sign in again.")
        if (code >= 400) throw SyncException("$action failed ($code).")
        return bytes
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** name -> file. When a name exists twice the newest wins and the others are removed. */
    fun list(): Map<String, Remote> {
        val found = LinkedHashMap<String, Remote>()
        var page: String? = null
        do {
            val url = "$FILES?spaces=appDataFolder&pageSize=1000&orderBy=${enc("modifiedTime desc")}&q=${enc("trashed = false")}" +
                "&fields=${enc("nextPageToken,files(id,name,md5Checksum,modifiedTime,size)")}" +
                (page?.let { "&pageToken=${enc(it)}" } ?: "")
            val o = JSONObject(String(open("GET", url).body("Reading Google Drive")))
            val files = o.optJSONArray("files")
            for (i in 0 until (files?.length() ?: 0)) {
                val f = files!!.getJSONObject(i)
                val name = f.optString("name")
                if (!(name in DataFiles.ALL || SongFiles.NAME.matches(name)) || f.optString("size").toLongOrNull().let { it == null || it > MAX_BYTES }) continue
                if (RequestFiles.NAME.matches(name) && f.optString("size").toLong() > RequestFiles.MAX_BYTES) continue
                if (name in found) delete(f.getString("id"))
                else found[name] = Remote(f.getString("id"), f.optString("md5Checksum"),
                    runCatching { Instant.parse(f.optString("modifiedTime")).toEpochMilli() }.getOrDefault(0L))
            }
            page = o.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        return found
    }

    fun download(id: String): ByteArray = open("GET", "$FILES/$id?alt=media").body("Downloading a song").also {
        if (it.size > MAX_BYTES) throw SyncException("A file in Google Drive is too large to be a song, so it was not used.")
    }

    fun delete(id: String) {
        val c = open("DELETE", "$FILES/$id")
        val code = c.responseCode
        c.disconnect()
        if (code == 401) throw SyncException("Google access expired. Tap Connect to sign in again.")
        if (code >= 400 && code != 404) throw SyncException("Deleting a song in Google Drive failed ($code).")
    }

    fun upload(name: String, content: ByteArray, existingId: String?) {
        if (existingId != null) {
            // HttpURLConnection has no PATCH; Google APIs accept POST with a method-override header.
            val c = open("POST", "$UPLOAD/$existingId?uploadType=media&fields=id", "application/json")
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.outputStream.use { it.write(content) }
            c.body("Uploading to Google Drive")
            return
        }
        val boundary = "tonenai-" + ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val metadata = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("mimeType", "application/json").toString()
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n"
        val c = open("POST", "$UPLOAD?uploadType=multipart&fields=id", "multipart/related; boundary=$boundary")
        c.outputStream.use {
            it.write(head.toByteArray())
            it.write(content)
            it.write("\r\n--$boundary--\r\n".toByteArray())
        }
        c.body("Uploading to Google Drive")
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val MAX_BYTES = 2L * 1024 * 1024
        private const val FILES = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    }
}

class SyncCounts(var uploaded: Int = 0, var downloaded: Int = 0, var deletedHere: Int = 0, var deletedInDrive: Int = 0, var conflicts: Int = 0) {
    val changedHere get() = downloaded + deletedHere > 0
    override fun toString() = "$uploaded sent, $downloaded received, ${deletedHere + deletedInDrive} removed" +
        if (conflicts > 0) ", $conflicts changed on both sides (older copy kept)" else ""
}

/**
 * Mirrors the song folder and your data files ([DataFiles]) with Drive. Same rules as the desktop app, so the two never fight.
 * The data files are never deleted by a sync: a copy that went missing on one side is restored from the other.
 */
class DriveSync(context: Context, private val songsDir: File, private val dataDir: File) {
    private val prefs = context.getSharedPreferences("drive", Context.MODE_PRIVATE)
    private val keepDir = File(context.filesDir, "sync-kept")     // older copies of conflicts, songs removed by a sync

    var connected: Boolean
        get() = prefs.getBoolean("connected", false)
        set(value) = prefs.edit().putBoolean("connected", value).apply()
    var lastSyncedAt: String?
        get() = prefs.getString("last_synced_at", null)
        private set(value) = prefs.edit().putString("last_synced_at", value).apply()
    var lastResult: String?
        get() = prefs.getString("last_result", null)
        private set(value) = prefs.edit().putString("last_result", value).apply()
    var lastError: String?
        get() = prefs.getString("last_error", null)
        set(value) = prefs.edit().putString("last_error", value).apply()

    private var base: Map<String, String>
        get() = runCatching {
            val o = JSONObject(prefs.getString("files", "{}")!!)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(value) = prefs.edit().putString("files", JSONObject(value).toString()).apply()

    fun disconnect() = prefs.edit().clear().apply()

    private val requestsDir get() = File(dataDir, "requests")

    private fun fileFor(name: String) = when {
        RequestFiles.NAME.matches(name) -> File(requestsDir, name)       // song requests for the computer, and their results
        name in DataFiles.ALL -> File(dataDir, name)
        else -> File(songsDir, name)
    }

    private fun local(): Map<String, String> {
        val files = songsDir.listFiles().orEmpty()
            .filter { it.isFile && it.name !in DataFiles.ALL && !RequestFiles.NAME.matches(it.name) && SongFiles.NAME.matches(it.name) && it.length() <= DriveFiles.MAX_BYTES }
            .associate { it.name to md5(it.readBytes()) }
            .toMutableMap()
        for (f in requestsDir.listFiles().orEmpty()) {
            if (f.isFile && RequestFiles.NAME.matches(f.name) && f.length() <= RequestFiles.MAX_BYTES) files[f.name] = md5(f.readBytes())
        }
        for (name in DataFiles.ALL.keys) {
            val f = File(dataDir, name)
            if (f.isFile && f.length() <= DriveFiles.MAX_BYTES) files[name] = md5(f.readBytes())
        }
        return files
    }

    private fun keep(name: String, content: ByteArray) {
        keepDir.mkdirs()
        File(keepDir, "${System.currentTimeMillis()}-$name").writeBytes(content)
    }

    private fun write(name: String, content: ByteArray) {
        if (RequestFiles.NAME.matches(name)) {
            if (!RequestFiles.valid(name, String(content))) throw SyncException("$name in Google Drive is not a valid song request, so it was ignored.")
        } else if (name in DataFiles.ALL) {
            if (!DataFiles.valid(name, String(content))) throw SyncException("$name in Google Drive is not in the expected format, so nothing was changed.")
        } else {
            runCatching { JSONObject(String(content)) }.recoverCatching { org.json.JSONArray(String(content)) }
                .getOrElse { throw SyncException("$name in Google Drive is not a song file, so nothing was changed.") }
        }
        val target = fileFor(name)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$name.part")
        temp.writeBytes(content)
        if (!temp.renameTo(target)) throw SyncException("Could not save $name.")
    }

    /** Runs on a background thread. */
    fun sync(drive: DriveFiles): SyncCounts {
        try {
            val remoteFiles = drive.list()
            val counts = SyncCounts()
            val actions = SyncPlan.plan(local(), remoteFiles.mapValues { it.value.md5 }, base)
            for ((name, planned) in actions) {
                val file = fileFor(name)
                val remote = remoteFiles[name]
                var action = planned
                if (name in DataFiles.ALL) {               // never deleted by a sync: a missing copy is restored
                    if (action == SyncPlan.DELETE_LOCAL) action = SyncPlan.UPLOAD
                    else if (action == SyncPlan.DELETE_REMOTE) action = SyncPlan.DOWNLOAD
                }
                if (action == SyncPlan.CONFLICT) {
                    counts.conflicts++
                    if (remote!!.modified >= file.lastModified()) {
                        keep(name, file.readBytes())
                        action = SyncPlan.DOWNLOAD
                    } else {
                        keep(name, drive.download(remote.id))
                        action = SyncPlan.UPLOAD
                    }
                }
                when (action) {
                    SyncPlan.UPLOAD -> { drive.upload(name, file.readBytes(), remote?.id); counts.uploaded++ }
                    SyncPlan.DOWNLOAD -> {
                        try {
                            write(name, drive.download(remote!!.id))
                        } catch (e: SyncException) {
                            if (!RequestFiles.NAME.matches(name)) throw e
                            continue                                    // a malformed request file is skipped: it must never block the sync
                        }
                        counts.downloaded++
                    }
                    SyncPlan.DELETE_LOCAL -> {
                        if (!RequestFiles.NAME.matches(name)) keep(name, file.readBytes())   // a finished request is not worth keeping
                        file.delete()
                        counts.deletedHere++
                    }
                    SyncPlan.DELETE_REMOTE -> { drive.delete(remote!!.id); counts.deletedInDrive++ }
                }
            }
            base = local()
            lastSyncedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
            lastResult = counts.toString()
            lastError = null
            return counts
        } catch (e: SyncException) {
            lastError = e.message
            throw e
        } catch (e: Exception) {
            lastError = "Sync failed: " + (e.message ?: e.javaClass.simpleName)
            throw SyncException(lastError!!)
        }
    }

    companion object {
        fun md5(data: ByteArray): String = MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
    }
}
