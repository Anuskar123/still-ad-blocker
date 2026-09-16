# Still: complete source code

The nine requested files appear first, followed by the remaining project configuration, resources and tests. The DNS-only route is intentionally 10.0.0.2/32. A default route would break ordinary traffic without a full forwarding stack. See README.md for setup and limitations. The Gradle wrapper scripts and binary are included in the project folder.

## app/build.gradle.kts

```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.android.gms.oss-licenses-plugin")
}
val releaseSecrets = Properties().apply {
    val location = rootProject.file("signing/release.properties")
    if (location.exists()) location.inputStream().use { load(it) }
}
android {
    namespace = "dev.still.dns"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.still.dns"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Still"
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (releaseSecrets.isNotEmpty()) create("release") {
            storeFile = rootProject.file(releaseSecrets.getProperty("storeFile"))
            storePassword = releaseSecrets.getProperty("storePassword")
            keyAlias = releaseSecrets.getProperty("keyAlias")
            keyPassword = releaseSecrets.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSecrets.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        create("releaseCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = ".releasecheck"
            manifestPlaceholders["appLabel"] = "Still release check"
            matchingFallbacks += "release"
        }
    }
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")

    // Reserved for local AI experiments; no shipped feature currently calls this SDK.
    debugImplementation("com.google.mediapipe:tasks-genai:0.10.14")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
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
    <queries>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data android:scheme="https" />
        </intent>
    </queries>
    <application android:label="${appLabel}" android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher" android:allowBackup="false" android:fullBackupContent="false" android:dataExtractionRules="@xml/data_extraction_rules" android:supportsRtl="true" android:theme="@android:style/Theme.Material.NoActionBar">
        <activity android:name=".OnboardingActivity" android:exported="false" />
        <activity android:name="com.google.android.gms.oss.licenses.OssLicensesMenuActivity" android:theme="@style/StillLicensesTheme" android:exported="false" />
        <activity android:name="com.google.android.gms.oss.licenses.OssLicensesActivity" android:theme="@style/StillLicensesTheme" android:exported="false" />
        <service android:name=".StillTileService" android:exported="true" android:icon="@drawable/ic_shield_outline"
            android:label="Still DNS" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE" /></intent-filter>
            <meta-data android:name="android.service.quicksettings.TOGGLEABLE_TILE" android:value="true" />
        </service>
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".PrivateBrowserActivity" android:exported="false"
            android:excludeFromRecents="true" android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize" />
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
fun StillTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val context = LocalContext.current
    val colors = if (settings.dynamicColors && Build.VERSION.SDK_INT >= 31) {
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
```

## app/src/main/java/dev/still/dns/AdBlockerViewModel.kt

