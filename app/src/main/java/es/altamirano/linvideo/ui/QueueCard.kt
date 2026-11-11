package es.altamirano.linvideo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import es.altamirano.linvideo.queue.ItemSnapshot
import es.altamirano.linvideo.queue.JobState
import es.altamirano.linvideo.queue.QueueHub
import java.io.File

@Composable
fun QueueCard(item: ItemSnapshot, hub: QueueHub, onLog: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val failed = item.state == JobState.FAILED
    Surface(
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                item.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            val status = when (item.state) {
                JobState.RUNNING -> item.stage.ifEmpty {
                    listOf(item.size, item.speed, item.eta.takeIf { it.isNotEmpty() }?.let { "quedan $it" }.orEmpty())
                        .filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Empezando…" }
                }
                JobState.FAILED -> item.error
                else -> item.state.title
            }
            Text(
                "${item.kind.shortTitle} · $status",
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (item.state == JobState.RUNNING) {
                val bar = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp)
                if (item.fraction >= 0 && item.stage.isEmpty()) {
                    LinearWavyProgressIndicator(progress = { item.fraction.toFloat() }, modifier = bar)
                } else {
                    LinearWavyProgressIndicator(bar)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (item.state) {
                    JobState.WAITING, JobState.RUNNING ->
                        FilledTonalButton(onClick = { hub.cancel(item.id) }, shapes = ButtonDefaults.shapes()) { Text("Cancelar") }
                    JobState.FAILED, JobState.CANCELLED ->
                        Button(onClick = { hub.retry(item.id) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Reintentar")
                        }
                    JobState.DONE -> item.files.lastOrNull()?.let { file ->
                        Button(onClick = { openFile(context, file) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Abrir")
                        }
                        IconButton(onClick = { shareFiles(context, item.files) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onLog, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Ver registro")
                }
                if (item.state.finished) {
                    IconButton(onClick = { hub.remove(item.id) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar")
                    }
                }
            }
        }
    }
}

@Composable
fun LogDialog(item: ItemSnapshot, lines: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Registro", maxLines = 1) },
        text = {
            SelectionContainer {
                Text(
                    lines.joinToString("\n").ifEmpty { "Sin registro todavía." },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
    )
}

private fun uriFor(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

private fun mimeOf(file: File) =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

fun openFile(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(context, file), mimeOf(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No hay ninguna app para abrir ${file.extension}.", Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, "El archivo ya no está en Descargas.", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(context: Context, files: List<File>) {
    val existing = files.filter { it.exists() }
    if (existing.isEmpty()) {
        Toast.makeText(context, "El archivo ya no está en Descargas.", Toast.LENGTH_SHORT).show()
        return
    }
    val uris = ArrayList(existing.map { uriFor(context, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(mimeOf(existing[0]))
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
    }
    context.startActivity(Intent.createChooser(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
}
