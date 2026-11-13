package es.altamirano.linvideo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import es.altamirano.linvideo.LinVideoApp
import es.altamirano.linvideo.engine.Links
import es.altamirano.linvideo.service.SharedNotification

/**
 * «Compartir → LinVideo» y los enlaces `linvideo:`. Sin pantalla: mete los enlaces en la cola con
 * el tipo por defecto, avisa y se cierra. El tipo se cambia desde la notificación.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urls = when (intent?.action) {
            Intent.ACTION_SEND -> Links.extractUrls(
                listOfNotNull(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
                    .joinToString(" "),
            ).distinct()
            Intent.ACTION_VIEW -> listOfNotNull(intent.dataString?.let(Links::fromArgument))
            else -> emptyList()
        }
        if (urls.isEmpty()) {
            Toast.makeText(this, "Lo compartido no tiene ningún enlace.", Toast.LENGTH_SHORT).show()
            finish()
        } else if (intent?.action == Intent.ACTION_VIEW) {
            // Un `linvideo:` puede venir de cualquier página web, sin que nadie lo haya pedido:
            // antes de descargar nada, se pregunta. «Compartir» no, que lo elige quien comparte.
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            AlertDialog.Builder(this, if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("¿Descargar con LinVideo?")
                .setMessage(urls.first())
                .setPositiveButton("Descargar") { _, _ -> enqueue(urls) }
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener { finish() }
                .show()
        } else {
            enqueue(urls)
            finish()
        }
    }

    private fun enqueue(urls: List<String>) {
        run {
            val hub = (application as LinVideoApp).hub
            val entries = urls.map { hub.add(it) }
            val kind = entries.first().kind.title
            Toast.makeText(
                this,
                if (entries.size == 1) "Añadido a LinVideo: $kind" else "${entries.size} enlaces añadidos: $kind",
                Toast.LENGTH_SHORT,
            ).show()
            entries.forEach { SharedNotification.show(this, it) }
        }
    }
}
