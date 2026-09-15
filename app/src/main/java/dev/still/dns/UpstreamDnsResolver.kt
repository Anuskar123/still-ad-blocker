package dev.still.dns

import android.net.VpnService
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream

class UpstreamDnsResolver(
    private val address: String,
    private val protectUdp: (DatagramSocket) -> Boolean,
    private val protectTcp: (Socket) -> Boolean,
    private val port: Int = 53,
    private val timeoutMillis: Int = 800,
    private val backup: String = backupAddress(address)
) : Closeable {
    constructor(service: VpnService, address: String = "8.8.8.8") :
        this(address, { service.protect(it) }, { service.protect(it) })

    companion object {
        // Keep queries with the provider chosen in Settings.
        fun backupAddress(address: String): String = when (address) {
            "8.8.8.8" -> "8.8.4.4"
            "1.1.1.1" -> "1.0.0.1"
            "9.9.9.9" -> "149.112.112.112"
            else -> address
        }
    }

    private val sockets = mutableSetOf<Closeable>()
    private var closed = false

    fun resolve(query: PacketParser.Query): ByteArray {
        var failure: Exception? = null
        val endpoints = listOf(address, backup).distinct()
        for (endpoint in endpoints) {
            try {
                val dns = udp(query, endpoint)
                // Complete truncated replies here: the local tunnel handles UDP only.
                return if (PacketParser.u16(dns, 2) and 0x0200 != 0) tcp(query, endpoint) else dns
            } catch (error: Exception) {
                failure = error
            }
        }
        // Some networks drop UDP/53. Try TCP before failing the lookup.
        for (endpoint in endpoints) {
            try { return tcp(query, endpoint) } catch (error: Exception) { failure = error }
        }
        throw failure ?: IllegalStateException("No DNS endpoint available")
    }

    private fun register(socket: Closeable) = synchronized(sockets) {
        if (closed) { socket.close(); error("Resolver closed") }
        sockets.add(socket)
    }

    private fun validate(query: PacketParser.Query, dns: ByteArray): ByteArray {
        require(dns.size >= 12 && PacketParser.u16(dns, 0) == PacketParser.u16(query.dns, 0))
        require(PacketParser.u16(dns, 2) and 0xf800 == 0x8000)
        val question = PacketParser.question(dns)
        require(question?.name == query.question.name && question.type == query.question.type)
        val rcode = PacketParser.u16(dns, 2) and 15
        require(rcode != 2 && rcode != 5) { "Upstream DNS failed or refused the query" }
        return dns
    }

    private fun udp(query: PacketParser.Query, endpoint: String): ByteArray {
        val socket = DatagramSocket()
        register(socket)
        try {
            check(protectUdp(socket)) { "Cannot protect upstream DNS socket" }
            socket.soTimeout = timeoutMillis
            socket.connect(InetAddress.getByName(endpoint), port)
            // A connected socket accepts replies only from the selected upstream endpoint.
            socket.send(DatagramPacket(query.dns, query.dns.size))
            val buffer = ByteArray(65507)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            return validate(query, buffer.copyOf(reply.length))
        } finally {
            synchronized(sockets) { sockets.remove(socket) }
            socket.close()
        }
    }

    private fun tcp(query: PacketParser.Query, endpoint: String): ByteArray {
        val socket = Socket()
        register(socket)
        try {
            check(protectTcp(socket)) { "Cannot protect upstream DNS socket" }
            socket.soTimeout = timeoutMillis
            socket.connect(InetSocketAddress(endpoint, port), timeoutMillis)
            val output = DataOutputStream(socket.getOutputStream())
            output.writeShort(query.dns.size)
            output.write(query.dns)
            output.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            require(length in 12..65507)
            val dns = ByteArray(length)
            input.readFully(dns)
            validate(query, dns)
            require(PacketParser.u16(dns, 2) and 0x0200 == 0) { "Truncated TCP response" }
            return dns
        } finally {
            synchronized(sockets) { sockets.remove(socket) }
            socket.close()
        }
    }

    override fun close() = synchronized(sockets) {
        closed = true
        sockets.forEach { it.close() }
        sockets.clear()
    }
}
