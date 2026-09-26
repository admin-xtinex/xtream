package app.xtream.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Entry(val url: String, val title: String)

object Library {
    private const val prefs = "xtream"
    private const val bookmarksKey = "bookmarks"
    private const val historyKey = "history"

    private const val defaultBookmarkUrl = "https://ogomovies2.com.pk/"
    private const val defaultBookmarkTitle = "OgoMovies"

    fun bookmarks(context: Context): List<Entry> {
        val sp = context.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        if (!sp.getBoolean("seeded_ogomovies_v1", false)) {
            val current = read(context, bookmarksKey).toMutableList()
            if (current.none { it.url == defaultBookmarkUrl || it.url == "https://ogomovies2.com.pk" }) {
                current.add(0, Entry(defaultBookmarkUrl, defaultBookmarkTitle))
                write(context, bookmarksKey, current)
            }
            sp.edit().putBoolean("seeded_ogomovies_v1", true).apply()
        }
        return read(context, bookmarksKey)
    }

    fun history(context: Context): List<Entry> = read(context, historyKey)

    fun isBookmarked(context: Context, url: String): Boolean =
        bookmarks(context).any { it.url == url }

    fun toggleBookmark(context: Context, url: String, title: String): Boolean {
        val next = bookmarks(context).filterNot { it.url == url }.toMutableList()
        val added = next.size == bookmarks(context).size
        if (added) next.add(0, Entry(url, title.ifBlank { url }))
        write(context, bookmarksKey, next.take(80))
        return added
    }

    fun removeBookmark(context: Context, url: String) {
        write(context, bookmarksKey, bookmarks(context).filterNot { it.url == url })
    }

    fun removeHistory(context: Context, url: String) {
        write(context, historyKey, history(context).filterNot { it.url == url })
    }

    fun clearBookmarks(context: Context) {
        write(context, bookmarksKey, emptyList())
    }

    fun clearHistory(context: Context) {
        write(context, historyKey, emptyList())
    }

    fun visit(context: Context, url: String, title: String) {
        val next = history(context).filterNot { it.url == url }.toMutableList()
        next.add(0, Entry(url, title.ifBlank { url }))
        write(context, historyKey, next.take(40))
    }

    private fun read(context: Context, key: String): List<Entry> {
        val raw = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (url.isBlank()) continue
                add(Entry(url, item.optString("title").ifBlank { url }))
            }
        }
    }

    private fun write(context: Context, key: String, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("url", entry.url).put("title", entry.title))
        }
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
    }
}
