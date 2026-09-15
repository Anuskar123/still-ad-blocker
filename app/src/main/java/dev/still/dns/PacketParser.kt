package dev.still.dns

import java.io.ByteArrayOutputStream
import java.util.Locale

/** Strict IPv4/UDP DNS codec. Malformed and fragmented packets are rejected. */
object PacketParser {
    data class Question(val name: String, val type: Int, val wire: ByteArray)
    data class Query(val source: ByteArray, val destination: ByteArray, val port: Int,
                     val dns: ByteArray, val question: Question)

    fun u16(b: ByteArray, p: Int): Int = ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
    fun put16(b: ByteArray, p: Int, n: Int) { b[p] = (n ushr 8).toByte(); b[p + 1] = n.toByte() }

    fun parse(packet: ByteArray): Query? = runCatching {
        require(packet.size >= 28 && (packet[0].toInt() ushr 4 and 15) == 4)
        val ihl = (packet[0].toInt() and 15) * 4
        val total = u16(packet, 2)
        require(ihl >= 20 && total <= packet.size && total >= ihl + 8)
        require(packet[9].toInt() == 17 && u16(packet, 6) and 0x3fff == 0)
        require(checksum(packet.copyOfRange(0, ihl)) == 0)
        val len = u16(packet, ihl + 4)
        require(len >= 20 && ihl + len == total && u16(packet, ihl + 2) == 53)
        val src = packet.copyOfRange(12, 16)
        val dst = packet.copyOfRange(16, 20)
        require(u16(packet, ihl) != 0)
        val udp = packet.copyOfRange(ihl, total)
        if (u16(udp, 6) != 0) require(checksum(pseudo(src, dst, len) + udp) == 0)
        val dns = udp.copyOfRange(8, udp.size)
        require(u16(dns, 2) and 0xf800 == 0 && u16(dns, 4) == 1)
        Query(src, dst, u16(packet, ihl), dns, question(dns) ?: error("Bad DNS question"))
    }.getOrNull()

    fun question(dns: ByteArray): Question? = runCatching {
        require(dns.size >= 17 && u16(dns, 4) == 1)
        var cursor = 12
        var end = -1
        var expanded = 1
        val seen = HashSet<Int>()
        val labels = mutableListOf<String>()
        while (true) {
            require(cursor in dns.indices && seen.add(cursor))
            val length = dns[cursor].toInt() and 255
            if (length and 0xc0 == 0xc0) {
                require(cursor + 1 < dns.size)
                if (end < 0) end = cursor + 2
                cursor = ((length and 63) shl 8) or (dns[cursor + 1].toInt() and 255)
                continue
            }
            require(length <= 63)
            cursor++
            if (length == 0) { if (end < 0) end = cursor; break }
            require(cursor + length <= dns.size)
            val label = dns.copyOfRange(cursor, cursor + length)
            require(label.all { (it.toInt() and 255) in 33..126 && it != '.'.code.toByte() })
            labels += label.toString(Charsets.US_ASCII)
            expanded += length + 1
            require(expanded <= 255)
            cursor += length
        }
        require(end + 4 <= dns.size && u16(dns, end + 2) == 1)
        val wire = ByteArrayOutputStream()
        labels.forEach { wire.write(it.length); wire.write(it.toByteArray(Charsets.US_ASCII)) }
        wire.write(0)
        wire.write(dns, end, 4)
        Question(labels.joinToString(".").lowercase(Locale.ROOT), u16(dns, end), wire.toByteArray())
    }.getOrNull()

    fun blocked(name: String, rules: Set<String>): Boolean =
        rules.any { name == it || name.endsWith(".$it") }

    /** A -> 0.0.0.0, AAAA -> ::, other record types -> NOERROR/NODATA. */
    fun response(query: Query, error: Int = 0): ByteArray {
        val size = if (error != 0) 0 else when (query.question.type) { 1 -> 4; 28 -> 16; else -> 0 }
        val header = ByteArray(12)
        put16(header, 0, u16(query.dns, 0))
        put16(header, 2, 0x8080 or (u16(query.dns, 2) and 0x0100) or error)
        put16(header, 4, 1)
        put16(header, 6, if (size > 0) 1 else 0)
        val answer = if (size > 0) ByteArray(12 + size).also {
            put16(it, 0, 0xc00c); put16(it, 2, query.question.type); put16(it, 4, 1)
            put16(it, 8, 60); put16(it, 10, size)
        } else byteArrayOf()
        return header + query.question.wire + answer
    }

    fun wrap(query: Query, dns: ByteArray): ByteArray {
        require(dns.size <= 65507)
        val packet = ByteArray(28 + dns.size)
        packet[0] = 0x45; put16(packet, 2, packet.size)
        packet[8] = 64; packet[9] = 17
        query.destination.copyInto(packet, 12); query.source.copyInto(packet, 16)
        put16(packet, 20, 53); put16(packet, 22, query.port); put16(packet, 24, 8 + dns.size)
        dns.copyInto(packet, 28)
        val udpChecksum = checksum(pseudo(query.destination, query.source, 8 + dns.size) + packet.copyOfRange(20, packet.size))
        put16(packet, 26, if (udpChecksum == 0) 0xffff else udpChecksum)
        put16(packet, 10, checksum(packet.copyOfRange(0, 20)))
        return packet
    }

    private fun pseudo(src: ByteArray, dst: ByteArray, length: Int): ByteArray =
        (src + dst + byteArrayOf(0, 17, 0, 0)).also { put16(it, 10, length) }

    /** One's-complement sum, network byte order, zero-padded odd final octet. */
    fun checksum(bytes: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < bytes.size) { sum += u16(bytes, i); i += 2 }
        if (i < bytes.size) sum += (bytes[i].toInt() and 255) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt().inv() and 0xffff
    }
}
