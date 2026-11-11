package es.altamirano.linvideo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Material 3 Expressive con los colores del fondo de pantalla (Material You). */
@Composable
fun LinVideoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}

/** Forma de los campos rellenos: esquinas redondeadas, como en Expressive. */
val FieldShape = RoundedCornerShape(16.dp)

/** Colores de campo relleno sin la línea inferior, para que encaje dentro de las tarjetas. */
@Composable
fun fieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

/** Desplegable: un campo de sólo lectura con su menú. */
@Composable
fun <T> Dropdown(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { if (enabled) open = it }, modifier = modifier) {
        TextField(
            value = label(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = title?.let { { Text(it, maxLines = 1) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            shape = FieldShape,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; onSelect(option) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Bloque con fondo tonal y esquinas grandes que agrupa controles relacionados. */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = color, shape = RoundedCornerShape(28.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Título de sección, en el color de acento. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * Texto de un campo cuyo valor vive en un StateFlow. El flujo llega un fotograma tarde y, si se
 * escribe deprisa, devolvería al campo un valor viejo que se come letras. Aquí el campo manda: lo
 * que vuelve del flujo y es eco de lo escrito se ignora; sólo se adopta un cambio de fuera (Pegar…).
 */
@Composable
fun rememberFieldText(external: String, onChange: (String) -> Unit): Pair<String, (String) -> Unit> {
    var local by remember { mutableStateOf(external) }
    val sent = remember { ArrayDeque<String>() }
    if (external != local) {
        if (external in sent) {
            // Eco de algo que escribimos: lo olvidamos y lo anterior también.
            while (sent.isNotEmpty() && sent.removeFirst() != external) Unit
        } else {
            sent.clear()
            local = external
        }
    } else {
        sent.clear()
    }
    return local to { text: String ->
        local = text
        sent.addLast(text)
        if (sent.size > 64) sent.removeFirst()
        onChange(text)
    }
}
