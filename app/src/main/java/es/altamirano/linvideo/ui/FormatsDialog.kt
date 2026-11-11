package es.altamirano.linvideo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.altamirano.linvideo.engine.Formats
import es.altamirano.linvideo.engine.MediaFormat

private sealed interface FormatsState {
    data object Loading : FormatsState
    data class Failed(val message: String) : FormatsState
    data class Loaded(val title: String, val formats: List<MediaFormat>) : FormatsState
}

/** «Formatos…»: lo que la web ofrece de verdad para un enlace, para elegir uno. */
@Composable
fun FormatsDialog(link: String, onChoose: (selector: String, description: String) -> Unit, onDismiss: () -> Unit) {
    var state by remember(link) { mutableStateOf<FormatsState>(FormatsState.Loading) }
    LaunchedEffect(link) {
        state = try {
            val (title, found) = Formats.inspect(link)
            if (found.isEmpty()) FormatsState.Failed("La web no ha devuelto ningún formato.")
            else FormatsState.Loaded(title, found)
        } catch (e: Exception) {
            FormatsState.Failed(e.message ?: e.toString())
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Formatos disponibles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
                when (val s = state) {
                    FormatsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Preguntando a la web…")
                        }
                    }
                    is FormatsState.Failed -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No se pudieron leer los formatos", style = MaterialTheme.typography.titleMedium)
                        Text(s.message)
                    }
                    is FormatsState.Loaded -> FormatList(s, onChoose)
                }
            }
        }
    }
}

@Composable
private fun FormatList(state: FormatsState.Loaded, onChoose: (String, String) -> Unit) {
    val order = compareByDescending<MediaFormat> { it.height }.thenByDescending { it.bitrate ?: 0.0 }
    val groups = listOf(
        "Vídeo con audio" to state.formats.filter { it.isComplete },
        "Sólo vídeo (se le añade audio)" to state.formats.filter { it.isVideoOnly },
        "Sólo audio" to state.formats.filter { it.isAudioOnly },
    ).filter { it.second.isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Al elegir uno de sólo vídeo se le añade la mejor pista de audio (AAC si la hay). " +
                        "Se aplica a la próxima descarga.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groups.forEach { (name, formats) ->
            item {
                Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(formats.sortedWith(order), key = { name + it.id }) { f ->
                val details = listOfNotNull(f.codecText, f.ext, f.sizeText, f.note).joinToString("  ·  ")
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onChoose(f.selector, "${f.qualityText} · ${f.codecText} (${f.id})") }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.qualityText, style = MaterialTheme.typography.bodyLarge)
                        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(f.id, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}
