package es.altamirano.linvideo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import es.altamirano.linvideo.LinVideoApp
import es.altamirano.linvideo.R
import es.altamirano.linvideo.engine.Formatters
import es.altamirano.linvideo.queue.BatchSummary
import es.altamirano.linvideo.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Mantiene vivas las descargas en segundo plano: notificación del lote (n de m y porcentaje) con
 * «Cancelar todo». Se para solo cuando no queda nada por descargar.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val hub get() = (application as LinVideoApp).hub

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        // Con la pantalla apagada el procesador se duerme aunque haya un servicio en primer plano.
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LinVideo:descargas")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
    }

    @OptIn(FlowPreview::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Cada startForegroundService exige su startForeground, aunque luego no haya nada.
        startForeground(PROGRESS_ID, progressNotification(hub.summary.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (intent?.action == ACTION_CANCEL_ALL) hub.cancelAll()
        if (watcher == null) {
            watcher = scope.launch {
                hub.summary.sample(1000).collect { summary ->
                    if (summary.active) {
                        notificationManager().notify(PROGRESS_ID, progressNotification(summary))
                    } else {
                        finish(summary)
                    }
                }
            }
        }
        // Si el sistema mata el proceso, la cola pendiente se ofrece al volver a abrir la app.
        return START_NOT_STICKY
    }

    /** Android 15 corta los servicios `dataSync` tras 6 h al día. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        hub.queue.stopForExit()
        stopSelf()
    }

    private fun finish(summary: BatchSummary) {
        if (summary.done + summary.failed > 0) {
            notificationManager().notify(DONE_ID, doneNotification(summary))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(s: BatchSummary): Notification {
        val cancelAll = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val percent = (s.fraction * 100).toInt()
        val title = if (s.total > 1) "Descargando ${minOf(s.done + 1, s.total)} de ${s.total}" else "Descargando"
        val text = buildList {
            add("$percent %")
            if (s.speedBytes > 0) add("${Formatters.humanBytes(s.speedBytes)}/s")
            if (s.waiting > 0) add("${s.waiting} en espera")
        }.joinToString(" · ")
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, s.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
            .addAction(0, "Cancelar todo", cancelAll)
            .build()
    }

    private fun doneNotification(s: BatchSummary): Notification {
        val title = when {
            s.failed == 0 -> if (s.done == 1) "Descarga terminada" else "${s.done} descargas terminadas"
            s.done == 0 -> if (s.failed == 1) "La descarga falló" else "${s.failed} descargas fallaron"
            else -> "${s.done} terminadas, ${s.failed} con fallos"
        }
        return NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
    }

    private fun notificationManager() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_CANCEL_ALL = "es.altamirano.linvideo.CANCEL_ALL"
        private const val CHANNEL_PROGRESS = "descargas"
        private const val CHANNEL_DONE = "terminadas"
        private const val PROGRESS_ID = 1
        private const val DONE_ID = 2

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
