package com.tonenai.app.data

import com.tonenai.app.katana.Slots
import com.tonenai.app.katana.Tsl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivesetsTest {
    /** A real entry of the desktop app's data/livesets.json. */
    private val desktop = """[
     {"id": "new-liveset-1-79356", "name": "New liveset 1",
      "slots": {"A1": {"song": "angra-carry-on", "index": 1}, "B4": {"song": "metallica-whiskey-in-the-jar", "index": 1}, "C9": {"song": "x", "index": 0}},
      "notes": ""},
     {"id": "", "name": "broken", "slots": {}, "notes": ""},
     {"id": "evil", "name": "Evil", "slots": {"A1": {"song": "../etc/passwd", "index": 0}}, "notes": ""}]"""

    @Test
    fun readsTheDesktopFileAndSkipsWhatIsWrong() {
        val items = LivesetFiles.parse(desktop)
        assertEquals(listOf("new-liveset-1-79356", "evil"), items.map { it.id })
        assertEquals(SlotRef("angra-carry-on", 1), items[0].slots["A1"])
        assertEquals(setOf("A1", "B4"), items[0].slots.keys)          // C9 is not a slot
        assertTrue(items[1].slots.isEmpty())                          // an unsafe song id is dropped
    }

    @Test
    fun writesWhatTheDesktopReads() {
        val first = LivesetFiles.parse(desktop)
        val again = LivesetFiles.parse(LivesetFiles.toJson(first))
        assertEquals(first, again)
    }

    @Test
    fun assignAndClearASlot() {
        val ls = Liveset("a", "A", emptyMap(), "")
        val set = ls.assign("A2", SlotRef("song", 0))
        assertEquals(SlotRef("song", 0), set.slots["A2"])
        assertNull(set.assign("A2", null).slots["A2"])
    }

    @Test
    fun newIdsAreSlugsWithDigits() {
        assertEquals("new-liveset-1-79356", LivesetFiles.newId("New liveset 1", 1_700_079_356))
        assertTrue(LivesetFiles.newId("???", 12).startsWith("liveset-"))
        assertEquals("A · CH-3", LivesetFiles.label("A3"))
    }

    @Test
    fun slotAddressesFollowTheDesktopLayout() {
        assertEquals(0x10000000, Slots.base(0))
        assertEquals(0x10010000, Slots.base(1))            // A1
        assertEquals(0x10080000, Slots.base(8))            // B4
        assertEquals(0x10010000, Tsl.address("PatchName", 0, Slots.base(1)))
        assertEquals(0x10010010, Tsl.address("Patch_0", 0, Slots.base(1)))
        assertEquals(9, Slots.KEYS.size)
    }

    @Test
    fun aBackupIsALivesetTheAppCanReadBack() {
        val name = "Hello World".padEnd(16).map { it.code }
        val blocks = mapOf("PatchName" to name, "Patch_0" to List(72) { it % 100 }, "Status" to listOf(1, 2, 3))
        val tsl = Tsl.build("Slot backup", listOf("A1" to blocks, "B2" to blocks))
        val parsed = Tsl.parse(tsl)
        assertEquals(listOf("A1", "B2"), parsed.map { it.memo })     // the memo carries the slot
        assertEquals(blocks, parsed[0].blocks)
        assertEquals("Hello World", Tsl.patchName(parsed[0].blocks))
        assertTrue(Tsl.SLOT_BLOCKS.none { it == "Status" } && Tsl.ALL_BLOCKS.contains("Status"))
    }
}
