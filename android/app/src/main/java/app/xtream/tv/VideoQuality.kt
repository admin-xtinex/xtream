package app.xtream.tv

import android.content.Context

/** Saved video quality: "best" (default), a height such as "1080", or "auto" to leave it to the site. */
object VideoQuality {
    private const val PREF = "xtream"
    private const val KEY = "video_quality"

    val values = arrayOf("best", "1080", "720", "480", "360", "auto")
    val labels = arrayOf("Best (1080p minimum, up to 4K)", "1080p", "720p", "480p", "360p", "Auto (site decides)")

    fun label(context: Context): String = labels[values.indexOf(get(context)).coerceAtLeast(0)]

    fun get(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "best") ?: "best"

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }
}
