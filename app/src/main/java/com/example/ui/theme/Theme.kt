package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TacticalDarkColorScheme = darkColorScheme(
    primary = CyanSensor,
    onPrimary = Color(0xFF002026),
    primaryContainer = CyanSensorContainer,
    onPrimaryContainer = CyanSensor,
    secondary = AmberTrigger,
    onSecondary = Color(0xFF281900),
    secondaryContainer = AmberTriggerContainer,
    onSecondaryContainer = AmberTrigger,
    tertiary = EmeraldPulse,
    onTertiary = Color(0xFF002912),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldPulse,
    error = CoralAlert,
    onError = Color(0xFF330005),
    errorContainer = CoralContainer,
    onErrorContainer = CoralAlert,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceHigh,
    surfaceContainerHigh = SlateSurfaceHighest,
    outline = SlateOutline,
    outlineVariant = Color(0xFF1E2D3D)
)

private val TacticalLightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = LightCyanContainer,
    onPrimaryContainer = Color(0xFF003640),
    secondary = LightAmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = Color(0xFF331D00),
    tertiary = Color(0xFF008F4C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD0F7E2),
    onTertiaryContainer = Color(0xFF00381C),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

val PixelTapShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PixelTapTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TacticalDarkColorScheme else TacticalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PixelTapShapes,
        content = content
    )
}
