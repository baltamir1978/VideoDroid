package es.altamirano.linvideo.engine

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** yt-dlp + FFmpeg dentro del APK (youtubedl-android). Arranque y actualización. */
object Engine {
    private const val TAG = "LinVideo"

    sealed interface Status {
        data object Starting : Status
        data class Ready(val version: String?) : Status
        data class Failed(val message: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<Status>(Status.Starting)
    val status: StateFlow<Status> = _status.asStateFlow()
    private lateinit var app: Context

    fun start(context: Context) {
        app = context.applicationContext
        scope.launch {
            _status.value = try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                Status.Ready(YoutubeDL.getInstance().version(app))
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar yt-dlp", e)
                Status.Failed(e.message ?: e.toString())
            }
        }
    }

    /**
     * Si está activado, comprueba si hay un yt-dlp nuevo como mucho una vez al día, al abrir.
     * [lastCheck] y [markChecked] guardan cuándo fue la última vez.
     */
    fun autoUpdateIfDue(enabled: Boolean, lastCheck: Long, markChecked: (Long) -> Unit) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastCheck < 24 * 60 * 60 * 1000L) return
        scope.launch {
            status.first { it !is Status.Starting }
            if (status.value !is Status.Ready) return@launch
            val result = update()
            Log.i(TAG, "Comprobación diaria: $result")
            if (!result.startsWith("No se pudo")) markChecked(now)
        }
    }

    /** Actualiza yt-dlp al último estable. Devuelve un texto para mostrar. */
    suspend fun update(): String = withContext(Dispatchers.IO) {
        try {
            val result = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE)
            val version = YoutubeDL.getInstance().version(app)
            _status.value = Status.Ready(version)
            when (result) {
                YoutubeDL.UpdateStatus.DONE -> "yt-dlp actualizado a $version"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp ya estaba al día ($version)"
                else -> "yt-dlp $version"
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo actualizar yt-dlp", e)
            "No se pudo actualizar yt-dlp: ${e.message}"
        }
    }

}
