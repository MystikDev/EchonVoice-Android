package com.echon.voice.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.echon.voice.core.designsystem.Avatar
import com.echon.voice.model.Attachment
import com.echon.voice.model.Message
import com.echon.voice.model.Reaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MessageRow(
    message: Message,
    repliedTo: Message?,
    myUserId: String?,
    onToggleReaction: (String) -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    onTapAuthor: () -> Unit = {},
    onOpenImage: (Attachment) -> Unit = {},
) {
    if (message.isSystem == true) {
        Text(
            message.content.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (message.isLocalEcho && !message.sendFailed) 0.55f else 1f)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(user = message.author, size = 36.dp, modifier = Modifier.clickable(onClick = onTapAuthor))

        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            if (message.replyToId != null) ReplyPreview(repliedTo)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    message.author?.username ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.editedAt != null) {
                    Text("(edited)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.isPinned == true) {
                    Text("📌", style = MaterialTheme.typography.labelSmall)
                }
            }

            message.content?.takeIf { it.isNotEmpty() }?.let { content ->
                MessageContent(content)
            }

            message.attachments?.forEach { attachment ->
                AttachmentView(attachment, onOpenImage)
            }

            message.reactions?.takeIf { it.isNotEmpty() }?.let { reactions ->
                ReactionsRow(reactions, myUserId, onToggleReaction)
            }

            if (message.sendFailed) {
                message.sendError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Failed to send", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    Text("Retry", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable(onClick = onRetry))
                    Text("Discard", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable(onClick = onDiscard))
                }
            }
        }
    }
}

@Composable
private fun MessageContent(content: String) {
    val uriHandler = LocalUriHandler.current
    val links = remember(content) { findMessageLinks(content) }
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(content, links, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            links.forEach { link ->
                append(content, cursor, link.start)
                withLink(
                    LinkAnnotation.Url(
                        url = link.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                        ),
                    ),
                ) { append(content, link.start, link.endExclusive) }
                cursor = link.endExclusive
            }
            append(content, cursor, content.length)
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
    )

    val youtubeLinks = remember(content) {
        links.mapNotNull { link -> youtubeVideoId(link.url)?.let { id -> link.url to id } }
            .distinctBy { it.second }
            .take(2)
    }
    youtubeLinks.forEach { (url, videoId) ->
        YouTubePreview(videoId) { runCatching { uriHandler.openUri(url) } }
    }
}

@Composable
private fun YouTubePreview(videoId: String, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                onClick = onOpen,
                onClickLabel = "Open video in YouTube",
            ),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            contentDescription = "YouTube video preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Open YouTube video", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.68f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun ReplyPreview(repliedTo: Message?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("↩", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            repliedTo?.author?.username ?: "Original message",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (repliedTo != null) {
            Text(
                repliedTo.content?.takeIf { it.isNotEmpty() } ?: "Attachment",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AttachmentView(attachment: Attachment, onOpenImage: (Attachment) -> Unit) {
    if (attachment.isImage) {
        var aspectRatio by remember(attachment.resolvedUrl) { mutableFloatStateOf(16f / 10f) }
        SubcomposeAsyncImage(
            model = attachment.resolvedUrl,
            contentDescription = attachment.filename,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = 4.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                // Keep extreme panoramas/portraits usable while ContentScale.Fit
                // guarantees that no part of the actual image is cropped.
                .aspectRatio(aspectRatio.coerceIn(0.65f, 2f))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    onClick = { onOpenImage(attachment) },
                    onClickLabel = "Open image full screen",
                ),
            onSuccess = { state ->
                val drawable = state.result.drawable
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    aspectRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                }
            },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            },
            error = { FileChip(attachment.filename ?: "image", note = "Image unavailable") },
        )
    } else {
        FileChip(attachment.filename ?: "file", note = null)
    }
}

/** Full-window, uncropped image viewer. Tapping outside the image or Close dismisses it. */
@Composable
internal fun FullScreenImageViewer(attachment: Attachment, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = attachment.resolvedUrl,
                contentDescription = attachment.filename ?: "Full-screen image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Image unavailable", color = Color.White)
                    }
                },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close full-screen image", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FileChip(name: String, note: String?) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("📄")
        Column {
            Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ReactionsRow(reactions: List<Reaction>, myUserId: String?, onToggle: (String) -> Unit) {
    Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.forEach { reaction ->
            val mine = reaction.includes(myUserId)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (mine) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50)) else Modifier)
                    .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
                    .clickable { reaction.emojiKey?.let(onToggle) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .wrapContentWidth(),
            ) {
                Text(reaction.emojiKey ?: "", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${reaction.count ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun formatTimestamp(iso: String?): String {
    iso ?: return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val zoned = instant.atZone(ZoneId.systemDefault())
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    return if (zoned.toLocalDate() == today) zoned.format(timeFormatter) else zoned.format(dateFormatter)
}
