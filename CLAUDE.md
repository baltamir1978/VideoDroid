# VideoDroid — contexto del repositorio

Versión para Android de **LinVideo** (`../LinVideo`), en **Kotlin + Jetpack Compose** sobre
**youtubedl-android** 0.18.1 (yt-dlp, Python, FFmpeg y QuickJS dentro del APK). El plan completo
está en `LinVideo-Android.md`. «VideoDroid» es la carpeta; **en el móvil se llama LinVideo**
(`es.altamirano.linvideo`, como los demás `*Droid`). Uso personal: APK instalado a mano.

La especificación es el código de LinVideo: mismo comportamiento y mismos textos, adaptados a
Android. Ante cualquier duda, leer el módulo de Python equivalente (tabla del plan, §3).

## Estado (13-nov-2026)

**0.2.2 (13-nov-2026): afinar la búsqueda.** Hecha en el servidor (`plex`), como la 0.2.1: **no
hay APK**; hay que compilarla y firmarla en el portátil (`git pull` antes). Sin probar en el
móvil ni en el emulador (el APK sólo trae librerías arm64).
- Las webs devuelven lo que quieren cuando hay varias palabras, y casi ninguna admite comillas ni
  exclusiones. Ahora se afina **en el móvil, sobre lo ya cargado** (`search/Refine.kt`, lógica
  pura con `RefineTest`), igual para las 19 webs:
  - `"frase exacta"` y `-palabra` en la consulta valen siempre.
  - «Todas las palabras»: exige las de la consulta en el título o el autor. Sin tildes pero con
    ñ, y casando por el principio de la palabra («gato» con «gatos», no con «alegato»).
  - Corto / Medio / Largo, con los cortes de YouTube (4 y 20 min). Con el filtro puesto, lo que
    no trae duración se esconde.
  - «Orden»: como llegan, más vistos, más largos, más cortos; lo que no trae el dato, al final.
- Como sólo se filtra lo cargado, la pantalla dice cuántos esconde el filtro («Se muestran 3 de
  24 cargados») para que «Más resultados» tenga sentido. Los filtros no se guardan entre arranques.
- **Qué se manda a cada web** (`Search.search`): YouTube, Archive y Reddit
  (`SearchSource.understandsOperators`) reciben la consulta tal cual, con comillas, `-palabra` y
  sus operadores (`intitle:`, `year:`…); las demás, sólo las palabras que se buscan
  (`ParsedQuery.plain`), porque los signos les estropeaban la búsqueda.
- Esto **no está en LinVideo** (ver «Diferencias»). 138 pruebas, 0 errores de lint.

**0.2.1 (13-nov-2026), revisada en el servidor (`plex`), sin móvil y sin la clave de firma: no hay
APK de esta versión.**
- **Un enlace `linvideo:` pregunta antes de descargar** (`ShareActivity`): el filtro es
  `BROWSABLE`, así que cualquier página web podía encolar una descarga sin que nadie la pidiera.
  «Compartir → LinVideo» sigue entrando directo. El diálogo es de Android, no de Compose: la
  actividad es translúcida y sin pantalla.
- **El `cookies.txt` importado va a `noBackupFilesDir`** (`LinVideoApp.cookiesFile`): son sesiones
  iniciadas y entraban en las copias de seguridad. Al arrancar se mueve el de la 0.2.0, y si los
  ajustes apuntan a un archivo que ya no está, se quita. Reglas de copia en
  `res/xml/data_extraction_rules.xml`.
- **`minSdk` 31** (era 30): `dynamicDarkColorScheme` no existe en Android 11 y la app se cerraba
  al abrir; era además el único error de lint.
- Dependencias a la par que los demás `*Droid`: Kotlin 2.4.21, Media3 1.11.1, OkHttp 5.5.1,
  lifecycle 2.11, coroutines 1.11, serialization 1.13, DataStore 1.3. material3 sigue en
  1.5.0-rc01 (no hay 1.5.0 estable) y youtubedl-android en 0.18.1 (la última).
- **CI** en GitHub Actions (`.github/workflows/android.yml`): pruebas, lint y APK de depuración
  en cada push. `./gradlew testDebugUnitTest lintDebug` pasa (129 pruebas, 0 errores de lint).
- Sin probar en el móvil: el diálogo del enlace, la mudanza de las cookies y la vista previa
  con el Media3 nuevo.

**0.2.0, probada por primera vez en el móvil** (Pixel 11 Pro, Android 17, por `adb`). APK firmado en
`~/Documentos/LinVideo-0.2.0.apk`.

Funciona en el móvil:
- yt-dlp arranca; descarga de YouTube completa y con el nombre limpio («Me at the zoo.mp4»).
- Búsqueda en YouTube (por yt-dlp) y en «Para adultos» (llegan resultados de 11 webs).

Arreglado al probar:
- **Android no admite `(?U)`** en las expresiones (ICU): la app se cerraba al renombrar al final de
  cada descarga y fallaban las búsquedas. Ahora `engine/URegex.kt` (`uRegex`) pone la bandera sólo
  en la JVM; en Android `\w`, `\s` y `\b` ya son Unicode. **No escribir `(?U)` a mano.**