```kotlin
package dev.still.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Connection { Disconnected, Connecting, Connected }
enum class QueryResult { Allowed, Blocked, Failed }
data class RecentQuery(val domain: String, val result: QueryResult)
data class ProtectionState(
    val connection: Connection = Connection.Disconnected,
    val blocked: Long = 0,
    val queries: Long = 0,
    val failures: Long = 0,
    val error: String? = null,
    val lastDnsMillis: Long? = null,
    val consecutiveFailures: Int = 0,
    val recentQueries: List<RecentQuery> = emptyList()
) {
    val connected get() = connection == Connection.Connected
    val savedMb get() = blocked * 50_000.0 / 1_000_000.0
}

/** Process-local repository shared by the service and every activity instance. */
object ProtectionStore {
    private val privacyLock = Any()
    @Volatile var historyEpoch: Long = 0
        private set
    private var privateSessionActive = false
    fun beginPrivateSession() = synchronized(privacyLock) { historyEpoch++; privateSessionActive = true }
    fun endPrivateSession() = synchronized(privacyLock) { historyEpoch++; privateSessionActive = false }
    private val mutable = MutableStateFlow(ProtectionState())
    val state = mutable.asStateFlow()
    fun update(change: (ProtectionState) -> ProtectionState) = mutable.update(change)
    fun record(domain: String, result: QueryResult, keepHistory: Boolean, millis: Long? = null,
        epoch: Long = historyEpoch) = synchronized(privacyLock) { update {
        it.copy(
            lastDnsMillis = millis ?: it.lastDnsMillis,
            consecutiveFailures = when (result) {
                QueryResult.Failed -> it.consecutiveFailures + 1
                QueryResult.Allowed -> 0
                QueryResult.Blocked -> it.consecutiveFailures
            },
            recentQueries = when {
                !keepHistory -> emptyList()
                privateSessionActive || epoch != historyEpoch -> it.recentQueries
                else -> (listOf(RecentQuery(domain, result)) +
                    it.recentQueries.filterNot { entry -> entry.domain == domain }).take(30)
            }
        )
    } }
}

class AdBlockerViewModel : ViewModel() {
    fun loadFilters(repository: FilterRepository) { viewModelScope.launch { repository.load() } }
    fun updateFilters(repository: FilterRepository, selected: Set<String>) {
        viewModelScope.launch { repository.refresh(selected) }
    }
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
import androidx.compose.foundation.selection.selectable
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
fun DashboardScreen(
    state: ProtectionState, onToggle: () -> Unit, onDismissError: () -> Unit,
    preferences: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    onResetStatistics: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    filterLibrary: FilterLibraryState = FilterLibraryState(),
    onUpdateFilters: () -> Unit = {},
    onOpenPrivateBrowser: () -> Unit = {},
    onOpenOtherBrowser: () -> Unit = {},
    lifetime: LifetimeTotals = LifetimeTotals()
) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    if (settings) {
        SettingsScreen(state, preferences, onSettingsChange, onToggle, onResetStatistics) { settings = false }
        return
    }
    if (about) { AboutScreen { about = false }; return }
    val colors = MaterialTheme.colorScheme
    val accent by animateColorAsState(if (state.connected) colors.primary else colors.error, label = "statusAccent")
    val animatedBlocked by animateFloatAsState(state.blocked.toFloat(), tween(500), label = "blockedCount")
    Scaffold(containerColor = colors.background) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(accent, RoundedCornerShape(3.dp)))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Shield, null, tint = colors.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("still", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, "About Still") }
                    IconButton(onClick = { settings = true }, modifier = Modifier.border(1.dp, colors.outlineVariant, CircleShape)) {
                        Icon(Icons.Outlined.Settings, "Protection settings")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${NumberFormat.getIntegerInstance().format(animatedBlocked.toLong())} requests blocked this session", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Text(if (state.connected) "A quieter internet." else "Your space, protected.", style = MaterialTheme.typography.displaySmall)
                    Text("Keep known ad domains out of your day.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PowerButton(state, onToggle)
                    OutlinedButton(onClick = onToggle) {
                        Text(when (state.connection) {
                            Connection.Connected -> "Turn off protection"
                            Connection.Connecting -> "Cancel connection"
                            Connection.Disconnected -> "Turn on protection"
                        })
                    }
                    Text(when (state.connection) {
                        Connection.Connected -> "Protection is on"
                        Connection.Connecting -> "Starting protection"
                        Connection.Disconnected -> "Tap to Protect"
                    }, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(if (state.connected) "DNS filtering is active. Tap to turn it off." else "Control DNS filtering with the button above.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
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
                        StatCard("Blocked requests", NumberFormat.getIntegerInstance().format(state.blocked), "DNS requests stopped", Icons.Outlined.GppGood, Modifier.weight(1f))
                        StatCard("Data Saved", String.format(Locale.getDefault(), "%.2f MB", state.savedMb), "Estimated, 50 KB / block", Icons.Outlined.DataUsage, Modifier.weight(1f))
                    }
                    ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = colors.surface)) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VerifiedUser, null, tint = colors.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Status", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                Text(if (state.connected) "Filtering active" else "Protection off", style = MaterialTheme.typography.titleMedium)
                            }
                            Box(Modifier.size(8.dp).background(if (state.connected) colors.primary else colors.outline, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("DNS", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
                ProtectionControls(state, preferences, onSettingsChange, onClearHistory)
                Text("All time", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Blocked", NumberFormat.getIntegerInstance().format(lifetime.blocked), "Saved on this device", Icons.Outlined.GppGood, Modifier.weight(1f))
                    StatCard("Queries", NumberFormat.getIntegerInstance().format(lifetime.queries), "DNS requests checked", Icons.Outlined.Dns, Modifier.weight(1f))
                }
                BrowseCard(state.connected, onOpenPrivateBrowser, onOpenOtherBrowser)
                FilterListsCard(filterLibrary, preferences, onSettingsChange, onUpdateFilters)
                HorizontalDivider(color = colors.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Local protection. No account needed.", style = MaterialTheme.typography.labelLarge)
                        Text("Domains are checked on-device. Allowed DNS queries go to ${preferences.dnsProvider.name}. Browsing traffic is not encrypted by Still.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text("${NumberFormat.getIntegerInstance().format(state.queries)} queries checked  /  ${state.failures} upstream failures", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
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
                .semantics { contentDescription = when (state.connection) {
                    Connection.Connected -> "Disconnect DNS protection"
                    Connection.Connecting -> "Cancel DNS connection"
                    Connection.Disconnected -> "Connect DNS protection"
                } }
                .clickable(role = Role.Button, onClick = onToggle), contentAlignment = Alignment.Center) {
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
import android.system.Os
import android.system.ErrnoException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.*

class AdBlockerService : VpnService() {
    companion object {
        const val STOP = "dev.still.dns.STOP"
        const val RELOAD = "dev.still.dns.RELOAD"
        val BLOCKLIST = FilterPolicy.ads
    }
    private var session: Session? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startup: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopProtection(); return START_NOT_STICKY }
        if (intent?.action == RELOAD) {
            session?.reloadSettings()
            if (session == null && startup?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        if (session != null || startup?.isActive == true) return START_NOT_STICKY
        ProtectionStore.update { it.copy(connection = Connection.Connecting, error = null) }
        try {
            foreground()
            startup = serviceScope.launch {
              try {
                // Load validated cached filters on an I/O worker before capturing DNS.
                FilterLibrary.get(this@AdBlockerService).load()
            val tunnel = Builder().setSession("Still DNS protection")
                // The DNS endpoint must not be a local interface address, or the
                // kernel can deliver queries locally instead of through the TUN.
                .setMtu(1500).addAddress("10.0.0.1", 32).addDnsServer("10.0.0.2")
                // DNS-only split route. A default route requires a complete TCP/IP forwarding stack.
                .addRoute("10.0.0.2", 32).allowFamily(OsConstants.AF_INET6)
                .setBlocking(false).setMeteredCompat()
                .establish() ?: error("VPN permission was revoked")
            val current = Session(tunnel)
            session = current
            ProtectionStore.update { it.copy(connection = Connection.Connected, lastDnsMillis = null, consecutiveFailures = 0) }
            current.start()
              } catch (cancelled: CancellationException) {
                  throw cancelled
              } catch (_: Exception) {
                  stopProtection("Unable to load filters or start the DNS tunnel. Try again.")
              }
            }
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
        startup?.cancel()
        startup = null
        session?.close()
        session = null
        ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = error) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { stopProtection("VPN permission was revoked or another VPN was started."); super.onRevoke() }
    override fun onDestroy() {
        serviceScope.cancel()
        session?.close(); session = null
        ProtectionStore.update { it.copy(connection = Connection.Disconnected) }
        super.onDestroy()
    }

    private inner class Session(private val tunnel: ParcelFileDescriptor) {
        private val running = AtomicBoolean(true)
        private val writeLock = Any()
        @Volatile private var settings = AppSettings.load(this@AdBlockerService)
        private val historyLock = Any()
        private val resolver = UpstreamDnsResolver(this@AdBlockerService, AppSettings.load(this@AdBlockerService).dnsProvider.address)
        private val workers = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128))
        private var reader: Thread? = null

        fun reloadSettings() = synchronized(historyLock) {
            settings = AppSettings.load(this@AdBlockerService)
            if (!settings.keepRecentDomains) ProtectionStore.update { it.copy(recentQueries = emptyList()) }
        }

        private fun record(query: PacketParser.Query, result: QueryResult, epoch: Long, millis: Long? = null) = synchronized(historyLock) {
            if (running.get()) ProtectionStore.record(query.question.name, result, settings.keepRecentDomains, millis, epoch)
        }

        fun start() {
            reader = thread(name = "still-tun-reader") {
                try {
                    val buffer = ByteArray(65535)
                    while (running.get()) {
                        // Nonblocking I/O keeps disconnect independent of incoming traffic.
                        val count = synchronized(writeLock) {
                            if (!running.get()) return@thread
                            try { Os.read(tunnel.fileDescriptor, buffer, 0, buffer.size) }
                            catch (error: ErrnoException) {
                                if (error.errno == OsConstants.EAGAIN) 0 else throw error
                            }
                        }
                        if (count < 0) break
                        if (count == 0) { Thread.sleep(20); continue }
                        val query = PacketParser.parse(buffer.copyOf(count)) ?: continue
                        val historyEpoch = ProtectionStore.historyEpoch
                        if (!query.destination.contentEquals(byteArrayOf(10, 0, 0, 2))) continue
                        ProtectionStore.update { it.copy(queries = it.queries + 1) }
                        val isBlocked = FilterPolicy.blocked(query.question.name, settings, FilterLibrary.get(this@AdBlockerService).state.value)
                        LifetimeStatistics.get(this@AdBlockerService).record(isBlocked)
                        if (isBlocked) {
                            write(query, PacketParser.response(query))
                            ProtectionStore.update { it.copy(blocked = it.blocked + 1) }
                            record(query, QueryResult.Blocked, historyEpoch)
                        } else {
                            try {
                                workers.execute {
                                    val started = android.os.SystemClock.elapsedRealtime()
                                    val response = try {
                                        resolver.resolve(query).also {
                                            record(query, QueryResult.Allowed, historyEpoch, android.os.SystemClock.elapsedRealtime() - started)
                                        }
                                    } catch (_: Exception) {
                                        if (running.get()) ProtectionStore.update { it.copy(failures = it.failures + 1) }
                                        record(query, QueryResult.Failed, historyEpoch)
                                        PacketParser.response(query, error = 2)
                                    }
                                    try { write(query, response) } catch (_: Exception) { failed() }
                                }
                            } catch (_: java.util.concurrent.RejectedExecutionException) {
                                ProtectionStore.update { it.copy(failures = it.failures + 1) }
                                record(query, QueryResult.Failed, historyEpoch)
                                write(query, PacketParser.response(query, error = 2))
                            }
                        }
                    }
                    if (running.get()) failed()
                } catch (_: Exception) { if (running.get()) failed() }
            }
        }

        private fun write(query: PacketParser.Query, dns: ByteArray) = synchronized(writeLock) {
            if (running.get()) {
                val packet = PacketParser.wrap(query, dns)
                Os.write(tunnel.fileDescriptor, packet, 0, packet.size)
            }
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
            // Wake the idle reader; the descriptor has a single owner and cannot block close.
            reader?.interrupt()
            synchronized(writeLock) {
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
import java.net.InetSocketAddress
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream

class UpstreamDnsResolver(
    private val address: String,
    private val protectUdp: (DatagramSocket) -> Boolean,
    private val protectTcp: (Socket) -> Boolean,
    private val port: Int = 53,
    private val timeoutMillis: Int = 800,
    private val backup: String = backupAddress(address)
) : Closeable {
    constructor(service: VpnService, address: String = "8.8.8.8") :
        this(address, { service.protect(it) }, { service.protect(it) })

    companion object {
        // Keep queries with the provider chosen in Settings.
        fun backupAddress(address: String): String = when (address) {
            "8.8.8.8" -> "8.8.4.4"
            "1.1.1.1" -> "1.0.0.1"
            "9.9.9.9" -> "149.112.112.112"
            else -> address
        }
    }

    private val sockets = mutableSetOf<Closeable>()
    private var closed = false

    fun resolve(query: PacketParser.Query): ByteArray {
        var failure: Exception? = null
        val endpoints = listOf(address, backup).distinct()
        for (endpoint in endpoints) {
            try {
                val dns = udp(query, endpoint)
                // Complete truncated replies here: the local tunnel handles UDP only.
                return if (PacketParser.u16(dns, 2) and 0x0200 != 0) tcp(query, endpoint) else dns
            } catch (error: Exception) {
                failure = error
            }
        }
        // Some networks drop UDP/53. Try TCP before failing the lookup.
        for (endpoint in endpoints) {
            try { return tcp(query, endpoint) } catch (error: Exception) { failure = error }
        }
        throw failure ?: IllegalStateException("No DNS endpoint available")
    }

    private fun register(socket: Closeable) = synchronized(sockets) {
        if (closed) { socket.close(); error("Resolver closed") }
        sockets.add(socket)
    }

    private fun validate(query: PacketParser.Query, dns: ByteArray): ByteArray {
        require(dns.size >= 12 && PacketParser.u16(dns, 0) == PacketParser.u16(query.dns, 0))
        require(PacketParser.u16(dns, 2) and 0xf800 == 0x8000)
        val question = PacketParser.question(dns)
        require(question?.name == query.question.name && question.type == query.question.type)
        val rcode = PacketParser.u16(dns, 2) and 15
        require(rcode != 2 && rcode != 5) { "Upstream DNS failed or refused the query" }
        return dns
    }

    private fun udp(query: PacketParser.Query, endpoint: String): ByteArray {
        val socket = DatagramSocket()
        register(socket)
        try {
            check(protectUdp(socket)) { "Cannot protect upstream DNS socket" }
            socket.soTimeout = timeoutMillis
            socket.connect(InetAddress.getByName(endpoint), port)
            // A connected socket accepts replies only from the selected upstream endpoint.
            socket.send(DatagramPacket(query.dns, query.dns.size))
            val buffer = ByteArray(65507)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            return validate(query, buffer.copyOf(reply.length))
        } finally {
            synchronized(sockets) { sockets.remove(socket) }
            socket.close()
        }
    }

    private fun tcp(query: PacketParser.Query, endpoint: String): ByteArray {
        val socket = Socket()
        register(socket)
        try {
            check(protectTcp(socket)) { "Cannot protect upstream DNS socket" }
            socket.soTimeout = timeoutMillis
            socket.connect(InetSocketAddress(endpoint, port), timeoutMillis)
            val output = DataOutputStream(socket.getOutputStream())
            output.writeShort(query.dns.size)
            output.write(query.dns)
            output.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            require(length in 12..65507)
            val dns = ByteArray(length)
            input.readFully(dns)
            validate(query, dns)
            require(PacketParser.u16(dns, 2) and 0x0200 == 0) { "Truncated TCP response" }
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
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

## settings.gradle.kts

```kotlin
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:0.10.6")
            }
        }
    }
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
systemProp.gradle.user.home=C\:/Users/Anuskar/.gradle
```

## gradle/wrapper/gradle-wrapper.properties

```properties
distributionBase=PROJECT
distributionPath=.gradle/wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
networkTimeout=120000
validateDistributionUrl=true
zipStoreBase=PROJECT
zipStorePath=.gradle/wrapper/dists
```

## app/src/main/res/drawable/ic_shield.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M12,2L3,6v6c0,5 4,8 9,10 5,-2 9,-5 9,-10V6z" />
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

## app/src/main/java/dev/still/dns/AppSettings.kt

```kotlin
package dev.still.dns

