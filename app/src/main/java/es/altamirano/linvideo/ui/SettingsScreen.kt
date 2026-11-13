package es.altamirano.linvideo.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.altamirano.linvideo.LinVideoApp
import es.altamirano.linvideo.engine.Engine
import es.altamirano.linvideo.engine.Storage
import es.altamirano.linvideo.model.DownloadOptions
import es.altamirano.linvideo.model.FilenameCleanup
import es.altamirano.linvideo.model.Legal
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.model.Settings
import es.altamirano.linvideo.model.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Ajustes: se guardan al momento, sin botón de aceptar (`ui/preferences.py`). */
@Composable
fun SettingsScreen() {
    val repo = LinVideoApp.instance.settings
    val s by repo.settings.collectAsStateWithLifecycle()
    val engine by Engine.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var showLegal by remember { mutableStateOf(false) }
    fun set(change: (Settings) -> Settings) = repo.update(change)
    fun opt(change: (DownloadOptions) -> DownloadOptions) = repo.update { it.copy(options = change(it.options)) }
    val o = s.options

    val importCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val target = es.altamirano.linvideo.LinVideoApp.cookiesFile(context)
                    context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                    target.absolutePath
                }.getOrNull()
            }
            if (ok != null) opt { it.copy(cookiesFile = ok) }
            Toast.makeText(context, if (ok != null) "cookies.txt importado" else "No se pudo leer el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    val cookiesLoaded = o.cookiesFile.isNotEmpty() && File(o.cookiesFile).exists()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Group("Descargas") {
            row { EntryRow("Carpeta dentro de Descargas", s.destination, Storage.DEFAULT_FOLDER,
                "Se guarda en ${Storage.downloadDirectory(s.destination).absolutePath}") { v -> set { it.copy(destination = v) } } }
            row { ChoiceRow("Formato por omisión", s.kind, MediaKind.entries, { it.title }) { v -> set { it.copy(kind = v) } } }
            row { ChoiceRow("Techo de resolución", o.resolution, VideoResolution.entries, { it.title }) { v -> opt { it.copy(resolution = v) } } }
            row { StepRow("Descargas simultáneas", s.maxConcurrent, 1, 8) { v -> set { it.copy(maxConcurrent = v) } } }
            row { SwitchRow("Descargar la lista completa", "Si el enlace es una lista de reproducción, bajarla entera", o.downloadPlaylist) { v ->
                opt { it.copy(downloadPlaylist = v) } } }
            row { SwitchRow("Ofrecer el enlace copiado", "Al volver a la app con un enlace en el portapapeles", s.watchClipboard) { v ->
                set { it.copy(watchClipboard = v) } } }
        }

        Group("Nombre del archivo") {
            row { ChoiceRow("Limpieza del nombre", o.filenameCleanup, FilenameCleanup.entries, { it.title }, o.filenameCleanup.help) { v ->
                opt { it.copy(filenameCleanup = v) } } }
            row { SwitchRow("Conservar el identificador", "Deja el [id] del vídeo al final del nombre", o.keepIdentifier) { v -> opt { it.copy(keepIdentifier = v) } } }
        }

        Group("Contenido") {
            row { SwitchRow("Metadatos", "Título, artista, fecha y descripción", o.embedMetadata) { v -> opt { it.copy(embedMetadata = v) } } }
            row { SwitchRow("Artista y título a partir del nombre", "En audio, si el vídeo se llama «Artista - Título»", o.smartAudioTags) { v ->
                opt { it.copy(smartAudioTags = v) } } }
            row { SwitchRow("Portada en el audio", "La miniatura, recortada en cuadrado", o.embedThumbnail) { v -> opt { it.copy(embedThumbnail = v) } } }
            row { SwitchRow("Subtítulos en el vídeo", "Español e inglés, si los hay", o.embedSubtitles) { v -> opt { it.copy(embedSubtitles = v) } } }
        }

        Group("GIF") {
            row { StepRow("Fotogramas por segundo", o.gifFps, 1, 50) { v -> opt { it.copy(gifFps = v) } } }
            row { StepRow("Ancho en píxeles", o.gifWidth, 64, 1920, step = 16) { v -> opt { it.copy(gifWidth = v) } } }
        }

        Group("Red") {
            row {
                ListItem(
                    headlineContent = { Text("Cookies") },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (cookiesLoaded) "cookies.txt importado" else "Sin cookies", fontWeight = FontWeight.Medium)
                            Text(
                                "Las cookies sirven para las webs que piden confirmar la edad o iniciar sesión. En Android no se " +
                                    "pueden leer las del navegador: exporta un cookies.txt (formato Netscape) desde un navegador " +
                                    "donde tengas la sesión abierta e impórtalo aquí. Se queda dentro de la app.",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { importCookies.launch(arrayOf("text/*", "application/octet-stream")) },
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text("Importar…") }
                                if (o.cookiesFile.isNotEmpty()) {
                                    TextButton(
                                        onClick = { File(o.cookiesFile).delete(); opt { it.copy(cookiesFile = "") } },
                                        shapes = ButtonDefaults.shapes(),
                                    ) { Text("Quitar") }
                                }
                            }
                        }
                    },
                    colors = rowColors(),
                )
            }
            row { EntryRow("Límite de velocidad", o.speedLimit, "Sin límite (ej.: 2M, 500K)") { v -> opt { it.copy(speedLimit = v) } } }
            row { StepRow("Fragmentos simultáneos", o.concurrentFragments, 1, 16) { v -> opt { it.copy(concurrentFragments = v) } } }
            row { EntryRow("Opciones extra de yt-dlp", o.extraArguments, "Se respetan las comillas") { v -> opt { it.copy(extraArguments = v) } } }
        }

        Group("Herramientas") {
            row {
                ListItem(
                    headlineContent = { Text("yt-dlp") },
                    supportingContent = {
                        Text(
                            when (val e = engine) {
                                Engine.Status.Starting -> "Preparando…"
                                is Engine.Status.Ready -> if (updating) "Descargando la última versión…" else e.version ?: "versión desconocida"
                                is Engine.Status.Failed -> "No arrancó: ${e.message}"
                            },
                        )
                    },
                    trailingContent = {
                        if (updating) {
                            LoadingIndicator(Modifier.size(40.dp))
                        } else {
                            FilledTonalButton(
                                enabled = engine is Engine.Status.Ready,
                                shapes = ButtonDefaults.shapes(),
                                onClick = {
                                    updating = true
                                    scope.launch {
                                        val message = Engine.update()
                                        if (!message.startsWith("No se pudo")) set { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                                        updating = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                            ) { Text("Actualizar ahora") }
                        }
                    },
                    colors = rowColors(),
                )
            }
            row { SwitchRow("Actualizar yt-dlp solo", "Como mucho una comprobación al día, al abrir", s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } } }
        }

        Group("Acerca de") {
            row {
                ListItem(
                    headlineContent = { Text("Aviso legal") },
                    colors = rowColors(),
                    modifier = Modifier.clickable { showLegal = true },
                )
            }
        }
    }

    if (showLegal) LegalDialog(firstTime = false) { showLegal = false }
}

