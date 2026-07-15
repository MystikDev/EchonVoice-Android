package com.echon.voice.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echon.voice.model.User

/** Presence dot colors, matching the iOS theme (success / warning). */
private val OnlineColor = Color(0xFF4ADE4A)
private val IdleColor = Color(0xFFFFD24D)

private fun presenceColor(status: String?, offline: Color): Color = when (status) {
    "online" -> OnlineColor
    "idle" -> IdleColor
    else -> offline
}

/**
 * Small status dot for a user's presence ("online" | "idle" | anything else =
 * offline), ringed with the surface color so it reads on top of an avatar.
 */
@Composable
fun PresenceDot(status: String?, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    val shape = if (LocalRetroSkin.current) RectangleShape else CircleShape
    val label = when (status) {
        "online" -> "Online"
        "idle" -> "Idle"
        else -> "Offline"
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(presenceColor(status, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
            .border(1.5.dp, MaterialTheme.colorScheme.background, shape)
            .semantics { contentDescription = label },
    )
}

/**
 * [Avatar] with a [PresenceDot] overlaid bottom-trailing, as on iOS. Pass
 * `null` [status] for users with no known presence; [showWhenUnknown] decides
 * whether that renders an offline dot (friends list) or nothing (DM list).
 */
@Composable
fun AvatarWithPresence(
    user: User?,
    status: String?,
    size: Dp = 36.dp,
    showWhenUnknown: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Avatar(user = user, size = size)
        if (status != null || showWhenUnknown) {
            PresenceDot(status = status, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}
