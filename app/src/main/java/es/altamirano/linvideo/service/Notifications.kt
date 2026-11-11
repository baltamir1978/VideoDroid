package es.altamirano.linvideo.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import es.altamirano.linvideo.LinVideoApp
import es.altamirano.linvideo.R
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.model.PendingEntry
import es.altamirano.linvideo.ui.MainActivity

/** Aviso de «Compartir → LinVideo»: entra con el tipo por defecto y desde aquí se puede cambiar. */
object SharedNotification {
    private const val CHANNEL = "compartidos"
    private const val EXTRA_ID = "id"
    private const val EXTRA_KIND = "kind"
    const val ACTION_CHANGE_KIND = "es.altamirano.linvideo.CHANGE_KIND"

    /** Las otras modalidades que se ofrecen, en este orden (caben tres botones). */
    private val ALTERNATIVES = listOf(MediaKind.VIDEO_MAX, MediaKind.AUDIO_MP3, MediaKind.GIF, MediaKind.VIDEO_COMPAT, MediaKind.AUDIO_ORIGINAL)

    fun show(context: Context, entry: PendingEntry, tag: String = entry.id) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            androidx.core.app.NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Enlaces compartidos").build(),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Añadido como ${entry.kind.title}")
            .setContentText(entry.title.ifEmpty { entry.url })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60 * 1000L)
        ALTERNATIVES.filter { it != entry.kind }.take(3).forEach { kind ->
            val intent = Intent(context, ChangeKindReceiver::class.java)
                .setAction(ACTION_CHANGE_KIND)
                .putExtra(EXTRA_ID, entry.id)
                .putExtra(EXTRA_KIND, kind.value)
                .putExtra("tag", tag)
            val pending = PendingIntent.getBroadcast(
                context, (entry.id + kind.value).hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, kind.shortTitle, pending)
        }
        manager.notify(tag, ID, builder.build())
    }

    private const val ID = 3

    class ChangeKindReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_ID) ?: return
            val kind = intent.getStringExtra(EXTRA_KIND)?.let(MediaKind::fromValue) ?: return
            val tag = intent.getStringExtra("tag") ?: id
            val entry = (context.applicationContext as LinVideoApp).hub.changeKind(id, kind)
            if (entry != null) {
                show(context, entry, tag)
            } else {
                NotificationManagerCompat.from(context).cancel(tag, ID)
            }
        }
    }
}