import android.content.Context

enum class Appearance { System, Light, Dark }
enum class DnsProvider(val address: String) {
    Google("8.8.8.8"), Cloudflare("1.1.1.1"), Quad9("9.9.9.9")
}

data class AppSettings(
    val appearance: Appearance = Appearance.System,
    val dynamicColors: Boolean = true,
    val dnsProvider: DnsProvider = DnsProvider.Google,
    val protectionLevel: ProtectionLevel = ProtectionLevel.Basic,
    val allowedDomains: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val keepRecentDomains: Boolean = false,
    val enabledSubscriptions: Set<String> = emptySet(),
    val autoUpdateFilters: Boolean = true,
    val notifyFilterUpdates: Boolean = true
) {
    fun save(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("appearance", appearance.name)
            .putBoolean("dynamicColors", dynamicColors)
            .putString("dnsProvider", dnsProvider.name)
            .putString("protectionLevel", protectionLevel.name)
            .putStringSet("allowedDomains", allowedDomains)
            .putStringSet("blockedDomains", blockedDomains)
            .putBoolean("keepRecentDomains", keepRecentDomains)
            .putStringSet("enabledSubscriptions", enabledSubscriptions)
            .putBoolean("autoUpdateFilters", autoUpdateFilters)
            .putBoolean("notifyFilterUpdates", notifyFilterUpdates).apply()
    }

    companion object {
        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return AppSettings(
                Appearance.entries.firstOrNull { it.name == prefs.getString("appearance", null) } ?: Appearance.System,
                prefs.getBoolean("dynamicColors", true),
                DnsProvider.entries.firstOrNull { it.name == prefs.getString("dnsProvider", null) } ?: DnsProvider.Google,
                ProtectionLevel.entries.firstOrNull { it.name == prefs.getString("protectionLevel", null) } ?: ProtectionLevel.Basic,
                prefs.getStringSet("allowedDomains", emptySet())!!.toSet(),
                prefs.getStringSet("blockedDomains", emptySet())!!.toSet(),
                prefs.getBoolean("keepRecentDomains", false),
                prefs.getStringSet("enabledSubscriptions", emptySet())!!.toSet(),
                prefs.getBoolean("autoUpdateFilters", true),
                prefs.getBoolean("notifyFilterUpdates", true)
            )
        }
    }
}
```

## app/src/test/java/dev/still/dns/UpstreamDnsResolverTest.kt

```kotlin
package dev.still.dns

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class UpstreamDnsResolverTest {
    private fun query(): PacketParser.Query {
        val dns = "1234010000010000000000000377777707796f757475626503636f6d0000010001"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return PacketParser.Query(byteArrayOf(10, 0, 0, 3), byteArrayOf(10, 0, 0, 2),
            12345, dns, PacketParser.question(dns)!!)
    }

    @Test fun usesBackupWhenPrimaryDoesNotReply() {
        val loopback = InetAddress.getByName("127.0.0.1")
        DatagramSocket(0, loopback).use { server ->
            DatagramSocket(server.localPort, InetAddress.getByName("127.0.0.2")).use {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val task = executor.submit {
                        server.soTimeout = 3000
                        val packet = DatagramPacket(ByteArray(512), 512)
                        server.receive(packet)
                        val answer = PacketParser.response(query())
                        server.send(DatagramPacket(answer, answer.size, packet.socketAddress))
                    }
                    UpstreamDnsResolver("127.0.0.2", { true }, { true }, server.localPort,
                        200, "127.0.0.1").use { resolver ->
                        assertArrayEquals(PacketParser.response(query()), resolver.resolve(query()))
                    }
                    task.get(3, TimeUnit.SECONDS)
                } finally { executor.shutdownNow() }
            }
        }
    }

    private fun checkTcpFallback(truncated: Boolean) {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 1, loopback).use { tcp ->
            DatagramSocket(tcp.localPort, loopback).use { udp ->
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val task = executor.submit {
                        if (truncated) {
                            udp.soTimeout = 3000
                            val packet = DatagramPacket(ByteArray(512), 512)
                            udp.receive(packet)
                            val answer = PacketParser.response(query())
                            PacketParser.put16(answer, 2, 0x8380)
                            udp.send(DatagramPacket(answer, answer.size, packet.socketAddress))
                        }
                        tcp.soTimeout = 3000
                        tcp.accept().use { socket ->
                            socket.soTimeout = 3000
                            val input = DataInputStream(socket.getInputStream())
                            val request = ByteArray(input.readUnsignedShort())
                            input.readFully(request)
                            assertArrayEquals(query().dns, request)
                            val answer = PacketParser.response(query())
                            val output = DataOutputStream(socket.getOutputStream())
                            output.writeShort(answer.size)
                            output.write(answer)
                            output.flush()
                        }
                    }
                    UpstreamDnsResolver("127.0.0.1", { true }, { true }, tcp.localPort, 500).use {
                        assertArrayEquals(PacketParser.response(query()), it.resolve(query()))
                    }
                    task.get(3, TimeUnit.SECONDS)
                } finally { executor.shutdownNow() }
            }
        }
    }

    @Test fun retriesOverTcpWhenUdpTimesOut() = checkTcpFallback(false)
    @Test fun completesTruncatedUdpReplyOverTcp() = checkTcpFallback(true)

    @Test fun preservesSelectedProvider() {
        assertEquals("8.8.4.4", UpstreamDnsResolver.backupAddress("8.8.8.8"))
        assertEquals("1.0.0.1", UpstreamDnsResolver.backupAddress("1.1.1.1"))
        assertEquals("149.112.112.112", UpstreamDnsResolver.backupAddress("9.9.9.9"))
    }

    @Test fun closedResolverRejectsRequests() {
        val resolver = UpstreamDnsResolver("127.0.0.1", { true }, { true })
        resolver.close()
        assertThrows(IllegalStateException::class.java) { resolver.resolve(query()) }
    }
}
```

## app/src/androidTest/java/dev/still/dns/FilterDownloadTest.kt

```kotlin
package dev.still.dns

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class FilterDownloadTest {
    @Test fun downloadsValidatedFiltersAndReopensSavedCopiesOnPhone() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "filter-network-test-").toFile()
        try {
            val repository = FilterRepository(directory)
            val selected = DownloadableFilter.entries.map { it.name }.toSet()
            repository.refresh(selected)
            for (filter in DownloadableFilter.entries) {
                val entry = repository.state.value.entries[filter]!!
                assertNull("${filter.name}: ${entry.error}", entry.error)
                assertTrue(entry.list!!.domains.size > 1000)
            }
            val cached = FilterRepository(directory, { error("Cache loading must not use the network") })
            cached.load()
            for (filter in DownloadableFilter.entries) {
                assertEquals(repository.state.value.entries[filter]!!.list, cached.state.value.entries[filter]!!.list)
            }
        } finally { directory.deleteRecursively() }
    }
}
```

## app/src/androidTest/java/dev/still/dns/PrivateBrowserTest.kt

```kotlin
package dev.still.dns

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PrivateBrowserTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val origin = "https://still-privacy-test.invalid"

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < end) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for private-browser state")
    }

    private fun evaluate(browser: WebView, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync { browser.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue("JavaScript callback", done.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun launchReady(): Pair<ActivityScenario<PrivateBrowserActivity>, WebView> {
        var supported = false
        instrumentation.runOnMainSync { supported = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA) }
        assumeTrue("Requires WebView browsing-data deletion support", supported)
        val scenario = ActivityScenario.launch(PrivateBrowserActivity::class.java)
        var browser: WebView? = null
        waitFor { scenario.onActivity { browser = findWebView(it.window.decorView) }; browser != null }
        scenario.onActivity {
            assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        instrumentation.runOnMainSync {
            browser!!.loadDataWithBaseURL(origin, "<html><body>Private storage test<script>window.loaded=true;</script></body></html>", "text/html", "UTF-8", null)
        }
        waitFor { evaluate(browser!!, "window.loaded === true") == "true" }
        return scenario to browser!!
    }

    private fun verifyErase(background: Boolean) {
        val (scenario, browser) = launchReady()
        try {
            evaluate(browser, "localStorage.setItem('privateMarker','secret'); sessionStorage.setItem('privateMarker','secret'); document.cookie='privateMarker=secret; Secure; SameSite=Strict'; 'done'")
            assertEquals("\"secret\"", evaluate(browser, "localStorage.getItem('privateMarker')"))
            assertTrue(evaluate(browser, "document.cookie").contains("privateMarker=secret"))
            evaluate(browser, "window.dbReady=false; var r=indexedDB.open('privateDatabase',1); r.onupgradeneeded=function(){r.result.createObjectStore('values')}; r.onsuccess=function(){r.result.close();window.dbReady=true};")
            waitFor { evaluate(browser, "window.dbReady") == "true" }
            if (background) scenario.moveToState(Lifecycle.State.CREATED)
            else scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitFor { scenario.state == Lifecycle.State.DESTROYED }
            var cookies: String? = null
            instrumentation.runOnMainSync { cookies = CookieManager.getInstance().getCookie(origin) }
            assertTrue("Session cookies erased", cookies.isNullOrEmpty())
        } finally { scenario.close() }

        val (fresh, newBrowser) = launchReady()
        try {
            assertEquals("null", evaluate(newBrowser, "localStorage.getItem('privateMarker')"))
            assertEquals("null", evaluate(newBrowser, "sessionStorage.getItem('privateMarker')"))
            assertFalse(evaluate(newBrowser, "document.cookie").contains("privateMarker"))
            evaluate(newBrowser, "window.databaseCount=-1; indexedDB.databases().then(function(d){window.databaseCount=d.length;});")
            waitFor { evaluate(newBrowser, "window.databaseCount") != "-1" }
            assertEquals("0", evaluate(newBrowser, "window.databaseCount"))
        } finally { fresh.close() }
    }

    @Test fun closeErasesCookiesLocalStorageSessionStorageAndIndexedDb() = verifyErase(false)
    @Test fun backgroundingEndsAndErasesSession() = verifyErase(true)
}
```

## app/src/main/java/dev/still/dns/BrowseCard.kt

```kotlin
package dev.still.dns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrowseCard(connected: Boolean, onPrivate: () -> Unit, onOther: () -> Unit) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.PrivacyTip, null, tint = MaterialTheme.colorScheme.primary)
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
```

## app/src/main/java/dev/still/dns/BrowserNavigation.kt

```kotlin
package dev.still.dns

import java.net.URI
import java.net.URLEncoder

object BrowserNavigation {
    fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() && uri.userInfo == null
    }.getOrDefault(false)

    fun destination(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (value.contains("://") || value.startsWith("javascript:", true) || value.startsWith("data:", true) ||
            value.startsWith("file:", true) || value.startsWith("intent:", true) || value.startsWith("content:", true)) {
            return value.takeIf(::isWebUrl)
        }
        val asUrl = "https://$value"
        if (!value.any(Char::isWhitespace) && value.substringBefore('/').contains('.') && isWebUrl(asUrl)) return asUrl
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(value, "UTF-8")
    }
}
```

## app/src/main/java/dev/still/dns/FilterLibrary.kt

```kotlin
package dev.still.dns

