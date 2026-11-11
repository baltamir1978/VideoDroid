package es.altamirano.linvideo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueCard(item: ItemSnapshot, hub: QueueHub, onLog: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                style = MaterialTheme.typography.bodySmall,
                color = if (item.state == JobState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.state == JobState.RUNNING) {
                if (item.fraction >= 0 && item.stage.isEmpty()) {
                    LinearProgressIndicator(progress = { item.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (item.state) {
                    JobState.WAITING, JobState.RUNNING ->
                        TextButton(onClick = { hub.cancel(item.id) }) { Text("Cancelar") }
                    JobState.FAILED, JobState.CANCELLED -> {
                        TextButton(onClick = { hub.retry(item.id) }) { Text("Reintentar") }
                        TextButton(onClick = { hub.remove(item.id) }) { Text("Quitar") }
                    }
                    JobState.DONE -> {
                        item.files.lastOrNull()?.let { file ->
                            TextButton(onClick = { openFile(context, file) }) { Text("Abrir") }
                            TextButton(onClick = { shareFiles(context, item.files) }) { Text("Compartir") }
                        }
                        TextButton(onClick = { hub.remove(item.id) }) { Text("Quitar") }
                    }
                }
                TextButton(onClick = onLog) { Text("Ver registro") }
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
