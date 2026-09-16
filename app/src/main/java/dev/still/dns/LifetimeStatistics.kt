package dev.still.dns

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LifetimeTotals(val blocked: Long = 0, val queries: Long = 0)

/** A single writer prevents lost updates and stale settings from resetting totals. */
class LifetimeCounter(initial: LifetimeTotals, private val persist: (LifetimeTotals) -> Unit) {
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()

    @Synchronized fun record(blocked: Boolean) {
        val old = mutable.value
        val next = LifetimeTotals(
            if (blocked && old.blocked < Long.MAX_VALUE) old.blocked + 1 else old.blocked,
            if (old.queries < Long.MAX_VALUE) old.queries + 1 else old.queries
        )
        persist(next)
        mutable.value = next
    }
}

object LifetimeStatistics {
    @Volatile private var instance: LifetimeCounter? = null
    fun get(context: Context): LifetimeCounter = instance ?: synchronized(this) {
        instance ?: run {
            val prefs = context.applicationContext.getSharedPreferences("lifetime-statistics", Context.MODE_PRIVATE)
            LifetimeCounter(LifetimeTotals(prefs.getLong("blocked", 0), prefs.getLong("queries", 0))) {
                prefs.edit().putLong("blocked", it.blocked).putLong("queries", it.queries).apply()
            }.also { instance = it }
        }
    }
}