import android.content.Context
import java.io.File

object FilterLibrary {
    @Volatile private var instance: FilterRepository? = null
    fun get(context: Context): FilterRepository = instance ?: synchronized(this) {
        instance ?: FilterRepository(File(context.applicationContext.filesDir, "dns-filters")).also { instance = it }
    }
}
```

## app/src/main/java/dev/still/dns/FilterListsCard.kt

```kotlin
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
            Text("Add maintained domain lists to your protection level. Selected lists update daily when automatic updates are enabled in Settings. You can also download them now. Saved lists work offline.", style = MaterialTheme.typography.bodySmall)
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
```

## app/src/main/java/dev/still/dns/FilterPolicy.kt

```kotlin
package dev.still.dns

import java.net.IDN
import java.util.Locale

enum class ProtectionLevel(val title: String, val detail: String) {
    Basic("Basic", "Four ad-domain rules. Lowest risk of breaking sites."),
    Balanced("Balanced", "Adds a small set of advertising and analytics domains."),
    Strict("Strict", "Also blocks selected crash reporting and usage analytics. Some app features may stop working.")
}

/** Small, bundled starter lists, not a malware or complete advertising database. */
object FilterPolicy {
    val ads = setOf("ads.google.com", "doubleclick.net", "googlesyndication.com", "googleadservices.com")
    private val tracking = setOf("google-analytics.com", "adnxs.com", "criteo.com", "scorecardresearch.com", "quantserve.com")
    private val telemetry = setOf("app-measurement.com", "crashlytics.com", "bugsnag.com", "mixpanel.com", "amplitude.com")

    fun rules(level: ProtectionLevel): Set<String> = when (level) {
        ProtectionLevel.Basic -> ads
        ProtectionLevel.Balanced -> ads + tracking
        ProtectionLevel.Strict -> ads + tracking + telemetry
    }

    fun blocked(domain: String, settings: AppSettings, library: FilterLibraryState = FilterLibraryState()): Boolean {
        if (PacketParser.blocked(domain, settings.allowedDomains)) return false
        return PacketParser.blocked(domain, settings.blockedDomains) ||
            PacketParser.blocked(domain, rules(settings.protectionLevel)) ||
            library.blocked(domain, settings.enabledSubscriptions)
    }

    fun normalizeDomain(input: String): String? = runCatching {
        val domain = IDN.toASCII(input.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        require(domain.length <= 253 && '.' in domain)
        require(domain.split('.').all { label ->
            label.length in 1..63 && label.first() != '-' && label.last() != '-' &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        })
        require(!domain.all { it.isDigit() || it == '.' })
        domain
    }.getOrNull()
}
```

## app/src/main/java/dev/still/dns/FilterRepository.kt

```kotlin
package dev.still.dns

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class DownloadableFilter(val title: String, val detail: String, val fileName: String) {
    AdsTrackers("Ads and trackers", "HaGeZi Multi LIGHT: a compact list for advertising and tracking domains.", "light.txt"),
    Threats("Threat domains", "HaGeZi TIF Mini: domains listed for threats such as phishing and malware. Not a guarantee of safety.", "tif.mini.txt");

    val url get() = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/$fileName"
}

data class DomainList(val domains: Set<String>, val version: String) {
    fun matches(name: String): Boolean {
        var candidate = name
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}

object DomainListParser {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_RULES = 300_000

    /** Only the advertised wildcard-domain format is accepted, never browser rules. */
    fun parse(bytes: ByteArray): DomainList {
        require(bytes.size in 1..MAX_BYTES) { "Filter is empty or too large" }
        val domains = HashSet<String>()
        var expected: Int? = null
        var version = "Unknown"
        var ruleCount = 0
        bytes.toString(Charsets.UTF_8).lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("# Number of entries:") -> expected = line.substringAfter(':').trim().toIntOrNull()
                line.startsWith("# Version:") -> version = line.substringAfter(':').trim().take(80)
                line.isEmpty() || line.startsWith('#') -> Unit
                else -> {
                    require(line.startsWith("*.")) { "Unexpected filter format" }
                    val domain = FilterPolicy.normalizeDomain(line.substring(2)) ?: error("Invalid filter domain")
                    domains.add(domain)
                    ruleCount++
                    require(ruleCount <= MAX_RULES) { "Too many filter rules" }
                }
            }
        }
        require(expected != null && expected == ruleCount && domains.isNotEmpty()) { "Incomplete or empty filter download" }
        return DomainList(domains, version)
    }
}

data class FilterEntry(val list: DomainList? = null, val downloadedAt: Long = 0, val error: String? = null)
data class FilterLibraryState(
    val ready: Boolean = false,
    val updating: Boolean = false,
    val entries: Map<DownloadableFilter, FilterEntry> = emptyMap()
) {
    fun blocked(domain: String, enabled: Set<String>): Boolean = DownloadableFilter.entries.any {
        it.name in enabled && entries[it]?.list?.matches(domain) == true
    }
}

