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
