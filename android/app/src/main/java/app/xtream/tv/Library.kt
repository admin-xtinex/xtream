package app.xtream.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Entry(val url: String, val title: String)

object Library {
    private const val prefs = "xtream"
    private const val bookmarksKey = "bookmarks"
    private const val historyKey = "history"

    fun bookmarks(context: Context): List<Entry> = read(context, bookmarksKey)

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

    fun visit(context: Context, url: String, title: String) {
        val next = history(context).filterNot { it.url == url }.toMutableList()
        next.add(0, Entry(url, title.ifBlank { url }))
        write(context, historyKey, next.take(20))
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
