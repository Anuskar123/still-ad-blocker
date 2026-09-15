package dev.still.dns

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ProtectionStoreTest {
    @Before fun reset() { ProtectionStore.update { ProtectionState() } }

    @Test fun privateSessionDoesNotLogDomainsOrLateReplies() {
        ProtectionStore.record("before.example", QueryResult.Allowed, true)
        val before = ProtectionStore.historyEpoch
        ProtectionStore.beginPrivateSession()
        val during = ProtectionStore.historyEpoch
        ProtectionStore.record("private.example", QueryResult.Allowed, true, epoch = during)
        ProtectionStore.endPrivateSession()
        ProtectionStore.record("late-private.example", QueryResult.Allowed, true, epoch = during)
        ProtectionStore.record("late-before.example", QueryResult.Allowed, true, epoch = before)
        assertEquals(listOf("before.example"), ProtectionStore.state.value.recentQueries.map { it.domain })
        ProtectionStore.record("after.example", QueryResult.Allowed, true)
        assertEquals("after.example", ProtectionStore.state.value.recentQueries.first().domain)
    }

    @Test fun disabledHistoryDoesNotKeepDomains() {
        ProtectionStore.record("example.com", QueryResult.Allowed, false, 25)
        assertTrue(ProtectionStore.state.value.recentQueries.isEmpty())
        assertEquals(25L, ProtectionStore.state.value.lastDnsMillis)
    }

    @Test fun historyIsBoundedUniqueAndNewestFirst() {
        repeat(40) { ProtectionStore.record("site$it.example", QueryResult.Blocked, true) }
        assertEquals(30, ProtectionStore.state.value.recentQueries.size)
        assertEquals("site39.example", ProtectionStore.state.value.recentQueries.first().domain)
        ProtectionStore.record("site20.example", QueryResult.Allowed, true, 10)
        assertEquals(30, ProtectionStore.state.value.recentQueries.size)
        assertEquals(RecentQuery("site20.example", QueryResult.Allowed), ProtectionStore.state.value.recentQueries.first())
    }

    @Test fun disablingHistoryClearsExistingEntries() {
        ProtectionStore.record("example.com", QueryResult.Blocked, true)
        ProtectionStore.record("example.org", QueryResult.Allowed, false)
        assertTrue(ProtectionStore.state.value.recentQueries.isEmpty())
    }

    @Test fun blockedQueryDoesNotHideResolverFailuresButSuccessClearsThem() {
        ProtectionStore.record("example.com", QueryResult.Failed, false)
        ProtectionStore.record("example.org", QueryResult.Blocked, false)
        assertEquals(1, ProtectionStore.state.value.consecutiveFailures)
        ProtectionStore.record("example.net", QueryResult.Allowed, false, 40)
        assertEquals(0, ProtectionStore.state.value.consecutiveFailures)
    }
}
