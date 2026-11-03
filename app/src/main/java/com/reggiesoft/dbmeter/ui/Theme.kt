package com.reggiesoft.dbmeter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.reggiesoft.dbmeter.R

val BACKGROUND = Color(0xFF101517)
val PANEL = Color(0xFF191F22)
val INSET = Color(0xFF0E1214)
val TEAL = Color(0xFF00E5C5)
val AMBER = Color(0xFFFFBE35)
val CORAL = Color(0xFFFFAFAE)
val MUTED = Color(0xFF9AA6AC)
val INTER = FontFamily(
    Font(R.font.inter_regular),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)
val MONO = FontFamily(Font(R.font.jetbrains_mono))
private val COLORS = darkColorScheme(
    primary = TEAL, onPrimary = INSET, secondary = AMBER, tertiary = CORAL,
    background = BACKGROUND, surface = PANEL, surfaceVariant = Color(0xFF20272C),
    onSurface = Color(0xFFE5EAED), onBackground = Color(0xFFE5EAED),
    onSurfaceVariant = MUTED, outline = Color(0xFF4C5C61), outlineVariant = Color(0xFF293237)
)

private val TYPOGRAPHY = createTypography()

private fun createTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = INTER),
        displayMedium = base.displayMedium.copy(fontFamily = INTER),
        displaySmall = base.displaySmall.copy(fontFamily = INTER),
        headlineLarge = base.headlineLarge.copy(fontFamily = INTER),
        headlineMedium = base.headlineMedium.copy(fontFamily = INTER),
        headlineSmall = base.headlineSmall.copy(fontFamily = INTER),
        titleLarge = base.titleLarge.copy(fontFamily = INTER),
        titleMedium = base.titleMedium.copy(fontFamily = INTER),
        titleSmall = base.titleSmall.copy(fontFamily = INTER),
        bodyLarge = base.bodyLarge.copy(fontFamily = INTER),
        bodyMedium = base.bodyMedium.copy(fontFamily = INTER),
        bodySmall = base.bodySmall.copy(fontFamily = INTER),
        labelLarge = base.labelLarge.copy(fontFamily = INTER),
        labelMedium = base.labelMedium.copy(fontFamily = INTER),
        labelSmall = base.labelSmall.copy(fontFamily = INTER)
    )
}

@Composable
fun SoundCaliperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = COLORS, typography = TYPOGRAPHY, content = content)
}
