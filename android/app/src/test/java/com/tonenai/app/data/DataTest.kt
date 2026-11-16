package com.tonenai.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DataTest {
    /** The same table as tests/test_drive_sync.py: the phone and the computer must decide alike. */
    @Test
    fun syncPlanMatchesTheDesktop() {
        assertEquals(
            mapOf("b" to "delete_local", "c" to "upload", "d" to "conflict", "e" to "upload", "f" to "delete_remote", "g" to "download"),
            SyncPlan.plan(
                mapOf("a" to "1", "b" to "1", "c" to "2", "d" to "2", "e" to "1"),
                mapOf("a" to "1", "c" to "1", "d" to "3", "f" to "1", "g" to "1"),
                mapOf("a" to "1", "b" to "1", "c" to "1", "d" to "1", "f" to "1"),
            ).toMap(),
        )
    }

    @Test
    fun aSideThatLostEverythingDeletesNothing() {
        val all = mapOf("a.json" to "1", "b.json" to "1", "c.json" to "1")
        assertEquals(setOf("upload"), SyncPlan.plan(all, emptyMap(), all).values.toSet())
        assertEquals(setOf("download"), SyncPlan.plan(emptyMap(), all, all).values.toSet())
    }

    @Test
    fun readsADesktopSong() {
        val json = """[{"name": "Clean", "memo": "Metallica - The Unforgiven: clean intro.", "amp": {"type": "CLEAN"},
            "band": {"bpm": 70}, "mod": {"type": "CHORUS", "on": true}, "delay": {"type": "DIGITAL", "on": false},
            "reverb": {"type": "PLATE", "on": true}},
            {"name": "Lead", "memo": "Metallica - The Unforgiven: solo.", "amp": {"type": "LEAD"}, "booster": {"type": "T-SCREAM"}}]"""
        val song = SongFiles.fromJson("metallica-the-unforgiven", json, 0)
        assertEquals("Metallica", song.artist)
        assertEquals("The Unforgiven", song.title)
        assertEquals(listOf("CHORUS", "PLATE reverb"), song.patches[0].effects)
        assertEquals("clean intro.", song.patches[0].note)
        assertEquals(listOf("T-SCREAM"), song.patches[1].effects)
        assertEquals(70, song.band!!.getInt("bpm"))
    }

    @Test
    fun md5IsDrivesChecksumFormat() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", DriveSync.md5("hello".toByteArray()))
    }

    @Test
    fun importedFileNameBecomesASongId() {
        assertEquals("metallica-master-of-puppets", SongFiles.idFor("Metallica - Master Of Puppets.tsl"))
        assertEquals("pixies-where-is-my-mind", SongFiles.idFor("Download/Pixies_Where is my mind!.TSL"))
        assertEquals("imported", SongFiles.idFor("???.tsl"))
        assertTrue(SongFiles.NAME.matches(SongFiles.idFor("Ünïcode ✓ name.tsl") + ".tsl"))
    }

    @Test
    fun anImportedLivesetShowsItsArtistAndTitle() {
        val tsl = """{"device":"KATANA MkII","data":[[{"memo":"Pixies - Where Is My Mind: clean arpeggio","paramSet":{"UserPatch%PatchName":["50","69"]}}]]}"""
        val song = SongFiles.fromTsl("pixies-where-is-my-mind", tsl, 0)
        assertEquals("Pixies", song.artist)
        assertEquals("Where Is My Mind", song.title)
        assertEquals("clean arpeggio", song.patches[0].note)
        val plain = SongFiles.fromTsl("my-sound", """{"device":"KATANA MkII","data":[[{"memo":"","paramSet":{}}]]}""", 0)
        assertEquals("Imported", plain.artist)
        assertEquals("my-sound", plain.title)
    }

    @Test
    fun aHugeFileIsRefusedWhileReading() {
        assertEquals(5, SongFiles.readLimited("hello".byteInputStream(), 10).size)
        try {
            SongFiles.readLimited(ByteArray(11).inputStream(), 10)
            fail("expected the limit to apply")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("too big"))
        }
    }

    @Test
    fun dataFilesMustHaveTheirOwnShape() {
        assertTrue(DataFiles.valid("livesets.json", """[{"id":"a"}]"""))
        assertTrue(DataFiles.valid("livesets.json", "[]"))
        assertTrue(!DataFiles.valid("livesets.json", """{"id":"a"}"""))
        assertTrue(!DataFiles.valid("guitars.json", "[1,2]"))
        assertTrue(DataFiles.valid("song_meta.json", """{"a-song":{"status":"learning"}}"""))
        assertTrue(!DataFiles.valid("song_meta.json", "[]"))
        assertTrue(!DataFiles.valid("tabs.json", "not json"))
    }
}
