package com.echon.voice.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.echon.voice.model.User

/** Round avatar: remote image (pinned via the app ImageLoader) or initial fallback. */
@Composable
fun Avatar(user: User?, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val url = user?.avatarUrl
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = user.username,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(EchonColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (user?.username?.firstOrNull()?.uppercase() ?: "?"),
                color = EchonColors.OnPrimary,
                fontSize = (size.value * 0.42f).sp,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
