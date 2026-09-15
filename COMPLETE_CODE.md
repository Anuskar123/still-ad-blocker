# Still: complete source code

The nine requested files appear first, followed by the remaining project configuration, resources and tests. The DNS-only route is intentionally 10.0.0.2/32. A default route would break ordinary traffic without a full forwarding stack. See README.md for setup and limitations. The Gradle wrapper scripts and binary are included in the project folder.

## app/build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.still.dns"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.still.dns"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
```

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application android:label="Still" android:icon="@drawable/ic_shield" android:allowBackup="false" android:fullBackupContent="false" android:dataExtractionRules="@xml/data_extraction_rules" android:supportsRtl="true" android:theme="@android:style/Theme.Material.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <!-- VPN consent grants VPN eligibility for systemExempted. AGP 8.7 lint only
             models the alternative alarm permission path. No alarms are used.
             https://developer.android.com/develop/background-work/services/fgs/service-types#system-exempted -->
        <service android:name=".AdBlockerService" android:exported="false" android:permission="android.permission.BIND_VPN_SERVICE" android:foregroundServiceType="systemExempted" tools:ignore="ForegroundServicePermission">
            <intent-filter><action android:name="android.net.VpnService" /></intent-filter>
            <meta-data android:name="android.net.VpnService.SUPPORTS_ALWAYS_ON" android:value="false" />
        </service>
    </application>
</manifest>
```

## app/src/main/java/dev/still/dns/Theme.kt

```kotlin
package dev.still.dns

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF68E0BD), onPrimary = Color(0xFF00382A),
    secondary = Color(0xFF87BDFF), background = Color(0xFF0D1317),
    surface = Color(0xFF141D22), surfaceVariant = Color(0xFF233139),
    onSurface = Color(0xFFF0F5F3), onSurfaceVariant = Color(0xFFA5B7B5)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF006D53), onPrimary = Color.White,
    secondary = Color(0xFF315F99), background = Color(0xFFF2F6F4),
    surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE3EEE8)
)
private val StillTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp)
)

@Composable
fun StillTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = StillTypography, content = content)
}
```

## app/src/main/java/dev/still/dns/MainActivity.kt

```kotlin
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

class MainActivity : ComponentActivity() {
    private val viewModel: AdBlockerViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startProtection() else viewModel.permissionDenied()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StillTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                DashboardScreen(state, onToggle = {
                    if (state.connected) stopService(Intent(this, AdBlockerService::class.java))
                    else if (state.connection == Connection.Disconnected) {
                        val permission = VpnService.prepare(this)
                        if (permission != null) vpnPermission.launch(permission) else startProtection()
                    }
                }, onDismissError = viewModel::dismissError)
            }
        }
    }

    private fun startProtection() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, AdBlockerService::class.java))
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (_: Exception) { viewModel.failure() }
    }
}
```

## app/src/main/java/dev/still/dns/AdBlockerViewModel.kt

```kotlin
package dev.still.dns

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Connection { Disconnected, Connecting, Connected }
data class ProtectionState(
    val connection: Connection = Connection.Disconnected,
    val blocked: Long = 0,
    val queries: Long = 0,
    val failures: Long = 0,
    val error: String? = null
) {
    val connected get() = connection == Connection.Connected
    val savedMb get() = blocked * 50_000.0 / 1_000_000.0
}

/** Process-local repository shared by the service and every activity instance. */
object ProtectionStore {
    private val mutable = MutableStateFlow(ProtectionState())
    val state = mutable.asStateFlow()
    fun update(change: (ProtectionState) -> ProtectionState) = mutable.update(change)
}

class AdBlockerViewModel : ViewModel() {
    val state = ProtectionStore.state
    fun permissionDenied() = ProtectionStore.update { it.copy(error = "VPN permission is needed to filter DNS.") }
    fun failure() = ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = "Could not start protection. Try again.") }
    fun dismissError() = ProtectionStore.update { it.copy(error = null) }
}
```

## app/src/main/java/dev/still/dns/DashboardScreen.kt

```kotlin
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
```

## app/src/main/java/dev/still/dns/AdBlockerService.kt

