package com.tonenai.app.data

import android.content.Context
import java.io.File
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** Your own notes, tab links, guitars and gear: the same files as the desktop app's data/ folder, synced through Drive. */

// ------------------------------------------------------------------ practice: status + notes (song_meta.json)
data class SongMeta(val status: String = "", val notes: String = "") {
    val isEmpty get() = status.isEmpty() && notes.isEmpty()
}

object SongMetaFiles {
    val STATUSES = listOf("", "learning", "played")
    const val MAX_NOTES = 2000
    private val ID = Regex("[a-z0-9][a-z0-9\\-]*")

    fun parse(text: String): Map<String, SongMeta> {
        val root = JSONObject(text)
        val out = LinkedHashMap<String, SongMeta>()
        for (id in root.keys()) {
            if (!ID.matches(id)) continue
            val v = root.optJSONObject(id) ?: continue
            val status = v.optString("status")
            val meta = SongMeta(if (status in STATUSES) status else "", v.optString("notes").take(MAX_NOTES))
            if (!meta.isEmpty) out[id] = meta
        }
        return out
    }

    fun toJson(map: Map<String, SongMeta>): String {
        val root = JSONObject()
        for ((id, m) in map) {
            if (m.isEmpty) continue
            val e = JSONObject()
            if (m.status.isNotEmpty()) e.put("status", m.status)
            if (m.notes.isNotEmpty()) e.put("notes", m.notes)
            root.put(id, e)
        }
        return root.toString(1)
    }
}

// ------------------------------------------------------------------ guitar tabs (tabs.json): the site you use, never downloaded
data class TabsConfig(val service: String = "", val template: String = "", val links: Map<String, String> = emptyMap())

object TabFiles {
    /** key -> (label, search URL with {q}). "custom" uses your own template. */
    val SERVICES = linkedMapOf(
        "ultimate-guitar" to ("Ultimate Guitar" to "https://www.ultimate-guitar.com/search.php?search_type=title&value={q}"),
        "songsterr" to ("Songsterr" to "https://www.songsterr.com/?pattern={q}"),
        "google" to ("Google (tabs)" to "https://www.google.com/search?q={q}+guitar+tab"),
        "custom" to ("Another site…" to ""),
    )
    private val HTTP = Regex("https?://\\S+")
    private val ID = Regex("[a-z0-9][a-z0-9\\-]*")

    fun parse(text: String): TabsConfig {
        val o = JSONObject(text)
        val links = LinkedHashMap<String, String>()
        o.optJSONObject("links")?.let { l ->
            for (id in l.keys()) {
                val url = l.optString(id)
                if (ID.matches(id) && HTTP.matches(url)) links[id] = url
            }
        }
        return TabsConfig(o.optString("service"), o.optString("template").take(300), links)
    }

