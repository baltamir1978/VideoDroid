package es.altamirano.linvideo.engine

import java.util.Locale

/** Cifras que ve el usuario (`formatters.py`). */
object Formatters {
    fun humanBytes(bytes: Double): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        if (unit == 0) return "${value.toLong()} B"
        // Coma decimal, como se escribe en español.
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]).replace('.', ',')
    }

    fun duration(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0) return ""
        val total = seconds.toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
