package dev.still.dns

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    private var showVpnDisclosure by mutableStateOf(false)
    private val viewModel: AdBlockerViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startProtection() else viewModel.permissionDenied()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!getSharedPreferences("onboarding", MODE_PRIVATE).getBoolean("complete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        FilterUpdateWorker.schedule(this, AppSettings.load(this))
        val filterRepository = FilterLibrary.get(this)
        viewModel.loadFilters(filterRepository)
        setContent {
            var settings by remember { mutableStateOf(AppSettings.load(this)) }
            StillTheme(settings) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val filterLibrary by filterRepository.state.collectAsStateWithLifecycle()
                val lifetime by LifetimeStatistics.get(this).state.collectAsStateWithLifecycle()
                DashboardScreen(state, onToggle = {
                    if (state.connection != Connection.Disconnected) {
                        startService(Intent(this, AdBlockerService::class.java).setAction(AdBlockerService.STOP))
                    }
                    else if (state.connection == Connection.Disconnected) {
                        if (!getSharedPreferences("onboarding", MODE_PRIVATE).getBoolean("vpnDisclosureAccepted", false)) showVpnDisclosure = true
                        else requestProtection()
                    }
                }, onDismissError = viewModel::dismissError, preferences = settings,
                    onSettingsChange = {
                        settings = it
                        it.save(this)
                        FilterUpdateWorker.schedule(this, it)
                        if (!it.keepRecentDomains) ProtectionStore.update { current -> current.copy(recentQueries = emptyList()) }
                        if (state.connection != Connection.Disconnected) {
                            startService(Intent(this, AdBlockerService::class.java).setAction(AdBlockerService.RELOAD))
                        }
                    },
                    onResetStatistics = { ProtectionStore.update { it.copy(blocked = 0, queries = 0, failures = 0, lastDnsMillis = null, consecutiveFailures = 0) } },
                    onClearHistory = { ProtectionStore.update { it.copy(recentQueries = emptyList()) } },
                    lifetime = lifetime,
                    filterLibrary = filterLibrary,
                    onUpdateFilters = { viewModel.updateFilters(filterRepository, settings.enabledSubscriptions) },
                    onOpenPrivateBrowser = { startActivity(Intent(this, PrivateBrowserActivity::class.java)) },
                    onOpenOtherBrowser = {
                        try {
                            val browse = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                            startActivity(Intent.createChooser(browse, "Choose a browser"))
                        } catch (_: android.content.ActivityNotFoundException) {
                            ProtectionStore.update { it.copy(error = "No browser is available to open this link.") }
                        }
                    })
                if (showVpnDisclosure) androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showVpnDisclosure = false },
                    title = { androidx.compose.material3.Text("Allow local DNS filtering?") },
                    text = { androidx.compose.material3.Text("Still uses a local VPN to inspect DNS domain names and block matching requests. Allowed names are sent unencrypted to your selected DNS provider. Still does not send domain logs to its developer, hide your IP address, or encrypt browsing traffic. You can turn it off at any time.") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = {
                        getSharedPreferences("onboarding", MODE_PRIVATE).edit().putBoolean("vpnDisclosureAccepted", true).apply()
                        showVpnDisclosure = false
                        requestProtection()
                    }) { androidx.compose.material3.Text("Agree and continue") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { showVpnDisclosure = false }) { androidx.compose.material3.Text("Not now") } }
                )
            }
        }
    }

    private fun requestProtection() {
        val permission = VpnService.prepare(this)
        if (permission != null) vpnPermission.launch(permission) else startProtection()
    }

    private fun startProtection() {
        try {
            ProtectionStore.update { it.copy(connection = Connection.Connecting, error = null) }
            ContextCompat.startForegroundService(this, Intent(this, AdBlockerService::class.java))
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (_: Exception) { viewModel.failure() }
    }
}
