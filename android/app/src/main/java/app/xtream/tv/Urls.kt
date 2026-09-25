package app.xtream.tv

import android.net.Uri
import java.net.URLEncoder

object Urls {
    fun resolve(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()
        if (
            lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("intent:") ||
            lower.startsWith("data:")
        ) {
            return null
        }
        val looksLikeAddress = !text.contains(" ") && (text.contains(".") || lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("localhost"))
        val candidate = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> text
            looksLikeAddress -> "https://$text"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(text, Charsets.UTF_8.name())
        }
        val uri = Uri.parse(candidate)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return uri.toString()
    }
}
