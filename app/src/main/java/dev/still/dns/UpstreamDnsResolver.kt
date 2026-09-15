package dev.still.dns

import android.net.VpnService
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UpstreamDnsResolver(private val service: VpnService) : Closeable {
    private val sockets = mutableSetOf<DatagramSocket>()
    private var closed = false

    fun resolve(query: PacketParser.Query): ByteArray {
        val socket = DatagramSocket()
        synchronized(sockets) {
            if (closed) { socket.close(); error("Resolver closed") }
            sockets.add(socket)
        }
        try {
            check(service.protect(socket)) { "Cannot protect upstream DNS socket" }
            socket.soTimeout = 3000
            socket.connect(InetAddress.getByName("8.8.8.8"), 53)
            // A connected socket accepts replies only from the selected upstream endpoint.
            socket.send(DatagramPacket(query.dns, query.dns.size))
            val buffer = ByteArray(65507)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            val dns = buffer.copyOf(reply.length)
            require(dns.size >= 12 && PacketParser.u16(dns, 0) == PacketParser.u16(query.dns, 0))
            require(PacketParser.u16(dns, 2) and 0xf800 == 0x8000)
            val question = PacketParser.question(dns)
            require(question?.name == query.question.name && question.type == query.question.type)
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