```kotlin
package dev.still.dns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AdBlockerService : VpnService() {
    companion object {
        const val STOP = "dev.still.dns.STOP"
        val BLOCKLIST = setOf("ads.google.com", "doubleclick.net", "googlesyndication.com", "googleadservices.com")
    }
    private var session: Session? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopProtection(); return START_NOT_STICKY }
        if (session != null) return START_NOT_STICKY
        ProtectionStore.update { it.copy(connection = Connection.Connecting, error = null) }
        try {
            foreground()
            val tunnel = Builder().setSession("Still DNS protection")
                .setMtu(1500).addAddress("10.0.0.2", 24).addDnsServer("10.0.0.2")
                // DNS-only split route. A default route requires a complete TCP/IP forwarding stack.
                .addRoute("10.0.0.2", 32).allowFamily(OsConstants.AF_INET6)
                .setBlocking(true).setMeteredCompat()
                .establish() ?: error("VPN permission was revoked")
            val current = Session(tunnel)
            session = current
            ProtectionStore.update { it.copy(connection = Connection.Connected) }
            current.start()
        } catch (_: Exception) {
            stopProtection("Unable to establish the local VPN. Check VPN permission and try again.")
        }
        return START_NOT_STICKY
    }

    private fun Builder.setMeteredCompat(): Builder {
        if (Build.VERSION.SDK_INT >= 29) setMetered(false)
        return this
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("protection", "DNS protection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AdBlockerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "protection")
            .setSmallIcon(R.drawable.ic_shield).setContentTitle("Still protection is on")
            .setContentText("Filtering known ad domains on this device")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(1, notification)
    }

    private fun stopProtection(error: String? = null) {
        session?.close()
        session = null
        ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = error) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { stopProtection("VPN permission was revoked or another VPN was started."); super.onRevoke() }
    override fun onDestroy() {
        session?.close(); session = null
        ProtectionStore.update { it.copy(connection = Connection.Disconnected) }
        super.onDestroy()
    }

    private inner class Session(private val tunnel: ParcelFileDescriptor) {
        private val running = AtomicBoolean(true)
        private val input = FileInputStream(tunnel.fileDescriptor)
        private val output = FileOutputStream(tunnel.fileDescriptor)
        private val writeLock = Any()
        private val resolver = UpstreamDnsResolver(this@AdBlockerService)
        private val workers = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128))
        private var reader: Thread? = null

        fun start() {
            reader = thread(name = "still-tun-reader") {
                try {
                    val buffer = ByteArray(65535)
                    while (running.get()) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        val query = PacketParser.parse(buffer.copyOf(count)) ?: continue
                        if (!query.destination.contentEquals(byteArrayOf(10, 0, 0, 2))) continue
                        ProtectionStore.update { it.copy(queries = it.queries + 1) }
                        if (PacketParser.blocked(query.question.name, BLOCKLIST)) {
                            write(query, PacketParser.response(query))
                            ProtectionStore.update { it.copy(blocked = it.blocked + 1) }
                        } else {
                            try {
                                workers.execute {
                                    val response = try { resolver.resolve(query) } catch (_: Exception) {
                                        if (running.get()) ProtectionStore.update { it.copy(failures = it.failures + 1) }
                                        PacketParser.response(query, error = 2)
                                    }
                                    try { write(query, response) } catch (_: Exception) { failed() }
                                }
                            } catch (_: java.util.concurrent.RejectedExecutionException) {
                                ProtectionStore.update { it.copy(failures = it.failures + 1) }
                                write(query, PacketParser.response(query, error = 2))
                            }
                        }
                    }
                    if (running.get()) failed()
                } catch (_: Exception) { if (running.get()) failed() }
            }
        }

        private fun write(query: PacketParser.Query, dns: ByteArray) = synchronized(writeLock) {
            if (running.get()) output.write(PacketParser.wrap(query, dns))
        }

        private fun failed() {
            android.os.Handler(mainLooper).post {
                if (session === this && running.get()) stopProtection("The DNS tunnel stopped. Tap to reconnect.")
            }
        }

        fun close() {
            if (!running.getAndSet(false)) return
            resolver.close()
            workers.shutdownNow()
            // Interrupt blocking descriptor I/O and dispose stream owners before a new session.
            reader?.interrupt()
            synchronized(writeLock) {
                runCatching { input.close() }
                runCatching { output.close() }
                runCatching { tunnel.close() }
            }
        }
    }
}
```

## app/src/main/java/dev/still/dns/PacketParser.kt

