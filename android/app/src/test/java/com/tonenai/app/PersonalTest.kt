package com.tonenai.app

import com.tonenai.app.data.GearFiles
import com.tonenai.app.data.GuitarFiles
import com.tonenai.app.data.SongMeta
import com.tonenai.app.data.SongMetaFiles
import com.tonenai.app.data.TabFiles
import com.tonenai.app.data.TabsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalTest {
    @Test fun metaRoundtripDropsEmptyAndBadStatus() {
        val map = SongMetaFiles.parse("""{"a-b":{"status":"learning","notes":"capo 2"},"c":{"status":"bogus"},"BAD id":{"status":"played"}}""")
        assertEquals(setOf("a-b"), map.keys)
        assertEquals(SongMeta("learning", "capo 2"), map["a-b"])
        assertEquals(map, SongMetaFiles.parse(SongMetaFiles.toJson(map)))
    }

    @Test fun tabUrls() {
        val cfg = TabsConfig("songsterr", "", mapOf("x" to "https://example.com/t"))
        assertEquals("https://example.com/t", TabFiles.url(cfg, "x", "A", "B"))
        assertEquals("https://www.songsterr.com/?pattern=Pink%20Floyd%20Time", TabFiles.url(cfg, "y", "Pink Floyd", "Time"))
        assertNull(TabFiles.url(TabsConfig(), "y", "A", "B"))
        assertEquals("A%20Song", TabFiles.query("A", "Song (Live)"))
        assertEquals("A%20Song%20%28Part%202%29", TabFiles.query("A", "Song (Part 2)"))
    }

    @Test fun tabsParseRejectsNonHttpLinks() {
        val c = TabFiles.parse("""{"service":"google","links":{"a":"javascript:x","b":"https://ok.com"}}""")
        assertEquals(mapOf("b" to "https://ok.com"), c.links)
    }

    @Test fun guitarsRoundtripAndIds() {
        val list = GuitarFiles.parse("""[{"id":"g1","name":"Strat","pickups":"sss","finish":"blue"},{"id":"","name":"x"}]""")
        assertEquals(1, list.size)
        assertEquals(list, GuitarFiles.parse(GuitarFiles.toJson(list)))
        assertEquals("fender-strat-2", GuitarFiles.newId("Fender Strat", setOf("fender-strat")))
        assertEquals("guitar", GuitarFiles.newId("!!", emptySet()))
    }

    private val catalogJson = """{"amps":[{"id":"katana-mkii","name":"K"}],"pedals":[
        {"id":"whammy-dt","name":"W","kind":"whammy","default":true},{"id":"trio-plus","name":"T","kind":"trio","default":true},{"id":"x","name":"X"}]}"""

    @Test fun rigDefaultsAndOwnership() {
        val cat = GearFiles.catalog(catalogJson)
        val rig = GearFiles.parseRig(null, cat)
        assertEquals(listOf("whammy-dt", "trio-plus"), rig.pedals)
        assertTrue(GearFiles.owns(rig, cat, "whammy"))
        val none = GearFiles.parseRig("""{"pedals":[],"amp":"nope"}""", cat)
        assertFalse(GearFiles.owns(none, cat, "trio"))
        assertEquals("katana-mkii", none.amp)
    }

    @Test fun customPedalsKeepTheirProfile() {
        val cat = GearFiles.catalog(catalogJson)
        val text = """{"amp":"katana-mkii","pedals":["custom-big"],"custom":[{"id":"custom-big","name":"Big","profile":{"k":1}},{"id":"bad id","name":"Z"}]}"""
        val rig = GearFiles.parseRig(text, cat)
        assertEquals(1, rig.customRaw.size)
        assertTrue(GearFiles.toJson(rig).contains("\"profile\""))
        assertEquals("custom-big-2", GearFiles.newCustomId("Big", setOf("custom-big")))
    }
}
