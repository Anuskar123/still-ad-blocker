package dev.still.dns

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrowseCard(connected: Boolean, onPrivate: () -> Unit, onOther: () -> Unit) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Browse your way", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onPrivate, modifier = Modifier.fillMaxWidth()) { Text("Open Still private browser") }
            Text("A fresh session with your filter rules. Leaving the private browser erases its cookies, website storage and browsing history. After a forced stop, cleanup runs before the next session.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOther, enabled = connected, modifier = Modifier.fillMaxWidth()) { Text("Open another browser") }
            Text(if (connected) "DNS protection is on for browsers using the system DNS resolver. Choose a browser to continue."
                else "Turn on DNS protection below to use it with Chrome, Firefox or another browser.", style = MaterialTheme.typography.bodySmall)
            Text("A browser's own encrypted DNS can bypass this filter. Other browsers keep their own history and cookies; Still cannot erase that data.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
