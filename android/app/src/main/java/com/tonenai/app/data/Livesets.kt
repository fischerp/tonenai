package com.tonenai.app.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** One amp slot of a liveset: patch number [index] of song [song]. */
data class SlotRef(val song: String, val index: Int)

/** A set list for the amp's eight bank slots (A1..A4, B1..B4). Same file format as the desktop app (data/livesets.json). */
data class Liveset(val id: String, val name: String, val slots: Map<String, SlotRef>, val notes: String) {
    fun assign(key: String, ref: SlotRef?) = copy(slots = if (ref == null) slots - key else slots + (key to ref))
}

object LivesetFiles {
    val KEYS = listOf("A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4")
    const val MAX_NAME = 40
    const val MAX_NOTES = 300
    private val SONG_ID = Regex("[a-z0-9][a-z0-9\\-]*")

    /** "A1" -> "A · CH-1" */
    fun label(key: String) = "${key[0]} · CH-${key[1]}"

    /** Reads livesets.json; entries that do not make sense are skipped, never fatal. */
    fun parse(text: String): List<Liveset> {
        val array = JSONArray(text.trim())
        val out = ArrayList<Liveset>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val name = o.optString("name").trim().take(MAX_NAME)
            if (id.isEmpty() || name.isEmpty()) continue
            val slots = LinkedHashMap<String, SlotRef>()
            val raw = o.optJSONObject("slots")
            for (key in KEYS) {
                val v = raw?.optJSONObject(key) ?: continue
                val song = v.optString("song")
                if (SONG_ID.matches(song)) slots[key] = SlotRef(song, v.optInt("index", 0))
            }
            out += Liveset(id, name, slots, o.optString("notes").take(MAX_NOTES))
        }
        return out
    }

    fun toJson(items: List<Liveset>): String {
        val array = JSONArray()
        for (ls in items) {
            val slots = JSONObject()
            for (key in KEYS) ls.slots[key]?.let { slots.put(key, JSONObject().put("song", it.song).put("index", it.index)) }
            array.put(JSONObject().put("id", ls.id).put("name", ls.name).put("slots", slots).put("notes", ls.notes))
        }
        return array.toString(1)
    }

    /** A new id: the name as a slug plus a few digits of the time, like the desktop app. */
    fun newId(name: String, nowSeconds: Long): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).trim('-').ifEmpty { "liveset" }
        return "$slug-${nowSeconds % 100000}"
    }
}

/** Livesets and the safety copies of amp slots, kept in this app's private storage. */
class LivesetStore(context: Context) {
    /** The file the Drive sync mirrors; same name and format as the desktop app's data/livesets.json. */
    val file = File(context.filesDir, "livesets.json")
    private val backups = File(context.filesDir, "slot-backups").apply { mkdirs() }

    fun load(): List<Liveset> = runCatching { LivesetFiles.parse(file.readText()) }.getOrDefault(emptyList())

    fun save(items: List<Liveset>) {
        val temp = File(file.parentFile, "livesets.json.part")
        temp.writeText(LivesetFiles.toJson(items))
        check(temp.renameTo(file)) { "Could not save the livesets" }
    }

    /** What the amp slots held before a send, as a Tone Studio .tsl (the memo of each patch is its slot, e.g. "A1"). */
    fun saveBackup(stamp: String, tsl: String) {
        File(backups, "slots-$stamp.tsl").writeText(tsl)
        backups.listFiles().orEmpty().filter { it.name.startsWith("slots-") }.sortedByDescending { it.name }.drop(20).forEach { it.delete() }
    }

    fun lastBackup(): File? = backups.listFiles().orEmpty().filter { it.name.startsWith("slots-") }.maxByOrNull { it.name }
}
