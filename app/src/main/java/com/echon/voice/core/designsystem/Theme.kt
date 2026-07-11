package com.echon.voice.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echon.voice.R

/** Echon default brand palette (dark, mirroring the iOS theme). */
object EchonColors {
    val Background = Color(0xFF0D0F1A)
    val Surface = Color(0xFF161A2B)
    val Primary = Color(0xFF4F46E5)
    val PrimaryDark = Color(0xFF3730A3)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFFE7E9F2)
    val OnSurfaceVariant = Color(0xFF9AA0B5)
}

/** A selectable color scheme. Retro palettes power the optional 8-bit skin. */
data class EchonPalette(
    val id: String,
    val name: String,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val primaryDark: Color,
    val onPrimary: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
)

object EchonPalettes {
    val Default = EchonPalette(
        "default", "Default",
        background = EchonColors.Background, surface = EchonColors.Surface,
        primary = EchonColors.Primary, primaryDark = EchonColors.PrimaryDark,
        onPrimary = EchonColors.OnPrimary, onBackground = EchonColors.OnBackground,
        onSurfaceVariant = EchonColors.OnSurfaceVariant,
    )

    val GameBoy = EchonPalette(
        "gameboy", "Game Boy",
        background = Color(0xFF0F380F), surface = Color(0xFF306230),
        primary = Color(0xFF9BBC0F), primaryDark = Color(0xFF8BAC0F),
        onPrimary = Color(0xFF0F380F), onBackground = Color(0xFF9BBC0F),
        onSurfaceVariant = Color(0xFF8BAC0F),
    )

    val Commodore64 = EchonPalette(
        "c64", "Commodore 64",
        background = Color(0xFF352879), surface = Color(0xFF40318D),
        primary = Color(0xFF7C70DA), primaryDark = Color(0xFF6C5EB5),
        onPrimary = Color(0xFF1A1040), onBackground = Color(0xFFBCB2FF),
        onSurfaceVariant = Color(0xFF9088DA),
    )

    val Nes = EchonPalette(
        "nes", "NES",
        background = Color(0xFF0D0D0D), surface = Color(0xFF1C1C1C),
        primary = Color(0xFFE60012), primaryDark = Color(0xFFA3000D),
        onPrimary = Color(0xFFFFFFFF), onBackground = Color(0xFFE0E0E0),
        onSurfaceVariant = Color(0xFF9E9E9E),
    )

    val Synthwave = EchonPalette(
        "synthwave", "Synthwave",
        background = Color(0xFF1A0B2E), surface = Color(0xFF2D1B4E),
        primary = Color(0xFFFF2E97), primaryDark = Color(0xFFB81E6B),
        onPrimary = Color(0xFF1A0B2E), onBackground = Color(0xFF5CE0E6),
        onSurfaceVariant = Color(0xFFB070C0),
    )

    val Amber = EchonPalette(
        "amber", "Amber terminal",
        background = Color(0xFF1A0F00), surface = Color(0xFF2B1A00),
        primary = Color(0xFFFFB000), primaryDark = Color(0xFFCC8000),
        onPrimary = Color(0xFF1A0F00), onBackground = Color(0xFFFFB000),
        onSurfaceVariant = Color(0xFFB37B00),
    )

    val Zelda = EchonPalette(
        "zelda", "Zelda",
        background = Color(0xFF000000), surface = Color(0xFF1A1A1A),
        primary = Color(0xFFE0B000), primaryDark = Color(0xFFA37E00),
        onPrimary = Color(0xFF000000), onBackground = Color(0xFFD8E0A0),
        onSurfaceVariant = Color(0xFF7A8A4A),
    )

    val PipBoy = EchonPalette(
        "pipboy", "Pip-Boy",
        background = Color(0xFF001100), surface = Color(0xFF002800),
        primary = Color(0xFF41FF00), primaryDark = Color(0xFF1A9E00),
        onPrimary = Color(0xFF001100), onBackground = Color(0xFF41FF00),
        onSurfaceVariant = Color(0xFF2A8A00),
    )

    val all = listOf(Default, GameBoy, Commodore64, Nes, Synthwave, Amber, Zelda, PipBoy)

    fun byId(id: String): EchonPalette = all.firstOrNull { it.id == id } ?: Default
}

/** True when the 8-bit skin is active — components (e.g. [Avatar]) branch on it. */
val LocalRetroSkin = staticCompositionLocalOf { false }

private val PixelFont = FontFamily(Font(R.font.press_start_2p))

/** Every corner squared off — the defining move of the pixel look. */
private val RetroShapes = RoundedCornerShape(0.dp).let {
    Shapes(extraSmall = it, small = it, medium = it, large = it, extraLarge = it)
}

/**
 * Press Start 2P everywhere, at reduced sizes (its glyphs fill the em box, so it
 * reads ~30% larger than the nominal sp) with generous line height for breathing
 * room. Built off the default Material typography so unset styles still resolve.
 */
private fun retroTypography(base: Typography): Typography {
    fun TextStyle.pixel(size: TextUnit) = copy(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = size,
        lineHeight = size * 1.6f,
        letterSpacing = 0.sp,
    )
    return base.copy(
        displayLarge = base.displayLarge.pixel(22.sp),
        displayMedium = base.displayMedium.pixel(19.sp),
        displaySmall = base.displaySmall.pixel(16.sp),
        headlineLarge = base.headlineLarge.pixel(16.sp),
        headlineMedium = base.headlineMedium.pixel(14.sp),
        headlineSmall = base.headlineSmall.pixel(13.sp),
        titleLarge = base.titleLarge.pixel(13.sp),
        titleMedium = base.titleMedium.pixel(11.sp),
        titleSmall = base.titleSmall.pixel(10.sp),
        bodyLarge = base.bodyLarge.pixel(10.sp),
        bodyMedium = base.bodyMedium.pixel(9.sp),
        bodySmall = base.bodySmall.pixel(8.sp),
        labelLarge = base.labelLarge.pixel(9.sp),
        labelMedium = base.labelMedium.pixel(8.sp),
        labelSmall = base.labelSmall.pixel(8.sp),
    )
}

/**
 * App theme. Echon is dark-only. When [retro] is on, the whole app reskins to an
 * 8-bit look: the chosen [palette], squared corners, and the pixel font. Off, it
 * renders the untouched default look regardless of [palette].
 */
@Composable
fun EchonTheme(
    palette: EchonPalette = EchonPalettes.Default,
    retro: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryDark,
        onPrimaryContainer = palette.onPrimary,
        background = palette.background,
        onBackground = palette.onBackground,
        surface = palette.surface,
        onSurface = palette.onBackground,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.onSurfaceVariant,
        error = Color(0xFFE24B4A),
        onError = Color(0xFFFFFFFF),
    )
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        shapes = if (retro) RetroShapes else Shapes(),
        typography = if (retro) retroTypography(base) else base,
    ) {
        if (retro) {
            CompositionLocalProvider(
                LocalRetroSkin provides true,
                androidx.compose.material3.LocalTextStyle provides
                    TextStyle(fontFamily = PixelFont, fontSize = 10.sp, lineHeight = 16.sp),
                content = content,
            )
        } else {
            CompositionLocalProvider(LocalRetroSkin provides false, content = content)
        }
    }
}
