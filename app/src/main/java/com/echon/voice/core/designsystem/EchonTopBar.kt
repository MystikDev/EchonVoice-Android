package com.echon.voice.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ConnectedColor = Color(0xFF4ADE4A) // matches the presence "online" green

/**
 * Branded header for the signed-in tabs: a waveform glyph + "Echon" wordmark,
 * with a small realtime-connection dot on the trailing edge (green while the
 * event socket is up, amber while reconnecting). Squares off under the 8-bit
 * skin; the wordmark picks up the pixel font from the global typography.
 */
@Composable
fun EchonTopBar(isConnected: Boolean, modifier: Modifier = Modifier) {
    val retro = LocalRetroSkin.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaveformGlyph(retro = retro)
        Text(
            "Echon",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = if (retro) 0.sp else 1.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(if (retro) RectangleShape else CircleShape)
                .background(if (isConnected) ConnectedColor else MaterialTheme.colorScheme.onSurfaceVariant)
                .semantics { contentDescription = if (isConnected) "Connected" else "Reconnecting" },
        )
    }
}

/** Three-bar audio-level mark in the brand primary — the "echo" in Echon. */
@Composable
private fun WaveformGlyph(retro: Boolean) {
    val shape = if (retro) RectangleShape else RoundedCornerShape(2.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(11.dp, 20.dp, 14.dp).forEach { barHeight: Dp ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
