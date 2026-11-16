package com.tonenai.app

import com.tonenai.app.data.RequestFiles
import com.tonenai.app.data.RequestStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestsTest {
    private fun tmp() = File.createTempFile("req", "").let { it.delete(); it.mkdirs(); it }

    @Test fun textIsOneShortPlainLine() {
        assertEquals("Under the Bridge", RequestFiles.cleanText("  Under   the Bridge "))
        assertEquals("a b", RequestFiles.cleanText("a\nb"))
        assertNull(RequestFiles.cleanText("   "))
        assertNull(RequestFiles.cleanText("x".repeat(201)))
        assertNull(RequestFiles.cleanText("bad\u0000text"))
    }

    @Test fun validChecksMatchTheDesktopRules() {
        val id = "0123456789ab"
        assertTrue(RequestFiles.valid("req-$id.json", RequestFiles.requestJson(id, "Song", 5L)))
        assertFalse(RequestFiles.valid("req-$id.json", RequestFiles.requestJson("ffffffffffff", "Song", 5L)))
        assertFalse(RequestFiles.valid("req-xyz.json", RequestFiles.requestJson(id, "Song", 5L)))
        assertTrue(RequestFiles.valid("res-$id.json", """{"id":"$id","status":"done","note":"ok","song":"a-b","updated":1}"""))
        assertFalse(RequestFiles.valid("res-$id.json", """{"id":"$id","status":"nope","updated":1}"""))
        assertFalse(RequestFiles.valid("res-$id.json", """{"id":"$id","status":"done","song":"../x","updated":1}"""))
    }

    @Test fun storeCreatesMergesResultsAndRemoves() {
        val store = RequestStore(tmp())
        val made = store.create("Under the Bridge, RHCP")!!
        assertEquals("new", store.items().single().status)
        File(store.dir, "res-${made.id}.json").writeText("""{"id":"${made.id}","status":"done","note":"Song created","song":"rhcp-under-the-bridge","updated":9}""")
        val item = store.items().single()
        assertEquals("done", item.status)
        assertEquals("rhcp-under-the-bridge", item.song)
        store.remove(made.id)
        assertTrue(store.items().isEmpty())
    }

    @Test fun storeRefusesBadTextAndTooManyOpen() {
        val store = RequestStore(tmp())
        assertNull(store.create("   "))
        repeat(RequestFiles.MAX_OPEN) { assertNotNull(store.create("Song $it")) }
        assertNull(store.create("one more"))
    }

    @Test fun idsAreTwelveHexDigits() {
        assertTrue(Regex("[0-9a-f]{12}").matches(RequestFiles.newId()))
    }
}
