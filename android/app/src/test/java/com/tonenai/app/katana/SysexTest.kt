package com.tonenai.app.katana

import org.junit.Assert.assertEquals
import org.junit.Test

class SysexTest {
    @Test
    fun editorModeMessage() {
        assertEquals("F0 41 00 00 00 00 33 12 7F 00 00 01 01 7F F7",
            Sysex.hex(Sysex.dt1(Sysex.EDITOR_MODE, listOf(1))))
    }

    @Test
    fun readPatchNameMessage() {
        assertEquals("F0 41 00 00 00 00 33 11 60 00 00 00 00 00 00 10 10 F7",
            Sysex.hex(Sysex.rq1(Sysex.TEMP, 16)))
    }

    @Test
    fun sevenBitAddresses() {
        assertEquals(128, Sysex.toLinear(0x100))
        assertEquals(0x120, Sysex.to7bit(160))
        assertEquals(0x60000120, Tsl.address("Fx(1)", 32))
        assertEquals(0x60000560, Tsl.address("Patch_1", 32))
    }

    @Test
    fun parsesReply() {
        val reply = Sysex.dt1(Sysex.TEMP, listOf(0x41, 0x42))
        assertEquals(Sysex.TEMP to listOf(0x41, 0x42), Sysex.parseDt1(reply))
    }

    @Test
    fun assemblesSplitSysex() {
        val got = ArrayList<String>()
        val a = SysexAssembler { got += Sysex.hex(it) }
        val msg = Sysex.dt1(Sysex.TEMP, listOf(1, 2, 3))
        a.feed(msg, 0, 5)
        a.feed(byteArrayOf(0xF8.toByte()), 0, 1)
        a.feed(msg, 5, msg.size - 5)
        assertEquals(listOf(Sysex.hex(msg)), got)
    }
}
