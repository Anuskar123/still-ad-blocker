package dev.still.dns

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class UpstreamDnsResolverTest {
    private fun query(): PacketParser.Query {
        val dns = "1234010000010000000000000377777707796f757475626503636f6d0000010001"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return PacketParser.Query(byteArrayOf(10, 0, 0, 3), byteArrayOf(10, 0, 0, 2),
            12345, dns, PacketParser.question(dns)!!)
    }

    @Test fun usesBackupWhenPrimaryDoesNotReply() {
        val loopback = InetAddress.getByName("127.0.0.1")
        DatagramSocket(0, loopback).use { server ->
            DatagramSocket(server.localPort, InetAddress.getByName("127.0.0.2")).use {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val task = executor.submit {
                        server.soTimeout = 3000
                        val packet = DatagramPacket(ByteArray(512), 512)
                        server.receive(packet)
                        val answer = PacketParser.response(query())
                        server.send(DatagramPacket(answer, answer.size, packet.socketAddress))
                    }
                    UpstreamDnsResolver("127.0.0.2", { true }, { true }, server.localPort,
                        200, "127.0.0.1").use { resolver ->
                        assertArrayEquals(PacketParser.response(query()), resolver.resolve(query()))
                    }
                    task.get(3, TimeUnit.SECONDS)
                } finally { executor.shutdownNow() }
            }
        }
    }

    private fun checkTcpFallback(truncated: Boolean) {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 1, loopback).use { tcp ->
            DatagramSocket(tcp.localPort, loopback).use { udp ->
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val task = executor.submit {
                        if (truncated) {
                            udp.soTimeout = 3000
                            val packet = DatagramPacket(ByteArray(512), 512)
                            udp.receive(packet)
                            val answer = PacketParser.response(query())
                            PacketParser.put16(answer, 2, 0x8380)
                            udp.send(DatagramPacket(answer, answer.size, packet.socketAddress))
                        }
                        tcp.soTimeout = 3000
                        tcp.accept().use { socket ->
                            socket.soTimeout = 3000
                            val input = DataInputStream(socket.getInputStream())
                            val request = ByteArray(input.readUnsignedShort())
                            input.readFully(request)
                            assertArrayEquals(query().dns, request)
                            val answer = PacketParser.response(query())
                            val output = DataOutputStream(socket.getOutputStream())
                            output.writeShort(answer.size)
                            output.write(answer)
                            output.flush()
                        }
                    }
                    UpstreamDnsResolver("127.0.0.1", { true }, { true }, tcp.localPort, 500).use {
                        assertArrayEquals(PacketParser.response(query()), it.resolve(query()))
                    }
                    task.get(3, TimeUnit.SECONDS)
                } finally { executor.shutdownNow() }
            }
        }
    }

    @Test fun retriesOverTcpWhenUdpTimesOut() = checkTcpFallback(false)
    @Test fun completesTruncatedUdpReplyOverTcp() = checkTcpFallback(true)

    @Test fun preservesSelectedProvider() {
        assertEquals("8.8.4.4", UpstreamDnsResolver.backupAddress("8.8.8.8"))
        assertEquals("1.0.0.1", UpstreamDnsResolver.backupAddress("1.1.1.1"))
        assertEquals("149.112.112.112", UpstreamDnsResolver.backupAddress("9.9.9.9"))
    }

    @Test fun closedResolverRejectsRequests() {
        val resolver = UpstreamDnsResolver("127.0.0.1", { true }, { true })
        resolver.close()
        assertThrows(IllegalStateException::class.java) { resolver.resolve(query()) }
    }
}
