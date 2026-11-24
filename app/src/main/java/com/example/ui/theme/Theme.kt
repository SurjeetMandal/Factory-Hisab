package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class LedgerColors(
    val khataStrip: Color,
    val registerPaper: Color,
    val registerRule: Color,
    val registerMargin: Color,
    val borderSubtle: Color,
    val settledGreen: Color,
    val settledContainer: Color,
    val pendingAmber: Color,
    val pendingContainer: Color,
    val advanceBlue: Color,
    val advanceContainer: Color,
    val dangerRed: Color,
    val dangerContainer: Color,
    val inkBlue: Color
)

private val LightLedgerColors = LedgerColors(
    khataStrip = KhataCreamStrip,
    registerPaper = RegisterPaperSurface,
    registerRule = RegisterRuleLine,
    registerMargin = RegisterMarginRed,
    borderSubtle = WarmBorder,
    settledGreen = ForestEmerald,
    settledContainer = EmeraldContainer,
    pendingAmber = AmberPending,
    pendingContainer = AmberContainer,
    advanceBlue = AdvanceBlue,
    advanceContainer = AdvanceContainer,
    dangerRed = CrimsonInk,
    dangerContainer = CrimsonContainer,
    inkBlue = InkNavy
)

private val DarkLedgerColors = LedgerColors(
    khataStrip = DarkSurfaceVariant,
    registerPaper = Color(0xFF19202B),
    registerRule = Color(0xFF283447),
    registerMargin = Color(0xFF8B4747),
    borderSubtle = DarkBorder,
    settledGreen = DarkEmerald,
    settledContainer = Color(0xFF153829),
    pendingAmber = Color(0xFFFBBF24),
    pendingContainer = Color(0xFF3E2B10),
    advanceBlue = Color(0xFF93C5FD),
    advanceContainer = Color(0xFF172D4B),
    dangerRed = DarkCrimson,
    dangerContainer = Color(0xFF471818),
    inkBlue = DarkPrimary
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }

private val LightColorScheme = lightColorScheme(
    primary = InkNavy,
    onPrimary = Color.White,
    primaryContainer = InkNavyContainer,
    onPrimaryContainer = OnInkNavyContainer,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,
    tertiary = ForestEmerald,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = WarmPaperBg,
    onBackground = SlateInk,
    surface = CrispCardSurface,
    onSurface = SlateInk,
    surfaceVariant = KhataCreamStrip,
    onSurfaceVariant = MutedSlate,
    outline = WarmBorder,
    error = CrimsonInk,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = Color(0xFF203A61),
    onPrimaryContainer = Color(0xFFE3ECF8),
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = Color(0xFF692919),
    onSecondaryContainer = Color(0xFFFCE6DF),
    tertiary = DarkEmerald,
    onTertiary = Color(0xFF0A3320),
    background = DarkBackground,
    onBackground = Color(0xFFEEECE6),
    surface = DarkSurface,
    onSurface = Color(0xFFEEECE6),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B9C8),
    outline = DarkBorder,
    error = DarkCrimson,
    onError = Color(0xFF4A0F0F),
    errorContainer = Color(0xFF5C1919),
    onErrorContainer = Color(0xFFFCE5E5)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FactoryHisabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val ledgerColors = if (darkTheme) DarkLedgerColors else LightLedgerColors

    CompositionLocalProvider(LocalLedgerColors provides ledgerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
