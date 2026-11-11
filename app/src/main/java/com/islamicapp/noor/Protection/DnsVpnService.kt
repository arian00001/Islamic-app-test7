package com.islamicapp.noor.Protection

import android.app.Notification
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.islamicapp.noor.EventBus
import com.islamicapp.noor.Notifications.AppNotifications
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Local DNS filter. Traffic is sent to the VPN's own DNS address inside the
 * tunnel, every query is forwarded to a family-safe upstream resolver, and
 * blocklisted names get an NXDOMAIN answer. No browsing history is stored and
 * nothing leaves the device except the DNS query itself.
 */
class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        if (!running) startTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        val builder = Builder()
            .setSession("Iman Sangi protection")
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(DNS_IN_TUNNEL)
            .addRoute(DNS_IN_TUNNEL, 32)
            .setBlocking(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        tun = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }

        val descriptor = tun
        if (descriptor == null) {
            isRunning = false
            return
        }

        running = true
        isRunning = true

        worker = thread(name = "dns-filter", start = true) {
            relay(descriptor)
        }

        val payload = JSONObject()
        payload.put("running", true)
        EventBus.emitJson("protection", payload)
    }

    private fun relay(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        val upstream = DatagramSocket()

        try {
            protect(upstream)
        } catch (ignored: Exception) {
        }

        try {
            upstream.soTimeout = 6000
            while (running) {
                val length = input.read(buffer)
                if (length <= 0) continue

                val headerLength = (buffer[0].toInt() and 0x0F) * 4
                if (length < headerLength + 8) continue
                val protocol = buffer[9].toInt() and 0xFF
                if (protocol != 17) continue

                val payloadOffset = headerLength + 8
                val payloadLength = length - payloadOffset
                if (payloadLength <= 0) continue

                val query = buffer.copyOfRange(payloadOffset, length)
                val host = extractHost(query)
                val answer = if (host != null && DomainBlockList.isBlocked(host)) {
                    nxDomain(query)
                } else {
                    askUpstream(upstream, query)
                }
                if (answer != null) {
                    output.write(buildReply(buffer, headerLength, answer))
                }
            }
        } catch (ignored: Exception) {
            // Socket closed on stop or revoked by the system.
        } finally {
            try {
                upstream.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun askUpstream(socket: DatagramSocket, query: ByteArray): ByteArray? {
        return try {
            val address = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(query, query.size, address, 53))
            val response = ByteArray(4096)
            val packet = DatagramPacket(response, response.size)
            socket.receive(packet)
            response.copyOf(packet.length)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildReply(request: ByteArray, headerLength: Int, payload: ByteArray): ByteArray {
        val total = headerLength + 8 + payload.size
        val out = ByteArray(total)
        System.arraycopy(request, 0, out, 0, headerLength)

        out[2] = ((total shr 8) and 0xFF).toByte()
        out[3] = (total and 0xFF).toByte()
        System.arraycopy(request, 16, out, 12, 4)
        System.arraycopy(request, 12, out, 16, 4)
        out[10] = 0
        out[11] = 0

        val udpLength = 8 + payload.size
        out[headerLength] = request[headerLength + 2]
        out[headerLength + 1] = request[headerLength + 3]
        out[headerLength + 2] = request[headerLength]
        out[headerLength + 3] = request[headerLength + 1]
        out[headerLength + 4] = ((udpLength shr 8) and 0xFF).toByte()
        out[headerLength + 5] = (udpLength and 0xFF).toByte()
        out[headerLength + 6] = 0
        out[headerLength + 7] = 0

        System.arraycopy(payload, 0, out, headerLength + 8, payload.size)
        return out
    }

    private fun extractHost(query: ByteArray): String? {
        if (query.size < 13) return null
        val builder = StringBuilder()
        var index = 12
        while (index < query.size) {
            val labelLength = query[index].toInt() and 0xFF
            if (labelLength == 0) break
            if (labelLength > 63) return null
            if (index + 1 + labelLength > query.size) return null
            if (builder.isNotEmpty()) builder.append('.')
            builder.append(String(query, index + 1, labelLength))
            index += labelLength + 1
        }
        return if (builder.isEmpty()) null else builder.toString().lowercase()
    }

    private fun nxDomain(query: ByteArray): ByteArray {
        val response = query.copyOf()
        response[2] = ((response[2].toInt() or 0x80) and 0xFF).toByte()
        response[3] = ((response[3].toInt() and 0xF0) or 0x03).toByte()
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }

    private fun stopTunnel() {
        running = false
        isRunning = false
        try {
            tun?.close()
        } catch (ignored: Exception) {
        }
        tun = null
        worker = null
        stopSelf()
        val payload = JSONObject()
        payload.put("running", false)
        EventBus.emitJson("protection", payload)
    }

    override fun onRevoke() {
        stopTunnel()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.islamicapp.noor.protection.STOP"
        const val UPSTREAM_DNS = "1.1.1.3"
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val DNS_IN_TUNNEL = "10.111.222.2"
        private const val NOTIFICATION_ID = 4242

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
