@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package lol.djungelskog.hcbx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.djungelskog.hcbx.data.Settings
import lol.djungelskog.hcbx.data.ThemeMode

// HCB red-based fallback palette for devices without dynamic color
private val Red = Color(0xFFEC3750)
private val LightScheme = lightColorScheme(
    primary = Color(0xFFBE1B38), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD9), onPrimaryContainer = Color(0xFF410010),
    secondary = Color(0xFF76565A), secondaryContainer = Color(0xFFFFD9DC), onSecondaryContainer = Color(0xFF2C1519),
    tertiary = Color(0xFF3A6A8E), tertiaryContainer = Color(0xFFCDE5FF), onTertiaryContainer = Color(0xFF001D32),
    background = Color(0xFFFFF8F7), surface = Color(0xFFFFF8F7),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF0F0), surfaceContainer = Color(0xFFFCE9EA),
    surfaceContainerHigh = Color(0xFFF6E3E4), surfaceContainerHighest = Color(0xFFF0DEDF),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB3B4), onPrimary = Color(0xFF680020),
    primaryContainer = Color(0xFF92001F), onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFFE5BDC0), secondaryContainer = Color(0xFF5C3F43), onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = Color(0xFFA2C9F1), tertiaryContainer = Color(0xFF204A6D), onTertiaryContainer = Color(0xFFCDE5FF),
    background = Color(0xFF1A1112), surface = Color(0xFF1A1112),
    surfaceContainerLowest = Color(0xFF140C0D), surfaceContainerLow = Color(0xFF22191A), surfaceContainer = Color(0xFF271D1E),
    surfaceContainerHigh = Color(0xFF322828), surfaceContainerHighest = Color(0xFF3D3233),
)

val Green = Color(0xFF33A854)
val Amber = Color(0xFFF5A623)
val Blue = Color(0xFF338EDA)
val Purple = Color(0xFF9A5FD4)
val Danger = Red
val Muted = Color(0xFF8C8C8C)

@Composable
fun isDark(): Boolean = when (Settings.themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

private val base = Typography()
// Expressive typography: heavier, tighter display styles
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun HcbTheme(content: @Composable () -> Unit) {
    val dark = isDark()
    val ctx = LocalContext.current
    val scheme = if (Settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) DarkScheme else LightScheme
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
