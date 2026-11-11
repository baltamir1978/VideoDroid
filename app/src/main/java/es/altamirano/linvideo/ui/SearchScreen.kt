package es.altamirano.linvideo.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import es.altamirano.linvideo.R
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import es.altamirano.linvideo.model.MediaKind
import es.altamirano.linvideo.search.SearchResult
import es.altamirano.linvideo.search.SearchScope

private fun scopeLabel(scope: SearchScope) = if (scope.source == null) "— ${scope.title} —" else scope.title

@Composable
fun SearchScreen(model: SearchViewModel, onPreview: (SearchResult) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val (query, setQuery) = rememberFieldText(state.query, model::setQuery)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = query,
                    onValueChange = setQuery,
                    placeholder = { Text("Buscar vídeos o audio") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); model.run() }),
                    trailingIcon = {
                        Button(
                            onClick = { focus.clearFocus(); model.run() },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.padding(end = 6.dp),
                        ) { Text("Buscar") }
                    },
                    shape = CircleShape,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Dropdown(state.scope, SCOPES, ::scopeLabel, model::setScope, Modifier.fillMaxWidth(), title = "Dónde")
            }
        }
        val messages = buildList {
            if (state.loading && state.scope.isMulti && state.remaining > 0) add("Faltan ${state.remaining} webs por contestar…")
            if (state.failures.isNotEmpty()) add("No contestaron: " + state.failures.joinToString("; ") { "${it.first.title}: ${it.second}" })
        }
        when {
            state.searched.isEmpty() -> item { Hint(state.scope) }
            state.results.isEmpty() && state.loading -> item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ContainedLoadingIndicator(Modifier.size(64.dp))
                    Text("Buscando «${state.searched}» en ${state.scope.title}…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.results.isEmpty() -> item {
                Column(Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sin resultados", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(
                        if (state.failures.isNotEmpty()) messages.joinToString("\n")
                        else "Nada para «${state.searched}» en ${state.scope.title}. Prueba con otras palabras.",
                    )
                }
            }
        }
        if (messages.isNotEmpty() && state.results.isNotEmpty()) {
            item { Text(messages.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        items(state.results, key = { it.source.value + it.pageUrl }) { result ->
            ResultCard(
                result, showSource = state.scope.isMulti,
                onDownload = { kind ->
                    model.download(result, kind)
                    Toast.makeText(context, "Añadido a la cola: ${kind.title}", Toast.LENGTH_SHORT).show()
                },
                onPreview = { onPreview(result) },
                onBrowser = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.pageUrl))) },
                modifier = Modifier.animateItem(),
            )
        }
        if (state.results.isNotEmpty() && (state.canLoadMore || state.loading)) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.loading) LoadingIndicator(Modifier.size(48.dp))
                    else FilledTonalButton(onClick = model::loadMore, shapes = ButtonDefaults.shapes()) { Text("Más resultados") }
                }
            }
        }
    }
}

@Composable
private fun Hint(scope: SearchScope) {
    val (title, text) = if (scope.source != null) {
        "Buscar en ${scope.source.title}" to scope.source.hint
    } else {
        "Buscar en ${scope.title.lowercase()}" to
            "Se consulta a la vez: ${scope.sources.joinToString(", ") { it.title }}. " +
            "Los resultados se mezclan para que ninguna web acapare la lista."
    }
    Column(Modifier.padding(top = 24.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultCard(
    result: SearchResult,
    showSource: Boolean,
    onDownload: (MediaKind) -> Unit,
    onPreview: () -> Unit,
    onBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val preferred = result.source.preferredKind
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.width(144.dp).height(81.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClick = onPreview),
                ) {
                    if (result.thumbnailUrl != null) {
                        AsyncImage(
                            model = result.thumbnailUrl, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                    result.durationText?.let {
                        Text(
                            it, color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                .background(Color(0xB0000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(if (showSource) result.source.title else null, result.uploader, result.viewsText)
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(onClick = { onDownload(preferred) }) {
                                Icon(painterResource(R.drawable.ic_tab_download), contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(preferred.shortTitle)
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(checked = menu, onCheckedChange = { menu = it }) {
                                val turn by animateFloatAsState(if (menu) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "flecha")
                                Icon(
                                    Icons.Default.KeyboardArrowDown, contentDescription = "Otro formato",
                                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = turn },
                                )
                            }
                        },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MediaKind.entries.forEach { kind ->
                            DropdownMenuItem(text = { Text(kind.title) }, onClick = { menu = false; onDownload(kind) })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = onPreview, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Ver")
                }
                IconButton(onClick = onBrowser, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Abrir en la web")
                }
            }
        }
    }
}
