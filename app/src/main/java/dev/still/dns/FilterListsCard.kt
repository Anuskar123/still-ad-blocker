package dev.still.dns

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var search by rememberSaveable { mutableStateOf("") }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filter lists", style = MaterialTheme.typography.titleMedium)
            Text("Choose one Multi list, then optional extra categories. Selecting a Multi list replaces the previous Multi selection.", style = MaterialTheme.typography.bodySmall)
            val selected = DownloadableFilter.entries.filter { it.name in preferences.enabledSubscriptions }
            val ready = selected.count { library.entries[it]?.list != null }
            Text("${selected.size} selected / $ready downloaded", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(value = search, onValueChange = { search = it.take(80) }, label = { Text("Find a filter list") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Add maintained domain lists to your protection level. Selected lists update daily when automatic updates are enabled in Settings. You can also download them now. Saved lists work offline.", style = MaterialTheme.typography.bodySmall)
            if (!library.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            val visible = DownloadableFilter.entries.filter { it.title.contains(search, true) || it.detail.contains(search, true) }
            if (visible.isEmpty()) Text("No matching lists.")
            visible.forEach { filter ->
                val enabled = filter.name in preferences.enabledSubscriptions
                val entry = library.entries[filter]
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(filter.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, enabled = library.ready,
                        onCheckedChange = { selected ->
                            onChange(preferences.copy(enabledSubscriptions = filter.select(preferences.enabledSubscriptions, selected)))
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
                Text(if (library.updating) {
                    val position = (library.completedDownloads + 1).coerceAtMost(library.totalDownloads)
                    "Downloading $position of ${library.totalDownloads}: ${library.activeFilter?.title.orEmpty()}"
                } else "Download / update selected lists")
            }
            if (library.updating) {
                LinearProgressIndicator(
                    progress = { if (library.totalDownloads == 0) 0f else library.completedDownloads.toFloat() / library.totalDownloads },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Large lists can take more than a minute on a mobile connection. Keep Still open until this finishes.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("Downloads connect to GitHub over HTTPS and use mobile data if Wi-Fi is unavailable. Still does not send your browsing history. Your allow rules take priority. A failed update keeps the last valid copy.", style = MaterialTheme.typography.bodySmall)
            Text("Still is independent of AdGuard and HaGeZi. These lists cannot remove page elements or reliably block YouTube video ads.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uriHandler.openUri("https://github.com/hagezi/dns-blocklists") }) { Text("List source and credits") }
            TextButton(onClick = { uriHandler.openUri("https://github.com/hagezi/dns-blocklists/blob/main/LICENSE") }) { Text("Filter licence (GPL-3.0)") }
        }
    }
}
