package es.altamirano.linvideo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.altamirano.linvideo.model.PendingEntry

/** «Recuperar descargas»: lo que quedó a medias la última vez, con una casilla por cada una. */
@Composable
fun RecoverDialog(entries: List<PendingEntry>, onDone: (List<PendingEntry>) -> Unit) {
    val chosen = remember(entries) { mutableStateListOf(*entries.map { it.id }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Recuperar descargas") },
        text = {
            Column {
                Text(
                    if (entries.size == 1) "Una descarga quedó sin terminar la última vez."
                    else "${entries.size} descargas quedaron sin terminar la última vez.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    entries.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = e.id in chosen,
                                onCheckedChange = { if (it) chosen += e.id else chosen -= e.id },
                            )
                            Text(
                                "${e.title.ifEmpty { e.url }} · ${e.kind.shortTitle}",
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(entries.filter { it.id in chosen }) }) { Text("Recuperar") }
        },
        dismissButton = { TextButton(onClick = { onDone(emptyList()) }) { Text("Descartar") } },
    )
}
