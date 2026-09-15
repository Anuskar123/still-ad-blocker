package dev.still.dns

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(state: ProtectionState, onToggle: () -> Unit, onDismissError: () -> Unit) {
    var settings by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Shield, null, tint = colors.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("still", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { settings = true }, modifier = Modifier.border(1.dp, colors.outlineVariant, CircleShape)) {
                        Icon(Icons.Outlined.Settings, "Protection settings")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LESS NOISE. MORE SPACE.", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = colors.primary)
                    Text(if (state.connected) "A quieter internet." else "Your space, protected.", style = MaterialTheme.typography.displaySmall)
                    Text("Keep known ad domains out of your day.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PowerButton(state, onToggle)
                    Text(when (state.connection) {
                        Connection.Connected -> "Protection is on"
                        Connection.Connecting -> "Starting protection"
                        Connection.Disconnected -> "Tap to Protect"
                    }, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(if (state.connected) "DNS filtering is active on this device" else "One tap for a little more peace of mind", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                if (state.error != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = colors.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(state.error, color = colors.onErrorContainer)
                            TextButton(onClick = onDismissError) { Text("Dismiss") }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Your impact", style = MaterialTheme.typography.titleMedium)
                        Text("THIS APP SESSION", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Ads Blocked", NumberFormat.getIntegerInstance().format(state.blocked), "DNS requests stopped", Icons.Outlined.GppGood, Modifier.weight(1f))
                        StatCard("Data Saved", String.format(Locale.getDefault(), "%.2f MB", state.savedMb), "Estimated, 50 KB / block", Icons.Outlined.DataUsage, Modifier.weight(1f))
                    }
                    ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = colors.surface)) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VerifiedUser, null, tint = colors.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Status", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                Text(if (state.connected) "Secure" else "Unprotected", style = MaterialTheme.typography.titleMedium)
                            }
                            Box(Modifier.size(8.dp).background(if (state.connected) colors.primary else colors.outline, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("DNS", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Local protection. No account needed.", style = MaterialTheme.typography.labelLarge)
                        Text("Domains are checked on-device. Allowed DNS queries go to Google DNS. Browsing traffic is not encrypted by Still.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text("${NumberFormat.getIntegerInstance().format(state.queries)} queries checked  /  ${state.failures} upstream failures", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false }, icon = { Icon(Icons.Outlined.Tune, null) },
            title = { Text("Protection settings") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Appearance follows your device, including dynamic colors on Android 12 and later.")
                    Text("DNS resolver\nGoogle DNS · 8.8.8.8", fontWeight = FontWeight.Medium)
                    Text("Built-in blocklist\n" + AdBlockerService.BLOCKLIST.sorted().joinToString("\n"))
                    Text("Subdomains are included. Counters last until the app process ends. Data savings are an estimate, not measured traffic.")
                    Text("This version filters IPv4 UDP DNS only. Private DNS, encrypted DNS, cached answers and app-specific resolvers may bypass filtering. TCP DNS fallback is not supported. Another VPN cannot run alongside Still.")
                }
            }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })
    }
}

@Composable
private fun PowerButton(state: ProtectionState, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val start by animateColorAsState(if (state.connected) Color(0xFF72E6B7) else colors.surfaceVariant, tween(600), label = "powerStart")
    val end by animateColorAsState(if (state.connected) Color(0xFF50ACCF) else colors.errorContainer, tween(600), label = "powerEnd")
    Box(Modifier.fillMaxWidth().height(258.dp), contentAlignment = Alignment.Center) {
        if (state.connected) {
            val transition = rememberInfiniteTransition(label = "protectionPulse")
            val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "ripple")
            repeat(2) { index ->
                val progress = (phase + index * 0.5f) % 1f
                Box(Modifier.size(190.dp).scale(1f + progress * 0.32f).border(1.dp, colors.primary.copy(alpha = (1f - progress) * 0.35f), CircleShape))
            }
        }
        Box(Modifier.size(204.dp).border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(176.dp).clip(CircleShape).background(Brush.linearGradient(listOf(start, end)))
                .semantics { contentDescription = if (state.connected) "Disconnect DNS protection" else "Connect DNS protection" }
                .clickable(enabled = state.connection != Connection.Connecting, role = Role.Button, onClick = onToggle), contentAlignment = Alignment.Center) {
                if (state.connection == Connection.Connecting) CircularProgressIndicator(modifier = Modifier.size(48.dp))
                else Icon(Icons.Outlined.PowerSettingsNew, null, modifier = Modifier.size(64.dp), tint = if (state.connected) Color(0xFF123B36) else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, detail: String, icon: ImageVector, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    ElevatedCard(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = colors.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectedPreview() = StillTheme { DashboardScreen(ProtectionState(Connection.Connected, 128, 340), {}, {}) }

@Preview(showBackground = true)
@Composable
private fun DisconnectedPreview() = StillTheme { DashboardScreen(ProtectionState(), {}, {}) }
