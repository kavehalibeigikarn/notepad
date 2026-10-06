package ir.kaveh.yaddashtyar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.kaveh.yaddashtyar.R

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private fun TextStyle.v() = copy(fontFamily = Vazir)

private val Base = Typography()
val AppTypography = Typography(
    displayLarge = Base.displayLarge.v(),
    displayMedium = Base.displayMedium.v(),
    displaySmall = Base.displaySmall.v(),
    headlineLarge = Base.headlineLarge.v(),
    headlineMedium = Base.headlineMedium.v(),
    headlineSmall = Base.headlineSmall.v(),
    titleLarge = Base.titleLarge.v(),
    titleMedium = Base.titleMedium.v(),
    titleSmall = Base.titleSmall.v(),
    bodyLarge = Base.bodyLarge.v(),
    bodyMedium = Base.bodyMedium.v(),
    bodySmall = Base.bodySmall.v(),
    labelLarge = Base.labelLarge.v(),
    labelMedium = Base.labelMedium.v(),
    labelSmall = Base.labelSmall.v()
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF1B1259),
    secondary = Color(0xFF5E5C71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E0F7),
    onSecondaryContainer = Color(0xFF1B1A2C),
    tertiary = Color(0xFFC77D0A),
    onTertiary = Color.White,
    background = Color(0xFFF5F4FB),
    onBackground = Color(0xFF1A1A24),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1A1A24),
    surfaceVariant = Color(0xFFE6E3F0),
    onSurfaceVariant = Color(0xFF55536A),
    outline = Color(0xFF8A889C),
    outlineVariant = Color(0xFFD9D6E6),
    error = Color(0xFFC62F3E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F1FA),
    surfaceContainer = Color(0xFFEDEBF6),
    surfaceContainerHigh = Color(0xFFE8E5F3),
    surfaceContainerHighest = Color(0xFFE2DFEF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB0A5FF),
    onPrimary = Color(0xFF241A73),
    primaryContainer = Color(0xFF3B2FA6),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC9C4DE),
    onSecondary = Color(0xFF312F42),
    secondaryContainer = Color(0xFF474459),
    onSecondaryContainer = Color(0xFFE5E0F7),
    tertiary = Color(0xFFFFB955),
    onTertiary = Color(0xFF442B00),
    background = Color(0xFF0E0E16),
    onBackground = Color(0xFFE7E5F2),
    surface = Color(0xFF12121B),
    onSurface = Color(0xFFE7E5F2),
    surfaceVariant = Color(0xFF45435A),
    onSurfaceVariant = Color(0xFFC9C6DB),
    outline = Color(0xFF928FA6),
    outlineVariant = Color(0xFF2E2D40),
    error = Color(0xFFFF8A94),
    surfaceContainerLowest = Color(0xFF0A0A11),
    surfaceContainerLow = Color(0xFF171722),
    surfaceContainer = Color(0xFF1B1B27),
    surfaceContainerHigh = Color(0xFF242433),
    surfaceContainerHighest = Color(0xFF2D2D3E)
)

private val LightNote = listOf(
    Color.Unspecified, Color(0xFFFFF0B8), Color(0xFFD2F2E0), Color(0xFFD3E5FF),
    Color(0xFFFFD7E3), Color(0xFFE5DBFF), Color(0xFFE2E6ED)
)
private val DarkNote = listOf(
    Color.Unspecified, Color(0xFF4A3F10), Color(0xFF16402C), Color(0xFF173352),
    Color(0xFF4F1F31), Color(0xFF33285F), Color(0xFF2B3342)
)
val NoteAccents = listOf(
    Color(0xFF9A98AC), Color(0xFFE5A912), Color(0xFF2FA36B), Color(0xFF3B82F6),
    Color(0xFFE5568A), Color(0xFF8B5CF6), Color(0xFF64748B)
)

/** Background of a note card / editor for the given colour index. */
@Composable
fun noteBg(index: Int): Color {
    val list = if (isSystemInDarkTheme()) DarkNote else LightNote
    return if (index <= 0 || index >= list.size) MaterialTheme.colorScheme.surfaceContainerLowest else list[index]
}

@Composable
fun YaddashtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