/** No browsing data is sent when downloading. Failed updates keep the last valid copy. */
class FilterRepository(
    private val directory: File,
    private val fetch: (String) -> ByteArray = ::downloadFilter,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(FilterLibraryState())
    val state = mutable.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mutable.value.ready) return@withLock
            directory.mkdirs()
            val entries = DownloadableFilter.entries.associateWith { filter ->
                val file = File(directory, filter.fileName)
                if (!file.exists()) FilterEntry() else try {
                    require(file.length() <= DomainListParser.MAX_BYTES)
                    FilterEntry(DomainListParser.parse(file.readBytes()), file.lastModified())
                } catch (_: Exception) { FilterEntry(error = "Saved filter could not be loaded. Download it again.") }
            }
            mutable.value = FilterLibraryState(ready = true, entries = entries)
        }
    }

    suspend fun refresh(selected: Set<String>) {
        load()
        withContext(Dispatchers.IO) {
            mutex.lock()
            try {
                mutable.value = mutable.value.copy(updating = true)
                DownloadableFilter.entries.filter { it.name in selected }.forEach { filter ->
                    val old = mutable.value.entries[filter] ?: FilterEntry()
                    val updated = try {
                        val bytes = fetch(filter.url)
                        val list = DomainListParser.parse(bytes)
                        val target = File(directory, filter.fileName)
                        val temp = File(directory, "${filter.fileName}.tmp")
                        try {
                            temp.outputStream().use { it.write(bytes) }
                            temp.setLastModified(now())
                            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        } finally { temp.delete() }
                        FilterEntry(list, target.lastModified())
                    } catch (_: Exception) {
                        old.copy(error = if (old.list == null) "Download failed. Check your connection and try again."
                            else "Update failed. Keeping the previous filter.")
                    }
                    mutable.value = mutable.value.copy(entries = mutable.value.entries + (filter to updated))
                }
            } finally {
                mutable.value = mutable.value.copy(updating = false)
                mutex.unlock()
            }
        }
    }
}

private fun downloadFilter(address: String): ByteArray {
    val connection = URL(address).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "text/plain")
        check(connection.responseCode == 200) { "Filter server unavailable" }
        check(connection.contentLengthLong <= DomainListParser.MAX_BYTES) { "Filter too large" }
        val started = System.nanoTime()
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                check((System.nanoTime() - started) / 1_000_000 < 60_000) { "Filter download timed out" }
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= DomainListParser.MAX_BYTES) { "Filter too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } finally { connection.disconnect() }
}
```

## app/src/main/java/dev/still/dns/PrivateBrowserActivity.kt

```kotlin
package dev.still.dns

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/** One disposable WebView session. No URL, page state or form is saved by the activity. */
class PrivateBrowserActivity : ComponentActivity() {
    private var browser: WebView? = null
    private var ready by mutableStateOf(false)
    private var ending by mutableStateOf(false)
    private var address by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)
    private var pageProgress by mutableIntStateOf(100)
    private var pageTitle by mutableStateOf("")
    private var canGoBack by mutableStateOf(false)
    private var canGoForward by mutableStateOf(false)
    private var blocked by mutableIntStateOf(0)
    private var cleanupSupported = false
    private var sessionStarted = false
    private var browserDestroyed = false
    private lateinit var preferences: AppSettings
    private lateinit var repository: FilterRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preferences = AppSettings.load(this)
        repository = FilterLibrary.get(this)
        ProtectionStore.beginPrivateSession()
        sessionStarted = true
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { closeAndErase() }
        })
        setContent {
            StillTheme(preferences) {
                Scaffold(topBar = {
                    Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Still private", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = ::closeAndErase, enabled = !ending) { Text("Close and erase") }
                        }
                        OutlinedTextField(value = address, onValueChange = { address = it.take(4000) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = ready && !ending,
                            placeholder = { Text("Search or enter HTTPS address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onGo = { navigate() }),
                            trailingIcon = { TextButton(onClick = ::navigate, enabled = ready && !ending) { Text("Go") } })
                        if (pageTitle.isNotBlank()) Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { browser?.goBack() }, enabled = ready && !ending && canGoBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                            IconButton(onClick = { browser?.goForward() }, enabled = ready && !ending && canGoForward) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Forward") }
                            IconButton(onClick = { browser?.reload() }, enabled = ready && !ending) { Icon(Icons.Outlined.Refresh, "Reload") }
                            Text("$blocked blocked", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Leaving this screen ends and erases the session.", style = MaterialTheme.typography.labelSmall)
                        if (pageProgress < 100 && ready) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets).imePadding()) {
                        if (ready && !ending) {
                            AndroidView(factory = { createBrowser() }, modifier = Modifier.fillMaxSize())
                            if (address.isEmpty()) {
                                Column(Modifier.align(Alignment.Center).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("A fresh private session", style = MaterialTheme.typography.headlineSmall)
                                    Text("No saved history, passwords or downloads. Cookies and website storage are erased when you leave. The DNS domain log is paused during this session.")
                                    Text("Private browsing does not hide your IP address from websites or your internet provider. Searches go to DuckDuckGo.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text(when {
                                ending -> "Erasing browsing data..."
                                message != null -> message!!
                                else -> "Preparing a clean session..."
                            }, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        }
                    }
                }
            }
        }
        cleanupSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
        if (!cleanupSupported) {
            message = "Update Android System WebView to use private browsing with complete website-data cleanup."
            return
        }
        // Erase leftovers from a crash or forced stop before opening any website.
        eraseWebData {
            lifecycleScope.launch {
                repository.load()
                if (!ending) ready = true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser(): WebView = WebView(this).also { view ->
        browser = view
        view.isSaveEnabled = false
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        view.setDownloadListener { _, _, _, _, _ -> message = "Downloads are not saved in private sessions." }
        view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) { pageTitle = title.orEmpty().take(200) }
            override fun onProgressChanged(view: WebView?, newProgress: Int) { pageProgress = newProgress }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!BrowserNavigation.isWebUrl(request.url.toString())) {
                    message = "Only HTTPS pages open in this private session. External app links stay closed."
                    return true
                }
                return false
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host?.lowercase() ?: return null
                if (FilterPolicy.blocked(host, preferences, repository.state.value)) {
                    runOnUiThread { blocked++ }
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by Still", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageTitle = ""
                if (url != null && url != "about:blank") address = url
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) message = "Page could not load. Check your connection or allow rules."
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: android.net.http.SslError?) {
                handler.cancel()
                message = "The site's secure connection could not be verified."
            }
        }
    }

    private fun navigate() {
        if (!ready || ending) return
        val destination = BrowserNavigation.destination(address)
        if (destination == null) { message = "Enter a search or a valid HTTPS address."; return }
        message = null
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        currentFocus?.clearFocus()
        browser?.loadUrl(destination)
    }

    private fun eraseWebData(done: () -> Unit) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            WebStorageCompat.deleteBrowsingData(WebStorage.getInstance(), mainExecutorCompat(), Runnable {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
                    done()
                }
            })
        } else {
            message = "Update Android System WebView for complete website-data cleanup."
            done()
        }
    }

    private fun mainExecutorCompat() = java.util.concurrent.Executor { runOnUiThread(it) }

    private fun destroyBrowser() {
        if (browserDestroyed) return
        browserDestroyed = true
        browser?.let {
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.clearHistory()
            it.clearFormData()
            it.destroy()
        }
        browser = null
        address = ""
        pageTitle = ""
    }

    private fun closeAndErase() {
        if (ending) return
        ending = true
        ready = false
        destroyBrowser()
        if (cleanupSupported) eraseWebData {
            finishSession()
            finish()
        } else {
            finishSession()
            finish()
        }
    }

    private fun finishSession() {
        if (sessionStarted) { sessionStarted = false; ProtectionStore.endPrivateSession() }
    }

    override fun onStop() {
        super.onStop()
        closeAndErase()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Run lifecycle bookkeeping, but retain no page or address state for restoration.
        outState.clear()
    }

    override fun onDestroy() {
        destroyBrowser()
        if (!ending && cleanupSupported) eraseWebData { finishSession() }
        else if (!cleanupSupported) finishSession()
        super.onDestroy()
    }
}
```

## app/src/main/java/dev/still/dns/ProtectionControls.kt

```kotlin
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
```

## app/src/test/java/dev/still/dns/BrowserNavigationTest.kt

```kotlin
package dev.still.dns

import org.junit.Assert.*
import org.junit.Test

class BrowserNavigationTest {
    @Test fun opensHttpsAddressesAndBareDomains() {
        assertEquals("https://example.com/path?q=test", BrowserNavigation.destination("example.com/path?q=test"))
        assertEquals("https://example.com", BrowserNavigation.destination(" https://example.com "))
    }
    @Test fun turnsSearchTermsIntoEncodedSearches() {
        assertEquals("https://duckduckgo.com/?q=weather+in+Nepal", BrowserNavigation.destination("weather in Nepal"))
    }
    @Test fun rejectsInsecureAndLocalSchemes() {
        for (url in listOf("http://example.com", "file:///etc/passwd", "content://contacts", "javascript:alert(1)",
            "intent://example.com", "data:text/html,Hello", "https://user:password@example.com")) {
            assertNull(url, BrowserNavigation.destination(url))
            assertFalse(url, BrowserNavigation.isWebUrl(url))
        }
    }
    @Test fun emptyAddressDoesNotNavigate() { assertNull(BrowserNavigation.destination(" ")) }
}
```

## app/src/test/java/dev/still/dns/FilterPolicyTest.kt

```kotlin
package dev.still.dns

import org.junit.Assert.*
import org.junit.Test