```kotlin
package dev.still.dns

import java.io.ByteArrayOutputStream
import java.util.Locale

/** Strict IPv4/UDP DNS codec. Malformed and fragmented packets are rejected. */
object PacketParser {
    data class Question(val name: String, val type: Int, val wire: ByteArray)
    data class Query(val source: ByteArray, val destination: ByteArray, val port: Int,
                     val dns: ByteArray, val question: Question)

    fun u16(b: ByteArray, p: Int): Int = ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
    fun put16(b: ByteArray, p: Int, n: Int) { b[p] = (n ushr 8).toByte(); b[p + 1] = n.toByte() }

    fun parse(packet: ByteArray): Query? = runCatching {
        require(packet.size >= 28 && (packet[0].toInt() ushr 4 and 15) == 4)
        val ihl = (packet[0].toInt() and 15) * 4
        val total = u16(packet, 2)
        require(ihl >= 20 && total <= packet.size && total >= ihl + 8)
        require(packet[9].toInt() == 17 && u16(packet, 6) and 0x3fff == 0)
        require(checksum(packet.copyOfRange(0, ihl)) == 0)
        val len = u16(packet, ihl + 4)
        require(len >= 20 && ihl + len == total && u16(packet, ihl + 2) == 53)
        val src = packet.copyOfRange(12, 16)
        val dst = packet.copyOfRange(16, 20)
        require(u16(packet, ihl) != 0)
        val udp = packet.copyOfRange(ihl, total)
        if (u16(udp, 6) != 0) require(checksum(pseudo(src, dst, len) + udp) == 0)
        val dns = udp.copyOfRange(8, udp.size)
        require(u16(dns, 2) and 0xf800 == 0 && u16(dns, 4) == 1)
        Query(src, dst, u16(packet, ihl), dns, question(dns) ?: error("Bad DNS question"))
    }.getOrNull()

    fun question(dns: ByteArray): Question? = runCatching {
        require(dns.size >= 17 && u16(dns, 4) == 1)
        var cursor = 12
        var end = -1
        var expanded = 1
        val seen = HashSet<Int>()
        val labels = mutableListOf<String>()
        while (true) {
            require(cursor in dns.indices && seen.add(cursor))
            val length = dns[cursor].toInt() and 255
            if (length and 0xc0 == 0xc0) {
                require(cursor + 1 < dns.size)
                if (end < 0) end = cursor + 2
                cursor = ((length and 63) shl 8) or (dns[cursor + 1].toInt() and 255)
                continue
            }
            require(length <= 63)
            cursor++
            if (length == 0) { if (end < 0) end = cursor; break }
            require(cursor + length <= dns.size)
            val label = dns.copyOfRange(cursor, cursor + length)
            require(label.all { (it.toInt() and 255) in 33..126 && it != '.'.code.toByte() })
            labels += label.toString(Charsets.US_ASCII)
            expanded += length + 1
            require(expanded <= 255)
            cursor += length
        }
        require(end + 4 <= dns.size && u16(dns, end + 2) == 1)
        val wire = ByteArrayOutputStream()
        labels.forEach { wire.write(it.length); wire.write(it.toByteArray(Charsets.US_ASCII)) }
        wire.write(0)
        wire.write(dns, end, 4)
        Question(labels.joinToString(".").lowercase(Locale.ROOT), u16(dns, end), wire.toByteArray())
    }.getOrNull()

    fun blocked(name: String, rules: Set<String>): Boolean =
        rules.any { name == it || name.endsWith(".$it") }

    /** A -> 0.0.0.0, AAAA -> ::, other record types -> NOERROR/NODATA. */
    fun response(query: Query, error: Int = 0): ByteArray {
        val size = if (error != 0) 0 else when (query.question.type) { 1 -> 4; 28 -> 16; else -> 0 }
        val header = ByteArray(12)
        put16(header, 0, u16(query.dns, 0))
        put16(header, 2, 0x8080 or (u16(query.dns, 2) and 0x0100) or error)
        put16(header, 4, 1)
        put16(header, 6, if (size > 0) 1 else 0)
        val answer = if (size > 0) ByteArray(12 + size).also {
            put16(it, 0, 0xc00c); put16(it, 2, query.question.type); put16(it, 4, 1)
            put16(it, 8, 60); put16(it, 10, size)
        } else byteArrayOf()
        return header + query.question.wire + answer
    }

    fun wrap(query: Query, dns: ByteArray): ByteArray {
        require(dns.size <= 65507)
        val packet = ByteArray(28 + dns.size)
        packet[0] = 0x45; put16(packet, 2, packet.size)
        packet[8] = 64; packet[9] = 17
        query.destination.copyInto(packet, 12); query.source.copyInto(packet, 16)
        put16(packet, 20, 53); put16(packet, 22, query.port); put16(packet, 24, 8 + dns.size)
        dns.copyInto(packet, 28)
        val udpChecksum = checksum(pseudo(query.destination, query.source, 8 + dns.size) + packet.copyOfRange(20, packet.size))
        put16(packet, 26, if (udpChecksum == 0) 0xffff else udpChecksum)
        put16(packet, 10, checksum(packet.copyOfRange(0, 20)))
        return packet
    }

    private fun pseudo(src: ByteArray, dst: ByteArray, length: Int): ByteArray =
        (src + dst + byteArrayOf(0, 17, 0, 0)).also { put16(it, 10, length) }

    /** One's-complement sum, network byte order, zero-padded odd final octet. */
    fun checksum(bytes: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < bytes.size) { sum += u16(bytes, i); i += 2 }
        if (i < bytes.size) sum += (bytes[i].toInt() and 255) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt().inv() and 0xffff
    }
}
```

