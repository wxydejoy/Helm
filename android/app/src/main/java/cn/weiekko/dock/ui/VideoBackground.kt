package cn.weiekko.dock.ui

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import cn.weiekko.dock.R

@Composable
fun VideoBackground(
    uri: String,
    playing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failed by remember(uri) { mutableStateOf(false) }
    var player by remember(uri) { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(uri, context) {
        val exo = ExoPlayer.Builder(context)
            .setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setMediaCodecSelector { mimeType, secure, tunneling ->
                        runCatching {
                            MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
                                .sortedBy { info -> if (info.hardwareAccelerated) 0 else 1 }
                        }.getOrElse { MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling) }
                    },
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_500, 8_000, 1_000, 2_000)
                    .build(),
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        exo.addListener(listener)
        player = exo
        onDispose {
            player = null
            exo.removeListener(listener)
            exo.release()
        }
    }

    DisposableEffect(player, lifecycleOwner, playing) {
        val exo = player ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!failed && playing) exo.play()
                Lifecycle.Event.ON_STOP -> exo.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!failed && playing && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            exo.play()
        } else {
            exo.pause()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exo = player
    if (failed || exo == null) return

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.video_background, null) as PlayerView).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view ->
            if (view.player !== exo) {
                view.player = exo
            }
            if (exo.playbackState == Player.STATE_IDLE) {
                exo.prepare()
            }
            exo.playWhenReady = playing
        },
        onRelease = { view ->
            view.player = null
        },
    )
}
