package dev.still.dns

import org.junit.Assert.*
import org.junit.Test

class FilterCatalogTest {
    @Test fun selectingMainTierReplacesPreviousTierAndPreservesExtras() {
        val enabled = setOf("AdsTrackers", "Threats", "Gambling")
        assertEquals(setOf("ProMini", "Threats", "Gambling"), DownloadableFilter.ProMini.select(enabled, true))
        assertEquals(setOf("Threats", "Gambling"), DownloadableFilter.AdsTrackers.select(enabled, false))
        assertEquals(enabled + "FakeSites", DownloadableFilter.FakeSites.select(enabled, true))
    }

    @Test fun checkerHonorsAllowPrecedenceAndShowsMatchingSubscription() {
        val library = FilterLibraryState(ready = true, entries = mapOf(
            DownloadableFilter.FakeSites to FilterEntry(DomainList(setOf("example.com"), "test"))))
        val settings = AppSettings(enabledSubscriptions = setOf("FakeSites"))
        assertEquals("Blocked by: Fake and scam sites", FilterPolicy.explain("sub.example.com", settings, library))
        assertTrue(FilterPolicy.explain("sub.example.com", settings.copy(allowedDomains = setOf("example.com")), library).startsWith("Allowed"))
        assertTrue(FilterPolicy.explain("notexample.com", settings, library).startsWith("No block rule"))
        assertTrue(FilterPolicy.blocked("sub.example.com", settings, library))
        assertFalse(FilterPolicy.blocked("sub.example.com", settings.copy(enabledSubscriptions = emptySet()), library))
    }

    @Test fun checkerDistinguishesMissingDownloadsFromNoMatch() {
        assertTrue(FilterPolicy.explain("example.com", AppSettings(enabledSubscriptions = setOf("Threats")), FilterLibraryState()).contains("not been downloaded"))
        assertTrue(FilterPolicy.explain("example.com", AppSettings(blockedDomains = setOf("example.com")), FilterLibraryState()).contains("custom block"))
        assertTrue(FilterPolicy.explain("ads.google.com", AppSettings(), FilterLibraryState()).contains("built-in Basic"))
    }
}
