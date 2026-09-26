package app.xtream.tv

import android.content.Context

/** Saved video quality: "best" (default), a height such as "1080", or "auto" to leave it to the site. */
object VideoQuality {
    private const val PREF = "xtream"
    private const val KEY = "video_quality"

    fun get(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "best") ?: "best"

    fun set(context: Context, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }
}
