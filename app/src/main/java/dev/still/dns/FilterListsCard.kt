package dev.still.dns

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun FilterListsCard(library: FilterLibraryState, preferences: AppSettings,
    onChange: (AppSettings) -> Unit, onUpdate: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filter lists", style = MaterialTheme.typography.titleMedium)
            Text("Add maintained domain lists to your protection level. Select a list, then download it. Updates are manual and saved lists work offline.", style = MaterialTheme.typography.bodySmall)
            if (!library.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            DownloadableFilter.entries.forEach { filter ->
                val enabled = filter.name in preferences.enabledSubscriptions
                val entry = library.entries[filter]
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(filter.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, enabled = library.ready,
                        onCheckedChange = { selected ->
                            onChange(preferences.copy(enabledSubscriptions = if (selected)
                                preferences.enabledSubscriptions + filter.name else preferences.enabledSubscriptions - filter.name))
                        }, modifier = Modifier.semantics { contentDescription = "Enable ${filter.title} filter list" })
                }
                Text(filter.detail, style = MaterialTheme.typography.bodySmall)
                Text(when {
                    !library.ready -> "Loading saved filters"
                    entry?.list == null && enabled -> "Selected, but not downloaded yet"
                    entry?.list == null -> "Not downloaded"
                    enabled -> "${NumberFormat.getIntegerInstance().format(entry.list.domains.size)} rules ready when protection is on"
                    else -> "Disabled / ${NumberFormat.getIntegerInstance().format(entry.list.domains.size)} saved rules"
                }, style = MaterialTheme.typography.labelMedium)
                if (entry?.list != null) {
                    Text("Downloaded: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.downloadedAt))}\nList version: ${entry.list.version}", style = MaterialTheme.typography.bodySmall)
                }
                entry?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = onUpdate, enabled = library.ready && !library.updating && preferences.enabledSubscriptions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) {
                Text(if (library.updating) "Updating filters..." else "Download / update selected lists")
            }
            if (library.updating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Downloads connect to GitHub over HTTPS and use mobile data if Wi-Fi is unavailable. Still does not send your browsing history. Your allow rules take priority. A failed update keeps the last valid copy.", style = MaterialTheme.typography.bodySmall)
            Text("Still is independent of AdGuard and HaGeZi. These lists cannot remove page elements or reliably block YouTube video ads.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uriHandler.openUri("https://github.com/hagezi/dns-blocklists") }) { Text("List source and credits") }
            TextButton(onClick = { uriHandler.openUri("https://github.com/hagezi/dns-blocklists/blob/main/LICENSE") }) { Text("Filter licence (GPL-3.0)") }
        }
    }
}
