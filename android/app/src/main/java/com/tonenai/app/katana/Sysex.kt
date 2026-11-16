package com.tonenai.app.katana

/** Roland DT1/RQ1 SysEx for the Katana MkII. Kotlin port of tools/katana.py. */
object Sysex {
    private val HEADER = listOf(0x41, 0x00, 0x00, 0x00, 0x00, 0x33)
    private const val DT1 = 0x12
    private const val RQ1 = 0x11

    /** The temporary (live) patch. */
    const val TEMP = 0x60000000

    /** Write 1 here before reading or writing (editor mode). */
    const val EDITOR_MODE = 0x7F000001

    val IDENTITY_REQUEST: ByteArray = bytes(listOf(0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7))

    fun checksum(body: List<Int>): Int = (128 - body.sum() % 128) % 128

    /** Addresses are written one byte per 7-bit digit: 0x60000010 is sent as 60 00 00 10. */
    fun addrBytes(a: Int): List<Int> =
        listOf((a ushr 24) and 0xFF, (a ushr 16) and 0xFF, (a ushr 8) and 0xFF, a and 0xFF)

    fun toLinear(a7: Int): Int =
        (((a7 ushr 24) and 0x7F) shl 21) or (((a7 ushr 16) and 0x7F) shl 14) or
            (((a7 ushr 8) and 0x7F) shl 7) or (a7 and 0x7F)

    fun to7bit(n: Int): Int =
        (((n ushr 21) and 0x7F) shl 24) or (((n ushr 14) and 0x7F) shl 16) or
            (((n ushr 7) and 0x7F) shl 8) or (n and 0x7F)

    fun dt1(addr: Int, values: List<Int>): ByteArray = message(DT1, addrBytes(addr) + values)

    fun rq1(addr: Int, size: Int): ByteArray = message(RQ1, addrBytes(addr) + addrBytes(size))

    private fun message(command: Int, body: List<Int>): ByteArray =
        bytes(listOf(0xF0) + HEADER + command + body + checksum(body) + 0xF7)

    /** A DT1 message from the amp as (address, data), or null when [msg] is something else. */
    fun parseDt1(msg: ByteArray): Pair<Int, List<Int>>? {
        if (msg.size < 14) return null
        val m = msg.map { it.toInt() and 0xFF }
        if (m[0] != 0xF0 || m[1] != 0x41 || m[6] != 0x33 || m[7] != DT1 || m.last() != 0xF7) return null
        val addr = (m[8] shl 24) or (m[9] shl 16) or (m[10] shl 8) or m[11]
        return addr to m.subList(12, m.size - 2)
    }

    fun hex(msg: ByteArray): String = msg.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun bytes(values: List<Int>): ByteArray = ByteArray(values.size) { values[it].toByte() }
}

/** Collects a MIDI byte stream into complete SysEx messages (F0 ... F7). */
class SysexAssembler(private val onMessage: (ByteArray) -> Unit) {
    private val buffer = java.io.ByteArrayOutputStream()
    private var open = false

    @Synchronized
    fun feed(data: ByteArray, offset: Int, count: Int) {
        for (i in offset until offset + count) {
            val b = data[i].toInt() and 0xFF
            when {
                b >= 0xF8 -> Unit                       // real-time bytes may appear anywhere
                b == 0xF0 -> { buffer.reset(); buffer.write(b); open = true }
                b == 0xF7 -> if (open) { buffer.write(b); open = false; onMessage(buffer.toByteArray()) }
                b >= 0x80 -> open = false               // another status byte aborts the SysEx
                open -> buffer.write(b)
            }
        }
    }
}
