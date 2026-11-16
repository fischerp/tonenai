package com.tonenai.app

import com.tonenai.app.data.SongSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSearchTest {
    @Test fun parseDedupesCleansTitlesAndBuildsTheRequestLine() {
        val json = """{"results":[
            {"trackName":"Back In Black (Remastered)","artistName":"AC/DC","collectionName":"Back In Black"},
            {"trackName":"Back In Black","artistName":"AC/DC","collectionName":"Live"},
            {"trackName":"Thunderstruck","artistName":"AC/DC","collectionName":"The Razors Edge"},
            {"trackName":"","artistName":"X"}]}"""
        val hits = SongSearch.parse(json)
        assertEquals(listOf("AC/DC - Back In Black", "AC/DC - Thunderstruck"), hits.map { it.request })
        assertEquals("Back In Black", hits[0].album)
    }

    @Test fun titlesKeepNonNoiseBrackets() {
        assertEquals("Song (Part 2)", SongSearch.clean("Song (Part 2)"))
        assertEquals("Song", SongSearch.clean("Song (Live at Wembley)"))
    }
}
