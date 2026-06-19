package com.echon.voice.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import com.echon.voice.core.designsystem.Avatar
import com.echon.voice.core.designsystem.EchonColors
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
    onTapAuthor: () -> Unit = {},
    modifier: Modifier = Modifier,
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

            if (!message.content.isNullOrEmpty()) {
                Text(message.content!!, style = MaterialTheme.typography.bodyMedium)
            }

            message.attachments?.forEach { AttachmentView(it) }

            message.reactions?.takeIf { it.isNotEmpty() }?.let { reactions ->
                ReactionsRow(reactions, myUserId, onToggleReaction)
            }

            if (message.sendFailed) {
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
private fun AttachmentView(attachment: Attachment) {
    if (attachment.isImage) {
        AsyncImage(
            model = attachment.resolvedUrl,
            contentDescription = attachment.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(width = 240.dp, height = 170.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("📄")
            Text(attachment.filename ?: "file", style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
                    .then(if (mine) Modifier.border(1.dp, EchonColors.Primary, RoundedCornerShape(50)) else Modifier)
                    .background(if (mine) EchonColors.Primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
                    .clickable { reaction.emojiKey?.let(onToggle) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .wrapContentWidth(),
            ) {
                Text(reaction.emojiKey ?: "", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${reaction.count ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mine) EchonColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
