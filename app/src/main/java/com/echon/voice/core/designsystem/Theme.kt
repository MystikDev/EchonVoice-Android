package com.echon.voice.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Echon brand palette (dark-only, mirroring the iOS theme). */
object EchonColors {
    val Background = Color(0xFF0D0F1A)
    val Surface = Color(0xFF161A2B)
    val Primary = Color(0xFF4F46E5)
    val PrimaryDark = Color(0xFF3730A3)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFFE7E9F2)
    val OnSurfaceVariant = Color(0xFF9AA0B5)
}

private val EchonDarkScheme = darkColorScheme(
    primary = EchonColors.Primary,
    onPrimary = EchonColors.OnPrimary,
    primaryContainer = EchonColors.PrimaryDark,
    background = EchonColors.Background,
    onBackground = EchonColors.OnBackground,
    surface = EchonColors.Surface,
    onSurface = EchonColors.OnBackground,
    onSurfaceVariant = EchonColors.OnSurfaceVariant,
)

/** App theme. Echon is dark-only, so [darkTheme] defaults on regardless of system. */
@Composable
fun EchonTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = EchonDarkScheme,
        content = content,
    )
}
