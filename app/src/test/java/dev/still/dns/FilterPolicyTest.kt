package dev.still.dns

import org.junit.Assert.*
import org.junit.Test

class FilterPolicyTest {
    @Test fun levelsIncreaseCoverageWithoutChangingBasic() {
        val basic = AppSettings()
        assertTrue(FilterPolicy.blocked("ads.google.com", basic))
        assertFalse(FilterPolicy.blocked("google-analytics.com", basic))
        assertTrue(FilterPolicy.blocked("google-analytics.com", basic.copy(protectionLevel = ProtectionLevel.Balanced)))
        assertFalse(FilterPolicy.blocked("crashlytics.com", basic.copy(protectionLevel = ProtectionLevel.Balanced)))
        assertTrue(FilterPolicy.blocked("crashlytics.com", basic.copy(protectionLevel = ProtectionLevel.Strict)))
    }

    @Test fun allowRulesOverrideBuiltInAndCustomBlocksIncludingSubdomains() {
        val settings = AppSettings(protectionLevel = ProtectionLevel.Strict,
            allowedDomains = setOf("doubleclick.net", "example.com"),
            blockedDomains = setOf("example.com", "blocked.example.com"))
        assertFalse(FilterPolicy.blocked("x.doubleclick.net", settings))
        assertFalse(FilterPolicy.blocked("blocked.example.com", settings))
        assertTrue(FilterPolicy.blocked("ads.google.com", settings))
    }

    @Test fun customRulesRespectLabelBoundaries() {
        val settings = AppSettings(blockedDomains = setOf("example.com"))
        assertTrue(FilterPolicy.blocked("x.example.com", settings))
        assertFalse(FilterPolicy.blocked("notexample.com", settings))
        assertFalse(FilterPolicy.blocked("example.com.other.org", settings))
    }

    @Test fun youtubeContentDomainsRemainAllowedAtEveryLevel() {
        for (level in ProtectionLevel.entries) {
            for (domain in listOf("youtube.com", "www.youtube.com", "i.ytimg.com", "rr1.googlevideo.com", "youtubei.googleapis.com")) {
                assertFalse("$level: $domain", FilterPolicy.blocked(domain, AppSettings(protectionLevel = level)))
            }
        }
    }

    @Test fun normalizesCaseTrailingDotAndInternationalDomains() {
        assertEquals("example.com", FilterPolicy.normalizeDomain("  EXAMPLE.COM. "))
        assertEquals("xn--bcher-kva.de", FilterPolicy.normalizeDomain("b\u00fccher.de"))
    }

    @Test fun rejectsUrlsWildcardsIpAddressesAndInvalidLabels() {
        for (input in listOf("", "localhost", "https://example.com", "example.com/path", "*.example.com",
            "1.1.1.1", "a..com", "-example.com", "example-.com", "a b.com", "a".repeat(64) + ".com")) {
            assertNull(input, FilterPolicy.normalizeDomain(input))
        }
    }
}
