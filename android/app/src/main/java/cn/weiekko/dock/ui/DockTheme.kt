package cn.weiekko.dock.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0E0E0E)
private val Panel = Color(0xFF181818)
private val Ivory = Color(0xFFE8E4DC)
private val Mute = Color(0xFF8E8A82)
private val Accent = Color(0xFFD4C4A8)
private val Danger = Color(0xFFE8A0A0)

private val DockColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2A261C),
    onPrimaryContainer = Ivory,
    background = Ink,
    onBackground = Ivory,
    surface = Panel,
    onSurface = Ivory,
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Mute,
    outline = Color(0xFF333333),
    error = Danger,
    onError = Ink,
    errorContainer = Color(0xFF2A1C1C),
    onErrorContainer = Danger,
)

private val DockTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 112.sp,
        lineHeight = 112.sp,
        letterSpacing = (-4).sp,
        color = Ivory,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
        color = Ivory,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Ivory,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Mute,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Mute,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Mute,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        color = Mute,
    ),
)

private val DockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun DockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DockColors,
        typography = DockTypography,
        shapes = DockShapes,
        content = content,
    )
}
