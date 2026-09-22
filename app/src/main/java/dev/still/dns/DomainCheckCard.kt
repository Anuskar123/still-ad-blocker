package dev.still.dns

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DomainCheckCard(settings: AppSettings, library: FilterLibraryState) {
    var input by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf<String?>(null) }
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Check a domain", style = MaterialTheme.typography.titleMedium)
            Text("See how your current rules handle a domain. This check stays on your device and does not visit the site.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = input, onValueChange = { input = it.take(253); checked = null },
                label = { Text("Domain, for example example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { checked = input }, enabled = library.ready && input.isNotBlank()) { Text("Check rules") }
            checked?.let { raw ->
                val domain = FilterPolicy.normalizeDomain(raw)
                Text(if (domain == null) "Enter a domain without a URL path, port or IP address."
                    else "$domain: ${FilterPolicy.explain(domain, settings, library)}")
            }
            Text("Results describe saved rules. Device-wide filtering requires protection to be on; encrypted DNS may bypass it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
