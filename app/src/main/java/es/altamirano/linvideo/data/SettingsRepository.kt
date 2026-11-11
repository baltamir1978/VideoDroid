package es.altamirano.linvideo.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.Serializer
import es.altamirano.linvideo.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Los ajustes en `filesDir/ajustes.json` con DataStore: mismo JSON que LinVideo, escrito de forma atómica. */
class SettingsRepository(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: DataStore<Settings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        // Un ajustes.json ilegible no debe impedir abrir la app: se empieza de cero.
        corruptionHandler = ReplaceFileCorruptionHandler { Settings() },
        scope = scope,
        produceFile = { File(context.filesDir, "ajustes.json") },
    )

    /** Se lee una vez al arrancar para que la cola tenga el límite de simultáneas desde el principio. */
    val settings: StateFlow<Settings> = store.data.stateIn(
        scope, SharingStarted.Eagerly, runBlocking { store.data.first() },
    )

    fun update(change: (Settings) -> Settings) {
        scope.launch { store.updateData(change) }
    }

    private object SettingsSerializer : Serializer<Settings> {
        override val defaultValue = Settings()

        override suspend fun readFrom(input: InputStream): Settings {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) return defaultValue
            val data = try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw CorruptionException("ajustes.json estropeado", e)
            }
            return (data as? JsonObject)?.let(Settings::fromJson) ?: defaultValue
        }

        override suspend fun writeTo(t: Settings, output: OutputStream) {
            output.write(PrettyJson.encodeToString(JsonElement.serializer(), t.toJson()).encodeToByteArray())
        }
    }
}
