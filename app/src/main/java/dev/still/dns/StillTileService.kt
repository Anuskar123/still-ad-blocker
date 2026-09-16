package dev.still.dns

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class StillTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observer?.cancel()
        observer = scope.launch { ProtectionStore.state.collect { render(it) } }
    }

    private fun render(state: ProtectionState) {
        qsTile?.apply {
            label = "Still DNS"
            this.state = if (state.connection == Connection.Disconnected) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            icon = Icon.createWithResource(this@StillTileService,
                if (state.connected) R.drawable.ic_shield else R.drawable.ic_shield_outline)
            contentDescription = when (state.connection) {
                Connection.Connected -> "Still DNS on. Tap to turn off."
                Connection.Connecting -> "Still DNS connecting. Tap to cancel."
                Connection.Disconnected -> "Still DNS off. Tap to turn on."
            }
            if (Build.VERSION.SDK_INT >= 29) subtitle = when (state.connection) {
                Connection.Connected -> "On"
                Connection.Connecting -> "Connecting"
                Connection.Disconnected -> "Off"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        try {
            if (ProtectionStore.state.value.connection != Connection.Disconnected) {
                startService(Intent(this, AdBlockerService::class.java).setAction(AdBlockerService.STOP))
            } else if (!getSharedPreferences("onboarding", MODE_PRIVATE).getBoolean("vpnDisclosureAccepted", false) || VpnService.prepare(this) != null) {
                openDashboard()
            } else {
                ProtectionStore.update { it.copy(connection = Connection.Connecting, error = null) }
                ContextCompat.startForegroundService(this, Intent(this, AdBlockerService::class.java))
            }
        } catch (_: Exception) {
            ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = "Open Still to start protection.") }
            openDashboard()
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is required below API 34; guarded below.
    private fun openDashboard() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 3, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else startActivityAndCollapse(intent)
    }

    override fun onStopListening() { observer?.cancel(); super.onStopListening() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
