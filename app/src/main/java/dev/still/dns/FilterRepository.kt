package dev.still.dns

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.net.ssl.SSLException
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class DownloadableFilter(val title: String, val detail: String, val fileName: String, val mainList: Boolean = false) {
    AdsTrackers("Multi Light", "Relaxed ad and tracker filtering. Lowest risk of breaking sites.", "light.txt", true),
    ProMini("Multi Pro Mini", "Compact, broader ad and tracker coverage. Some services may need allow rules.", "pro.mini.txt", true),
    ProPlusMini("Multi Pro++ Mini", "More aggressive tracking protection. Higher risk of breaking app and website features.", "pro.plus.mini.txt", true),
    Threats("Threat domains", "HaGeZi TIF Mini: phishing and malware domains. Not a guarantee of safety.", "tif.mini.txt"),
    FakeSites("Fake and scam sites", "Domains associated with fake shops, scams and deceptive sites.", "fake.txt"),
    Popups("Pop-up ad domains", "Blocks known pop-up advertising domains, not page elements or all pop-up windows.", "popupads.txt"),
    Gambling("Gambling domains", "Gambling Mini: optionally restrict known gambling sites. Not comprehensive parental control.", "gambling.mini.txt"),
    AdultContent("Adult domains", "Optionally restrict listed adult sites. May block legitimate content and can be bypassed.", "nsfw.txt");

    val url get() = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/$fileName"

    fun select(enabled: Set<String>, selected: Boolean): Set<String> {
        if (!selected) return enabled - name
        val remaining = if (mainList) enabled - entries.filter { it.mainList }.map { it.name }.toSet() else enabled
        return remaining + name
    }
}

data class DomainList(val domains: Set<String>, val version: String) {
    fun matches(name: String): Boolean {
        var candidate = name
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}

object DomainListParser {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_RULES = 300_000

    /** Only the advertised wildcard-domain format is accepted, never browser rules. */
    fun parse(bytes: ByteArray): DomainList {
        require(bytes.size in 1..MAX_BYTES) { "Filter is empty or too large" }
        val domains = HashSet<String>()
        var expected: Int? = null
        var version = "Unknown"
        var ruleCount = 0
        bytes.toString(Charsets.UTF_8).lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("# Number of entries:") -> expected = line.substringAfter(':').trim().toIntOrNull()
                line.startsWith("# Version:") -> version = line.substringAfter(':').trim().take(80)
                line.isEmpty() || line.startsWith('#') -> Unit
                else -> {
                    require(line.startsWith("*.")) { "Unexpected filter format" }
                    val domain = FilterPolicy.normalizeDomain(line.substring(2)) ?: error("Invalid filter domain")
                    domains.add(domain)
                    ruleCount++
                    require(ruleCount <= MAX_RULES) { "Too many filter rules" }
                }
            }
        }
        require(expected != null && expected == ruleCount && domains.isNotEmpty()) { "Incomplete or empty filter download" }
        return DomainList(domains, version)
    }
}

data class FilterEntry(val list: DomainList? = null, val downloadedAt: Long = 0, val error: String? = null)
data class FilterLibraryState(
    val ready: Boolean = false,
    val updating: Boolean = false,
    val activeFilter: DownloadableFilter? = null,
    val completedDownloads: Int = 0,
    val totalDownloads: Int = 0,
    val entries: Map<DownloadableFilter, FilterEntry> = emptyMap()
) {
    fun blocked(domain: String, enabled: Set<String>): Boolean = DownloadableFilter.entries.any {
        it.name in enabled && entries[it]?.list?.matches(domain) == true
    }
}

/** No browsing data is sent when downloading. Failed updates keep the last valid copy. */
class FilterRepository(
    private val directory: File,
    private val fetch: (String) -> ByteArray = ::downloadFilter,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(FilterLibraryState())
    val state = mutable.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mutable.value.ready) return@withLock
            directory.mkdirs()
            val entries = DownloadableFilter.entries.associateWith { filter ->
                val file = File(directory, filter.fileName)
                if (!file.exists()) FilterEntry() else try {
                    require(file.length() <= DomainListParser.MAX_BYTES)
                    FilterEntry(DomainListParser.parse(file.readBytes()), file.lastModified())
                } catch (_: Exception) { FilterEntry(error = "Saved filter could not be loaded. Download it again.") }
            }
            mutable.value = FilterLibraryState(ready = true, entries = entries)
        }
    }

    suspend fun refresh(selected: Set<String>) {
        load()
        withContext(Dispatchers.IO) {
            mutex.lock()
            try {
                val filters = DownloadableFilter.entries.filter { it.name in selected }
                mutable.value = mutable.value.copy(updating = true, activeFilter = filters.firstOrNull(),
                    completedDownloads = 0, totalDownloads = filters.size)
                filters.forEachIndexed { index, filter ->
                    mutable.value = mutable.value.copy(activeFilter = filter, completedDownloads = index)
                    val old = mutable.value.entries[filter] ?: FilterEntry()
                    val updated = try {
                        val bytes = fetch(filter.url)
                        val list = DomainListParser.parse(bytes)
                        val target = File(directory, filter.fileName)
                        val temp = File(directory, "${filter.fileName}.tmp")
                        try {
                            temp.outputStream().use { it.write(bytes) }
                            temp.setLastModified(now())
                            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        } finally { temp.delete() }
                        FilterEntry(list, target.lastModified())
                    } catch (error: Exception) {
                        old.copy(error = downloadErrorMessage(error, old.list != null))
                    }
                    mutable.value = mutable.value.copy(entries = mutable.value.entries + (filter to updated),
                        completedDownloads = index + 1)
                }
            } finally {
                mutable.value = mutable.value.copy(updating = false, activeFilter = null)
                mutex.unlock()
            }
        }
    }
}

private fun downloadErrorMessage(error: Exception, hasSavedCopy: Boolean): String {
    val reason = when (error) {
        is UnknownHostException -> "No internet connection or GitHub could not be reached."
        is SocketTimeoutException -> "The connection was too slow and timed out."
        is SSLException -> "A secure connection to GitHub could not be established."
        else -> error.message?.takeIf { it.isNotBlank() }?.take(120) ?: "The download could not be completed."
    }
    return if (hasSavedCopy) "$reason The previous filter is still active." else "$reason Try again when the connection is stable."
}

private fun downloadFilter(address: String): ByteArray {
    val connection = URL(address).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "text/plain")
        check(connection.responseCode == 200) { "Filter server unavailable" }
        check(connection.contentLengthLong <= DomainListParser.MAX_BYTES) { "Filter too large" }
        val started = System.nanoTime()
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                check((System.nanoTime() - started) / 1_000_000 < 60_000) { "Filter download timed out" }
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= DomainListParser.MAX_BYTES) { "Filter too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } finally { connection.disconnect() }
}
