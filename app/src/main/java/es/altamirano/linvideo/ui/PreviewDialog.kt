package es.altamirano.linvideo.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import es.altamirano.linvideo.engine.Preview
import es.altamirano.linvideo.search.Net

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Failed(val message: String) : PreviewState
    data class Ready(val stream: Preview.Stream) : PreviewState
}

/** Vista previa con ExoPlayer, sin descargar nada. */
@Composable
fun PreviewDialog(link: String, title: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    var state by remember(link) { mutableStateOf<PreviewState>(PreviewState.Loading) }
    LaunchedEffect(link) {
        state = try {
            PreviewState.Ready(Preview.resolve(link))
        } catch (e: Exception) {
            PreviewState.Failed(e.message ?: e.toString())
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        modifier = Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (val s = state) {
                        PreviewState.Loading -> CircularProgressIndicator()
                        is PreviewState.Failed -> Text(s.message, color = Color.White, modifier = Modifier.padding(24.dp))
                        is PreviewState.Ready -> Player(s.stream) { state = PreviewState.Failed(it) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    (state as? PreviewState.Ready)?.let {
                        Text(it.stream.kind.title, color = Color.LightGray, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                    } ?: Box(Modifier.weight(1f))
                    Button(onClick = { onDownload(); onDismiss() }) { Text("Descargar") }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Player(stream: Preview.Stream, onError: (String) -> Unit) {
    val context = LocalContext.current
    val player = remember(stream) {
        val headers = stream.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(stream.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: Net.USER_AGENT)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
            val item = MediaItem.Builder().setUri(stream.url)
                .apply { if (stream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
            setMediaItem(item)
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError("No se pudo reproducir: ${error.errorCodeName}")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS) } },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
    )
}
