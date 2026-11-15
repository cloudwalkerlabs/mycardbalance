package app.cardbalances.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Card face gradients. A card keeps the same one for life, picked from its last four digits. */
object CardPalette {
    private val gradients =
        listOf(
            listOf(Color(0xFF14857F), Color(0xFF0B4F52)),
            listOf(Color(0xFF3A4FA8), Color(0xFF1F2A66)),
            listOf(Color(0xFF7A62C4), Color(0xFF4A2F8F)),
            listOf(Color(0xFFE0674F), Color(0xFFA83A2E)),
            listOf(Color(0xFF2F3A40), Color(0xFF121A1E)),
            listOf(Color(0xFFC58A12), Color(0xFF7A4E00)),
        )

    fun colors(last4: String): List<Color> {
        val n = last4.toIntOrNull() ?: 0
        return gradients[n % gradients.size]
    }

    fun brush(last4: String) = Brush.linearGradient(colors(last4))
}
