package es.altamirano.linvideo.model

/** Textos legales (`legal.py`), adaptados a Android donde hablaban de Linux. */
object Legal {
    const val SUMMARY = "LinVideo es una interfaz para yt-dlp y FFmpeg pensada para uso personal. " +
        "Quien la usa es responsable de lo que descarga."

    val POINTS = listOf(
        "Derechos de autor" to
            "Descargar obras protegidas sin autorización puede infringir los derechos de sus " +
            "titulares. Que una herramienta permita hacerlo no significa que sea legal en tu caso: " +
            "depende del contenido, del uso que le des y de la legislación de tu país.",
        "Condiciones de uso de las webs" to
            "Varias plataformas —YouTube entre ellas— prohíben en sus condiciones descargar " +
            "contenido salvo con las funciones que ellas mismas ofrecen. Usar esta app puede " +
            "incumplir ese contrato con la plataforma, aunque no haya infracción de copyright.",
        "Contenido para adultos" to
            "Las webs para adultos incluidas sólo deben usarse por personas mayores de edad y donde " +
            "ese contenido sea legal. Queda fuera de toda justificación el material ilegal o " +
            "publicado sin el consentimiento de quien aparece.",
        "Sin afiliación" to
            "Esta app no está asociada ni respaldada por YouTube, Google, ni por ninguna de las webs " +
            "a las que se conecta. Los nombres pertenecen a sus titulares y se usan sólo para " +
            "identificar cada fuente.",
        "Sin garantía" to
            "El programa se entrega tal cual, sin garantía de ningún tipo, y su autor no responde de " +
            "los daños o consecuencias de su uso. Este texto es informativo y no constituye " +
            "asesoramiento legal.",
        "Privacidad" to
            "No hay telemetría ni historial de descargas. Las peticiones van sólo a las webs que " +
            "buscas o descargas. El cookies.txt, si importas uno, se queda dentro de la app y sólo " +
            "lo lee yt-dlp en el móvil.",
    )

    val DEPENDENCIES = listOf(
        Triple("yt-dlp", "Dominio público (The Unlicense)",
            "Hace todo el trabajo de extracción y descarga. Va dentro de la app y se actualiza desde Ajustes."),
        Triple("FFmpeg, Python y QuickJS", "LGPL / GPL, PSF y MIT",
            "Vienen dentro de la app con youtubedl-android (GPL-3.0)."),
    )
}
