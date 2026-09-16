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
