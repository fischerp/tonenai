package com.tonenai.app.katana

import java.io.Closeable

/** A way to exchange SysEx with the amp. Two exist: Android's MIDI API and direct USB. */
interface KatanaLink : Closeable {
    val description: String
    var onSysex: ((ByteArray) -> Unit)?
    fun send(message: ByteArray)
}
