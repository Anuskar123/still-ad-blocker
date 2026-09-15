package dev.still.dns

import org.junit.Assert.*
import org.junit.Test

class BrowserNavigationTest {
    @Test fun opensHttpsAddressesAndBareDomains() {
        assertEquals("https://example.com/path?q=test", BrowserNavigation.destination("example.com/path?q=test"))
        assertEquals("https://example.com", BrowserNavigation.destination(" https://example.com "))
    }
    @Test fun turnsSearchTermsIntoEncodedSearches() {
        assertEquals("https://duckduckgo.com/?q=weather+in+Nepal", BrowserNavigation.destination("weather in Nepal"))
    }
    @Test fun rejectsInsecureAndLocalSchemes() {
        for (url in listOf("http://example.com", "file:///etc/passwd", "content://contacts", "javascript:alert(1)",
            "intent://example.com", "data:text/html,Hello", "https://user:password@example.com")) {
            assertNull(url, BrowserNavigation.destination(url))
            assertFalse(url, BrowserNavigation.isWebUrl(url))
        }
    }
    @Test fun emptyAddressDoesNotNavigate() { assertNull(BrowserNavigation.destination(" ")) }
}
