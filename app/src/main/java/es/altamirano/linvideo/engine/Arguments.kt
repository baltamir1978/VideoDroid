package es.altamirano.linvideo.engine

import es.altamirano.linvideo.model.DownloadOptions
import es.altamirano.linvideo.model.MediaKind

/** La línea de órdenes de yt-dlp (`DownloadRequest.ytdlp_arguments` de LinVideo). */
object Arguments {
    // Marcas para leer la salida de yt-dlp sin ambigüedad.
    const val TITLE_TAG = "LV-TITLE|"
    const val FILE_TAG = "LV-FILE|"
    const val PROGRESS_TAG = "LV-PROG|"

    const val OUTPUT_TEMPLATE = "%(title).150B [%(id)s].%(ext)s"

    /**
     * Argumentos para yt-dlp, con el enlace al final. En Android la librería añade sola
     * `--ffmpeg-location` y el intérprete de JavaScript; [ffmpegDir] queda por si hiciera falta.
     */
    fun build(
        url: String,
        kind: MediaKind,
        options: DownloadOptions,
        outputDirectory: String,
        ffmpegDir: String? = null,
    ): List<String> {
        val args = mutableListOf(
            "--ignore-config",
            "--no-color",
            "--newline",
            "--progress",
            "--no-simulate",
            "--no-quiet",
            "--no-warnings",
            "--no-mtime",
            "--concurrent-fragments", maxOf(1, options.concurrentFragments).toString(),
            "--retries", "10",
            "--fragment-retries", "10",
            "--progress-template",
            "download:$PROGRESS_TAG%(progress.downloaded_bytes)s|%(progress.total_bytes)s|" +
                "%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s",
            "--print", "$TITLE_TAG%(title)s",
            "--print", "after_move:$FILE_TAG%(filepath)s",
            "--paths", outputDirectory,
            "-o", OUTPUT_TEMPLATE,
        )

        args += if (options.downloadPlaylist) "--yes-playlist" else "--no-playlist"

        if (ffmpegDir != null) args += listOf("--ffmpeg-location", ffmpegDir)

        val cookies = options.cookiesFile.trim()
        if (cookies.isNotEmpty()) args += listOf("--cookies", cookies)

        downloadSection(options.clipStart, options.clipEnd)?.let {
            args += listOf("--download-sections", it, "--force-keyframes-at-cuts")
        }

        val limit = options.speedLimit.trim()
        if (limit.isNotEmpty()) args += listOf("--limit-rate", limit)

        val height = options.resolution.heightFilter

        // Formato elegido a mano: manda sobre el modo y la calidad.
        val chosen = options.formatOverride.trim()
        if (chosen.isNotEmpty() && kind != MediaKind.GIF && !kind.isAudio) {
            args += listOf("-f", chosen, "--merge-output-format", "mp4")
            if (options.embedMetadata) args += "--embed-metadata"
            args += url
            return args
        }

        args += when (kind) {
            MediaKind.VIDEO_MAX -> listOf(
                "-f", "bv*$height+ba/b$height/bv*+ba/b",
                "--merge-output-format", "mp4", "--remux-video", "mp4",
            )
            MediaKind.VIDEO_COMPAT -> listOf(
                "-f",
                "bv*$height[vcodec^=avc1]+ba[acodec^=mp4a]/bv*$height[vcodec^=avc1]+ba/" +
                    "b$height[ext=mp4]/bv*$height+ba/b",
                "--merge-output-format", "mp4",
            )
            MediaKind.AUDIO_MP3 -> listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "0")
            MediaKind.AUDIO_ORIGINAL -> listOf("-f", "ba/b", "-x", "--audio-format", "best")
            // En LinVideo el GIF lo hacía ffmpeg aparte. Aquí lo hace el propio yt-dlp al
            // terminar (--recode-video gif), con el mismo filtro de paleta: así no hay que lanzar
            // el ffmpeg de la librería a mano. -an -sn porque yt-dlp mapea todas las pistas.
            MediaKind.GIF -> listOf(
                "-f", "bv*$height/b$height/bv*/b",
                "--recode-video", "gif",
                "--postprocessor-args", "VideoConvertor:-vf \"${gifFilter(options)}\" -an -sn -loop 0",
            )
        }

        if (options.embedMetadata) {
            args += "--embed-metadata"
            if (kind.isAudio) args += audioTaggingArguments(options)
        }
        // La miniatura sólo se incrusta en audio: en MP4 haría falta AtomicParsley.
        if (options.embedThumbnail && kind.isAudio) {
            // JPG y recorte cuadrado: es lo que esperan los reproductores como portada.
            args += listOf(
                "--embed-thumbnail", "--convert-thumbnails", "jpg",
                "--postprocessor-args", "ThumbnailsConvertor:-vf crop=ih:ih",
            )
        }
        if (options.embedSubtitles && !kind.isAudio && kind != MediaKind.GIF) {
            args += listOf("--embed-subs", "--sub-langs", "es.*,en.*", "--compat-options", "no-keep-subs")
        }

        val extra = options.extraArguments.trim()
        if (extra.isNotEmpty()) {
            // Se respetan las comillas («--opción "con espacios"»), como en LinVideo.
            args += Shlex.split(extra) ?: extra.split(Regex("\\s+"))
        }

        args += url
        return args
    }

    /**
     * Etiquetas para los archivos de audio. YouTube casi nunca rellena artista o álbum: se toman
     * los campos de música si existen y, si no, el canal. «Artista - Canción» se reparte en los
     * dos campos. El álbum no se toca: forzarlo acababa escribiendo «NA».
     */
    private fun audioTaggingArguments(options: DownloadOptions): List<String> {
        val args = mutableListOf(
            "--parse-metadata", "%(artist,album_artist,creator,uploader)s:%(meta_artist)s",
            "--parse-metadata", "%(track,title)s:%(meta_title)s",
        )
        if (options.smartAudioTags) {
            // Si el patrón no encaja, yt-dlp deja los valores anteriores intactos.
            args += listOf("--parse-metadata", "title:(?P<meta_artist>[^-]+?) - (?P<meta_title>.+)")
        }
        // Fuera «(Official Video)», «[HD]» y compañía del título de la etiqueta.
        args += listOf(
            "--replace-in-metadata", "meta_title",
            """(?i)\s*[\(\[](?:official|video|audio|lyric|visualizer|hd|4k)[^)\]]*[\)\]]""", "",
        )
        return args
    }

    /** El filtro de `_convert_to_gif`: paleta propia, tramado bayer y sólo las zonas que cambian. */
    fun gifFilter(options: DownloadOptions): String {
        val fps = maxOf(1, options.gifFps)
        val width = maxOf(64, options.gifWidth)
        return "fps=$fps,scale=$width:-1:flags=lanczos,split[s0][s1];" +
            "[s0]palettegen=stats_mode=diff[p];" +
            "[s1][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle"
    }

    /** Los campos de recorte en el formato de `--download-sections`: `*inicio-fin`. */
    fun downloadSection(start: String, end: String): String? {
        val a = normalizeTimestamp(start)
        val b = normalizeTimestamp(end)
        return when {
            a == null && b == null -> null
            b == null -> "*$a-inf"
            a == null -> "*0-$b"
            else -> "*$a-$b"
        }
    }

    /** Acepta `mm:ss`, `hh:mm:ss` o segundos sueltos; cualquier otra cosa se descarta. */
    fun normalizeTimestamp(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !Regex("[0-9:.]+").matches(trimmed)) return null
        return trimmed
    }
}
