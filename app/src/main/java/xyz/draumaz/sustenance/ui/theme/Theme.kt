package xyz.draumaz.sustenance.ui.theme

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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.draumaz.sustenance.R

@OptIn(ExperimentalTextApi::class)
private fun createGoogleSansFlexFont(
    weight: FontWeight = FontWeight.Normal,
): Font {
    return Font(
        resId = R.font.google_sans_flex,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.width(100f),
            FontVariation.slant(0f),
            FontVariation.Setting("ROND", 0f),
        )
    )
}

val GoogleSansFlexFontFamily = FontFamily(
    createGoogleSansFlexFont(FontWeight.Thin),
    createGoogleSansFlexFont(FontWeight.ExtraLight),
    createGoogleSansFlexFont(FontWeight.Light),
    createGoogleSansFlexFont(FontWeight.Normal),
    createGoogleSansFlexFont(FontWeight.Medium),
    createGoogleSansFlexFont(FontWeight.SemiBold),
    createGoogleSansFlexFont(FontWeight.Bold),
    createGoogleSansFlexFont(FontWeight.ExtraBold),
    createGoogleSansFlexFont(FontWeight.Black),
)

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexVariationSettings = FontVariation.Settings(
    FontVariation.weight(400),
    FontVariation.width(100f),
    FontVariation.slant(0f),
    FontVariation.Setting("ROND", 0f),
)

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

@OptIn(ExperimentalTextApi::class)
private fun TextStyle.withGoogleSansFlex(
    weight: FontWeight? = null
): TextStyle {
    val targetWeight = weight ?: this.fontWeight ?: FontWeight.Normal
    return this.copy(
        fontFamily = GoogleSansFlexFontFamily,
        fontWeight = targetWeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontFeatureSettings = "'wght' ${targetWeight.weight}, 'wdth' 100, 'slnt' 0, 'ROND' 0",
    )
}

private val defaultTypography = Typography()

private val VitalsTypography = Typography(
    displayLarge = defaultTypography.displayLarge.withGoogleSansFlex(),
    displayMedium = defaultTypography.displayMedium.withGoogleSansFlex(),
    displaySmall = defaultTypography.displaySmall.withGoogleSansFlex(FontWeight.Black).copy(
        letterSpacing = (-1).sp
    ),
    headlineLarge = defaultTypography.headlineLarge.withGoogleSansFlex(FontWeight.Black).copy(
        letterSpacing = (-1.5).sp,
        fontSize = 42.sp,
        lineHeight = 48.sp
    ),
    headlineMedium = defaultTypography.headlineMedium.withGoogleSansFlex(FontWeight.ExtraBold).copy(
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = defaultTypography.headlineSmall.withGoogleSansFlex(),
    titleLarge = defaultTypography.titleLarge.withGoogleSansFlex(FontWeight.Bold),
    titleMedium = defaultTypography.titleMedium.withGoogleSansFlex(),
    titleSmall = defaultTypography.titleSmall.withGoogleSansFlex(),
    bodyLarge = defaultTypography.bodyLarge.withGoogleSansFlex(),
    bodyMedium = defaultTypography.bodyMedium.withGoogleSansFlex(),
    bodySmall = defaultTypography.bodySmall.withGoogleSansFlex(),
    labelLarge = defaultTypography.labelLarge.withGoogleSansFlex(),
    labelMedium = defaultTypography.labelMedium.withGoogleSansFlex(FontWeight.ExtraBold).copy(
        letterSpacing = 0.5.sp
    ),
    labelSmall = defaultTypography.labelSmall.withGoogleSansFlex(),
)

@Composable
fun SustenanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(colorScheme = colors, typography = VitalsTypography, content = content)
}
