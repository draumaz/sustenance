package dev.easonhuang.sustenance.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Fallback palette (teal "vitals" brand) used on devices without Material You dynamic color.
private val BrandDark = darkColorScheme(
    primary = Color(0xFF5EDDC4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005044),
    onPrimaryContainer = Color(0xFF7DF9DF),
    secondary = Color(0xFFB1CCC4),
    tertiary = Color(0xFFADC9E6),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E0),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainer = Color(0xFF1A2320),
    surfaceContainerHigh = Color(0xFF242E2A),
    outlineVariant = Color(0xFF3F4945),
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF006B5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7DF9DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635C),
    tertiary = Color(0xFF436278),
    background = Color(0xFFF4FBF7),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFF4FBF7),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainer = Color(0xFFE9F0EC),
    surfaceContainerHigh = Color(0xFFE3EBE6),
    outlineVariant = Color(0xFFBFC9C3),
)

private val VitalsTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, fontSize = 42.sp, lineHeight = 48.sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
    )
}

@Composable
fun SustenanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(colorScheme = colors, typography = VitalsTypography, content = content)
}
