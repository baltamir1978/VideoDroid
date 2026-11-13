package es.altamirano.linvideo

import android.app.Application
import es.altamirano.linvideo.data.SettingsRepository
import es.altamirano.linvideo.engine.Engine
import es.altamirano.linvideo.queue.QueueHub
import java.io.File

class LinVideoApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var hub: QueueHub
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        keepCookiesOutOfBackups()
        hub = QueueHub(this, settings)
        // Descomprimir Python y FFmpeg tarda unos segundos la primera vez: va en segundo plano.
        Engine.start(this)
        settings.settings.value.let { s ->
            Engine.autoUpdateIfDue(s.autoUpdate, s.lastUpdateCheck) { now ->
                settings.update { it.copy(lastUpdateCheck = now) }
            }
        }
    }

    /**
     * El `cookies.txt` importado son sesiones iniciadas: va en `noBackupFilesDir`, fuera de las
     * copias de seguridad. Hasta la 0.2.0 se guardaba en `filesDir`: se mueve. Y si los ajustes
     * apuntan a un archivo que ya no está (ajustes restaurados de una copia), se quita, que
     * yt-dlp fallaría con un `--cookies` que no existe.
     */
    private fun keepCookiesOutOfBackups() {
        val current = settings.settings.value.options.cookiesFile
        if (current.isEmpty()) return
        val file = File(current)
        val target = cookiesFile(this)
        val moved = when {
            !file.exists() -> ""
            file.absolutePath == target.absolutePath -> return
            else -> runCatching { file.copyTo(target, overwrite = true); file.delete(); target.absolutePath }.getOrDefault(current)
        }
        if (moved != current) settings.update { it.copy(options = it.options.copy(cookiesFile = moved)) }
    }

    companion object {
        /** Dónde se guarda el `cookies.txt` importado. */
        fun cookiesFile(context: android.content.Context) = File(context.noBackupFilesDir, "cookies.txt")

        lateinit var instance: LinVideoApp
            private set
    }
}
