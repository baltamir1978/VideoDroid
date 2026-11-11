package es.altamirano.linvideo

import android.app.Application
import es.altamirano.linvideo.data.SettingsRepository
import es.altamirano.linvideo.engine.Engine
import es.altamirano.linvideo.queue.QueueHub

class LinVideoApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var hub: QueueHub
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        hub = QueueHub(this, settings)
        // Descomprimir Python y FFmpeg tarda unos segundos la primera vez: va en segundo plano.
        Engine.start(this)
        settings.settings.value.let { s ->
            Engine.autoUpdateIfDue(s.autoUpdate, s.lastUpdateCheck) { now ->
                settings.update { it.copy(lastUpdateCheck = now) }
            }
        }
    }

    companion object {
        lateinit var instance: LinVideoApp
            private set
    }
}
