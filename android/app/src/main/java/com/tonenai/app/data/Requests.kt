package com.tonenai.app.data

import java.io.File
import java.security.SecureRandom
import org.json.JSONObject

/**
 * Song requests for your computer, carried by Google Drive. The phone writes req-<id>.json (the words to look up); the computer
 * makes the song with your Claude sign-in and writes res-<id>.json, which this app shows. Each file has one writer, so the two
 * never overwrite each other. Same rules as app/phone_requests.py.
 */
object RequestFiles {
    val NAME = Regex("(req|res)-([0-9a-f]{12})\\.json")
    const val MAX_TEXT = 200
    const val MAX_BYTES = 4096L
    const val MAX_OPEN = 10                                   // requests the phone keeps at once
    val STATUSES = setOf("awaiting-approval", "working", "done", "failed", "declined")
    private val SONG_ID = Regex("[a-z0-9][a-z0-9\\-]{0,99}")

    /** One line of plain text, or null when it cannot be sent. */
    fun cleanText(text: String): String? {
        val s = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        return s.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT && it.none { c -> c.code < 32 || c.code == 127 } }
    }

    fun newId(): String = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun requestJson(id: String, text: String, created: Long): String =
        JSONObject().put("id", id).put("text", text).put("created", created).toString(1)

    /** A file from Drive is only stored when it looks exactly like a request or a result. */
    fun valid(name: String, text: String): Boolean = runCatching {
        val m = NAME.matchEntire(name) ?: return false
        val o = JSONObject(text)
        if (o.optString("id") != m.groupValues[2]) return false
        if (m.groupValues[1] == "req") {
            cleanText(o.optString("text")) == o.optString("text") && o.has("created") && o.optLong("created", -1) > 0
        } else {
            o.optString("status") in STATUSES && o.optString("note").length <= 200 && o.has("updated") &&
                (o.optString("song").isEmpty() || SONG_ID.matches(o.optString("song")))
        }
    }.getOrDefault(false)
}

/** A request with its result (if the computer has answered). [status] is "new" until it has. */
data class RequestItem(val id: String, val text: String, val created: Long, val status: String, val note: String, val song: String)

class RequestStore(val dir: File) {
    fun items(): List<RequestItem> {
        val out = ArrayList<RequestItem>()
        for (f in dir.listFiles().orEmpty()) {
            val m = RequestFiles.NAME.matchEntire(f.name) ?: continue
            if (m.groupValues[1] != "req" || f.length() > RequestFiles.MAX_BYTES) continue
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            if (!RequestFiles.valid(f.name, text)) continue
            val req = JSONObject(text)
            val res = File(dir, "res-${m.groupValues[2]}.json").takeIf { it.isFile && it.length() <= RequestFiles.MAX_BYTES }
                ?.let { r -> runCatching { r.readText() }.getOrNull()?.takeIf { RequestFiles.valid(r.name, it) }?.let(::JSONObject) }
            out += RequestItem(req.getString("id"), req.getString("text"), req.getLong("created"), res?.optString("status") ?: "new",
                res?.optString("note").orEmpty(), res?.optString("song").orEmpty())
        }
        return out.sortedByDescending { it.created }
    }

    /** Writes a new request; null when the text cannot be sent or too many are open. */
    fun create(text: String, now: Long = System.currentTimeMillis()): RequestItem? {
        val clean = RequestFiles.cleanText(text) ?: return null
        val open = items().count { it.status in setOf("new", "awaiting-approval", "working") }
        if (open >= RequestFiles.MAX_OPEN) return null
        dir.mkdirs()
        val id = RequestFiles.newId()
        val target = File(dir, "req-$id.json")
        val temp = File(dir, "req-$id.json.part")
        temp.writeText(RequestFiles.requestJson(id, clean, now))
        check(temp.renameTo(target)) { "Could not save the request" }
        return RequestItem(id, clean, now, "new", "", "")
    }

    /** Takes it off this phone; the next sync removes it from Drive and the computer. */
    fun remove(id: String) {
        File(dir, "req-$id.json").delete()
        File(dir, "res-$id.json").delete()
    }
}
