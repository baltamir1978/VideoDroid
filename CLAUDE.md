# VideoDroid — contexto del repositorio

Versión para Android de **LinVideo** (`../LinVideo`), en **Kotlin + Jetpack Compose** sobre
**youtubedl-android** 0.18.1 (yt-dlp, Python, FFmpeg y QuickJS dentro del APK). El plan completo
está en `LinVideo-Android.md`. «VideoDroid» es la carpeta; **en el móvil se llama LinVideo**
(`es.altamirano.linvideo`, como los demás `*Droid`). Uso personal: APK instalado a mano.

La especificación es el código de LinVideo: mismo comportamiento y mismos textos, adaptados a
Android. Ante cualquier duda, leer el módulo de Python equivalente (tabla del plan, §3).

## Estado (11-nov-2026)

**0.1.0: las fases 0–8 del plan, escritas y compiladas. Nada probado en el móvil** (no había
ninguno conectado a `adb`). APK firmado en `~/Documentos/LinVideo-0.1.0.apk` (83 MB, sólo arm64).

Comprobado sin móvil:
- 129 pruebas JVM (`./gradlew testDebugUnitTest`): las de LinVideo portadas (argumentos, errores,
  nombres, cola, ajustes, formatos, vista previa, lectores de búsqueda con los mismos fragmentos
  HTML) y alguna más.
- `Tools/probar-ytdlp.sh`: los argumentos de verdad contra **el yt-dlp que trae la librería**
  (2025.11.12), con el Python y el ffmpeg del equipo, sobre un vídeo servido por HTTP. Pasan las
  cinco modalidades y el recorte.
- Búsquedas reales (`LINVIDEO_LIVE=1 ./gradlew testDebugUnitTest --tests '*LiveSearchTest*'`):
  16 de 17 webs devuelven resultados. **Reddit responde 403** a todo (también a LinVideo y a curl
  desde este equipo): lo ha cerrado Reddit, no es la traducción. YouTube y SoundCloud van por
  yt-dlp y sólo se pueden probar en el móvil.

Pendiente de probar en el móvil (criterios del plan, §6):
- Fase 0: que yt-dlp arranque, descargue de YouTube (con QuickJS para el reto de JS) y que
  «Actualizar ahora» funcione.
- Fase 2: 3 descargas con 2 simultáneas, cancelar, reintentar, matar la app y recuperar.
- Fase 3: «Compartir → LinVideo» desde YouTube; cambiar el tipo desde la notificación.
- Fase 4: GIF en el móvil (en el equipo, sí).
- Fase 6: vista previa de YouTube (HLS) y de alguna web con MP4.
- Que el `MediaScanner` haga aparecer los archivos en la galería.

## Diferencias con LinVideo (decididas aquí)

- **El GIF lo hace yt-dlp** al terminar (`--recode-video gif` con el filtro de paleta de
  `_convert_to_gif` en `--postprocessor-args VideoConvertor:…`), no un ffmpeg aparte: así no hay
  que lanzar a mano el binario de la librería. Comprobado en el equipo: sólo queda el `.gif`.
- **Cookies**: un `cookies.txt` importado (`--cookies`) en lugar de `--cookies-from-browser`. Los
  textos de error que mandaban a elegir navegador dicen ahora que se importe el archivo.
  Pendiente (plan §4): iniciar sesión en un WebView y volcar sus cookies.
- **Destino**: subcarpeta de `Download/` (por omisión `Download/LinVideo`), sin permisos.
- **Ajustes** con DataStore sobre `filesDir/ajustes.json`, mismos campos (`cookies_file` en vez de
  `cookie_source`, y `last_update_check` para la comprobación diaria de yt-dlp).
- **`pendientes.json`** en `filesDir`. Mientras no se decide qué recuperar, lo de la vez anterior
  sigue guardado aunque se añadan descargas nuevas.
- **TNAFlix**: el primer resultado lleva la miniatura en `src`, no en `data-src`; aquí se lee
  (LinVideo la deja en blanco).
- Sin R8: casi todo el peso son binarios nativos y la librería usa Jackson por reflexión.

## Cómo está hecho

- `engine/`: `Arguments` (la línea de yt-dlp, portada exacta), `Errors`, `Filenames`, `Formats`,
  `Preview`, `Output` (eventos y lector de la salida), `DownloadJob` (`YtdlpJob`), `Engine`
  (arranque y actualización), `Shlex` (comillas como `shlex.split`), `Links`.
- `queue/`: `DownloadQueue` (sin Android, probado en JVM) y `QueueHub` (la de la app).
- `service/`: `DownloadService` (`dataSync`, notificación del lote con «Cancelar todo», wake lock
  parcial) y la notificación de «Compartir» con botones para cambiar el tipo.
- `search/`: un fichero por familia de webs; misma mezcla por rondas que `search_many`.
- `ui/`: pestañas Descargar · Buscar · Ajustes, «Formatos…», vista previa (ExoPlayer),
  recuperar descargas, aviso legal al primer arranque, `ShareActivity` (`ACTION_SEND` y
  `linvideo:`).
- La librería añade sola `--ffmpeg-location` y `--js-runtimes quickjs:…`.

## Compilar

Entorno en `~/Android` (el de los demás `*Droid`). Después:
`./gradlew testDebugUnitTest assembleRelease` →
`app/build/outputs/apk/release/app-release.apk` y `adb install -r …`.

**Firma de release**: `~/.android/claves/linvideo-release.jks`, datos en
`~/.android/claves/linvideo-release.properties` (lo lee `app/build.gradle.kts`). Fuera del repo y
sin copia: **si se pierde, no se puede actualizar la app instalada**. Huella SHA-256:
`4F:B1:6E:E7:B7:67:70:D8:6F:11:0B:FB:5F:C4:4C:18:E1:BC:72:50:7F:97:EE:83:48:A7:71:88:7F:56:A5:36`.

**Al diagnosticar una web de búsqueda «rota»**: probar antes con una búsqueda corriente
(«amateur», no «jazz piano»), como dice el `CLAUDE.md` de LinVideo.
