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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
                    val target = File(context.filesDir, "cookies.txt")
                    context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                    target.absolutePath
                }.getOrNull()
            }
            if (ok != null) opt { it.copy(cookiesFile = ok) }
            Toast.makeText(context, if (ok != null) "cookies.txt importado" else "No se pudo leer el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Group("Descargas")
        EntryRow("Carpeta dentro de Descargas", s.destination, Storage.DEFAULT_FOLDER) { v -> set { it.copy(destination = v) } }
        Caption("Se guarda en ${Storage.downloadDirectory(s.destination).absolutePath}")
        ChoiceRow("Formato por omisión", s.kind, MediaKind.entries, { it.title }) { v -> set { it.copy(kind = v) } }
        ChoiceRow("Techo de resolución", o.resolution, VideoResolution.entries, { it.title }) { v -> opt { it.copy(resolution = v) } }
        StepRow("Descargas simultáneas", s.maxConcurrent, 1, 8) { v -> set { it.copy(maxConcurrent = v) } }
        SwitchRow("Descargar la lista completa", "Si el enlace es una lista de reproducción, bajarla entera", o.downloadPlaylist) { v ->
            opt { it.copy(downloadPlaylist = v) }
        }
        SwitchRow("Ofrecer el enlace copiado", "Al volver a la app con un enlace en el portapapeles", s.watchClipboard) { v ->
            set { it.copy(watchClipboard = v) }
        }

        Group("Nombre del archivo")
        ChoiceRow("Limpieza del nombre", o.filenameCleanup, FilenameCleanup.entries, { it.title }) { v -> opt { it.copy(filenameCleanup = v) } }
        Caption(o.filenameCleanup.help)
        SwitchRow("Conservar el identificador", "Deja el [id] del vídeo al final del nombre", o.keepIdentifier) { v -> opt { it.copy(keepIdentifier = v) } }

        Group("Contenido")
        SwitchRow("Metadatos", "Título, artista, fecha y descripción", o.embedMetadata) { v -> opt { it.copy(embedMetadata = v) } }
        SwitchRow("Artista y título a partir del nombre", "En audio, si el vídeo se llama «Artista - Título»", o.smartAudioTags) { v ->
            opt { it.copy(smartAudioTags = v) }
        }
        SwitchRow("Portada en el audio", "La miniatura, recortada en cuadrado", o.embedThumbnail) { v -> opt { it.copy(embedThumbnail = v) } }
        SwitchRow("Subtítulos en el vídeo", "Español e inglés, si los hay", o.embedSubtitles) { v -> opt { it.copy(embedSubtitles = v) } }

        Group("GIF")
        StepRow("Fotogramas por segundo", o.gifFps, 1, 50) { v -> opt { it.copy(gifFps = v) } }
        StepRow("Ancho en píxeles", o.gifWidth, 64, 1920, step = 16) { v -> opt { it.copy(gifWidth = v) } }

        Group("Red")
        Caption(
            "Las cookies sirven para las webs que piden confirmar la edad o iniciar sesión. En Android no se " +
                "pueden leer las del navegador: exporta un cookies.txt (formato Netscape) desde un navegador " +
                "donde tengas la sesión abierta e impórtalo aquí. Se queda dentro de la app.",
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cookies", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (o.cookiesFile.isNotEmpty() && File(o.cookiesFile).exists()) "cookies.txt importado" else "Sin cookies",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { importCookies.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Importar…") }
            if (o.cookiesFile.isNotEmpty()) {
                TextButton(onClick = { File(o.cookiesFile).delete(); opt { it.copy(cookiesFile = "") } }) { Text("Quitar") }
            }
        }
        EntryRow("Límite de velocidad", o.speedLimit, "Sin límite (ej.: 2M, 500K)") { v -> opt { it.copy(speedLimit = v) } }
        StepRow("Fragmentos simultáneos", o.concurrentFragments, 1, 16) { v -> opt { it.copy(concurrentFragments = v) } }
        EntryRow("Opciones extra de yt-dlp", o.extraArguments, "Se respetan las comillas") { v -> opt { it.copy(extraArguments = v) } }

        Group("Herramientas")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("yt-dlp", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (val e = engine) {
                        Engine.Status.Starting -> "Preparando…"
                        is Engine.Status.Ready -> if (updating) "Descargando la última versión…" else e.version ?: "versión desconocida"
                        is Engine.Status.Failed -> "No arrancó: ${e.message}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                enabled = engine is Engine.Status.Ready && !updating,
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
        SwitchRow("Actualizar yt-dlp solo", "Como mucho una comprobación al día, al abrir", s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } }

        Group("Acerca de")
        Text(
            "Aviso legal", style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable { showLegal = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        )
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

@Composable
private fun Group(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, value: T, options: List<T>, label: (T) -> String, onChange: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Dropdown(value, options, label, onChange, Modifier.widthIn(max = 220.dp))
    }
}

@Composable
private fun StepRow(title: String, value: Int, low: Int, high: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(low, high)) }, enabled = value > low) { Text("−") }
        Text("$value", style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange((value + step).coerceIn(low, high)) }, enabled = value < high) { Text("+") }
    }
}

/** Texto que se guarda al pulsar «Guardar», como el EntryRow con botón de aplicar. */
@Composable
private fun EntryRow(title: String, value: String, hint: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val context = LocalContext.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(title) },
        placeholder = { Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        trailingIcon = {
            if (text.trim() != value) {
                TextButton(onClick = {
                    onSave(text.trim())
                    Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
                }) { Text("Guardar") }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