class FilterPolicyTest {
    @Test fun levelsIncreaseCoverageWithoutChangingBasic() {
        val basic = AppSettings()
        assertTrue(FilterPolicy.blocked("ads.google.com", basic))
        assertFalse(FilterPolicy.blocked("google-analytics.com", basic))
        assertTrue(FilterPolicy.blocked("google-analytics.com", basic.copy(protectionLevel = ProtectionLevel.Balanced)))
        assertFalse(FilterPolicy.blocked("crashlytics.com", basic.copy(protectionLevel = ProtectionLevel.Balanced)))
        assertTrue(FilterPolicy.blocked("crashlytics.com", basic.copy(protectionLevel = ProtectionLevel.Strict)))
    }

    @Test fun allowRulesOverrideBuiltInAndCustomBlocksIncludingSubdomains() {
        val settings = AppSettings(protectionLevel = ProtectionLevel.Strict,
            allowedDomains = setOf("doubleclick.net", "example.com"),
            blockedDomains = setOf("example.com", "blocked.example.com"))
        assertFalse(FilterPolicy.blocked("x.doubleclick.net", settings))
        assertFalse(FilterPolicy.blocked("blocked.example.com", settings))
        assertTrue(FilterPolicy.blocked("ads.google.com", settings))
    }

    @Test fun customRulesRespectLabelBoundaries() {
        val settings = AppSettings(blockedDomains = setOf("example.com"))
        assertTrue(FilterPolicy.blocked("x.example.com", settings))
        assertFalse(FilterPolicy.blocked("notexample.com", settings))
        assertFalse(FilterPolicy.blocked("example.com.other.org", settings))
    }

    @Test fun youtubeContentDomainsRemainAllowedAtEveryLevel() {
        for (level in ProtectionLevel.entries) {
            for (domain in listOf("youtube.com", "www.youtube.com", "i.ytimg.com", "rr1.googlevideo.com", "youtubei.googleapis.com")) {
                assertFalse("$level: $domain", FilterPolicy.blocked(domain, AppSettings(protectionLevel = level)))
            }
        }
    }

    @Test fun normalizesCaseTrailingDotAndInternationalDomains() {
        assertEquals("example.com", FilterPolicy.normalizeDomain("  EXAMPLE.COM. "))
        assertEquals("xn--bcher-kva.de", FilterPolicy.normalizeDomain("b\u00fccher.de"))
    }

