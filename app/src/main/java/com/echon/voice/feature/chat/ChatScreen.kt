package com.echon.voice.feature.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.feature.moderation.ReportSheet
import com.echon.voice.feature.moderation.ReportTarget
import com.echon.voice.model.Message
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProfile: (com.echon.voice.model.User) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val messages by viewModel.visibleMessages.collectAsStateWithLifecycle()
    val typing by viewModel.typingNames.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var actionTarget by remember { mutableStateOf<Message?>(null) }
    var reportTarget by remember { mutableStateOf<Message?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        viewModel.onEnter()
        onDispose { viewModel.onLeave() }
    }

    // Keep the newest message in view as new ones arrive.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // Paginate older when the top (oldest, last in reverse layout) is reached.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible -> if (lastVisible >= messages.size - 3 && messages.isNotEmpty()) viewModel.loadOlder() }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris -> viewModel.attach(uris) }

    // Camera capture: hold the target URI created before launch, attach it on success.
    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) viewModel.attach(listOf(uri))
        pendingCameraUri = null
    }

    // The manifest declares CAMERA (for in-call camera streaming), and Android
    // rejects ACTION_IMAGE_CAPTURE with a SecurityException when a declared
    // CAMERA permission isn't granted — so the photo button must secure the
    // grant first, then launch the system camera.
    fun launchCamera() {
        try {
            val uri = viewModel.newCameraCaptureUri()
            pendingCameraUri = uri
            takePhoto.launch(uri)
        } catch (e: Exception) {
            pendingCameraUri = null
            android.util.Log.w("EchonCamera", "camera launch failed", e)
            Toast.makeText(context, "Couldn't open the camera on this device.", Toast.LENGTH_SHORT).show()
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "Camera permission is needed to take a photo.", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("# ${viewModel.channelName}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            // Keep the composer above the keyboard (ime) AND the system navigation
            // bar — union takes the max per side so they don't double-pad.
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                TypingIndicator(typing)
                ContextBanner(viewModel)
                PendingChips(viewModel)
                Composer(
                    draft = viewModel.draft,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send,
                    onAttach = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onCapture = {
                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.CAMERA,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else cameraPermission.launch(android.Manifest.permission.CAMERA)
                    },
                    uploading = viewModel.uploading,
                    channelName = viewModel.channelName,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(
                count = messages.size,
                key = { messages[messages.size - 1 - it].id },
            ) { i ->
                val message = messages[messages.size - 1 - i]
                Box(
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (message.isSystem != true && !message.isLocalEcho) actionTarget = message
                        },
                    ),
                ) {
                    MessageRow(
                        message = message,
                        repliedTo = viewModel.repliedTo(message),
                        myUserId = viewModel.currentUserId,
                        onToggleReaction = { emoji -> viewModel.react(message, emoji) },
                        onRetry = { viewModel.retry(message) },
                        onDiscard = { viewModel.discard(message) },
                        onTapAuthor = { message.author?.let(onOpenProfile) },
                    )
                }
            }
        }
    }

    actionTarget?.let { target ->
        MessageActionsSheet(
            message = target,
            isMine = viewModel.isMine(target),
            onDismiss = { actionTarget = null },
            onReact = { emoji -> viewModel.react(target, emoji) },
            onReply = { viewModel.startReply(target) },
            onPin = { viewModel.togglePin(target) },
            onEdit = { viewModel.startEdit(target) },
            onDelete = { viewModel.delete(target) },
            onReport = { reportTarget = target },
            onBlock = { target.author?.let { author -> scope.launch { viewModel.block(author) } } },
        )
    }

    reportTarget?.let { target ->
        ReportSheet(target = ReportTarget.Message(target.id), onDismiss = { reportTarget = null })
    }
}

@Composable
private fun TypingIndicator(names: List<String>) {
    if (names.isEmpty()) return
    val verb = if (names.size == 1) "is" else "are"
    Text(
        "${names.joinToString(", ")} $verb typing…",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ContextBanner(viewModel: ChatViewModel) {
    val edit = viewModel.editTarget
    val reply = viewModel.replyTarget
    if (edit == null && reply == null) return
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (edit != null) "Editing message" else "Replying to ${reply?.author?.username ?: "Unknown"}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::clearContext) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }
    }
}

@Composable
private fun PendingChips(viewModel: ChatViewModel) {
    if (viewModel.pending.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        viewModel.pending.forEach { att ->
            Row(
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(att.filename, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                IconButton(onClick = { viewModel.removePending(att) }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onCapture: () -> Unit,
    uploading: Boolean,
    channelName: String,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onAttach, enabled = !uploading) {
                Icon(Icons.Default.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCapture, enabled = !uploading) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Take photo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Message #$channelName") },
                maxLines = 5,
                keyboardOptions = KeyboardOptions.Default,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSend, enabled = draft.isNotBlank() || !uploading) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