- El campo Enlace tenía autocorrección: el teclado metía espacios tras los puntos.
- Los campos perdían letras al escribir deprisa (el valor vuelve del `StateFlow` un fotograma
  tarde). `rememberFieldText` (`ui/Common.kt`) mantiene el texto en el campo; usarlo en todo campo
  ligado a un ViewModel.

Interfaz: **Material 3 Expressive** (`MaterialExpressiveTheme`, `LargeFlexibleTopAppBar`,
`ShortNavigationBar` con insignia, `SplitButtonLayout`, `LoadingIndicator`, barras onduladas,
ajustes en grupos segmentados). Para eso VideoDroid **va por delante de los demás `*Droid`**:
AGP 9.5.1 (Kotlin integrado: sin plugin `kotlin-android` ni `kotlinOptions`), Gradle 9.9.1,
Kotlin 2.3.21, compileSdk 37, targetSdk 36, BOM 2026.09.00 + material3 1.5.0-rc01, iconos de
`material-icons-core`. Las APIs experimentales se aceptan para todo el módulo en `app/build.gradle.kts`.

Pendiente de probar en el móvil (criterios del plan, §6):
- «Actualizar ahora» de yt-dlp.
- Fase 2: 3 descargas con 2 simultáneas, cancelar, reintentar, matar la app y recuperar.
- Fase 3: «Compartir → LinVideo» desde YouTube; cambiar el tipo desde la notificación.
- Fase 4: GIF en el móvil (en el equipo, sí).
- Fase 6: vista previa de YouTube (HLS) y de alguna web con MP4.
- Que el `MediaScanner` haga aparecer los archivos en la galería.
- **Reddit responde 403** a todo (también a LinVideo y a curl): lo ha cerrado Reddit.

Comprobado sin móvil: 138 pruebas JVM (`./gradlew testDebugUnitTest`), `Tools/probar-ytdlp.sh`
(argumentos contra el yt-dlp de la librería) y búsquedas reales
(`LINVIDEO_LIVE=1 ./gradlew testDebugUnitTest --tests '*LiveSearchTest*'`).

**Probar con `adb`**: `adb shell svc power stayon usb` para que no se bloquee; el desbloqueo es con
huella (lo hace el usuario). `adb shell input text` escribe muy deprisa: sirve para detectar
letras perdidas. Cuidado con `KEYCODE_BACK` sin teclado abierto: saca de la app.

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
- **Afinar la búsqueda** (filtros, orden, comillas y exclusiones; 0.2.2): sólo existe aquí.
- Sin R8: casi todo el peso son binarios nativos y la librería usa Jackson por reflexión.

## Cómo está hecho

- `engine/`: `Arguments` (la línea de yt-dlp, portada exacta), `Errors`, `Filenames`, `Formats`,
  `Preview`, `Output` (eventos y lector de la salida), `DownloadJob` (`YtdlpJob`), `Engine`
  (arranque y actualización), `Shlex` (comillas como `shlex.split`), `Links`.
- `queue/`: `DownloadQueue` (sin Android, probado en JVM) y `QueueHub` (la de la app).
- `service/`: `DownloadService` (`dataSync`, notificación del lote con «Cancelar todo», wake lock
  parcial) y la notificación de «Compartir» con botones para cambiar el tipo.
- `search/`: un fichero por familia de webs; misma mezcla por rondas que `search_many`.
  `Refine` filtra y ordena lo cargado; `SearchState.visible` es lo que se enseña.
- `ui/`: pestañas Descargar · Buscar · Ajustes, «Formatos…», vista previa (ExoPlayer),
  recuperar descargas, aviso legal al primer arranque, `ShareActivity` (`ACTION_SEND` y
  `linvideo:`).
- La librería añade sola `--ffmpeg-location` y `--js-runtimes quickjs:…`.

## Compilar

Entorno en `~/Android` (el de los demás `*Droid`, más la plataforma 37 y build-tools 37). Después:
`./gradlew testDebugUnitTest assembleRelease` →
`app/build/outputs/apk/release/app-release.apk` y `adb install -r …`.

**Firma de release**: `~/.android/claves/linvideo-release.jks`, datos en
`~/.android/claves/linvideo-release.properties` (lo lee `app/build.gradle.kts`). Fuera del repo,
**sólo en el portátil** y sin copia (en `plex` no está: allí la release sale sin firmar): **si se pierde, no se puede actualizar la app instalada**. Huella SHA-256:
`4F:B1:6E:E7:B7:67:70:D8:6F:11:0B:FB:5F:C4:4C:18:E1:BC:72:50:7F:97:EE:83:48:A7:71:88:7F:56:A5:36`.

**Al diagnosticar una web de búsqueda «rota»**: probar antes con una búsqueda corriente
(«amateur», no «jazz piano»), como dice el `CLAUDE.md` de LinVideo.