    fun toJson(c: TabsConfig): String {
        val links = JSONObject()
        for ((id, url) in c.links) links.put(id, url)
        return JSONObject().put("service", c.service).put("template", c.template).put("links", links).toString(1)
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** The search words for a song: "Artist Title", dropping "(live)", "(remastered)"-style notes from the title. */
    fun query(artist: String, title: String): String {
        val cleaned = title.replace(Regex("\\s*\\(.*?\\)")) { m ->
            if (Regex("live|remaster|acoustic|version", RegexOption.IGNORE_CASE).containsMatchIn(m.value)) "" else m.value
        }
        return encode((if (artist.isNotEmpty()) "$artist " else "") + cleaned)
    }

    /** Where "Tabs" goes for a song: your exact link, else your site's search; null until a site is chosen. */
    fun url(c: TabsConfig, id: String, artist: String, title: String): String? {
        c.links[id]?.let { return it }
        val template = if (c.service == "custom") c.template else SERVICES[c.service]?.second.orEmpty()
        return if (template.contains("{q}")) template.replace("{q}", query(artist, title)) else null
    }

    fun lessonsUrl(artist: String, title: String) = "https://www.youtube.com/results?search_query=${query(artist, title)}+guitar+lesson"
}

// ------------------------------------------------------------------ my guitars (guitars.json)
data class Guitar(
    val id: String, val name: String, val brand: String = "", val kind: String = "", val pickups: String = "humbucker",
    val pickupsOther: String = "", val finish: String = "", val notes: String = "",
)

/** A finish with the two colours used to draw its swatch (ARGB). */
class Finish(val key: String, val label: String, val top: Long, val bottom: Long)

object GuitarFiles {
    val PICKUPS = listOf("humbucker", "single-coil", "p90", "sss", "hh", "hs", "hss", "hsh", "other", "acoustic")
    val KINDS = listOf(
        "Stratocaster", "Telecaster", "Les Paul", "SG", "ES-335 / semi-hollow", "Jazzmaster / Jaguar", "Explorer",
        "PRS / Superstrat", "P-90 guitar (Junior, Special…)", "Dreadnought", "Concert / OM", "Classical", "Other",
    )
    val FINISHES = listOf(
        Finish("sunburst", "Sunburst", 0xFFF3C247, 0xFF3B0C05), Finish("cherryburst", "Cherry sunburst", 0xFFE8892A, 0xFF2D0504),
        Finish("honey", "Honey burst", 0xFFF1B94A, 0xFF8A4A12), Finish("butter", "Butterscotch", 0xFFF0CF7A, 0xFFC79A3B),
        Finish("tv", "TV yellow", 0xFFF2E3A2, 0xFFCDB15A), Finish("natural", "Natural", 0xFFE9C98B, 0xFFB9854A),
        Finish("cherry", "Cherry", 0xFFC32A24, 0xFF6A0D0B), Finish("red", "Red", 0xFFD23A2C, 0xFF7A1710),
        Finish("black", "Black", 0xFF2A2A2E, 0xFF08080A), Finish("white", "White", 0xFFFAF8F2, 0xFFCFCAB8),
        Finish("blue", "Blue", 0xFF4F86E0, 0xFF1B3A86), Finish("teal", "Seafoam", 0xFF83CBC0, 0xFF2F7F86),
        Finish("green", "Green burst", 0xFF41A77F, 0xFF06251B), Finish("grey", "Grey", 0xFF9A9AA8, 0xFF4A4A56),
    )
    private val BY_KEY = FINISHES.associateBy { it.key }
    fun finish(key: String): Finish? = BY_KEY[key]

    fun parse(text: String): List<Guitar> {
        val a = JSONArray(text)
        val out = ArrayList<Guitar>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val name = o.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) continue
            val pickups = o.optString("pickups").let { if (it in PICKUPS) it else "other" }
            out += Guitar(id, name.take(60), o.optString("brand").trim().take(40), o.optString("kind").take(40), pickups,
                o.optString("pickups_other").trim().take(40), o.optString("finish").lowercase().filter { it in 'a'..'z' }.take(20),
                o.optString("notes").take(200))
        }
        return out
    }

    fun toJson(items: List<Guitar>): String {
        val a = JSONArray()
        for (g in items) a.put(
            JSONObject().put("id", g.id).put("name", g.name).put("brand", g.brand).put("kind", g.kind).put("pickups", g.pickups)
                .put("pickups_other", if (g.pickups == "other") g.pickupsOther else "").put("finish", g.finish).put("notes", g.notes),
        )
        return a.toString(1)
    }

    /** The id the desktop app would make from a name; a number is added when the id is taken. */
    fun newId(name: String, taken: Set<String>): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "guitar" }
        var id = base
        var n = 2
        while (id in taken) id = "$base-${n++}"
        return id
    }
}

// ------------------------------------------------------------------ my gear: amp and pedals (gear/catalog.json + rig.json)
class Amp(val id: String, val name: String, val tested: Boolean, val about: String)

