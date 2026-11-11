# LinVideo para Android: plan de trabajo

App nativa en **Kotlin**, sobre **youtubedl-android** (yt-dlp + Python + FFmpeg dentro del APK).
Uso personal: se instala a mano, sin Play Store.

**Especificación:** el repo de LinVideo (`github.com/baltamir1978/LinVideo`), igual que LinVideo
se escribió con AppVideo como especificación. No es un port línea a línea: hay que reproducir el
comportamiento y los textos, adaptados a Android.

> Para Claude Code en tu equipo: clona LinVideo junto a este proyecto y empieza con
> «Lee `LinVideo-Android.md` y el código de `../LinVideo`. Haz la fase 0».

---

## 1. Preparar el equipo

Funciona igual en Linux, Mac o Windows.

1. **Android Studio** (trae su propio JDK 17 y el SDK). En Linux sin interfaz basta con
   `cmdline-tools` + `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`.
2. Comprueba que `adb` está en el PATH: `adb version`.
3. **En el móvil:** Ajustes → Información → pulsar 7 veces «Número de compilación». Después,
   en Opciones de desarrollador, activar **Depuración USB** (o **Depuración inalámbrica**, con
   `adb pair IP:PUERTO`).
4. `adb devices` debe listar el móvil.

## 2. Decisiones de base

| Tema | Decisión |
|---|---|
| Nombre / paquete | `LinVideo` · `io.github.baltamir1978.linvideo` (cámbialo si quieres otro) |
| minSdk / targetSdk | **30** (Android 11+, así se evita el almacenamiento «legacy») / 35 |
| ABI | Solo `arm64-v8a`: el APK pesa mucho menos |
| UI | Jetpack Compose + Material 3, una sola Activity y navegación Compose |
| Estado | ViewModel + `StateFlow`, corrutinas |
| Motor | `io.github.junkfood02.youtubedl-android:library` y `:ffmpeg` (0.18.1 o la última de Maven Central). `:aria2c` es opcional |
| Ajustes | DataStore (JSON con kotlinx.serialization, mismos campos que `settings.py`) |
| Cola pendiente | Fichero JSON en `filesDir/pendientes.json`, igual que en Linux |
| Red para buscar | OkHttp, con el mismo User-Agent de Safari en Mac que `search.py` |
| HTML | Portar las expresiones regulares de `search.py` tal cual (no usar Jsoup: los lectores ya están probados así) |
| Vista previa | Media3 ExoPlayer (con HLS) |
| Miniaturas | Coil |
| Descargas en segundo plano | Foreground Service (`dataSync`) con notificación de progreso |

### Gradle (lo imprescindible)

```kotlin
android {
    defaultConfig {
        minSdk = 30
        targetSdk = 35
        ndk { abiFilters += "arm64-v8a" }
    }
    packaging { jniLibs.useLegacyPackaging = true }   // equivale a extractNativeLibs=true
}
dependencies {
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")
}
```

En el `AndroidManifest.xml`: `android:extractNativeLibs="true"` y los permisos
`INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_DATA_SYNC`.

Inicialización en `Application.onCreate()`:

```kotlin
YoutubeDL.getInstance().init(this)
FFmpeg.getInstance().init(this)
```

## 3. De LinVideo a Android, módulo a módulo

| LinVideo (Python) | Android (Kotlin) | Notas |
|---|---|---|
| `models.py` | `model/` | Mismos enums y valores JSON (`videoMax`, `audioMP3`…) y los mismos textos de ayuda |
| `download.py` → `ytdlp_arguments` | `engine/Arguments.kt` | **Portar exacto.** Es el corazón: selectores de formato, recorte, MP3, etiquetas, subtítulos, miniatura |
| `download.py` → `DownloadJob` | `engine/DownloadJob.kt` | `YoutubeDLRequest` + `execute(request, processId) { progreso, eta, línea -> }`. Cada línea pasa por el mismo analizador que `_handle` (título, fase, fichero producido) |
| cancelar (`Popen.terminate`) | `destroyProcessById(id)` | Lo descargado se conserva y se reanuda, igual que ahora |
| `_convert_to_gif` | `engine/Gif.kt` | Ver §4, GIF |
| `errors.py` | `engine/Errors.kt` | Mismas reglas y en el mismo orden (ffmpeg antes que el 404) |
| `filenames.py` | `engine/Filenames.kt` | Limpieza básica/estricta, emojis, etiquetas de relleno, mayúsculas |
| `formats.py` | `engine/Formats.kt` | `-J` con `YoutubeDL.getInfo()` o `execute` + JSON |
| `preview.py` | `engine/Preview.kt` + pantalla ExoPlayer | Misma elección: el más cercano a 720p, sin DASH sueltos ni `mhtml` |
| `queue.py` | `queue/DownloadQueue.kt` | Simultáneas según ajustes, reintentar, quitar, limpiar terminadas, resumen del lote |
| `search.py` | `search/` (un fichero por familia de webs) | Las 19 fuentes, categorías, «todas», `says_no_results` y el intercalado de resultados |
| `settings.py` | `data/SettingsRepository.kt` | Mismos campos y valores por defecto |
| `updater.py` | `YoutubeDL.updateYoutubeDL(ctx, UpdateChannel.STABLE)` | Una vez al día si `auto_update` está activo |
| `legal.py` | Diálogo al primer arranque | Mismo texto |
| `ui/*` (GTK) | `ui/*` (Compose) | Pestañas Descargar · Buscar · Ajustes |
| `cli.py`, `.desktop`, `.deb` | — | No hacen falta en Android |

## 4. Lo que cambia en Android

- **Dónde se guarda.** En `Download/LinVideo/`, que con almacenamiento acotado se puede
  escribir sin permisos. Al terminar cada fichero, `MediaScannerConnection.scanFile` para que
  aparezca en la galería y en la app de música. En Ajustes se puede elegir otra subcarpeta de
  `Download/`.
