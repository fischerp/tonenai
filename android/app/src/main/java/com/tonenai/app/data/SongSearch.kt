package com.tonenai.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** One song found by a search. [request] is the exact line sent to the computer, so it knows which song is meant. */
data class SongHit(val artist: String, val title: String, val album: String) {
    val request: String get() = "$artist - $title"
}

/** Finds songs by name with Apple's public iTunes Search API (the same service the covers come from). Blocking: call it off the main thread. */
object SongSearch {
    private val NOISE = Regex("\\s*[(\\[][^)\\]]*(remaster|live|mono|stereo|deluxe|version|edit|karaoke)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)

    fun clean(title: String): String = NOISE.replace(title, "").trim().ifEmpty { title }

    fun parse(json: String, max: Int = 12): List<SongHit> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val seen = HashSet<String>()
        val hits = ArrayList<SongHit>()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val title = clean(r.optString("trackName"))
            val artist = r.optString("artistName").trim()
            if (title.isEmpty() || artist.isEmpty() || artist.length > 80 || title.length > 100) continue
            if (!seen.add(artist.lowercase() + "|" + title.lowercase())) continue
            hits += SongHit(artist, title, r.optString("collectionName").trim())
            if (hits.size >= max) break
        }
        return hits
    }

    fun search(term: String): List<SongHit> {
        val q = URLEncoder.encode(term.trim().take(100), "UTF-8")
        val c = URL("https://itunes.apple.com/search?term=$q&entity=song&media=music&limit=25").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) throw java.io.IOException("Search failed (${c.responseCode})")
            val body = c.inputStream.use { it.readBytes() }
            if (body.size > 2_000_000) throw java.io.IOException("Search answer too large")
            return parse(String(body))
        } finally {
            c.disconnect()
        }
    }
}
