# VideoDroid

En el móvil se llama **LinVideo**. Descarga vídeo y audio de YouTube y de cientos de webs más, con
yt-dlp y FFmpeg dentro de la app, y busca en 19 webs a la vez.

Versión para Android de [LinVideo](https://github.com/baltamir1978/LinVideo), la app de Linux. No
es un port línea a línea: se escribe en Kotlin con LinVideo como especificación, mismos
comportamientos y mismos textos. El plan está en `LinVideo-Android.md`.

**Estado: 0.2.1** (13-nov-2026; la 0.2.0, probada en un Pixel con Android 17; la 0.2.1, todavía no). Descargas de YouTube y
búsquedas (también la de YouTube y las de adultos) funcionando. Interfaz en **Material 3
Expressive**, con los colores del fondo de pantalla. Lo que queda por probar está en `CLAUDE.md`.

## Qué hace

- **Cinco modalidades**: vídeo a la máxima calidad, vídeo compatible (H.264 + AAC), MP3 con
  etiquetas y portada, audio original y **GIF** con paleta propia.
- Techo de resolución, **recorte** por tiempo y **«Formatos…»** para elegir a mano lo que ofrece
  la web.
- **Cola** con descargas simultáneas, en segundo plano con una notificación del lote y «Cancelar
  todo». Reintentar, abrir, compartir y ver el registro de cada una.
- Si la app se cierra a medias, al volver ofrece **recuperar** lo pendiente, con una casilla por
  descarga. Lo que se cancela se reanuda donde se quedó.
- **Compartir → LinVideo** desde cualquier app, y enlaces `linvideo:https://…`. Entra en la cola
  con el tipo por defecto, y desde la notificación se puede cambiar. Un enlace `linvideo:` (que
  puede venir de cualquier página web) pregunta antes de descargar.
- Aviso al volver a la app con un **enlace copiado**.
- **Buscador**: YouTube, Dailymotion, PeerTube, Archive.org, Reddit, SoundCloud, Mixcloud y doce
  webs para adultos. Una web, una categoría o todas, con los resultados mezclados por rondas.
  Miniaturas, duración, visitas y **vista previa** sin descargar.
- Nombres de archivo limpios (sin identificador, emojis, «Official Video»…), metadatos,
  subtítulos, límite de velocidad, `cookies.txt` y opciones extra de yt-dlp.
- yt-dlp se **actualiza solo** una vez al día, o a mano desde Ajustes.
- Guarda en `Download/LinVideo` (o la subcarpeta que se elija), sin pedir permisos, y los archivos
  aparecen en la galería y en la app de música.

## Compilar

Android 12 o posterior, arm64. Con el JDK 17 y el SDK 37 (AGP 9.5, Gradle 9.9, Kotlin 2.4; la
interfaz usa material3 1.5, que pide compileSdk 37):

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

La release se firma con una clave que vive fuera del repositorio (ver `CLAUDE.md`); sin ella sale
sin firmar. En cada push, GitHub Actions pasa las pruebas y el lint
(`.github/workflows/android.yml`). `Tools/probar-ytdlp.sh` prueba los argumentos de yt-dlp en el ordenador, sin móvil.

## Aviso

Pensada para uso personal. Quien la usa es responsable de lo que descarga: los derechos de autor y
las condiciones de cada web siguen valiendo. Las webs para adultos, sólo para mayores de edad. Sin
afiliación con ninguna de las webs a las que se conecta. El texto completo sale al abrir la app
por primera vez.

## Licencia

Usa [youtubedl-android](https://github.com/yausername/youtubedl-android) (GPL-3.0), que trae yt-dlp,
Python, FFmpeg y QuickJS.