- **Cookies del navegador.** No se pueden leer en Android. Hay dos sustitutos, por orden de
  esfuerzo: importar un `cookies.txt` (`--cookies`), o iniciar sesión en un WebView y volcar sus
  cookies a un `cookies.txt` propio. Empieza por el primero.
- **Arrastrar enlaces y `linvideo:`** pasan a ser un intent `ACTION_SEND` con `text/plain`
  («Compartir → LinVideo») y un `ACTION_VIEW` con el esquema `linvideo`. El enlace compartido
  entra directamente en la cola con el tipo por defecto, y en la notificación se puede cambiar.
- **Portapapeles.** Android 10+ solo deja leerlo con la app en primer plano, que es justo
  «al volver a la ventana». Se mantiene con `watch_clipboard`.
- **GIF.** Primero comprueba si basta con yt-dlp (`--recode-video gif` más
  `--postprocessor-args` con el filtro de paleta de `download.py`). Si no, ejecuta el binario
  `ffmpeg` que trae la librería desde `applicationInfo.nativeLibraryDir`, con
  `LD_LIBRARY_PATH` apuntando ahí, y los mismos argumentos de paleta. **Verificarlo en la
  fase 4.**
- **Segundo plano.** Las descargas van en un Foreground Service con una notificación de lote
  (n de m y porcentaje) y un botón «Cancelar todo». Con targetSdk 35, `dataSync` tiene un límite
  de 6 h al día: de sobra para uso personal.
- **Python de la librería.** Comprueba en la fase 0 que el yt-dlp que trae, actualizado con
  `updateYoutubeDL`, descarga de YouTube. Si fallara por la versión de Python, sube la versión
  de la librería.

## 5. Pantallas

1. **Descargar.** Campo de enlace (con pegar), selector de tipo (los cinco de `MediaKind`),
   resolución máxima, recorte desde/hasta, «Formatos…», botón Descargar. Debajo, la cola: una
   tarjeta por descarga con título, fase, barra, velocidad/ETA, y Reintentar · Abrir · Compartir
   · Ver registro.
2. **Buscar.** Chips de ámbito (fuente, categoría o todas), campo, resultados con miniatura,
   duración, visitas y fuente. Al tocar: vista previa. Además, botón «Descargar» con el tipo
   preferido de la fuente y «Cargar más».
3. **Ajustes.** Los mismos grupos que `ui/preferences.py`: tipo y carpeta por defecto,
   simultáneas, limpieza de nombres, metadatos, subtítulos, miniatura, fragmentos, GIF,
   límite de velocidad, argumentos extra (con `shlex` hecho a mano: respetar comillas),
   cookies, actualización de yt-dlp (versión y «Actualizar ahora»), aviso legal.
4. **Primer arranque:** aviso legal. **Al abrir con pendientes:** «Recuperar descargas» con una
   casilla por cada una.

## 6. Fases

Cada fase termina compilando, con sus pruebas en verde y probada en el móvil.

| # | Fase | Hecho cuando… |
|---|---|---|
| 0 | Esqueleto, librería iniciada, descarga de una URL fija a `Download/LinVideo` | Hay un MP4 en el móvil y `updateYoutubeDL` funciona |
| 1 | Modelos, `Arguments`, `Errors`, `Filenames` | Pasan en JVM los casos de `test_download_arguments.py`, `test_errors.py` y `test_filenames.py` portados |
| 2 | Cola + Foreground Service + notificaciones + `pendientes.json` | 3 descargas, 2 simultáneas; cancelar y reintentar; matar la app y recuperar |
| 3 | Pantalla Descargar + Compartir + portapapeles | Compartir desde YouTube añade a la cola |
| 4 | Formatos…, recorte, GIF, MP3 con etiquetas | Las cinco modalidades dan el fichero esperado |
| 5 | Buscar (las 19 fuentes) | Pasan `test_search_parsing.py` portado (con los mismos fixtures HTML) y hay resultados reales en todas |
| 6 | Vista previa con ExoPlayer | Reproduce YouTube y una web con HLS |
| 7 | Ajustes, actualización automática, aviso legal, cookies.txt | Todos los ajustes persisten y se aplican |
| 8 | APK de release firmado | Instalado con `adb install -r` |

**Pruebas:** porta los ~100 tests de `tests/` a JUnit (sin móvil, `./gradlew test`). Son la
mejor garantía de que se comporta como LinVideo.

**Al diagnosticar una web de búsqueda «rota»:** probar antes con una búsqueda corriente
(«amateur», no «jazz piano»). Lo explica el `CLAUDE.md` de LinVideo.

## 7. Compilar, firmar e instalar

```sh
# Una sola vez: clave propia (guárdala, sin ella no podrás actualizar encima)
keytool -genkeypair -v -keystore ~/.android/linvideo.jks -alias linvideo \
        -keyalg RSA -keysize 4096 -validity 36500

# Depurar en el móvil conectado
./gradlew installDebug

# Release firmado (con signingConfig apuntando al .jks en app/build.gradle.kts,
# contraseñas en ~/.gradle/gradle.properties, nunca en el repo)
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Sin cable: copia el APK al móvil (Drive, Syncthing, tu servidor…), ábrelo y permite
«Instalar apps desconocidas» para esa app. Instalar encima con la misma firma conserva los
ajustes.

## 8. Referencias

- youtubedl-android: `github.com/yausername/youtubedl-android` (README y app de ejemplo)
- Seal: `github.com/JunkFood02/Seal`, que usa la misma librería con Compose. Mírala para resolver
  dudas de uso de la librería (progreso, cancelar, ffmpeg), sin copiar su diseño.