## app/src/main/java/dev/still/dns/UpstreamDnsResolver.kt

```kotlin
package dev.still.dns

import android.net.VpnService
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UpstreamDnsResolver(private val service: VpnService) : Closeable {
    private val sockets = mutableSetOf<DatagramSocket>()
    private var closed = false

    fun resolve(query: PacketParser.Query): ByteArray {
        val socket = DatagramSocket()
        synchronized(sockets) {
            if (closed) { socket.close(); error("Resolver closed") }
            sockets.add(socket)
        }
        try {
            check(service.protect(socket)) { "Cannot protect upstream DNS socket" }
            socket.soTimeout = 3000
            socket.connect(InetAddress.getByName("8.8.8.8"), 53)
            // A connected socket accepts replies only from the selected upstream endpoint.
            socket.send(DatagramPacket(query.dns, query.dns.size))
            val buffer = ByteArray(65507)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            val dns = buffer.copyOf(reply.length)
            require(dns.size >= 12 && PacketParser.u16(dns, 0) == PacketParser.u16(query.dns, 0))
            require(PacketParser.u16(dns, 2) and 0xf800 == 0x8000)
            val question = PacketParser.question(dns)
            require(question?.name == query.question.name && question.type == query.question.type)
            return dns
        } finally {
            synchronized(sockets) { sockets.remove(socket) }
            socket.close()
        }
    }

    override fun close() = synchronized(sockets) {
        closed = true
        sockets.forEach { it.close() }
        sockets.clear()
    }
}
```

## build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

## settings.gradle.kts

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Still"
include(":app")
```

## gradle.properties

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

## gradle/wrapper/gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
distributionSha256Sum=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
networkTimeout=120000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

## app/src/main/res/drawable/ic_shield.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#51D7B3" android:pathData="M12,2L3,6v6c0,5 4,8 9,10 5,-2 9,-5 9,-10V6z" />
    <path android:strokeColor="#102820" android:strokeWidth="2" android:fillColor="#00000000" android:pathData="M7,12l3,3 7,-7" />
</vector>
```

## app/src/main/res/xml/data_extraction_rules.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
```

## app/src/test/java/dev/still/dns/PacketParserTest.kt

```kotlin
package dev.still.dns

import org.junit.Assert.*
import org.junit.Test

class PacketParserTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun query(type: Int = 1): PacketParser.Query {
        val dns = hex("1234010000010000000000000361647306676f6f676c6503636f6d0000010001")
        PacketParser.put16(dns, dns.size - 4, type)
        return PacketParser.Query(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 2), 43210, dns, PacketParser.question(dns)!!)
    }
    private fun incoming(): ByteArray {
        val q = query()
        val packet = PacketParser.wrap(q, q.dns)
        PacketParser.put16(packet, 20, q.port)
        PacketParser.put16(packet, 22, 53)
        // IPv4 permits zero UDP checksum, used here to independently construct input.
        PacketParser.put16(packet, 26, 0)
        return packet
    }
    @Test fun knownIpv4ChecksumVector() {
        assertEquals(0xb861, PacketParser.checksum(hex("450000730000400040110000c0a80001c0a800c7")))
    }
    @Test fun oddLengthChecksumVector() { assertEquals(0xfbfd, PacketParser.checksum(hex("010203"))) }
    @Test fun parsesQuestionAndSourcePort() {
        val parsed = PacketParser.parse(incoming())!!
        assertEquals("ads.google.com", parsed.question.name)
        assertEquals(43210, parsed.port)
    }
    @Test fun blockedARecordHasZeroAddressAndCorrectFlags() {
        val dns = PacketParser.response(query())
        assertEquals(0x1234, PacketParser.u16(dns, 0))
        assertEquals(0x8180, PacketParser.u16(dns, 2))
        assertEquals(1, PacketParser.u16(dns, 6))
        assertArrayEquals(ByteArray(4), dns.takeLast(4).toByteArray())
        assertEquals(4, PacketParser.u16(dns, dns.size - 6))
    }
    @Test fun blockedAAAAHasSixteenZeroBytes() {
        val dns = PacketParser.response(query(28))
        assertEquals(16, PacketParser.u16(dns, dns.size - 18))
        assertArrayEquals(ByteArray(16), dns.takeLast(16).toByteArray())
    }
    @Test fun otherTypesReturnNoData() { assertEquals(0, PacketParser.u16(PacketParser.response(query(65)), 6)) }
    @Test fun servfailPreservesQuestionWithoutAnswer() {
        val dns = PacketParser.response(query(), 2)
        assertEquals(2, PacketParser.u16(dns, 2) and 15)
        assertEquals(0, PacketParser.u16(dns, 6))
        assertEquals("ads.google.com", PacketParser.question(dns)!!.name)
    }
    @Test fun responseChecksumsVerifiedWithIndependentAccumulator() {
        for (dns in listOf(PacketParser.response(query()), PacketParser.response(query()) + byteArrayOf(7))) {
            val packet = PacketParser.wrap(query(), dns)
            fun sum(bytes: ByteArray): Int {
                var total = bytes.indices.step(2).sumOf { i ->
                    (bytes[i].toInt() and 255) * 256 + if (i + 1 < bytes.size) bytes[i + 1].toInt() and 255 else 0
                }
                while (total > 65535) total = total % 65536 + total / 65536
                return total
            }
            assertEquals(65535, sum(packet.copyOfRange(0, 20)))
            val pseudo = packet.copyOfRange(12, 20) + byteArrayOf(0, 17) + packet.copyOfRange(24, 26)
            assertEquals(65535, sum(pseudo + packet.copyOfRange(20, packet.size)))
            assertEquals(43210, PacketParser.u16(packet, 22))
        }
    }
    @Test fun rejectsTruncatedPackets() {
        val valid = incoming()
        for (length in 0 until valid.size) assertNull(PacketParser.parse(valid.copyOf(length)))
    }
    @Test fun rejectsFragmentsEvenWithValidHeaderChecksum() {
        val packet = incoming()
        PacketParser.put16(packet, 6, 0x2000)
        PacketParser.put16(packet, 10, 0)
        PacketParser.put16(packet, 10, PacketParser.checksum(packet.copyOfRange(0, 20)))
        assertNull(PacketParser.parse(packet))
    }
    @Test fun rejectsBadUdpChecksum() {
        val packet = incoming()
        PacketParser.put16(packet, 26, 123)
        assertNull(PacketParser.parse(packet))
    }
    @Test fun rejectsCompressionCycle() {
        assertNull(PacketParser.question(hex("123401000001000000000000c00c00010001")))
    }
    @Test fun resolvesCompressedQuestionSafely() {
        val dns = hex("123401000001000000000000c012000100010361647306676f6f676c6503636f6d00")
        assertEquals("ads.google.com", PacketParser.question(dns)!!.name)
        assertArrayEquals(query().question.wire, PacketParser.question(dns)!!.wire)
    }
    @Test fun matchingRespectsLabelBoundary() {
        val rules = setOf("doubleclick.net")
        assertTrue(PacketParser.blocked("x.doubleclick.net", rules))
        assertTrue(PacketParser.blocked("doubleclick.net", rules))
        assertFalse(PacketParser.blocked("notdoubleclick.net", rules))
        assertFalse(PacketParser.blocked("doubleclick.net.example.org", rules))
    }
    @Test fun repliesReverseDifferentAddresses() {
        val q = query().copy(source = byteArrayOf(10, 0, 0, 9), destination = byteArrayOf(10, 0, 0, 2))
        val packet = PacketParser.wrap(q, PacketParser.response(q))
        assertArrayEquals(q.destination, packet.copyOfRange(12, 16))
        assertArrayEquals(q.source, packet.copyOfRange(16, 20))
    }
    @Test fun acceptsValidNonzeroUdpChecksum() {
        val q = query()
        // Swapping UDP ports does not alter the one's-complement sum.
        val packet = PacketParser.wrap(q, q.dns)
        PacketParser.put16(packet, 20, q.port)
        PacketParser.put16(packet, 22, 53)
        assertNotNull(PacketParser.parse(packet))
    }
}
```