@Composable
fun LegalDialog(firstTime: Boolean, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!firstTime) onAccept() },
        title = { Text(if (firstTime) "Antes de empezar" else "Aviso legal") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Legal.SUMMARY)
                Legal.POINTS.forEach { (title, detail) ->
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
                Text("Programas que usa", style = MaterialTheme.typography.titleSmall)
                Legal.DEPENDENCIES.forEach { (name, licence, detail) ->
                    Text("· $name ($licence): $detail", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(if (firstTime) "Entendido" else "Cerrar") } },
    )
}

// --- Filas ---------------------------------------------------------------------------

private class GroupScope {
    val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) { rows += content }
}

/** Grupo de filas al estilo de los ajustes de Android: bloques separados, esquinas grandes en los extremos. */
@Composable
private fun Group(title: String, build: GroupScope.() -> Unit) {
    val rows = GroupScope().apply(build).rows
    Column {
        SectionTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { index, content ->
                val big = 24.dp
                val small = 4.dp
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) big else small, topEnd = if (index == 0) big else small,
                    bottomStart = if (index == rows.lastIndex) big else small, bottomEnd = if (index == rows.lastIndex) big else small,
                )
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SwitchRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = value, onCheckedChange = onChange,
                thumbContent = if (value) { { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(SwitchDefaults.IconSize)) } } else null,
            )
        },
        colors = rowColors(),
        modifier = Modifier.toggleable(value = value, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun <T> ChoiceRow(title: String, value: T, options: List<T>, label: (T) -> String, help: String? = null, onChange: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Dropdown(value, options, label, onChange, Modifier.fillMaxWidth(), title = title)
        help?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun StepRow(title: String, value: Int, low: Int, high: Int, step: Int = 1, onChange: (Int) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = { onChange((value - step).coerceIn(low, high)) }, enabled = value > low,
                    shapes = IconButtonDefaults.shapes(),
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("$value", style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 40.dp))
                FilledTonalIconButton(
                    onClick = { onChange((value + step).coerceIn(low, high)) }, enabled = value < high,
                    shapes = IconButtonDefaults.shapes(),
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        },
        colors = rowColors(),
    )
}

/** Texto que se guarda al pulsar «Guardar», como el EntryRow con botón de aplicar. */
@Composable
private fun EntryRow(title: String, value: String, hint: String, caption: String? = null, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(title) },
            placeholder = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            trailingIcon = {
                if (text.trim() != value) {
                    Button(
                        onClick = {
                            onSave(text.trim())
                            Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 6.dp),
                    ) { Text("Guardar") }
                }
            },
            shape = FieldShape,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        caption?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
