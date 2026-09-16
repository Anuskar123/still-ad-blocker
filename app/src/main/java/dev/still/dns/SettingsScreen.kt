package dev.still.dns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: ProtectionState, preferences: AppSettings, onSettingsChange: (AppSettings) -> Unit,
    onToggle: () -> Unit, onResetStatistics: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
    }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingSwitch("Protection", state.connection != Connection.Disconnected) { onToggle() }
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Appearance.entries.forEach { appearance ->
                SettingChoice(appearance.name, preferences.appearance == appearance) { onSettingsChange(preferences.copy(appearance = appearance)) }
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) SettingSwitch("Use device colors", preferences.dynamicColors) {
                onSettingsChange(preferences.copy(dynamicColors = it))
            }
            HorizontalDivider()
            Text("DNS resolver", style = MaterialTheme.typography.titleMedium)
            Text("Turn protection off to change the resolver. Queries are sent unencrypted.")
            DnsProvider.entries.forEach { provider ->
                SettingChoice("${provider.name} (${provider.address})", preferences.dnsProvider == provider,
                    state.connection == Connection.Disconnected) { onSettingsChange(preferences.copy(dnsProvider = provider)) }
            }
            HorizontalDivider()
            Text("Filter updates", style = MaterialTheme.typography.titleMedium)
            SettingSwitch("Update enabled lists daily", preferences.autoUpdateFilters) { onSettingsChange(preferences.copy(autoUpdateFilters = it)) }
            SettingSwitch("Notify when lists update", preferences.notifyFilterUpdates) { onSettingsChange(preferences.copy(notifyFilterUpdates = it)) }
            Text("Updates need internet access and run when Android allows background work. Enable lists on the dashboard. Failed updates keep the last valid list.")
            HorizontalDivider()
            Text("Statistics", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onResetStatistics) { Text("Reset session statistics") }
            Text("All-time totals remain on this device until app storage is cleared or Still is uninstalled. They count DNS requests, not individual ads. Data savings are estimates.")
            Text("Quick Settings", style = MaterialTheme.typography.titleMedium)
            Text("Edit your phone's Quick Settings panel and add Still DNS. The tile toggles protection; the first connection needs permission in the app.")
            Text("Limits", style = MaterialTheme.typography.titleMedium)
            Text("DNS filtering cannot reliably remove ads served from the same domains as content, including YouTube video ads. Private DNS and app-specific encrypted DNS may bypass filtering. Android allows only one active VPN.")
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled, Role.RadioButton, onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}
