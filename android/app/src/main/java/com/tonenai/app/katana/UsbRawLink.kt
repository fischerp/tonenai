package com.tonenai.app.katana

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The amp over plain USB bulk transfers, speaking USB-MIDI event packets ourselves.
 * This is the fallback for when Android does not list the amp as a MIDI device
 * (Roland/BOSS gear often exposes a vendor-specific interface instead of the standard class).
 */
class UsbRawLink private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
    override val description: String,
) : KatanaLink {
    @Volatile
    override var onSysex: ((ByteArray) -> Unit)? = null
    private val assembler = SysexAssembler { onSysex?.invoke(it) }
    private val running = AtomicBoolean(true)
    private val reader = thread(name = "katana-usb-in") {
        val buf = ByteArray(maxOf(64, epIn.maxPacketSize))
        while (running.get()) {
            val n = connection.bulkTransfer(epIn, buf, buf.size, 200)
            if (n < 0) {                                          // nothing yet, or the cable was pulled: do not spin
                Thread.sleep(5)
                continue
            }
            var i = 0
            while (i + 3 < n) {                                   // 4-byte USB-MIDI event packets
                val len = when (buf[i].toInt() and 0x0F) {
                    0x4, 0x7 -> 3                                 // SysEx starts/continues, or ends with 3 bytes
                    0x6 -> 2                                      // SysEx ends with 2 bytes
                    0x5 -> 1                                      // SysEx ends with 1 byte
                    else -> 0
                }
                if (len > 0) assembler.feed(buf, i + 1, len)
                i += 4
            }
        }
    }

    @Synchronized
    override fun send(message: ByteArray) {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < message.size) {
            val left = message.size - i
            val take = minOf(3, left)
            out.write(if (left > 3) 0x04 else 0x04 + left)        // 0x5, 0x6, 0x7: last 1, 2, 3 bytes
            for (k in 0 until 3) out.write(if (k < take) message[i + k].toInt() and 0xFF else 0)
            i += take
        }
        val data = out.toByteArray()
        val sent = connection.bulkTransfer(epOut, data, data.size, 1000)
        if (sent != data.size) throw java.io.IOException("USB write failed ($sent of ${data.size} bytes)")
    }

    override fun close() {
        running.set(false)
        runCatching { reader.join(500) }
        runCatching { connection.releaseInterface(usbInterface) }
        runCatching { connection.close() }
    }

    companion object {
        const val ROLAND_VENDOR_ID = 0x0582
        private const val ACTION_PERMISSION = "com.tonenai.app.USB_PERMISSION"

        private fun manager(context: Context) = context.getSystemService(Context.USB_SERVICE) as UsbManager

        fun devices(context: Context): List<UsbDevice> = manager(context).deviceList.values.toList()

        fun katana(context: Context): UsbDevice? = devices(context).firstOrNull { it.vendorId == ROLAND_VENDOR_ID }

        fun hasPermission(context: Context, device: UsbDevice): Boolean = manager(context).hasPermission(device)

        /** One line per device plus its interfaces: the facts we need if nothing works. */
        fun describe(d: UsbDevice): String {
            val sb = StringBuilder("%04X:%04X %s %s".format(d.vendorId, d.productId,
                d.manufacturerName ?: "?", d.productName ?: "?"))
            for (n in 0 until d.interfaceCount) {
                val itf = d.getInterface(n)
                val eps = (0 until itf.endpointCount).joinToString(",") { e ->
                    val ep = itf.getEndpoint(e)
                    val type = when (ep.type) {
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
                        UsbConstants.USB_ENDPOINT_XFER_INT -> "int"
                        UsbConstants.USB_ENDPOINT_XFER_ISOC -> "iso"
                        else -> "ctrl"
                    }
                    type + if (ep.direction == UsbConstants.USB_DIR_IN) "-in" else "-out"
                }
                sb.append("\n    if ${itf.id}/alt ${itf.alternateSetting}: class %02X sub %02X [%s]"
                    .format(itf.interfaceClass, itf.interfaceSubclass, eps))
            }
            return sb.toString()
        }

        suspend fun requestPermission(context: Context, device: UsbDevice): Boolean {
            val um = manager(context)
            if (um.hasPermission(device)) return true
            return suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        runCatching { context.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(um.hasPermission(device))
                    }
                }
                ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PERMISSION),
                    ContextCompat.RECEIVER_NOT_EXPORTED)
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val intent = Intent(ACTION_PERMISSION).setPackage(context.packageName)
                um.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
            }
        }

        /** Claims the first interface that has a bulk endpoint in each direction (the MIDI one). */
        fun open(context: Context, device: UsbDevice): UsbRawLink? {
            for (n in 0 until device.interfaceCount) {
                val itf = device.getInterface(n)
                var epIn: UsbEndpoint? = null
                var epOut: UsbEndpoint? = null
                for (e in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) epIn = epIn ?: ep else epOut = epOut ?: ep
                }
                if (epIn == null || epOut == null) continue
                val connection = manager(context).openDevice(device) ?: return null
                if (!connection.claimInterface(itf, true)) {
                    connection.close()
                    continue
                }
                if (itf.alternateSetting != 0) connection.setInterface(itf)
                return UsbRawLink(connection, itf, epIn, epOut,
                    "direct USB: %04X:%04X interface %d".format(device.vendorId, device.productId, itf.id))
            }
            return null
        }
    }
}
