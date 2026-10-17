package com.bottlepost.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// "Letter in a bottle": warm paper, ink blue, a touch of cork.
private val Ink = Color(0xFF2B5D8C)
private val InkLight = Color(0xFF8DB8E0)
private val Paper = Color(0xFFFBF7F0)
private val PaperDark = Color(0xFF121821)
private val Cork = Color(0xFFC9A66B)
private val Sea = Color(0xFF4F8A8B)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Sea,
    tertiary = Cork,
    background = Paper,
    surface = Paper,
    surfaceVariant = Color(0xFFF1EADF),
    onBackground = Color(0xFF1F2A3A),
    onSurface = Color(0xFF1F2A3A),
)

private val DarkColors = darkColorScheme(
    primary = InkLight,
    onPrimary = PaperDark,
    secondary = Color(0xFF8CC3C4),
    tertiary = Cork,
    background = PaperDark,
    surface = PaperDark,
    surfaceVariant = Color(0xFF1E2733),
    onBackground = Color(0xFFE8E4DC),
    onSurface = Color(0xFFE8E4DC),
)

/** Serif body text makes letters feel like letters; UI chrome stays sans-serif. */
val LetterTextStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp)

@Composable
fun BottlepostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Off by default so the paper-and-ink identity survives; flip on to follow wallpaper colors. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
