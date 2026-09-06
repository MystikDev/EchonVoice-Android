package com.echon.voice.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/** A TextureView participates in Compose clipping/transforms; a SurfaceView does not. */
@Composable
internal fun VideoStreamView(
    room: Room,
    track: VideoTrack,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    key(room, track) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextureViewRenderer(context).also { renderer ->
                    room.initVideoRenderer(renderer)
                    renderer.setMirror(false)
                    renderer.keepScreenOn = true
                    track.addRenderer(renderer)
                }
            },
            update = { renderer ->
                renderer.setScalingType(
                    if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL
                    else RendererCommon.ScalingType.SCALE_ASPECT_FIT,
                )
            },
            onRelease = { renderer ->
                track.removeRenderer(renderer)
                renderer.keepScreenOn = false
                renderer.release()
            },
        )
    }
}

@Composable
internal fun FullscreenStream(room: Room, stream: LiveStream, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
    ) {
        var viewport by remember { mutableStateOf(IntSize.Zero) }
        var transform by remember(stream.id, viewport) { mutableStateOf(VideoTransform()) }
        var fill by remember(stream.id) { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("Close") }
                TextButton(onClick = { fill = !fill; transform = VideoTransform() }) {
                    Text(if (fill) "Fit entire video" else "Fill screen")
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .onSizeChanged { viewport = it }
                    .pointerInput(stream.id, viewport) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            transform = transform.change(zoom, pan.x, pan.y, viewport.width, viewport.height)
                        }
                    },
            ) {
                VideoStreamView(
                    room, stream.track,
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.x
                        translationY = transform.y
                    },
                    fill = fill,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = {
                    transform = transform.change(1 / 1.25f, 0f, 0f, viewport.width, viewport.height)
                }, enabled = transform.scale > 1f) { Text("Zoom out") }
                TextButton(onClick = { transform = VideoTransform(); fill = false }) { Text("Reset") }
                TextButton(onClick = {
                    transform = transform.change(1.25f, 0f, 0f, viewport.width, viewport.height)
                }, enabled = transform.scale < 4f) { Text("Zoom in") }
            }
            Text(stream.title, color = Color.White, modifier = Modifier.padding(12.dp), maxLines = 1)
        }
    }
}
