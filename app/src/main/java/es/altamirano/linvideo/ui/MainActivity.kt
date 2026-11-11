package es.altamirano.linvideo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.altamirano.linvideo.LinVideoApp
import es.altamirano.linvideo.R
import es.altamirano.linvideo.engine.Links.extractUrls

enum class Tab(val title: String, val icon: Int) {
    DOWNLOAD("Descargar", R.drawable.ic_tab_download),
    SEARCH("Buscar", R.drawable.ic_tab_search),
    SETTINGS("Ajustes", R.drawable.ic_tab_settings),
}

class MainActivity : ComponentActivity() {
    private val downloads: DownloadViewModel by viewModels()
    private val searches: SearchViewModel by viewModels()

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val context = LocalContext.current
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colors) { App(downloads, searches) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrBlank()) downloads.clipboardSeen(text)
    }
}

@Composable
private fun App(downloads: DownloadViewModel, searches: SearchViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.DOWNLOAD) }
    var formatsFor by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf<es.altamirano.linvideo.search.SearchResult?>(null) }
    val hub = LinVideoApp.instance.hub
    val recoverable by hub.recoverable.collectAsStateWithLifecycle()
    val items by hub.items.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = {
                            val active = items.count { !it.state.finished }
                            Text(if (t == Tab.DOWNLOAD && active > 0) "${t.title} ($active)" else t.title)
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.DOWNLOAD -> DownloadScreen(downloads, onFormats = { formatsFor = extractUrls(downloads.form.value.url).firstOrNull() })
                Tab.SEARCH -> SearchScreen(searches, onPreview = { previewing = it })
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }

    previewing?.let { result ->
        PreviewDialog(
            result.pageUrl, result.title,
            onDownload = { searches.download(result, result.source.preferredKind) },
            onDismiss = { previewing = null },
        )
    }

    formatsFor?.let { link ->
        FormatsDialog(
            link,
            onChoose = { selector, description -> downloads.setFormat(selector, description); formatsFor = null },
            onDismiss = { formatsFor = null },
        )
    }

    // Aviso legal la primera vez y, después, las descargas que quedaron a medias.
    val settings by LinVideoApp.instance.settings.settings.collectAsStateWithLifecycle()
    if (!settings.legalAccepted) {
        LegalDialog(firstTime = true) { LinVideoApp.instance.settings.update { it.copy(legalAccepted = true) } }
    } else if (recoverable.isNotEmpty()) {
        RecoverDialog(recoverable) { hub.recover(it) }
    }
}