/** [kind] is "whammy", "trio" or "generic"; the song page follows it. [custom] pedals are yours. */
class Pedal(val id: String, val name: String, val short: String, val icon: String, val kind: String, val about: String, val custom: Boolean)

class GearCatalog(val amps: List<Amp>, val pedals: List<Pedal>, val defaults: List<String>)

/** What you own. [customRaw] keeps your pedals exactly as stored, including a researched profile made on the computer. */
class Rig(val amp: String, val pedals: List<String>, val customRaw: List<JSONObject>) {
    val custom: List<Pedal> get() = customRaw.map { GearFiles.customPedal(it) }
}

object GearFiles {
    fun catalog(text: String): GearCatalog {
        val root = JSONObject(text)
        val amps = ArrayList<Amp>()
        root.optJSONArray("amps")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { o ->
                if (o.optString("id").isNotEmpty()) amps += Amp(o.getString("id"), o.optString("name"), o.optBoolean("tested"), o.optString("about"))
            }
        }
        val pedals = ArrayList<Pedal>()
        val defaults = ArrayList<String>()
        root.optJSONArray("pedals")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { o ->
                val id = o.optString("id")
                if (id.isEmpty()) return@let
                pedals += Pedal(id, o.optString("name"), o.optString("short"), o.optString("icon"), o.optString("kind", "generic"), o.optString("about"), false)
                if (o.optBoolean("default")) defaults += id
            }
        }
        return GearCatalog(amps, pedals, defaults)
    }

    fun customPedal(o: JSONObject) = Pedal(o.optString("id"), o.optString("name"), o.optString("name").take(10).uppercase(), "🎛", "generic", o.optString("about"), true)

    fun newCustomId(name: String, taken: Set<String>): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(22).trim('-').ifEmpty { "pedal" }
        var id = "custom-$slug"
        var n = 2
        while (id in taken) id = "custom-$slug-${n++}"
        return id
    }

    /** rig.json, checked against the catalog like the desktop app does; [text] null means never saved (the catalog's defaults). */
    fun parseRig(text: String?, cat: GearCatalog): Rig {
        val root = runCatching { JSONObject(text ?: "{}") }.getOrDefault(JSONObject())
        val custom = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        root.optJSONArray("custom")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (o.optString("name").isBlank() || !Regex("custom-[a-z0-9-]{1,30}").matches(id) || !seen.add(id)) continue
                custom += o
            }
        }
        val known = cat.pedals.map { it.id } + custom.map { it.getString("id") }
        val pedals = root.optJSONArray("pedals")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in known } } ?: cat.defaults
        val amps = cat.amps.map { it.id }
        val amp = root.optString("amp").takeIf { it in amps } ?: amps.firstOrNull().orEmpty()
        return Rig(amp, pedals, custom)
    }

    fun toJson(rig: Rig): String {
        val custom = JSONArray()
        rig.customRaw.forEach { custom.put(it) }
        return JSONObject().put("amp", rig.amp).put("pedals", JSONArray(rig.pedals)).put("custom", custom).toString(1)
    }

    /** True when you own a pedal of this kind: the song page only shows the Whammy / Band sections for pedals you have. */
    fun owns(rig: Rig, cat: GearCatalog, kind: String): Boolean =
        (cat.pedals + rig.custom).any { it.id in rig.pedals && it.kind == kind }
}

/** Reads and writes your personal files in this app's private storage (atomic, so a sync never sees half a file). */
class PersonalStore(private val context: Context) {
    private val dir = context.filesDir

    fun read(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()

    fun write(name: String, text: String) {
        val temp = File(dir, "$name.part")
        temp.writeText(text)
        check(temp.renameTo(File(dir, name))) { "Could not save $name" }
    }

    fun gearCatalog(): GearCatalog = runCatching {
        GearFiles.catalog(context.assets.open("gear/catalog.json").bufferedReader().use { it.readText() })
    }.getOrDefault(GearCatalog(emptyList(), emptyList(), emptyList()))
}
