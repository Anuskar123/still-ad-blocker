package dev.still.dns

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var privacy by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { if (privacy) privacy = false else onBack() }
    fun open(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { error = "No app is available to open this link." }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(if (privacy) "Privacy policy" else "About Still") },
        navigationIcon = { IconButton(onClick = { if (privacy) privacy = false else onBack() }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
        } }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (privacy) {
                val policy = remember { context.assets.open("privacy-policy.txt").bufferedReader().use { it.readText() } }
                Text(policy, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("Still", style = MaterialTheme.typography.displaySmall)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("DNS rules are checked on this device. No account or developer-operated analytics service is used. Allowed DNS queries go to your selected resolver.")
                TextButton(onClick = { privacy = true }) { Text("Privacy policy") }
                TextButton(onClick = { open("https://github.com/Anuskar123/still-ad-blocker") }) { Text("GitHub source") }
                Text("Filter list credits", style = MaterialTheme.typography.titleMedium)
                Text("Optional HaGeZi Multi Light, Pro Mini, Pro++ Mini, TIF Mini, fake-site, pop-up, gambling and adult-domain lists are downloaded from HaGeZi's DNS blocklists repository. The project distributes these lists under GPL-3.0. Source and licence are available below.")
                TextButton(onClick = { open("https://github.com/hagezi/dns-blocklists") }) { Text("HaGeZi filter lists and credits") }
                TextButton(onClick = { open("https://github.com/hagezi/dns-blocklists/blob/main/LICENSE") }) { Text("Filter list licence") }
                TextButton(onClick = { context.startActivity(Intent(context, OssLicensesMenuActivity::class.java)) }) { Text("Open source licences") }
                TextButton(onClick = { open("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}") }) { Text("Rate on Google Play") }
                Text("The store page will be available after Still is published.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
