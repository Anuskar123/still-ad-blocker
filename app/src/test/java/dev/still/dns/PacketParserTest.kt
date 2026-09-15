package dev.still.dns

import org.junit.Assert.*
import org.junit.Test

class PacketParserTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun query(type: Int = 1): PacketParser.Query {
        val dns = hex("1234010000010000000000000361647306676f6f676c6503636f6d0000010001")
        PacketParser.put16(dns, dns.size - 4, type)
        return PacketParser.Query(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 2), 43210, dns, PacketParser.question(dns)!!)
    }
    private fun incoming(): ByteArray {
        val q = query()
        val packet = PacketParser.wrap(q, q.dns)
        PacketParser.put16(packet, 20, q.port)
        PacketParser.put16(packet, 22, 53)
        // IPv4 permits zero UDP checksum, used here to independently construct input.
        PacketParser.put16(packet, 26, 0)
        return packet
    }
    @Test fun knownIpv4ChecksumVector() {
        assertEquals(0xb861, PacketParser.checksum(hex("450000730000400040110000c0a80001c0a800c7")))
    }
    @Test fun oddLengthChecksumVector() { assertEquals(0xfbfd, PacketParser.checksum(hex("010203"))) }
    @Test fun parsesQuestionAndSourcePort() {
        val parsed = PacketParser.parse(incoming())!!
        assertEquals("ads.google.com", parsed.question.name)
        assertEquals(43210, parsed.port)
    }
    @Test fun blockedARecordHasZeroAddressAndCorrectFlags() {
        val dns = PacketParser.response(query())
        assertEquals(0x1234, PacketParser.u16(dns, 0))
        assertEquals(0x8180, PacketParser.u16(dns, 2))
        assertEquals(1, PacketParser.u16(dns, 6))
        assertArrayEquals(ByteArray(4), dns.takeLast(4).toByteArray())
        assertEquals(4, PacketParser.u16(dns, dns.size - 6))
    }
    @Test fun blockedAAAAHasSixteenZeroBytes() {
        val dns = PacketParser.response(query(28))
        assertEquals(16, PacketParser.u16(dns, dns.size - 18))
        assertArrayEquals(ByteArray(16), dns.takeLast(16).toByteArray())
    }
    @Test fun otherTypesReturnNoData() { assertEquals(0, PacketParser.u16(PacketParser.response(query(65)), 6)) }
    @Test fun servfailPreservesQuestionWithoutAnswer() {
        val dns = PacketParser.response(query(), 2)
        assertEquals(2, PacketParser.u16(dns, 2) and 15)
        assertEquals(0, PacketParser.u16(dns, 6))
        assertEquals("ads.google.com", PacketParser.question(dns)!!.name)
    }
    @Test fun responseChecksumsVerifiedWithIndependentAccumulator() {
        for (dns in listOf(PacketParser.response(query()), PacketParser.response(query()) + byteArrayOf(7))) {
            val packet = PacketParser.wrap(query(), dns)
            fun sum(bytes: ByteArray): Int {
                var total = bytes.indices.step(2).sumOf { i ->
                    (bytes[i].toInt() and 255) * 256 + if (i + 1 < bytes.size) bytes[i + 1].toInt() and 255 else 0
                }
                while (total > 65535) total = total % 65536 + total / 65536
                return total
            }
            assertEquals(65535, sum(packet.copyOfRange(0, 20)))
            val pseudo = packet.copyOfRange(12, 20) + byteArrayOf(0, 17) + packet.copyOfRange(24, 26)
            assertEquals(65535, sum(pseudo + packet.copyOfRange(20, packet.size)))
            assertEquals(43210, PacketParser.u16(packet, 22))
        }
    }
    @Test fun rejectsTruncatedPackets() {
        val valid = incoming()
        for (length in 0 until valid.size) assertNull(PacketParser.parse(valid.copyOf(length)))
    }
    @Test fun rejectsFragmentsEvenWithValidHeaderChecksum() {
        val packet = incoming()
        PacketParser.put16(packet, 6, 0x2000)
        PacketParser.put16(packet, 10, 0)
        PacketParser.put16(packet, 10, PacketParser.checksum(packet.copyOfRange(0, 20)))
        assertNull(PacketParser.parse(packet))
    }
    @Test fun rejectsBadUdpChecksum() {
        val packet = incoming()
        PacketParser.put16(packet, 26, 123)
        assertNull(PacketParser.parse(packet))
    }
    @Test fun rejectsCompressionCycle() {
        assertNull(PacketParser.question(hex("123401000001000000000000c00c00010001")))
    }
    @Test fun resolvesCompressedQuestionSafely() {
        val dns = hex("123401000001000000000000c012000100010361647306676f6f676c6503636f6d00")
        assertEquals("ads.google.com", PacketParser.question(dns)!!.name)
        assertArrayEquals(query().question.wire, PacketParser.question(dns)!!.wire)
    }
    @Test fun matchingRespectsLabelBoundary() {
        val rules = setOf("doubleclick.net")
        assertTrue(PacketParser.blocked("x.doubleclick.net", rules))
        assertTrue(PacketParser.blocked("doubleclick.net", rules))
        assertFalse(PacketParser.blocked("notdoubleclick.net", rules))
        assertFalse(PacketParser.blocked("doubleclick.net.example.org", rules))
    }
    @Test fun repliesReverseDifferentAddresses() {
        val q = query().copy(source = byteArrayOf(10, 0, 0, 9), destination = byteArrayOf(10, 0, 0, 2))
        val packet = PacketParser.wrap(q, PacketParser.response(q))
        assertArrayEquals(q.destination, packet.copyOfRange(12, 16))
        assertArrayEquals(q.source, packet.copyOfRange(16, 20))
    }
    @Test fun acceptsValidNonzeroUdpChecksum() {
        val q = query()
        // Swapping UDP ports does not alter the one's-complement sum.
        val packet = PacketParser.wrap(q, q.dns)
        PacketParser.put16(packet, 20, q.port)
        PacketParser.put16(packet, 22, 53)
        assertNotNull(PacketParser.parse(packet))
    }
}
