package app.xtream.tv

import android.content.Context
import androidx.webkit.WebViewCompat

/** Pages run on the device's own web engine, so an engine that stopped updating is the biggest risk. */
object EngineAge {
    // Chrome 131 shipped on 12 Nov 2024 and a new major follows about every four weeks.
    private const val KNOWN_MAJOR = 131
    private const val KNOWN_DAY = 20_039L // 2024-11-12 as days since 1970-01-01
    private const val RELEASE_DAYS = 28L
    private const val ALLOWED_BEHIND = 12 // roughly a year

    private const val PREF = "xtream"
    private const val KEY = "engine_warning"

    fun warningOn(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setWarning(context: Context, on: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    fun outdatedMajor(context: Context, today: Long = System.currentTimeMillis() / 86_400_000L): Int? {
        val version = try {
            WebViewCompat.getCurrentWebViewPackage(context)?.versionName
        } catch (_: Exception) {
            null
        } ?: return null
        val major = version.substringBefore('.').toIntOrNull() ?: return null
        return if (isOutdated(major, today)) major else null
    }

    fun isOutdated(major: Int, today: Long): Boolean {
        val expected = KNOWN_MAJOR + ((today - KNOWN_DAY) / RELEASE_DAYS).toInt()
        return major < expected - ALLOWED_BEHIND
    }
}
