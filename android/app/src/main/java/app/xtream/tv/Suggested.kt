package app.xtream.tv

import android.content.Context

object Suggested {
    fun load(context: Context): List<Entry> {
        val text = try {
            context.assets.open("suggested.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return emptyList()
        }
        return text.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val parts = trimmed.split("|", limit = 2)
            val rawUrl = if (parts.size == 2) parts[1].trim() else parts[0]
            val url = Urls.resolve(rawUrl) ?: return@mapNotNull null
            val label = if (parts.size == 2) parts[0].trim().ifBlank { hostOf(url) } else hostOf(url)
            Entry(url, label)
        }.toList()
    }

    private fun hostOf(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore("/").ifBlank { url }
}
