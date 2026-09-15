package dev.still.dns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ProtectionControls(state: ProtectionState, preferences: AppSettings,
    onSettingsChange: (AppSettings) -> Unit, onClearHistory: () -> Unit) {
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Protection level", style = MaterialTheme.typography.titleMedium)
            ProtectionLevel.entries.forEach { level ->
                Row(Modifier.fillMaxWidth().selectable(
                    selected = preferences.protectionLevel == level, role = Role.RadioButton,
                    onClick = { onSettingsChange(preferences.copy(protectionLevel = level)) }
                ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = preferences.protectionLevel == level, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(level.title, style = MaterialTheme.typography.titleSmall)
                        Text(level.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("${FilterPolicy.rules(preferences.protectionLevel).size} built-in rules. Changes apply to new DNS requests; cached answers may last longer.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { rulesOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Manage allowed and blocked domains")
            }
            Text("${preferences.allowedDomains.size} allowed / ${preferences.blockedDomains.size} custom blocked", style = MaterialTheme.typography.labelMedium)
        }
    }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connection health", style = MaterialTheme.typography.titleMedium)
            Text(when {
                !state.connected -> "Protection is off"
                state.consecutiveFailures > 0 -> "Recent DNS lookups failed"
                state.lastDnsMillis == null -> "Waiting for an allowed DNS lookup"
                else -> "Last DNS lookup succeeded"
            })
            state.lastDnsMillis?.let { Text("Last successful lookup: $it ms", style = MaterialTheme.typography.bodySmall) }
            Text("${state.failures} failed lookups this app session. DNS response time is not your internet speed.", style = MaterialTheme.typography.bodySmall)
            if (state.connected && state.consecutiveFailures > 0) {
                Text("Check Wi-Fi or mobile data. If failures continue, turn protection off and choose a different DNS resolver in Settings.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent domains", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = preferences.keepRecentDomains,
                    onCheckedChange = { onSettingsChange(preferences.copy(keepRecentDomains = it)) },
                    modifier = Modifier.semantics { contentDescription = "Keep recent domains in memory" })
            }
            Text("Optional: keep the latest 30 unique domains in memory on this device. Turning this off clears the list. Domain names can reveal browsing activity.", style = MaterialTheme.typography.bodySmall)
            if (preferences.keepRecentDomains) {
                if (state.recentQueries.isEmpty()) Text("No recent queries yet.")
                state.recentQueries.take(if (showAll) 30 else 5).forEach { entry ->
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.domain, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.result.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            if (entry.result == QueryResult.Blocked && preferences.allowedDomains.size < 100 &&
                                !PacketParser.blocked(entry.domain, preferences.allowedDomains)) {
                                TextButton(onClick = {
                                    onSettingsChange(preferences.copy(allowedDomains = preferences.allowedDomains + entry.domain))
                                }) { Text("Allow", modifier = Modifier.semantics { contentDescription = "Allow ${entry.domain}" }) }
                            }
                        }
                    }
                }
                if (state.recentQueries.size > 5) TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show fewer" else "Show all ${state.recentQueries.size}")
                }
                TextButton(onClick = onClearHistory) { Text("Clear recent domains") }
            }
            Text("A site not loading? Try Basic, or enable this log and allow a blocked domain. Allowing a domain also allows its subdomains. Reopen the affected app after changing rules.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (rulesOpen) DomainRulesDialog(preferences, onSettingsChange) { rulesOpen = false }
}

@Composable
private fun DomainRulesDialog(preferences: AppSettings, onChange: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var allow by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Domain rules") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enter a domain such as example.com, without a URL or path. Rules include subdomains. Allow rules always win over block rules.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = allow, onClick = { allow = true; error = null }, label = { Text("Allow") })
                FilterChip(selected = !allow, onClick = { allow = false; error = null }, label = { Text("Block") })
            }
            OutlinedTextField(value = input, onValueChange = { input = it.take(300); error = null },
                label = { Text("Domain") }, singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val domain = FilterPolicy.normalizeDomain(input)
                val existing = if (allow) preferences.allowedDomains else preferences.blockedDomains
                when {
                    domain == null -> error = "Enter a valid domain, such as example.com."
                    domain in existing -> error = "This rule already exists."
                    existing.size >= 100 -> error = "Remove a rule first. Each list supports 100 domains."
                    else -> {
                        onChange(if (allow) preferences.copy(allowedDomains = existing + domain)
                            else preferences.copy(blockedDomains = existing + domain))
                        input = ""
                    }
                }
            }) { Text(if (allow) "Add allow rule" else "Add block rule") }
            for ((title, domains, isAllow) in listOf(
                Triple("Allowed domains", preferences.allowedDomains, true),
                Triple("Custom blocked domains", preferences.blockedDomains, false)
            )) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (domains.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
                domains.sorted().forEach { domain ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(domain, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            onChange(if (isAllow) preferences.copy(allowedDomains = domains - domain)
                                else preferences.copy(blockedDomains = domains - domain))
                        }) { Text("Remove", modifier = Modifier.semantics { contentDescription = "Remove $domain from $title" }) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
