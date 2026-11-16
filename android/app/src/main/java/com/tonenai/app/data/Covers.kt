package com.tonenai.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Album art, found once through Apple's public iTunes Search API and cached in the app's private storage
 * (same source and rules as the desktop app). Covers are never packed into the app or synced.
 */
object Covers {
    private val memory = LruCache<String, ImageBitmap>(80)
    private val network = Mutex()                         // one lookup at a time: be gentle with the public API

    private fun dir(context: Context) = File(context.filesDir, "covers").apply { mkdirs() }

    fun cached(id: String): ImageBitmap? = memory.get(id)

    suspend fun load(context: Context, song: Song): ImageBitmap? {
        memory.get(song.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(dir(context), song.id + ".jpg")
            val miss = File(dir(context), song.id + ".none")
            if (!file.exists() && !miss.exists() && song.artist.isNotEmpty()) network.withLock {
                if (!file.exists()) runCatching { fetch(song, file, miss) }      // offline: try again next launch
            }
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(file.path) ?: return@withContext null
            bitmap.asImageBitmap().also { memory.put(song.id, it) }
        }
    }

    private fun get(url: String, limit: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > limit) throw java.io.IOException("too large")
            return bytes
        } finally {
            c.disconnect()
        }
    }

    private fun fetch(song: Song, file: File, miss: File) {
        val title = song.title.replace(Regex("\\s*\\(.*?\\)"), "").trim()
        val term = URLEncoder.encode("${song.artist} $title", "UTF-8")
        val results = JSONObject(String(get("https://itunes.apple.com/search?term=$term&entity=song&limit=8", 1_000_000)))
            .getJSONArray("results")
        val want = song.artist.lowercase().take(6)
        var art: String? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("artistName").lowercase().startsWith(want)) {
                art = r.optString("artworkUrl100").replace("100x100bb", "600x600bb")
                break
            }
        }
        val host = art?.let { runCatching { URL(it) }.getOrNull() }
        if (host == null || host.protocol != "https" || !host.host.endsWith(".mzstatic.com")) {   // only Apple's image servers
            miss.createNewFile()
            return
        }
        val data = get(art!!, 5_000_000)
        if (data.size < 2 || data[0] != 0xFF.toByte() || data[1] != 0xD8.toByte()) {             // not a JPEG
            miss.createNewFile()
            return
        }
        val temp = File(file.path + ".part")
        temp.writeBytes(data)
        temp.renameTo(file)
    }
}
