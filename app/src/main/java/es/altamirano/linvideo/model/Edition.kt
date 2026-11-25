package es.altamirano.linvideo.model

import es.altamirano.linvideo.BuildConfig

/**
 * Qué app es: LinVideo (todas las webs) o la edición «sólo YouTube», hecha para regalar
 * (`./gradlew assembleRelease -Pedicion=youtube`). Es el mismo código; lo que cambia lo decide esto.
 */
object Edition {
    val ONLY_YOUTUBE: Boolean = BuildConfig.SOLO_YOUTUBE
    val NAME: String = if (ONLY_YOUTUBE) "LinVideo YT" else "LinVideo"
}
