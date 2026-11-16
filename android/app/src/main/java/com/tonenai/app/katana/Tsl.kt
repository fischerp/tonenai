package com.tonenai.app.katana

import org.json.JSONArray
import org.json.JSONObject

class TslPatch(val name: String, val memo: String, val blocks: Map<String, List<Int>>)

/** BOSS TONE STUDIO liveset files (.tsl) and where each of their blocks lives in a patch. */
object Tsl {
    /** Block offsets inside a patch, in 7-bit address form (same table as tools/params.py). */
    val BLOCK_BASE: Map<String, Int> = mapOf(
        "PatchName" to 0x000, "Patch_0" to 0x010, "Eq(2)" to 0x060, "Fx(1)" to 0x100, "Fx(2)" to 0x300,
        "Delay(1)" to 0x500, "Delay(2)" to 0x520, "Patch_1" to 0x540, "Patch_2" to 0x620, "Status" to 0x650,
        "KnobAsgn" to 0x700, "ExpPedalAsgn" to 0x800, "ExpPedalAsgnMinMax" to 0x830,
        "GafcExp1Asgn" to 0x900, "GafcExp1AsgnMinMax" to 0x930, "GafcExp2Asgn" to 0xA00, "GafcExp2AsgnMinMax" to 0xA30,
        "GafcExp3Asgn" to 0xB00, "GafcExp3AsgnMinMax" to 0xB30, "GafcExExp1Asgn" to 0xC00, "GafcExExp1AsgnMinMax" to 0xC30,
        "GafcExExp2Asgn" to 0xD00, "GafcExExp2AsgnMinMax" to 0xD30, "GafcExExp3Asgn" to 0xE00, "GafcExExp3AsgnMinMax" to 0xE30,
        "CtrlAsgn" to 0xF00, "FsAsgn" to 0xF08, "Patch_Mk2V2" to 0xF10,
        "Contour(1)" to 0xF30, "Contour(2)" to 0xF38, "Contour(3)" to 0xF40,
    )

    /** What "play a patch" sends, in this order (tools/patch.py apply_live). */
    val LIVE_BLOCKS = listOf("PatchName", "Patch_0", "Fx(1)", "Fx(2)", "Delay(1)", "Delay(2)", "Patch_1", "Patch_2",
        "Eq(2)", "Patch_Mk2V2", "Status")

    /** Every block of a stored patch, in the order the desktop app writes them (tools/params.py TSL_BLOCKS). */
    val ALL_BLOCKS = listOf(
        "PatchName", "Patch_0", "Fx(1)", "Fx(2)", "Delay(1)", "Delay(2)", "Patch_1", "Patch_2", "Status", "KnobAsgn",
        "ExpPedalAsgn", "ExpPedalAsgnMinMax", "GafcExp1Asgn", "GafcExp1AsgnMinMax", "GafcExp2Asgn", "GafcExp2AsgnMinMax",
        "FsAsgn", "Patch_Mk2V2", "Contour(1)", "Contour(2)", "Contour(3)", "GafcExp3Asgn", "GafcExp3AsgnMinMax",
        "GafcExExp1Asgn", "GafcExExp1AsgnMinMax", "GafcExExp2Asgn", "GafcExExp2AsgnMinMax", "GafcExExp3Asgn",
        "GafcExExp3AsgnMinMax", "CtrlAsgn", "Eq(2)",
    )

    /** What is written into an amp memory slot: everything except Status, which the amp keeps. */
    val SLOT_BLOCKS = ALL_BLOCKS - "Status"

    /** The 16-character patch name stored in a patch's blocks. */
    fun patchName(blocks: Map<String, List<Int>>): String = blocks["PatchName"].orEmpty().map { it.toChar() }.joinToString("").trim()

    /** A .tsl liveset text from patches given as (memo, blocks). */
    fun build(name: String, patches: List<Pair<String, Map<String, List<Int>>>>): String {
        val list = JSONArray()
        for ((memo, blocks) in patches) {
            val set = JSONObject()
            for (block in ALL_BLOCKS) {
                val data = blocks[block] ?: continue
                set.put("UserPatch%$block", JSONArray(data.map { "%02X".format(it) }))
            }
            list.put(JSONObject().put("memo", memo).put("paramSet", set))
        }
        return JSONObject().put("name", name).put("formatRev", "0002").put("device", "KATANA MkII")
            .put("data", JSONArray().put(list)).toString(1)
    }

    /** Absolute address of byte [offset] (a plain index) of [block] in the patch stored at [base]. */
    fun address(block: String, offset: Int, base: Int = Sysex.TEMP): Int =
        base + Sysex.to7bit(Sysex.toLinear(BLOCK_BASE.getValue(block)) + offset)

    fun parse(text: String): List<TslPatch> {
        val root = JSONObject(text)
        require(root.optString("device") == "KATANA MkII") { "Not a KATANA MkII liveset (.tsl)" }
        val list = root.getJSONArray("data").getJSONArray(0)
        return (0 until list.length()).map { i ->
            val patch = list.getJSONObject(i)
            val set = patch.getJSONObject("paramSet")
            val blocks = HashMap<String, List<Int>>()
            for (key in set.keys()) {
                val arr = set.getJSONArray(key)
                blocks[key.substringAfter('%')] = List(arr.length()) { arr.getString(it).toInt(16) }
            }
            val name = blocks["PatchName"].orEmpty().map { it.toChar() }.joinToString("").trim()
            TslPatch(name.ifEmpty { "Patch ${i + 1}" }, patch.optString("memo"), blocks)
        }
    }
}
