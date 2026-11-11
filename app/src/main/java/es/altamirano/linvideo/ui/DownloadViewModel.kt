package es.altamirano.linvideo.ui

import androidx.lifecycle.ViewModel
import es.altamirano.linvideo.LinVideoApp
import es.altamirano.linvideo.engine.Links.extractUrls
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.model.VideoResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Lo que se está escribiendo en la pestaña Descargar. El tipo y la calidad empiezan con los de Ajustes. */
data class DownloadForm(
    val url: String = "",
    val kind: MediaKind,
    val resolution: VideoResolution,
    val clipStart: String = "",
    val clipEnd: String = "",
    val formatOverride: String = "",
    val formatLabel: String = "",
)

class DownloadViewModel : ViewModel() {
    private val app = LinVideoApp.instance
    val hub = app.hub

    private val _form = MutableStateFlow(
        app.settings.settings.value.let { DownloadForm(kind = it.kind, resolution = it.options.resolution) },
    )
    val form: StateFlow<DownloadForm> = _form.asStateFlow()

    fun setUrl(url: String) = _form.update { it.copy(url = url) }
    // Como en LinVideo, el tipo y la calidad elegidos pasan a ser los de siempre.
    fun setKind(kind: MediaKind) {
        _form.update { it.copy(kind = kind) }
        app.settings.update { it.copy(kind = kind) }
    }

    fun setResolution(r: VideoResolution) {
        _form.update { it.copy(resolution = r) }
        app.settings.update { it.copy(options = it.options.copy(resolution = r)) }
    }

    // --- Aviso del enlace copiado ---------------------------------------------------

    private var lastClipboard = ""
    private val _clipboardOffer = MutableStateFlow<String?>(null)
    /** El texto copiado que tiene enlaces, mientras se ofrece descargarlo. */
    val clipboardOffer: StateFlow<String?> = _clipboardOffer.asStateFlow()

    /** Al volver a la app. Android sólo deja leer el portapapeles con la ventana delante. */
    fun clipboardSeen(text: String) {
        if (!app.settings.settings.value.watchClipboard) return
        if (extractUrls(text).isEmpty() || text == lastClipboard) return
        lastClipboard = text
        if (text.trim() == _form.value.url.trim()) return
        _clipboardOffer.value = text
    }

    fun dismissClipboard() {
        _clipboardOffer.value = null
    }

    fun downloadClipboard() {
        val text = _clipboardOffer.value ?: return
        _clipboardOffer.value = null
        val f = _form.value
        extractUrls(text).forEach { url ->
            hub.add(url, f.kind) { it.copy(resolution = f.resolution) }
        }
    }
    fun setClipStart(t: String) = _form.update { it.copy(clipStart = t) }
    fun setClipEnd(t: String) = _form.update { it.copy(clipEnd = t) }
    fun setFormat(selector: String, label: String) = _form.update { it.copy(formatOverride = selector, formatLabel = label) }

    /** Añade a la cola todos los enlaces del campo. Devuelve cuántos. */
    fun download(): Int {
        val f = _form.value
        val urls = extractUrls(f.url)
        urls.forEach { url ->
            hub.add(url, f.kind) { options ->
                options.copy(
                    resolution = f.resolution,
                    clipStart = f.clipStart,
                    clipEnd = f.clipEnd,
                    // El formato elegido a mano es de un enlace concreto.
                    formatOverride = if (urls.size == 1) f.formatOverride else "",
                )
            }
        }
        if (urls.isNotEmpty()) {
            _form.update { it.copy(url = "", clipStart = "", clipEnd = "", formatOverride = "", formatLabel = "") }
        }
        return urls.size
    }
}
