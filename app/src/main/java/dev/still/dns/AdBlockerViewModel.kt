package dev.still.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Connection { Disconnected, Connecting, Connected }
enum class QueryResult { Allowed, Blocked, Failed }
data class RecentQuery(val domain: String, val result: QueryResult)
data class ProtectionState(
    val connection: Connection = Connection.Disconnected,
    val blocked: Long = 0,
    val queries: Long = 0,
    val failures: Long = 0,
    val error: String? = null,
    val lastDnsMillis: Long? = null,
    val consecutiveFailures: Int = 0,
    val recentQueries: List<RecentQuery> = emptyList()
) {
    val connected get() = connection == Connection.Connected
    val savedMb get() = blocked * 50_000.0 / 1_000_000.0
}

/** Process-local repository shared by the service and every activity instance. */
object ProtectionStore {
    private val privacyLock = Any()
    @Volatile var historyEpoch: Long = 0
        private set
    private var privateSessionActive = false
    fun beginPrivateSession() = synchronized(privacyLock) { historyEpoch++; privateSessionActive = true }
    fun endPrivateSession() = synchronized(privacyLock) { historyEpoch++; privateSessionActive = false }
    private val mutable = MutableStateFlow(ProtectionState())
    val state = mutable.asStateFlow()
    fun update(change: (ProtectionState) -> ProtectionState) = mutable.update(change)
    fun record(domain: String, result: QueryResult, keepHistory: Boolean, millis: Long? = null,
        epoch: Long = historyEpoch) = synchronized(privacyLock) { update {
        it.copy(
            lastDnsMillis = millis ?: it.lastDnsMillis,
            consecutiveFailures = when (result) {
                QueryResult.Failed -> it.consecutiveFailures + 1
                QueryResult.Allowed -> 0
                QueryResult.Blocked -> it.consecutiveFailures
            },
            recentQueries = when {
                !keepHistory -> emptyList()
                privateSessionActive || epoch != historyEpoch -> it.recentQueries
                else -> (listOf(RecentQuery(domain, result)) +
                    it.recentQueries.filterNot { entry -> entry.domain == domain }).take(30)
            }
        )
    } }
}

class AdBlockerViewModel : ViewModel() {
    fun loadFilters(repository: FilterRepository) { viewModelScope.launch { repository.load() } }
    fun updateFilters(repository: FilterRepository, selected: Set<String>) {
        viewModelScope.launch { repository.refresh(selected) }
    }
    val state = ProtectionStore.state
    fun permissionDenied() = ProtectionStore.update { it.copy(error = "VPN permission is needed to filter DNS.") }
    fun failure() = ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = "Could not start protection. Try again.") }
    fun dismissError() = ProtectionStore.update { it.copy(error = null) }
}
