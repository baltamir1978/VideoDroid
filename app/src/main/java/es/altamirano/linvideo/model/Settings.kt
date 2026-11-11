package es.altamirano.linvideo.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Ajustes guardados entre sesiones (`settings.py`). Mismos campos y valores por defecto.
 * [destination] es una subcarpeta de `Download/`; vacía, `Download/LinVideo`.
 */
data class Settings(
    val kind: MediaKind = MediaKind.VIDEO_MAX,
    val destination: String = "",
    val options: DownloadOptions = DownloadOptions(),
    val maxConcurrent: Int = 2,
    /** yt-dlp se actualiza solo, una vez al día. */
    val autoUpdate: Boolean = true,
    /** El aviso legal se enseña hasta que se acepta. */
    val legalAccepted: Boolean = false,
    /** Ofrecer el enlace copiado al volver a la app. */
    val watchClipboard: Boolean = true,
    /** Última comprobación de actualizaciones de yt-dlp (milisegundos). No está en LinVideo. */
    val lastUpdateCheck: Long = 0,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("kind", kind.value)
        put("destination", destination)
        put("options", options.toJson(persistentOnly = true))
        put("max_concurrent", maxConcurrent)
        put("auto_update", autoUpdate)
        put("legal_accepted", legalAccepted)
        put("watch_clipboard", watchClipboard)
        put("last_update_check", lastUpdateCheck)
    }

    companion object {
        /** Lee lo que se pueda; lo desconocido o mal formado se queda con su valor por omisión. */
        fun fromJson(data: JsonObject): Settings {
            val d = Settings()
            return Settings(
                kind = data.string("kind")?.let(MediaKind::fromValue) ?: d.kind,
                destination = data.string("destination") ?: d.destination,
                options = (data["options"] as? JsonObject)?.let(DownloadOptions::fromJson) ?: d.options,
                maxConcurrent = data.int("max_concurrent")?.takeIf { it > 0 } ?: d.maxConcurrent,
                autoUpdate = data.bool("auto_update") ?: d.autoUpdate,
                legalAccepted = data.bool("legal_accepted") ?: d.legalAccepted,
                watchClipboard = data.bool("watch_clipboard") ?: d.watchClipboard,
                lastUpdateCheck = data.long("last_update_check") ?: d.lastUpdateCheck,
            )
        }
    }
}

fun DownloadOptions.toJson(persistentOnly: Boolean = false): JsonObject = buildJsonObject {
    put("cookies_file", cookiesFile)
    put("resolution", resolution.value)
    if (!persistentOnly) {
        put("clip_start", clipStart)
        put("clip_end", clipEnd)
        put("format_override", formatOverride)
    }
    put("download_playlist", downloadPlaylist)
    put("speed_limit", speedLimit)
    put("filename_cleanup", filenameCleanup.value)
    put("keep_identifier", keepIdentifier)
    put("embed_metadata", embedMetadata)
    put("smart_audio_tags", smartAudioTags)
    put("embed_subtitles", embedSubtitles)
    put("embed_thumbnail", embedThumbnail)
    put("concurrent_fragments", concurrentFragments)
    put("gif_fps", gifFps)
    put("gif_width", gifWidth)
    put("extra_arguments", extraArguments)
}

fun DownloadOptions.Companion.fromJson(data: JsonObject): DownloadOptions {
    val d = DownloadOptions()
    return DownloadOptions(
        cookiesFile = data.string("cookies_file") ?: d.cookiesFile,
        resolution = data.int("resolution")?.let(VideoResolution::fromValue) ?: d.resolution,
        clipStart = data.string("clip_start") ?: d.clipStart,
        clipEnd = data.string("clip_end") ?: d.clipEnd,
        formatOverride = data.string("format_override") ?: d.formatOverride,
        downloadPlaylist = data.bool("download_playlist") ?: d.downloadPlaylist,
        speedLimit = data.string("speed_limit") ?: d.speedLimit,
        filenameCleanup = data.string("filename_cleanup")?.let(FilenameCleanup::fromValue) ?: d.filenameCleanup,
        keepIdentifier = data.bool("keep_identifier") ?: d.keepIdentifier,
        embedMetadata = data.bool("embed_metadata") ?: d.embedMetadata,
        smartAudioTags = data.bool("smart_audio_tags") ?: d.smartAudioTags,
        embedSubtitles = data.bool("embed_subtitles") ?: d.embedSubtitles,
        embedThumbnail = data.bool("embed_thumbnail") ?: d.embedThumbnail,
        concurrentFragments = data.int("concurrent_fragments") ?: d.concurrentFragments,
        gifFps = data.int("gif_fps") ?: d.gifFps,
        gifWidth = data.int("gif_width") ?: d.gifWidth,
        extraArguments = data.string("extra_arguments") ?: d.extraArguments,
    )
}

/** Una descarga sin terminar, tal como se guarda en `pendientes.json`. */
data class PendingEntry(
    val url: String,
    val kind: MediaKind,
    /** Ruta absoluta de la carpeta de destino. */
    val destination: String,
    @Volatile var title: String,
    val options: DownloadOptions,
    val id: String = UUID.randomUUID().toString().replace("-", ""),
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("url", url)
        put("kind", kind.value)
        put("destination", destination)
        put("title", title)
        put("options", options.toJson())
    }

    companion object {
        fun fromJson(data: JsonObject): PendingEntry? {
            val id = data.anyText("id") ?: return null
            val url = data.anyText("url") ?: return null
            val kind = data.string("kind")?.let(MediaKind::fromValue) ?: return null
            val destination = data.anyText("destination") ?: return null
            return PendingEntry(
                url = url, kind = kind, destination = destination,
                title = data.anyText("title") ?: "",
                options = (data["options"] as? JsonObject)?.let(DownloadOptions::fromJson) ?: DownloadOptions(),
                id = id,
            )
        }
    }
}

// --- Lectura tolerante: el tipo tiene que ser el esperado, si no se ignora -------------

private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

private fun JsonPrimitive.isPlainNumber() = !isString && booleanOrNull == null && content != "null"

internal fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content
internal fun JsonObject.bool(key: String): Boolean? = primitive(key)?.takeIf { !it.isString }?.booleanOrNull
internal fun JsonObject.int(key: String): Int? =
    primitive(key)?.takeIf { it.isPlainNumber() }?.content?.toIntOrNull()
internal fun JsonObject.long(key: String): Long? =
    primitive(key)?.takeIf { it.isPlainNumber() }?.content?.toLongOrNull()
/** Como `str(...)` en Python: vale cualquier valor simple que no sea null. */
private fun JsonObject.anyText(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.content != "null" || it.isString }?.content

