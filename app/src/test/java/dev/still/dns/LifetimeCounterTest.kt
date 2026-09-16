package dev.still.dns

import org.junit.Assert.*
import org.junit.Test
import kotlin.concurrent.thread

class LifetimeCounterTest {
    @Test fun resumesSavedTotalsAndCountsBothAllowedAndBlockedQueries() {
        var saved = LifetimeTotals(10, 20)
        val counter = LifetimeCounter(saved) { saved = it }
        counter.record(false)
        counter.record(true)
        assertEquals(LifetimeTotals(11, 22), saved)
        val reopened = LifetimeCounter(saved) { saved = it }
        reopened.record(true)
        assertEquals(LifetimeTotals(12, 23), reopened.state.value)
    }

    @Test fun concurrentEventsAreNotLost() {
        var saved = LifetimeTotals()
        val counter = LifetimeCounter(saved) { saved = it }
        val threads = List(4) { thread { repeat(500) { counter.record(it % 2 == 0) } } }
        threads.forEach { it.join() }
        assertEquals(LifetimeTotals(1000, 2000), saved)
        assertEquals(saved, counter.state.value)
    }

    @Test fun sessionResetDoesNotChangeLifetimeTotals() {
        val counter = LifetimeCounter(LifetimeTotals(5, 15)) { }
        ProtectionStore.update { it.copy(blocked = 0, queries = 0) }
        assertEquals(LifetimeTotals(5, 15), counter.state.value)
    }

    @Test fun totalsNeverOverflowToNegative() {
        val counter = LifetimeCounter(LifetimeTotals(Long.MAX_VALUE, Long.MAX_VALUE)) { }
        counter.record(true)
        assertEquals(LifetimeTotals(Long.MAX_VALUE, Long.MAX_VALUE), counter.state.value)
    }
}
