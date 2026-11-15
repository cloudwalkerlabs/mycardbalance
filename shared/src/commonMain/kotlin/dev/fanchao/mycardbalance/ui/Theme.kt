package dev.fanchao.mycardbalance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight

private val Light =
    lightColorScheme(
        primary = Color(0xFF0E716D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB6EDE5),
        onPrimaryContainer = Color(0xFF00201E),
        secondary = Color(0xFF4A6360),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCCE8E3),
        onSecondaryContainer = Color(0xFF051F1D),
        tertiary = Color(0xFF8A5A00),
        tertiaryContainer = Color(0xFFFFDDB0),
        onTertiaryContainer = Color(0xFF2C1700),
        error = Color(0xFFBA1A1A),
        background = Color(0xFFF6FAF8),
        onBackground = Color(0xFF171D1C),
        surface = Color(0xFFF6FAF8),
        onSurface = Color(0xFF171D1C),
        surfaceVariant = Color(0xFFDAE5E2),
        onSurfaceVariant = Color(0xFF3F4947),
        outline = Color(0xFF6F7977),
        outlineVariant = Color(0xFFBEC9C6),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F5F3),
        surfaceContainer = Color(0xFFEAEFED),
        surfaceContainerHigh = Color(0xFFE4E9E7),
        surfaceContainerHighest = Color(0xFFDEE4E2),
    )

private val Dark =
    darkColorScheme(
        primary = Color(0xFF8FDCD2),
        onPrimary = Color(0xFF003734),
        primaryContainer = Color(0xFF00504C),
        onPrimaryContainer = Color(0xFFB6EDE5),
        secondary = Color(0xFFB0CCC7),
        onSecondary = Color(0xFF1C3532),
        secondaryContainer = Color(0xFF324B48),
        onSecondaryContainer = Color(0xFFCCE8E3),
        tertiary = Color(0xFFF6B73C),
        tertiaryContainer = Color(0xFF694300),
        onTertiaryContainer = Color(0xFFFFDDB0),
        error = Color(0xFFFFB4AB),
        background = Color(0xFF0F1514),
        onBackground = Color(0xFFDEE4E2),
        surface = Color(0xFF0F1514),
        onSurface = Color(0xFFDEE4E2),
        surfaceVariant = Color(0xFF3F4947),
        onSurfaceVariant = Color(0xFFBEC9C6),
        outline = Color(0xFF899391),
        outlineVariant = Color(0xFF3F4947),
        surfaceContainerLowest = Color(0xFF0A0F0F),
        surfaceContainerLow = Color(0xFF171D1C),
        surfaceContainer = Color(0xFF1B2120),
        surfaceContainerHigh = Color(0xFF252B2A),
        surfaceContainerHighest = Color(0xFF303635),
    )

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography =
            base.copy(
                titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
            ),
        content = content,
    )
}

/**
 * Card face colours: common card colours, each a two-stop gradient dark enough for white text.
 * A card's colour is a hash of its last four digits, so it is stable and the Add screen preview
 * matches the saved card.
 */
object CardPalette {
    private class Swatch(val start: Color, val end: Color)

    private val swatches =
        listOf(
            Swatch(Color(0xFF2F6FDB), Color(0xFF1B4296)), // blue
            Swatch(Color(0xFF5257C9), Color(0xFF2E3290)), // indigo
            Swatch(Color(0xFF8452C9), Color(0xFF56308F)), // purple
            Swatch(Color(0xFFD2477F), Color(0xFF962656)), // pink
            Swatch(Color(0xFFD9483B), Color(0xFF9C2A22)), // red
            Swatch(Color(0xFFDB7A2E), Color(0xFFA14A10)), // orange
            Swatch(Color(0xFFB58210), Color(0xFF7A5406)), // amber
            Swatch(Color(0xFF2E9D5B), Color(0xFF1A6B3B)), // green
            Swatch(Color(0xFF14857F), Color(0xFF0B5A5E)), // teal
            Swatch(Color(0xFF55606B), Color(0xFF323A42)), // graphite
        )

    /** FNV-1a over the digits: spreads neighbouring numbers across the palette. */
    fun index(last4: String): Int {
        var h = 0x811C9DC5.toInt()
        for (c in last4) h = (h xor c.code) * 0x01000193
        return ((h % swatches.size) + swatches.size) % swatches.size
    }

    /**
     * In dark mode the gradient stops partway so the card's darker corner doesn't sink into the
     * near-black background; [CardOutline] adds a faint edge as well.
     */
    @Composable
    fun brush(last4: String): Brush {
        val s = swatches[index(last4)]
        val end = if (isDarkTheme()) lerp(s.start, s.end, 0.5f) else s.end
        return Brush.linearGradient(listOf(s.start, end))
    }
}

/** Faint edge for card shapes in dark mode; transparent in light mode. */
@Composable
fun cardOutline(): Color = if (isDarkTheme()) Color.White.copy(alpha = 0.12f) else Color.Transparent

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
