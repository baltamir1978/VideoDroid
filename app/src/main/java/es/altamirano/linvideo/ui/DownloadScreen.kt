package es.altamirano.linvideo.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.altamirano.linvideo.engine.Engine
import es.altamirano.linvideo.engine.Links.extractUrls
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.model.VideoResolution
import es.altamirano.linvideo.queue.ItemSnapshot

@Composable
fun DownloadScreen(model: DownloadViewModel, onFormats: () -> Unit) {
    val context = LocalContext.current
    val form by model.form.collectAsStateWithLifecycle()
    val items by model.hub.items.collectAsStateWithLifecycle()
    val summary by model.hub.summary.collectAsStateWithLifecycle()
    val engine by Engine.status.collectAsStateWithLifecycle()
    var logFor by remember { mutableStateOf<ItemSnapshot?>(null) }

    val offer by model.clipboardOffer.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        offer?.let { text ->
            item { ClipboardBanner(text, onDownload = model::downloadClipboard, onDismiss = model::dismissClipboard) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.url,
                    onValueChange = model::setUrl,
                    label = { Text("Enlace") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        TextButton(onClick = {
                            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clip.isBlank()) {
                                Toast.makeText(context, "El portapapeles no tiene texto", Toast.LENGTH_SHORT).show()
                            } else {
                                model.setUrl(clip.trim())
                            }
                        }) { Text("Pegar") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Dropdown(
                    value = form.kind, options = MediaKind.entries, label = { it.title },
                    onSelect = model::setKind, modifier = Modifier.fillMaxWidth(),
                )
                Text(form.kind.help, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown(
                        value = form.resolution, options = VideoResolution.entries, label = { "Calidad: " + it.title },
                        onSelect = model::setResolution, enabled = !form.kind.isAudio, modifier = Modifier.weight(1f),
                    )
                    Dropdown(
                        value = form.formatLabel.ifEmpty { "Formatos…" },
                        options = listOf("Formatos…") + if (form.formatOverride.isNotEmpty()) listOf("Quitar formato") else emptyList(),
                        label = { it },
                        onSelect = { if (it == "Quitar formato") model.setFormat("", "") else onFormats() },
                        enabled = form.kind != MediaKind.GIF && !form.kind.isAudio && extractUrls(form.url).size == 1,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.clipStart, onValueChange = model::setClipStart,
                        label = { Text("Desde") }, placeholder = { Text("mm:ss") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.clipEnd, onValueChange = model::setClipEnd,
                        label = { Text("Hasta") }, placeholder = { Text("mm:ss") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        if (model.download() == 0) Toast.makeText(context, "No hay ningún enlace válido.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = form.url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Descargar") }
                when (val s = engine) {
                    Engine.Status.Starting -> Text("Preparando yt-dlp…", style = MaterialTheme.typography.bodySmall)
                    is Engine.Status.Failed -> Text("yt-dlp no arrancó: ${s.message}", color = MaterialTheme.colorScheme.error)
                    is Engine.Status.Ready -> {}
                }
            }
        }
        if (items.isNotEmpty()) {
            item {
                Column {
                    Row {
                        Text(
                            if (summary.active) "Descargando ${summary.done} de ${summary.total}" else "Cola",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(top = 12.dp),
                        )
                        if (items.any { it.state.finished }) {
                            TextButton(onClick = { model.hub.clearFinished() }) { Text("Limpiar terminadas") }
                        }
                        if (summary.active) TextButton(onClick = { model.hub.cancelAll() }) { Text("Cancelar todo") }
                    }
                    if (summary.active) {
                        LinearProgressIndicator(progress = { summary.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            QueueCard(item, model.hub, onLog = { logFor = item })
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }

    logFor?.let { item -> LogDialog(item, model.hub.logOf(item.id)) { logFor = null } }
}

@Composable
private fun ClipboardBanner(text: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val urls = extractUrls(text)
    val shown = if (urls.size == 1) urls[0] else "${urls.size} enlaces"
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Has copiado $shown",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Ahora no") }
                TextButton(onClick = onDownload) { Text("Descargar") }
            }
        }
    }
}
