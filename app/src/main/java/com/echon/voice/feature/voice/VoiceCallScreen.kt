package com.echon.voice.feature.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echon.voice.core.designsystem.EchonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import javax.inject.Inject

@HiltViewModel
class VoiceCallViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: VoiceCallStore,
) : ViewModel() {
    val channelId: String = checkNotNull(savedState["channelId"])
    val channelName: String = savedState["channelName"] ?: "voice"

    val state = store.state
    val participants = store.participants
    val isMuted = store.isMuted
    val screenShareTrack = store.screenShareTrack
    val room: Room? get() = store.room

    fun join() = store.join(channelId, channelName)
    fun toggleMute() = store.toggleMute()
    fun leave() = store.leave()
}

@Composable
fun VoiceCallScreen(
    onBack: () -> Unit,
    viewModel: VoiceCallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val screenTrack by viewModel.screenShareTrack.collectAsStateWithLifecycle()

    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.join() else denied = true
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.join() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // The call persists when navigating away (Discord-style) so you stay connected
    // while browsing; it ends only via the explicit Leave button. join() is a no-op
    // if already connected to this channel.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔊 ${viewModel.channelName}", style = MaterialTheme.typography.titleLarge)

        when {
            denied -> {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Microphone permission is required to join voice.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state == VoiceCallStore.CallState.Connecting -> {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            else -> {
                val room = viewModel.room
                val track = screenTrack
                if (room != null && track != null) {
                    ScreenShareView(
                        room = room,
                        track = track,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(participants.filterNot { it.isScreenSharer }, key = { it.id }) { p ->
                        ParticipantTile(p)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = viewModel::toggleMute) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute")
                Text(if (isMuted) "Unmute" else "Mute", modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = { viewModel.leave(); onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Leave") }
        }
    }
}

@Composable
private fun ParticipantTile(p: CallParticipant) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (p.isSpeaking) Modifier.border(2.dp, EchonColors.Primary, RoundedCornerShape(16.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(EchonColors.Primary)
                    .padding(16.dp),
            ) {
                Text((p.name.firstOrNull()?.uppercase() ?: "?"), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(if (p.isLocal) "You" else p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            if (p.isMuted) Text("muted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** View-only screen-share renderer (LiveKit SurfaceViewRenderer in an AndroidView). */
@Composable
private fun ScreenShareView(room: Room, track: VideoTrack, modifier: Modifier = Modifier) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).also {
                room.initVideoRenderer(it)
                renderer = it
            }
        },
    )
    DisposableEffect(track, renderer) {
        val r = renderer
        if (r != null) track.addRenderer(r)
        onDispose { if (r != null) track.removeRenderer(r) }
    }
}