    @Test fun rejectsUrlsWildcardsIpAddressesAndInvalidLabels() {
        for (input in listOf("", "localhost", "https://example.com", "example.com/path", "*.example.com",
            "1.1.1.1", "a..com", "-example.com", "example-.com", "a b.com", "a".repeat(64) + ".com")) {
            assertNull(input, FilterPolicy.normalizeDomain(input))
        }
    }
}
```

## app/src/test/java/dev/still/dns/FilterRepositoryTest.kt

```kotlin
package dev.still.dns

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilterRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun feed(vararg domains: String) = (
        "# Version: test-1\n# Number of entries: ${domains.size}\n" +
            domains.joinToString("\n") { "*.$it" }
        ).toByteArray()

    @Test fun parsesWildcardsAndMatchesBaseAndSubdomainsOnly() {
        val parsed = DomainListParser.parse(feed("ads.example.com"))
        assertEquals("test-1", parsed.version)
        assertTrue(parsed.matches("ads.example.com"))
        assertTrue(parsed.matches("img.ads.example.com"))
        assertFalse(parsed.matches("notads.example.com"))
        assertFalse(parsed.matches("ads.example.com.attacker.org"))
    }

    @Test fun rejectsEmptyTruncatedHtmlAndBrowserRules() {
        for (data in listOf(
            "", "<html>Error</html>", "# Number of entries: 2\n*.example.com",
            "# Number of entries: 1\n||example.com^", "# Number of entries: 1\n*.https://example.com",
            "# Number of entries: 0\n"
        )) assertThrows(Exception::class.java) { DomainListParser.parse(data.toByteArray()) }
    }

    @Test fun rejectsOversizedDownloads() {
        assertThrows(IllegalArgumentException::class.java) {
            DomainListParser.parse(ByteArray(DomainListParser.MAX_BYTES + 1))
        }
    }

    @Test fun enabledListFiltersAndAllowRuleWins() {
        val library = FilterLibraryState(ready = true, entries = mapOf(
            DownloadableFilter.AdsTrackers to FilterEntry(DomainListParser.parse(feed("example.com")))
        ))
        val settings = AppSettings(enabledSubscriptions = setOf("AdsTrackers"))
        assertTrue(FilterPolicy.blocked("sub.example.com", settings, library))
        assertFalse(FilterPolicy.blocked("sub.example.com", settings.copy(enabledSubscriptions = emptySet()), library))
        assertFalse(FilterPolicy.blocked("sub.example.com", settings.copy(allowedDomains = setOf("example.com")), library))
    }

    @Test fun persistsDownloadsAndLoadsThemWithoutNetwork() = runBlocking {
        val repo = FilterRepository(folder.root, { feed("example.com") }, { 1_700_000_000_000 })
        repo.refresh(setOf("AdsTrackers"))
        assertTrue(repo.state.value.ready)
        assertFalse(repo.state.value.updating)
        val reloaded = FilterRepository(folder.root, { error("Must not download while loading cache") })
        reloaded.load()
        assertTrue(reloaded.state.value.blocked("sub.example.com", setOf("AdsTrackers")))
        assertEquals(1_700_000_000_000, reloaded.state.value.entries[DownloadableFilter.AdsTrackers]!!.downloadedAt)
    }

    @Test fun failedUpdateKeepsLastGoodMemoryAndDiskCopies() = runBlocking {
        var reply = feed("example.com")
        val repo = FilterRepository(folder.root, { reply })
        repo.refresh(setOf("AdsTrackers"))
        reply = "# Number of entries: 2\n*.bad.example".toByteArray()
        repo.refresh(setOf("AdsTrackers"))
        assertTrue(repo.state.value.blocked("example.com", setOf("AdsTrackers")))
        assertNotNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        val reloaded = FilterRepository(folder.root)
        reloaded.load()
        assertTrue(reloaded.state.value.blocked("example.com", setOf("AdsTrackers")))
        assertFalse(reloaded.state.value.blocked("bad.example", setOf("AdsTrackers")))
    }

    @Test fun oneFailedListDoesNotPreventOtherListUpdating() = runBlocking {
        val repo = FilterRepository(folder.root, { url ->
            if (url.endsWith("/light.txt")) error("Offline") else feed("threat.example")
        })
        repo.refresh(setOf("AdsTrackers", "Threats"))
        assertNotNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        assertTrue(repo.state.value.blocked("threat.example", setOf("Threats")))
        assertFalse(repo.state.value.updating)
    }

    @Test fun refreshOnlyDownloadsSelectedLists() = runBlocking {
        val requested = mutableListOf<String>()
        val repo = FilterRepository(folder.root, { requested += it; feed("example.com") })
        repo.refresh(setOf("AdsTrackers"))
        assertEquals(listOf(DownloadableFilter.AdsTrackers.url), requested)
    }

    @Test fun corruptCacheIsReportedAndCanBeReplaced() = runBlocking {
        File(folder.root, "light.txt").writeText("broken")
        val repo = FilterRepository(folder.root, { feed("example.com") })
        repo.load()
        assertNotNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        repo.refresh(setOf("AdsTrackers"))
        assertNull(repo.state.value.entries[DownloadableFilter.AdsTrackers]!!.error)
        assertTrue(repo.state.value.blocked("example.com", setOf("AdsTrackers")))
    }

    @Test fun publishedFeedSnapshotsParseAndKeepYoutubeContentAvailable() {
        val directory = System.getenv("STILL_FILTER_FIXTURES")
        assumeTrue("Optional live-source verification: set STILL_FILTER_FIXTURES", directory != null)
        for (filter in DownloadableFilter.entries) {
            val parsed = DomainListParser.parse(Files.readAllBytes(File(directory!!, filter.fileName).toPath()))
            assertTrue(parsed.domains.size > 1000)
            for (domain in listOf("youtube.com", "www.youtube.com", "i.ytimg.com", "rr1.googlevideo.com", "youtubei.googleapis.com")) {
                assertFalse("${filter.name}: $domain", parsed.matches(domain))
            }
            println("${filter.name}: ${parsed.domains.size} domains, version ${parsed.version}")
        }
    }
}
```

## app/src/test/java/dev/still/dns/ProtectionStoreTest.kt

```kotlin
package dev.still.dns

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ProtectionStoreTest {
    @Before fun reset() { ProtectionStore.update { ProtectionState() } }

    @Test fun privateSessionDoesNotLogDomainsOrLateReplies() {
        ProtectionStore.record("before.example", QueryResult.Allowed, true)
        val before = ProtectionStore.historyEpoch
        ProtectionStore.beginPrivateSession()
        val during = ProtectionStore.historyEpoch
        ProtectionStore.record("private.example", QueryResult.Allowed, true, epoch = during)
        ProtectionStore.endPrivateSession()
        ProtectionStore.record("late-private.example", QueryResult.Allowed, true, epoch = during)
        ProtectionStore.record("late-before.example", QueryResult.Allowed, true, epoch = before)
        assertEquals(listOf("before.example"), ProtectionStore.state.value.recentQueries.map { it.domain })
        ProtectionStore.record("after.example", QueryResult.Allowed, true)
        assertEquals("after.example", ProtectionStore.state.value.recentQueries.first().domain)
    }

    @Test fun disabledHistoryDoesNotKeepDomains() {
        ProtectionStore.record("example.com", QueryResult.Allowed, false, 25)
        assertTrue(ProtectionStore.state.value.recentQueries.isEmpty())
        assertEquals(25L, ProtectionStore.state.value.lastDnsMillis)
    }

    @Test fun historyIsBoundedUniqueAndNewestFirst() {
        repeat(40) { ProtectionStore.record("site$it.example", QueryResult.Blocked, true) }
        assertEquals(30, ProtectionStore.state.value.recentQueries.size)
        assertEquals("site39.example", ProtectionStore.state.value.recentQueries.first().domain)
        ProtectionStore.record("site20.example", QueryResult.Allowed, true, 10)
        assertEquals(30, ProtectionStore.state.value.recentQueries.size)
        assertEquals(RecentQuery("site20.example", QueryResult.Allowed), ProtectionStore.state.value.recentQueries.first())
    }

    @Test fun disablingHistoryClearsExistingEntries() {
        ProtectionStore.record("example.com", QueryResult.Blocked, true)
        ProtectionStore.record("example.org", QueryResult.Allowed, false)
        assertTrue(ProtectionStore.state.value.recentQueries.isEmpty())
    }

    @Test fun blockedQueryDoesNotHideResolverFailuresButSuccessClearsThem() {
        ProtectionStore.record("example.com", QueryResult.Failed, false)
        ProtectionStore.record("example.org", QueryResult.Blocked, false)
        assertEquals(1, ProtectionStore.state.value.consecutiveFailures)
        ProtectionStore.record("example.net", QueryResult.Allowed, false, 40)
        assertEquals(0, ProtectionStore.state.value.consecutiveFailures)
    }
}
```

## scripts/New-StoreAssets.ps1

```powershell
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'store'
function Draw-Shield($graphics, [single]$left, [single]$top, [single]$size) {
    $saved = $graphics.Save()
    $graphics.TranslateTransform($left, $top)
    $graphics.ScaleTransform($size / 108, $size / 108)
    $shield = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $shield.AddLines([System.Drawing.PointF[]]@([System.Drawing.PointF]::new(54,24),[System.Drawing.PointF]::new(30,34),[System.Drawing.PointF]::new(30,52)))
    $shield.AddBezier(30,52,30,67,40,77,54,84)
    $shield.AddBezier(54,84,68,77,78,67,78,52)
    $shield.AddLines([System.Drawing.PointF[]]@([System.Drawing.PointF]::new(78,52),[System.Drawing.PointF]::new(78,34),[System.Drawing.PointF]::new(54,24)))
    $shield.CloseFigure()
    $gradient = [System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Point]::new(30,24),[System.Drawing.Point]::new(78,84),[System.Drawing.ColorTranslator]::FromHtml('#82F2C5'),[System.Drawing.ColorTranslator]::FromHtml('#54B8D2'))
    $graphics.FillPath($gradient, $shield)
    $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#123B36'),5)
    $pen.StartCap = $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $graphics.DrawLines($pen,[System.Drawing.PointF[]]@([System.Drawing.PointF]::new(42,53),[System.Drawing.PointF]::new(50,61),[System.Drawing.PointF]::new(66,44)))
    $pen.Dispose(); $gradient.Dispose(); $shield.Dispose()
    $graphics.Restore($saved)
}
foreach ($kind in @('icon','feature')) {
    $width = if ($kind -eq 'icon') { 512 } else { 1024 }
    $height = if ($kind -eq 'icon') { 512 } else { 500 }
    $bitmap = [System.Drawing.Bitmap]::new($width,$height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#0D2428'))
    if ($kind -eq 'icon') { Draw-Shield $graphics 0 0 512 }
    else {
        Draw-Shield $graphics 28 72 356
        $titleFont = [System.Drawing.Font]::new('Segoe UI',72,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel)
        $bodyFont = [System.Drawing.Font]::new('Segoe UI',29,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
        $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#F0F5F3'))
        $graphics.DrawString('still',$titleFont,$brush,420,130)
        $graphics.DrawString("Local DNS filtering.`nYour rules. Your control.",$bodyFont,$brush,425,245)
        $titleFont.Dispose(); $bodyFont.Dispose(); $brush.Dispose()
    }
    $bitmap.Save((Join-Path $assetDirectory "$kind.png"),[System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose()
}
```

## scripts/New-ReleaseKey.ps1

```powershell
param([string]$Keytool = 'keytool')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$signingDirectory = Join-Path $projectRoot 'signing'
$keyPath = Join-Path $signingDirectory 'still-release.jks'
$propertiesPath = Join-Path $signingDirectory 'release.properties'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw 'Signing material already exists. It will not be overwritten.'
}
New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $signingDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict signing directory permissions.' }
$random = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($random)
$env:STILL_KEY_PASSWORD = [Convert]::ToBase64String($random)
try {
    & $Keytool -genkeypair -noprompt -keystore $keyPath -storetype JKS -alias still -keyalg RSA -keysize 2048 -validity 10000 -storepass:env STILL_KEY_PASSWORD -keypass:env STILL_KEY_PASSWORD -dname 'CN=Still Android Release'
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    @("storeFile=signing/still-release.jks", "storePassword=$env:STILL_KEY_PASSWORD", 'keyAlias=still', "keyPassword=$env:STILL_KEY_PASSWORD") | Set-Content -LiteralPath $propertiesPath -Encoding ascii
} finally { Remove-Item Env:STILL_KEY_PASSWORD -ErrorAction SilentlyContinue }
Write-Output 'Release signing material created in signing/. Back up this folder securely. Do not publish it.'
```

## app/src/test/java/dev/still/dns/LifetimeCounterTest.kt

```kotlin
package dev.still.dns

import org.junit.Assert.*
import org.junit.Test
import kotlin.concurrent.thread

class LifetimeCounterTest {
    @Test fun resumesSavedTotalsAndCountsBothAllowedAndBlockedQueries() {
        var saved = LifetimeTotals(10, 20)
        val counter = LifetimeCounter(saved) { saved = it }
        counter.record(false)
        counter.record(true)
        assertEquals(LifetimeTotals(11, 22), saved)
        val reopened = LifetimeCounter(saved) { saved = it }
        reopened.record(true)
        assertEquals(LifetimeTotals(12, 23), reopened.state.value)
    }

    @Test fun concurrentEventsAreNotLost() {
        var saved = LifetimeTotals()
        val counter = LifetimeCounter(saved) { saved = it }
        val threads = List(4) { thread { repeat(500) { counter.record(it % 2 == 0) } } }
        threads.forEach { it.join() }
        assertEquals(LifetimeTotals(1000, 2000), saved)
        assertEquals(saved, counter.state.value)
    }

    @Test fun sessionResetDoesNotChangeLifetimeTotals() {
        val counter = LifetimeCounter(LifetimeTotals(5, 15)) { }
        ProtectionStore.update { it.copy(blocked = 0, queries = 0) }
        assertEquals(LifetimeTotals(5, 15), counter.state.value)
    }

    @Test fun totalsNeverOverflowToNegative() {
        val counter = LifetimeCounter(LifetimeTotals(Long.MAX_VALUE, Long.MAX_VALUE)) { }
        counter.record(true)
        assertEquals(LifetimeTotals(Long.MAX_VALUE, Long.MAX_VALUE), counter.state.value)
    }
}
```

## app/src/main/res/values/styles.xml

```xml
<resources>
    <style name="StillLicensesTheme" parent="Theme.AppCompat.DayNight.DarkActionBar">
        <item name="colorPrimary">#0D2428</item>
        <item name="colorAccent">#006D53</item>
    </style>
</resources>
```

## app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## app/src/main/res/drawable/ic_shield_outline.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="2" android:pathData="M12,3L4,6.5V12c0,4 3.5,7 8,9 4.5,-2 8,-5 8,-9V6.5z" />
</vector>
```

## app/src/main/res/drawable/ic_launcher_foreground.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M54,24L30,34V52C30,67 40,77 54,84C68,77 78,67 78,52V34Z">
        <aapt:attr name="android:fillColor"><gradient android:startX="30" android:startY="24" android:endX="78" android:endY="84" android:type="linear"><item android:offset="0" android:color="#82F2C5" /><item android:offset="1" android:color="#54B8D2" /></gradient></aapt:attr>
    </path>
    <path android:fillColor="#00000000" android:strokeColor="#123B36" android:strokeWidth="5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M42,53L50,61L66,44" />
</vector>
```

## app/src/main/res/drawable/ic_launcher_background.xml

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#0D2428" />
</shape>
```

## app/src/main/java/dev/still/dns/StillTileService.kt

```kotlin
package dev.still.dns

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class StillTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observer?.cancel()
        observer = scope.launch { ProtectionStore.state.collect { render(it) } }
    }

    private fun render(state: ProtectionState) {
        qsTile?.apply {
            label = "Still DNS"
            this.state = if (state.connection == Connection.Disconnected) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            icon = Icon.createWithResource(this@StillTileService,
                if (state.connected) R.drawable.ic_shield else R.drawable.ic_shield_outline)
            contentDescription = when (state.connection) {
                Connection.Connected -> "Still DNS on. Tap to turn off."
                Connection.Connecting -> "Still DNS connecting. Tap to cancel."
                Connection.Disconnected -> "Still DNS off. Tap to turn on."
            }
            if (Build.VERSION.SDK_INT >= 29) subtitle = when (state.connection) {
                Connection.Connected -> "On"
                Connection.Connecting -> "Connecting"
                Connection.Disconnected -> "Off"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        try {
            if (ProtectionStore.state.value.connection != Connection.Disconnected) {
                startService(Intent(this, AdBlockerService::class.java).setAction(AdBlockerService.STOP))
            } else if (!getSharedPreferences("onboarding", MODE_PRIVATE).getBoolean("vpnDisclosureAccepted", false) || VpnService.prepare(this) != null) {
                openDashboard()
            } else {
                ProtectionStore.update { it.copy(connection = Connection.Connecting, error = null) }
                ContextCompat.startForegroundService(this, Intent(this, AdBlockerService::class.java))
            }
        } catch (_: Exception) {
            ProtectionStore.update { it.copy(connection = Connection.Disconnected, error = "Open Still to start protection.") }
            openDashboard()
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is required below API 34; guarded below.
    private fun openDashboard() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 3, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else startActivityAndCollapse(intent)
    }

    override fun onStopListening() { observer?.cancel(); super.onStopListening() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
```

## app/src/main/java/dev/still/dns/SettingsScreen.kt

```kotlin
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
```

## app/src/main/java/dev/still/dns/OnboardingActivity.kt

```kotlin
package dev.still.dns

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StillTheme(AppSettings.load(this)) {
                val pager = rememberPagerState { 3 }
                val scope = rememberCoroutineScope()
                Scaffold { insets ->
                    Column(Modifier.fillMaxSize().padding(insets).padding(24.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("still", style = MaterialTheme.typography.headlineMedium)
                            TextButton(onClick = ::finishOnboarding) { Text("Skip") }
                        }
                        HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                val pulse = rememberInfiniteTransition(label = "welcome")
                                val scale by pulse.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "shield")
                                Icon(Icons.Outlined.Shield, null, Modifier.size(112.dp).scale(scale), tint = MaterialTheme.colorScheme.primary)
                                Text(listOf("A quieter internet.", "Local filtering. Clear limits.", "You're in control.")[page],
                                    style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                                Text(listOf(
                                    "Still filters known ad and tracking domains. No account is needed. Choose the lists and rules that work for you.",
                                    "Still uses Android's VPN permission to inspect DNS names on this device. Allowed queries are sent unencrypted to your selected DNS provider. Websites and filter downloads also use the internet. Still does not hide your IP address or encrypt browsing traffic.",
                                    "Turn protection on or off at any time. Add Still DNS to Quick Settings for faster access. Enabled filter lists can update daily. Read the privacy policy in About before you start."
                                )[page], style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            }
                        }
                        Text("${pager.currentPage + 1} of 3", modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            if (pager.currentPage == 2) finishOnboarding()
                            else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (pager.currentPage == 2) "Get started" else "Next") }
                    }
                }
            }
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences("onboarding", MODE_PRIVATE).edit().putBoolean("complete", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
```

## app/src/main/java/dev/still/dns/LifetimeStatistics.kt

```kotlin
package dev.still.dns

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LifetimeTotals(val blocked: Long = 0, val queries: Long = 0)

/** A single writer prevents lost updates and stale settings from resetting totals. */
class LifetimeCounter(initial: LifetimeTotals, private val persist: (LifetimeTotals) -> Unit) {
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()

    @Synchronized fun record(blocked: Boolean) {
        val old = mutable.value
        val next = LifetimeTotals(
            if (blocked && old.blocked < Long.MAX_VALUE) old.blocked + 1 else old.blocked,
            if (old.queries < Long.MAX_VALUE) old.queries + 1 else old.queries
        )
        persist(next)
        mutable.value = next
    }
}

object LifetimeStatistics {
    @Volatile private var instance: LifetimeCounter? = null
    fun get(context: Context): LifetimeCounter = instance ?: synchronized(this) {
        instance ?: run {
            val prefs = context.applicationContext.getSharedPreferences("lifetime-statistics", Context.MODE_PRIVATE)
            LifetimeCounter(LifetimeTotals(prefs.getLong("blocked", 0), prefs.getLong("queries", 0))) {
                prefs.edit().putLong("blocked", it.blocked).putLong("queries", it.queries).apply()
            }.also { instance = it }
        }
    }
}
```

## app/src/main/java/dev/still/dns/FilterUpdateWorker.kt

```kotlin
package dev.still.dns

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class FilterUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettings.load(applicationContext)
        val selected = DownloadableFilter.entries.filter { it.name in settings.enabledSubscriptions }
        if (!settings.autoUpdateFilters || selected.isEmpty()) return Result.success()
        val repository = FilterLibrary.get(applicationContext)
        repository.refresh(selected.map { it.name }.toSet())
        val entries = repository.state.value.entries
        if (selected.any { entries[it]?.list == null || entries[it]?.error != null }) return Result.retry()
        if (settings.notifyFilterUpdates) notifyUpdated()
        return Result.success()
    }

    private fun notifyUpdated() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("filter-updates", "Filter updates", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(applicationContext, 2, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(2, NotificationCompat.Builder(applicationContext, "filter-updates")
            .setSmallIcon(R.drawable.ic_shield).setContentTitle("Filter lists updated")
            .setContentText("Your enabled DNS lists are ready.").setContentIntent(open).setAutoCancel(true).build())
    }

    companion object {
        fun schedule(context: Context, settings: AppSettings) {
            val manager = WorkManager.getInstance(context)
            if (!settings.autoUpdateFilters || settings.enabledSubscriptions.isEmpty()) {
                manager.cancelUniqueWork("daily-filter-update")
                return
            }
            val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            manager.enqueueUniquePeriodicWork("daily-filter-update", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
```

## app/src/main/java/dev/still/dns/AboutScreen.kt

```kotlin
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
                Text("Optional HaGeZi Multi LIGHT and TIF Mini lists are downloaded from HaGeZi's DNS blocklists repository. The project distributes these lists under GPL-3.0. Source and licence are available below.")
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
```

## app/src/main/assets/privacy-policy.txt

```text
Still privacy policy
Effective date: 16 September 2026

Still is maintained by the owner of the Anuskar123/still-ad-blocker GitHub repository. For privacy questions, contact the maintainer through that repository. Do not post browsing history or other sensitive information in a public issue.

Local DNS filtering
Still uses Android's VPN interface to inspect DNS requests and compare domain names with rules stored on your device. It does not route all browsing traffic through a developer-operated VPN server. Still does not hide your IP address or provide an encrypted VPN connection.

Information sent over the network
Allowed DNS queries are sent unencrypted to the DNS provider you select: Google, Cloudflare or Quad9. That provider and your network operator may observe these queries and your IP address. Optional filter downloads connect over HTTPS to GitHub's content servers, which receive ordinary connection information such as your IP address. Websites you visit and search providers receive your requests. These services have their own privacy policies.

Information kept on your device
Still stores preferences, custom domain rules, downloaded filter lists and aggregate lifetime query/block counts locally. Session totals and optional recent-domain history are held in memory. Recent-domain history is off by default and limited to the most recent 30 distinct domains. Turning it off or using Clear history removes the visible history. The private browser suppresses new recent-domain recording during its session.

Private browser
The private browser uses Android System WebView. Leaving it clears cookies, website storage and browsing history; after a forced stop, cleanup runs before the next session. Still blocks private browsing when WebView cannot perform the required cleanup. Visited sites and the search provider still receive network requests. Private browsing does not make you anonymous to websites, your network or DNS providers.

Background updates and notifications
When enabled, selected filter lists update approximately daily when Android permits background work and internet access is available. Disable automatic updates or update notifications in Settings. Protection uses a foreground notification while running.

Developer collection
Still has no account system, advertising SDK or developer-operated analytics endpoint. The app does not send browsing history, domain logs, or aggregate statistics to the developer. Android, your app store, WebView, DNS providers and visited sites may process information under their own policies.

Your controls
Turn off protection in the app, notification or Quick Settings tile. Change rules or clear recent history in the dashboard. Reset session counts in Settings. Clear Still's app storage or uninstall it to delete local preferences, rules, downloaded filters and lifetime totals. Android backup is disabled.

Children and general browsing
Still is a general-purpose utility with a browser that can access the open web. Threat lists are not parental controls and do not guarantee that content is safe or suitable for children.

Changes
This policy should be updated when app behavior or data practices change. Review the policy included with the app version you use.
```
