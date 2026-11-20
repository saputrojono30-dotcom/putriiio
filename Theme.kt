package id.dlms.dsp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet navy gelap yang sama dengan aplikasi laptop.
val BG = Color(0xFF070C18)
val PANEL = Color(0xFF0E1830)
val GRID = Color(0xFF2A3A5C)
val GRIDSUB = Color(0xFF16223C)
val TXT = Color(0xFFDCE6F5)
val DIM = Color(0xFF7C8CA8)
val HERO = Color(0xFFFF6B2C)
val SEL = Color(0xFF1E88FF)
val TEAL = Color(0xFF2ED0B0)
val STEEL = Color(0xFF6E96C8)
val VIOLET = Color(0xFFC08CFF)
val AMBER = Color(0xFFFFB020)
val RED = Color(0xFFE24B3A)
val GREEN = Color(0xFF5BE38A)
val OUT_COLOR = listOf(Color(0xFFFFB020), Color(0xFF3FC7FF), Color(0xFF5BE38A), Color(0xFFC08CFF))
val IN_COLOR = listOf(Color(0xFFE86A6A), Color(0xFFE8B26A))

private val scheme = darkColorScheme(
    primary = SEL, onPrimary = Color.White,
    secondary = TEAL, background = BG, onBackground = TXT,
    surface = PANEL, onSurface = TXT, surfaceVariant = Color(0xFF16223C), onSurfaceVariant = DIM,
    outline = GRID, error = RED,
)

@Composable
fun DlmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
