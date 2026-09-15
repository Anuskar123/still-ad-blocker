package dev.still.dns

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilterRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun feed(vararg domains: String) = (
        "# Version: test-1\n# Number of entries: ${domains.size}\n" +
            domains.joinToString("\n") { "*.$it" }
        ).toByteArray()

    @Test fun parsesWildcardsAndMatchesBaseAndSubdomainsOnly() {
        val parsed = DomainListParser.parse(feed("ads.example.com"))
        assertEquals("test-1", parsed.version)
        assertTrue(parsed.matches("ads.example.com"))
        assertTrue(parsed.matches("img.ads.example.com"))
        assertFalse(parsed.matches("notads.example.com"))
        assertFalse(parsed.matches("ads.example.com.attacker.org"))
    }

    @Test fun rejectsEmptyTruncatedHtmlAndBrowserRules() {
        for (data in listOf(
            "", "<html>Error</html>", "# Number of entries: 2\n*.example.com",
            "# Number of entries: 1\n||example.com^", "# Number of entries: 1\n*.https://example.com",
            "# Number of entries: 0\n"
        )) assertThrows(Exception::class.java) { DomainListParser.parse(data.toByteArray()) }
    }

    @Test fun rejectsOversizedDownloads() {
        assertThrows(IllegalArgumentException::class.java) {
            DomainListParser.parse(ByteArray(DomainListParser.MAX_BYTES + 1))
        }
    }

    @Test fun enabledListFiltersAndAllowRuleWins() {
        val library = FilterLibraryState(ready = true, entries = mapOf(
            DownloadableFilter.AdsTrackers to FilterEntry(DomainListParser.parse(feed("example.com")))
        ))
        val settings = AppSettings(enabledSubscriptions = setOf("AdsTrackers"))
        assertTrue(FilterPolicy.blocked("sub.example.com", settings, library))
        assertFalse(FilterPolicy.blocked("sub.example.com", settings.copy(enabledSubscriptions = emptySet()), library))
        assertFalse(FilterPolicy.blocked("sub.example.com", settings.copy(allowedDomains = setOf("example.com")), library))
    }

    @Test fun persistsDownloadsAndLoadsThemWithoutNetwork() = runBlocking {
        val repo = FilterRepository(folder.root, { feed("example.com") }, { 1_700_000_000_000 })
        repo.refresh(setOf("AdsTrackers"))
        assertTrue(repo.state.value.ready)
        assertFalse(repo.state.value.updating)
        val reloaded = FilterRepository(folder.root, { error("Must not download while loading cache") })
        reloaded.load()
        assertTrue(reloaded.state.value.blocked("sub.example.com", setOf("AdsTrackers")))
        assertEquals(1_700_000_000_000, reloaded.state.value.entries[DownloadableFilter.AdsTrackers]!!.downloadedAt)
    }

    @Test fun failedUpdateKeepsLastGoodMemoryAndDiskCopies() = runBlocking {
        var reply = feed("example.com")
        val repo = FilterRepository(folder.root, { reply })
        repo.refresh(setOf("AdsTrackers"))
        reply = "# Number of entries: 2\n*.bad.example".toByteArray()
        repo.refresh(setOf("AdsTrackers"))
        assertTrue(repo.state.value.blocked("example.com", setOf("AdsTrackers")))
        assertNotNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        val reloaded = FilterRepository(folder.root)
        reloaded.load()
        assertTrue(reloaded.state.value.blocked("example.com", setOf("AdsTrackers")))
        assertFalse(reloaded.state.value.blocked("bad.example", setOf("AdsTrackers")))
    }

    @Test fun oneFailedListDoesNotPreventOtherListUpdating() = runBlocking {
        val repo = FilterRepository(folder.root, { url ->
            if (url.endsWith("/light.txt")) error("Offline") else feed("threat.example")
        })
        repo.refresh(setOf("AdsTrackers", "Threats"))
        assertNotNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        assertTrue(repo.state.value.blocked("threat.example", setOf("Threats")))
        assertFalse(repo.state.value.updating)
    }

    @Test fun refreshOnlyDownloadsSelectedLists() = runBlocking {
        val requested = mutableListOf<String>()
        val repo = FilterRepository(folder.root, { requested += it; feed("example.com") })
        repo.refresh(setOf("AdsTrackers"))
        assertEquals(listOf(DownloadableFilter.AdsTrackers.url), requested)
    }

    @Test fun corruptCacheIsReportedAndCanBeReplaced() = runBlocking {
        File(folder.root, "light.txt").writeText("broken")
        val repo = FilterRepository(folder.root, { feed("example.com") })
        repo.load()
        assertNotNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        repo.refresh(setOf("AdsTrackers"))
        assertNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        assertTrue(repo.state.value.blocked("example.com", setOf("AdsTrackers")))
    }

    @Test fun publishedFeedSnapshotsParseAndKeepYoutubeContentAvailable() {
        val directory = System.getenv("STILL_FILTER_FIXTURES")
        assumeTrue("Optional live-source verification: set STILL_FILTER_FIXTURES", directory != null)
        for (filter in DownloadableFilter.entries) {
            val parsed = DomainListParser.parse(Files.readAllBytes(File(directory!!, filter.fileName).toPath()))
            assertTrue(parsed.domains.size > 1000)
            for (domain in listOf("youtube.com", "www.youtube.com", "i.ytimg.com", "rr1.googlevideo.com", "youtubei.googleapis.com")) {
                assertFalse("${filter.name}: $domain", parsed.matches(domain))
            }
            println("${filter.name}: ${parsed.domains.size} domains, version ${parsed.version}")
        }
    }
}
