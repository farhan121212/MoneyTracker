package com.farhan.paisa.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.farhan.paisa.R

object Pop {
    val Ink = Color(0xFF0B0B0E)
    val Surface = Color(0xFF17171B)
    val Raised = Color(0xFF232329)
    val Hairline = Color(0xFF2A2A31)

    val Text = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA3A3AE)
    val TextMuted = Color(0xFF74747F)
    /** Text and icons on top of a bright colour block. */
    val OnPop = Color(0xFF111114)
    val OnPopSoft = Color(0xB3111114)

    val Lime = Color(0xFFC6F24E)
    val Lavender = Color(0xFFB9B5FF)
    val Tangerine = Color(0xFFFFB057)
    val Pink = Color(0xFFFF8CC6)
    val Sky = Color(0xFF8FD8FF)
    val Mint = Color(0xFF7EEBC1)
    val Butter = Color(0xFFFFE27A)
    val Orchid = Color(0xFFE5A3FF)

    /** Reserved for state. Always shown with an icon and words, never colour alone. */
    val Good = Color(0xFF7EEBC1)
    val Warning = Color(0xFFFFC56B)
    val Danger = Color(0xFFFF6B6B)

    val Accents = listOf(Lime, Lavender, Tangerine, Pink, Sky, Mint, Butter, Orchid)

    /**
     * Deeper versions of [Accents] for multi-series charts, validated for colour blindness and
     * contrast against [Surface]. A category keeps its slot, so it never changes colour.
     */
    val Chart = listOf(
        Color(0xFF7DA405),
        Color(0xFF8A7BFE),
        Color(0xFFDD7303),
        Color(0xFFFF05A9),
        Color(0xFF009EDB),
        Color(0xFF05AC7D),
        Color(0xFFB48F00),
        Color(0xFFDC27FF),
    )
    val ChartNeutral = Color(0xFF6F6F7A)

    fun accent(index: Int): Color = Accents[index.mod(Accents.size)]
    fun categoryPop(index: Int): Color = if (index in Accents.indices) Accents[index] else Color(0xFFC9C9D2)
    fun categoryChart(index: Int): Color = if (index in Chart.indices) Chart[index] else ChartNeutral
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Jakarta = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

private val PaisaTypography = Typography(
    displayLarge = style(46, 52, FontWeight.ExtraBold, -0.035),
    displayMedium = style(36, 42, FontWeight.ExtraBold, -0.03),
    headlineLarge = style(32, 38, FontWeight.ExtraBold, -0.025),
    headlineMedium = style(24, 30, FontWeight.Bold, -0.02),
    titleLarge = style(20, 26, FontWeight.Bold, -0.015),
    titleMedium = style(16, 22, FontWeight.SemiBold, -0.01),
    titleSmall = style(15, 20, FontWeight.SemiBold, -0.005),
    bodyLarge = style(15, 22, FontWeight.Medium),
    bodyMedium = style(14, 20, FontWeight.Medium),
    bodySmall = style(13, 18, FontWeight.Medium),
    labelLarge = style(14, 18, FontWeight.Bold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 14, FontWeight.SemiBold),
)

private val Scheme = darkColorScheme(
    primary = Pop.Lime,
    onPrimary = Pop.OnPop,
    primaryContainer = Pop.Lime,
    onPrimaryContainer = Pop.OnPop,
    secondary = Pop.Lavender,
    onSecondary = Pop.OnPop,
    secondaryContainer = Pop.Raised,
    onSecondaryContainer = Pop.Text,
    tertiary = Pop.Tangerine,
    onTertiary = Pop.OnPop,
    background = Pop.Ink,
    onBackground = Pop.Text,
    surface = Pop.Ink,
    onSurface = Pop.Text,
    onSurfaceVariant = Pop.TextSecondary,
    surfaceContainerLowest = Pop.Ink,
    surfaceContainerLow = Pop.Surface,
    surfaceContainer = Pop.Surface,
    surfaceContainerHigh = Pop.Surface,
    surfaceContainerHighest = Pop.Raised,
    outline = Pop.Hairline,
    outlineVariant = Pop.Hairline,
    error = Pop.Danger,
)

@Composable
fun PaisaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = PaisaTypography) {
        CompositionLocalProvider(LocalContentColor provides Pop.Text, content = content)
    }
}
