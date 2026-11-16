package com.tonenai.app.katana

import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The amp's user memory: Panel, then banks A and B with four channels each. Same layout as the temporary patch. */
object Slots {
    val KEYS = listOf("PANEL", "A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4")
    fun base(index: Int) = 0x10000000 + (index shl 16)
}

/** The amp, over whichever [KatanaLink] reached it. */
class Katana(private val link: KatanaLink) : Closeable {
    val transport: String get() = link.description
    private val inbox = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        link.onSysex = { inbox.trySend(it) }
    }

    private fun drain() {
        while (inbox.tryReceive().isSuccess) Unit
    }

    private suspend fun await(timeoutMs: Long, match: (ByteArray) -> Boolean): ByteArray? =
        withTimeoutOrNull(timeoutMs) {
            var hit: ByteArray? = null
            while (hit == null) {
                val m = inbox.receive()
                if (match(m)) hit = m
            }
            hit
        }

    /** The amp's reply to a universal identity request, or null if it stayed silent. */
    suspend fun identity(): ByteArray? = withContext(Dispatchers.IO) {
        drain()
        link.send(Sysex.IDENTITY_REQUEST)
        await(1000) { it.size >= 6 && it[1] == 0x7E.toByte() && it[3] == 0x06.toByte() && it[4] == 0x02.toByte() }
    }

    suspend fun editorMode() = withContext(Dispatchers.IO) {
        link.send(Sysex.dt1(Sysex.EDITOR_MODE, listOf(1)))
        delay(200)
    }

    suspend fun read(addr: Int, size: Int, tries: Int = 4): List<Int>? = withContext(Dispatchers.IO) {
        repeat(tries) {
            drain()
            link.send(Sysex.rq1(addr, size))
            val data = await(1000) { Sysex.parseDt1(it)?.first == addr }?.let { Sysex.parseDt1(it)?.second }
            if (data != null && data.size >= size) return@withContext data.take(size)
        }
        null
    }

    /** Name of the patch that is playing now, or null when the amp does not answer. */
    suspend fun patchName(): String? {
        editorMode()
        return read(Sysex.TEMP, 16, tries = 2)?.map { it.toChar() }?.joinToString("")?.trim()
    }

    /** Sends a patch to the temporary (live) patch. The amp's memory slots are not touched. */
    suspend fun applyLive(blocks: Map<String, List<Int>>) = withContext(Dispatchers.IO) {
        editorMode()
        for (block in Tsl.LIVE_BLOCKS) {
            val data = blocks[block] ?: continue
            var offset = 0
            while (offset < data.size) {
                val n = minOf(32, data.size - offset)
                link.send(Sysex.dt1(Tsl.address(block, offset), data.subList(offset, offset + n)))
                delay(30)
                offset += n
            }
        }
        delay(300)
    }

    /** Name stored in memory slot [index] (see [Slots]), or null when the amp does not answer. */
    suspend fun slotName(index: Int): String? =
        read(Slots.base(index), 16, tries = 3)?.map { it.toChar() }?.joinToString("")?.trim()

    /** The names of all nine memory slots, in [Slots.KEYS] order. */
    suspend fun slotNames(): List<String?> {
        editorMode()
        val names = ArrayList<String?>()
        for (i in Slots.KEYS.indices) names.add(slotName(i))
        return names
    }

    /** Writes a patch into memory slot [index] (never the Panel). Overwrites what was there. */
    suspend fun writeSlot(index: Int, blocks: Map<String, List<Int>>) = withContext(Dispatchers.IO) {
        require(index in 1 until Slots.KEYS.size) { "Only A1 to B4 can be written" }
        editorMode()
        val base = Slots.base(index)
        for (block in Tsl.SLOT_BLOCKS) {
            val data = blocks[block] ?: continue
            var offset = 0
            while (offset < data.size) {
                val n = minOf(32, data.size - offset)
                link.send(Sysex.dt1(Tsl.address(block, offset, base), data.subList(offset, offset + n)))
                delay(30)
                offset += n
            }
        }
        delay(100)
    }

    /**
     * Reads what memory slot [index] holds now, block by block. [shape] (the patch about to be written) says which
     * blocks exist and how long they are; where the amp skips an unmapped gap its bytes are used to fill the hole.
     */
    suspend fun snapshotSlot(index: Int, shape: Map<String, List<Int>>): Map<String, List<Int>> = withContext(Dispatchers.IO) {
        editorMode()
        val base = Slots.base(index)
        val result = LinkedHashMap<String, List<Int>>()
        for (block in Tsl.ALL_BLOCKS) {
            val fill = shape[block] ?: continue
            val got = ArrayList<Int>()
            var offset = 0
            while (offset < fill.size) {
                val n = minOf(32, fill.size - offset)
                val chunk = read(Tsl.address(block, offset, base), n, tries = 2)
                if (chunk != null) {
                    got.addAll(chunk)
                } else {
                    var sub = 0
                    while (sub < n) {
                        val m = minOf(8, n - sub)
                        val piece = read(Tsl.address(block, offset + sub, base), m, tries = 1)
                        got.addAll(piece ?: fill.subList(offset + sub, offset + sub + m))
                        sub += m
                    }
                }
                offset += n
            }
            result[block] = got
        }
        result
    }

    override fun close() = link.close()
}
