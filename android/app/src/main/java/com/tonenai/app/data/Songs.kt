package com.tonenai.app.data

import android.content.Context
import com.tonenai.app.katana.Tsl
import java.io.File
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject

class Patch(val index: Int, val name: String, val amp: String, val note: String, val effects: List<String>)

class Song(
    val id: String,
    val artist: String,
    val title: String,
    val patches: List<Patch>,
    val band: JSONObject?,
    val whammy: JSONObject?,
    val guitar: JSONObject?,
    val added: Long,
)

/** Reads the same song files as the desktop app: <id>.json (the spec and notes) and <id>.tsl (the patch bytes). */
object SongFiles {
    val NAME = Regex("[a-z0-9][a-z0-9\\-]*\\.(json|tsl)")

    /** "Artist - Title: what this patch is" -> (artist, title). */
    fun splitTitle(memo: String, fallback: String): Pair<String, String> {
        val head = memo.substringBefore(":")
        val cut = head.indexOf(" - ")
        return if (cut > 0) head.substring(0, cut).trim() to head.substring(cut + 3).trim() else "" to fallback
    }

    fun fromJson(id: String, text: String, added: Long): Song {
        val trimmed = text.trim()
        val specs = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray().put(JSONObject(trimmed))
        val first = specs.getJSONObject(0)
        val (artist, title) = splitTitle(first.optString("memo"), id)
        val patches = (0 until specs.length()).map { i ->
            val s = specs.getJSONObject(i)
            val effects = ArrayList<String>()
            for ((key, label) in listOf("booster" to "", "mod" to "", "fx" to "", "delay" to " delay", "reverb" to " reverb")) {
                val e = s.optJSONObject(key) ?: continue
                if (e.optBoolean("on", true)) effects += e.optString("type").ifEmpty { key } + label
            }
            Patch(i, s.optString("name", "Patch ${i + 1}"), s.optJSONObject("amp")?.optString("type").orEmpty(),
                s.optString("memo").substringAfter(":", "").trim(), effects)
        }
        return Song(id, artist, title, patches, first.optJSONObject("band"), first.optJSONObject("whammy"),
            first.optJSONObject("guitar"), added)
    }

    fun fromTsl(id: String, text: String, added: Long): Song {
        val parsed = Tsl.parse(text)
        val (artist, title) = splitTitle(parsed.firstOrNull()?.memo.orEmpty(), id)
        val patches = parsed.mapIndexed { i, p ->
            Patch(i, p.name, "", p.memo.substringAfter(":", "").trim().ifEmpty { "From a .tsl file" }, emptyList())
        }
        return Song(id, artist.ifEmpty { "Imported" }, title, patches, null, null, null, added)
    }

    /** A song id (lowercase slug) made from a file name, the same way the desktop app does it. */
    fun idFor(fileName: String): String {
        val base = fileName.substringAfterLast('/').removeSuffix(".tsl").removeSuffix(".TSL")
        return base.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(80).trim('-').ifEmpty { "imported" }
    }

    const val MAX_IMPORT_BYTES = 2_000_000

    /** Reads at most [max] bytes; a bigger stream is refused instead of filling the phone's memory. */
    fun readLimited(input: InputStream, max: Int = MAX_IMPORT_BYTES): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            require(out.size() <= max) { "That file is too big to be a Tone Studio liveset" }
        }
        return out.toByteArray()
    }
}

/** The library on this phone: app-private files, seeded once from the starter songs packed in the APK. */
class SongStore(private val context: Context) {
    val dir = File(context.filesDir, "songs").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    /** Copies starter songs this install has not seen yet. A starter song you deleted does not come back. */
    fun seed() {
        val seen = prefs.getStringSet("seeded", emptySet()).orEmpty().toMutableSet()
        val names = context.assets.list("songs").orEmpty().filter { SongFiles.NAME.matches(it) && it !in seen }
        for (name in names) {
            val target = File(dir, name)
            if (!target.exists()) context.assets.open("songs/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            seen += name
        }
        if (names.isNotEmpty()) prefs.edit().putStringSet("seeded", seen).apply()
    }

    fun list(): List<Song> {
        val files = dir.listFiles().orEmpty().filter { SongFiles.NAME.matches(it.name) }
        val jsonIds = files.filter { it.extension == "json" }.map { it.nameWithoutExtension }.toSet()
        val songs = files.mapNotNull { f ->
            runCatching {
                when {
                    f.extension == "json" -> SongFiles.fromJson(f.nameWithoutExtension, f.readText(), f.lastModified())
                    f.nameWithoutExtension !in jsonIds -> SongFiles.fromTsl(f.nameWithoutExtension, f.readText(), f.lastModified())
                    else -> null
                }
            }.getOrNull()
        }
        return songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    }

    /** The bytes to send to the amp for one patch of a song. */
    fun blocks(id: String, index: Int): Map<String, List<Int>> {
        val tsl = File(dir, "$id.tsl")
        require(tsl.exists()) { "This song has no .tsl file yet. Open it once in the desktop app and sync again." }
        return Tsl.parse(tsl.readText())[index].blocks
    }

    /** Adds a Tone Studio .tsl to the library and returns its song id. Never overwrites a song you already have. */
    fun importTsl(fileName: String, text: String): String {
        require(Tsl.parse(text).isNotEmpty()) { "That liveset has no patches" }
        val base = SongFiles.idFor(fileName)
        var id = base
        var n = 2
        while (File(dir, "$id.tsl").exists() || File(dir, "$id.json").exists()) id = "$base-${n++}"
        File(dir, "$id.tsl").writeText(text)
        return id
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        File(dir, "$id.tsl").delete()
    }

    var favorites: Set<String>
        get() = prefs.getStringSet("favorites", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("favorites", value).apply()
}
