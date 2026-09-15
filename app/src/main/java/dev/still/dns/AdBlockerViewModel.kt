package dev.still.dns

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Connection { Disconnected, Connecting, Connected }
data class ProtectionState(
    val connection: Connection = Connection.Disconnected,
    val blocked: Long = 0,
    val queries: Long = 0,
    val failures: Long = 0,
    val error: String? = null
) {
    val connected get() = connection == Connection.Connected
    val savedMb get() = blocked * 50_000.0 / 1_000_000.0
}

/** Process-local repository shared by the service and every activity instance. */
object ProtectionStore {
    private val mutable = MutableStateFlow(ProtectionState())
    val state = mutable.asStateFlow()
    fun update(change: (ProtectionState) -> ProtectionState) = mutable.update(change)
}

class AdBlockerViewModel : ViewModel() {
    val state = ProtectionStore.state
    fun permissionDenied() = ProtectionStore.update { it.copy(error = "VPN permission is needed to filter DNS.") }
    fun failure() = ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = "Could not start protection. Try again.") }
    fun dismissError() = ProtectionStore.update { it.copy(error = null) }
}
