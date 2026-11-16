package com.tonenai.app.katana

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** The amp through android.media.midi: works when Android recognises it as a USB MIDI device. */
class MidiApiLink private constructor(
    private val device: MidiDevice,
    private val toAmp: MidiInputPort,
    private val fromAmp: MidiOutputPort,
    override val description: String,
) : KatanaLink {
    @Volatile
    override var onSysex: ((ByteArray) -> Unit)? = null
    private val assembler = SysexAssembler { onSysex?.invoke(it) }
    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            assembler.feed(msg, offset, count)
        }
    }

    init {
        fromAmp.connect(receiver)
    }

    override fun send(message: ByteArray) = toAmp.send(message, 0, message.size)

    override fun close() {
        runCatching { fromAmp.disconnect(receiver) }
        runCatching { fromAmp.close() }
        runCatching { toAmp.close() }
        runCatching { device.close() }
    }

    companion object {
        fun devices(context: Context): List<MidiDeviceInfo> {
            val mm = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return emptyList()
            return if (Build.VERSION.SDK_INT >= 33) {
                mm.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
            } else {
                @Suppress("DEPRECATION")
                mm.devices.toList()
            }
        }

        fun label(info: MidiDeviceInfo): String {
            val p = info.properties
            val name = p.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: listOfNotNull(
                    p.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER),
                    p.getString(MidiDeviceInfo.PROPERTY_PRODUCT),
                ).joinToString(" ").ifEmpty { "unnamed" }
            val type = when (info.type) {
                MidiDeviceInfo.TYPE_USB -> "USB"
                MidiDeviceInfo.TYPE_BLUETOOTH -> "Bluetooth"
                MidiDeviceInfo.TYPE_VIRTUAL -> "virtual"
                else -> "type ${info.type}"
            }
            // Android names ports from the device's side: its "input" is where we send.
            return "$name [$type, ${info.inputPortCount} in / ${info.outputPortCount} out]"
        }

        fun usable(info: MidiDeviceInfo): Boolean =
            info.type == MidiDeviceInfo.TYPE_USB && info.inputPortCount > 0 && info.outputPortCount > 0

        suspend fun open(context: Context, info: MidiDeviceInfo): MidiApiLink? {
            val mm = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return null
            val device = suspendCancellableCoroutine<MidiDevice?> { cont ->
                mm.openDevice(info, { d -> if (cont.isActive) cont.resume(d) else d?.close() },
                    Handler(Looper.getMainLooper()))
            } ?: return null
            val toAmp = device.openInputPort(0)
            val fromAmp = device.openOutputPort(0)
            if (toAmp == null || fromAmp == null) {
                toAmp?.close(); fromAmp?.close(); device.close()
                return null                                      // a port is already in use by another app
            }
            return MidiApiLink(device, toAmp, fromAmp, "Android MIDI API: " + label(info))
        }
    }
}
