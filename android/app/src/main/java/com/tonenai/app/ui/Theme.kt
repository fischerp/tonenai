package com.tonenai.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The TonenAI palette, carried over from the desktop app: near-black panels, one red accent. */
object Tone {
    val bg = Color(0xFF0B0B0F)
    val panel = Color(0xFF16161D)
    val raised = Color(0xFF1F1F29)
    val line = Color(0xFF2A2A36)
    val text = Color(0xFFF4F4F6)
    val muted = Color(0xFF9A9AA8)
    val accent = Color(0xFFE11D2E)
    val accentBright = Color(0xFFFF3B47)
    val accentDeep = Color(0xFFB20710)
    val ok = Color(0xFF3DDC84)
    val accentBrush = Brush.linearGradient(listOf(accentBright, accentDeep))
}

private val scheme = darkColorScheme(
    primary = Tone.accent, onPrimary = Color.White,
    secondary = Tone.accentBright, onSecondary = Color.White,
    background = Tone.bg, onBackground = Tone.text,
    surface = Tone.bg, onSurface = Tone.text,
    surfaceVariant = Tone.panel, onSurfaceVariant = Tone.muted,
    surfaceContainer = Tone.panel, surfaceContainerHigh = Tone.raised, surfaceContainerHighest = Tone.raised,
    outline = Tone.line, outlineVariant = Tone.line,
    error = Color(0xFFFF8A85), inverseSurface = Tone.text, inverseOnSurface = Tone.bg,
)

private fun style(size: Int, weight: FontWeight, spacing: Double = 0.0, line: Int = (size * 1.35).toInt()) =
    TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp, lineHeight = line.sp)

private val type = Typography(
    displaySmall = style(34, FontWeight.ExtraBold, -0.8, 38),
    headlineMedium = style(26, FontWeight.ExtraBold, -0.5, 30),
    headlineSmall = style(22, FontWeight.Bold, -0.3, 27),
    titleLarge = style(19, FontWeight.Bold, -0.2),
    titleMedium = style(16, FontWeight.SemiBold),
    titleSmall = style(14, FontWeight.SemiBold),
    bodyLarge = style(16, FontWeight.Normal),
    bodyMedium = style(14, FontWeight.Normal),
    bodySmall = style(12, FontWeight.Normal),
    labelLarge = style(14, FontWeight.SemiBold),
    labelMedium = style(12, FontWeight.SemiBold, 0.2),
    labelSmall = style(11, FontWeight.Bold, 1.1),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TonenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
