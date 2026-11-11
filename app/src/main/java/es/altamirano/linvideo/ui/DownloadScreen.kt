package es.altamirano.linvideo.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
    val (url, setUrl) = rememberFieldText(form.url, model::setUrl)
    val (clipStart, setClipStart) = rememberFieldText(form.clipStart, model::setClipStart)
    val (clipEnd, setClipEnd) = rememberFieldText(form.clipEnd, model::setClipEnd)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        offer?.let { text ->
            item(key = "portapapeles") {
                ClipboardBanner(text, onDownload = model::downloadClipboard, onDismiss = model::dismissClipboard, modifier = Modifier.animateItem())
            }
        }
        item(key = "formulario") {
            Section {
                TextField(
                    value = url,
                    onValueChange = setUrl,
                    label = { Text("Enlace") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    trailingIcon = {
                        FilledTonalButton(
                            onClick = {
                                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (clip.isBlank()) {
                                    Toast.makeText(context, "El portapapeles no tiene texto", Toast.LENGTH_SHORT).show()
                                } else {
                                    setUrl(clip.trim())
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                            modifier = Modifier.padding(end = 8.dp).height(ButtonDefaults.ExtraSmallContainerHeight),
                        ) { Text("Pegar") }
                    },
                    shape = FieldShape,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                    Dropdown(
                        value = form.kind, options = MediaKind.entries, label = { it.title },
                        onSelect = model::setKind, modifier = Modifier.fillMaxWidth(), title = "Tipo",
                    )
                    Text(
                        form.kind.help, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown(
                        value = form.resolution, options = VideoResolution.entries, label = { it.title },
                        onSelect = model::setResolution, enabled = !form.kind.isAudio, modifier = Modifier.weight(1f),
                        title = "Calidad",
                    )
                    Dropdown(
                        value = form.formatLabel.ifEmpty { "Formatos…" },
                        options = listOf("Formatos…") + if (form.formatOverride.isNotEmpty()) listOf("Quitar formato") else emptyList(),
                        label = { it },
                        onSelect = { if (it == "Quitar formato") model.setFormat("", "") else onFormats() },
                        enabled = form.kind != MediaKind.GIF && !form.kind.isAudio && extractUrls(form.url).size == 1,
                        modifier = Modifier.weight(1f),
                        title = "Formato",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = clipStart, onValueChange = setClipStart,
                        label = { Text("Desde") }, placeholder = { Text("mm:ss") }, singleLine = true,
                        shape = FieldShape, colors = fieldColors(), modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = clipEnd, onValueChange = setClipEnd,
                        label = { Text("Hasta") }, placeholder = { Text("mm:ss") }, singleLine = true,
                        shape = FieldShape, colors = fieldColors(), modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        if (model.download() == 0) Toast.makeText(context, "No hay ningún enlace válido.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = form.url.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                ) { Text("Descargar", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
                when (val s = engine) {
                    Engine.Status.Starting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoadingIndicator(Modifier.size(32.dp))
                        Text("Preparando yt-dlp…", style = MaterialTheme.typography.bodySmall)
                    }
                    is Engine.Status.Failed -> Text("yt-dlp no arrancó: ${s.message}", color = MaterialTheme.colorScheme.error)
                    is Engine.Status.Ready -> {}
                }
            }
        }
        if (items.isNotEmpty()) {
            item(key = "cabecera-cola") {
                Column(Modifier.animateItem().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (summary.active) "Descargando ${summary.done} de ${summary.total}" else "Cola",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        if (items.any { it.state.finished }) {
                            TextButton(onClick = { model.hub.clearFinished() }, shapes = ButtonDefaults.shapes()) { Text("Limpiar terminadas") }
                        }
                        if (summary.active) {
                            TextButton(onClick = { model.hub.cancelAll() }, shapes = ButtonDefaults.shapes()) { Text("Cancelar todo") }
                        }
                    }
                    if (summary.active) {
                        LinearWavyProgressIndicator(progress = { summary.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            QueueCard(item, model.hub, onLog = { logFor = item }, modifier = Modifier.animateItem())
        }
    }

    logFor?.let { item -> LogDialog(item, model.hub.logOf(item.id)) { logFor = null } }
}

@Composable
private fun ClipboardBanner(text: String, onDownload: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val urls = extractUrls(text)
    val shown = if (urls.size == 1) urls[0] else "${urls.size} enlaces"
    Section(modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "Has copiado $shown",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Ahora no") }
            Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) { Text("Descargar") }
        }
    }
}
