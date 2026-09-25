package app.xtream.tv

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.util.Collections
import android.webkit.WebResourceResponse

object AdBlock {
    private const val prefs = "xtream"
    private const val key = "adblock"

    @Volatile
    private var hosts: Set<String> = emptySet()

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getBoolean(key, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
    }

    fun blocks(context: Context, url: Uri, mainFrame: Boolean): Boolean {
        if (mainFrame || !enabled(context)) return false
        return isAd(context, url)
    }

    fun isAd(context: Context, url: Uri): Boolean {
        if (!enabled(context)) return false
        val scheme = url.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = url.host?.lowercase()?.removePrefix("www.") ?: return false
        if (host.isBlank() || !host.contains('.')) return false
        if (needles.any { host.contains(it) }) return true
        val list = hosts(context)
        var name = host
        while (name.contains('.')) {
            if (list.contains(name)) return true
            name = name.substringAfter('.')
        }
        return false
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            200,
            "OK",
            Collections.emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )

    private fun hosts(context: Context): Set<String> {
        val ready = hosts
        if (ready.isNotEmpty()) return ready
        synchronized(this) {
            if (hosts.isNotEmpty()) return hosts
            val parsed = LinkedHashSet<String>()
            try {
                context.assets.open("adblock.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim().lowercase()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                        val host = trimmed.substringAfterLast(' ').removePrefix("www.").trim('.')
                        if (host.contains('.') && host.none { it.isWhitespace() }) parsed.add(host)
                    }
                }
            } catch (_: Exception) {
                return emptySet()
            }
            hosts = parsed
            return hosts
        }
    }

    private val needles = listOf(
        "doubleclick",
        "googlesyndication",
        "googleadservices",
        "googletagservices",
        "google-analytics",
        "adservice.google",
        "2mdn.net",
        "adnxs.",
        "adsrvr.",
        "adform.net",
        "adsafeprotected",
        "amazon-adsystem",
        "casalemedia",
        "criteo.",
        "criteo.net",
        "demdex.net",
        "doubleverify",
        "moatads",
        "outbrain",
        "pubmatic",
        "rubiconproject",
        "scorecardresearch",
        "taboola",
        "smartadserver",
        "openx.net",
        "popads",
        "popcash",
        "propellerads",
        "revcontent",
        "sharethrough",
        "spotxchange",
        "teads.tv",
        "exoclick",
        "exosrv",
        "trafficjunky",
        "juicyads",
        "adsterra",
        "hilltopads",
        "clickadu",
        "monetag",
        "adskeeper",
        "magsrv",
        "realsrv",
        "tsyndicate",
        "trafficstars",
        "onclickads",
        "popunder",
        "clickaine",
        "galaksion",
        "admaven",
        "highrevenue",
        "serving-sys",
        "imasdk.googleapis",
        "fundingchoices",
        "pagead",
        "securepubads",
        "bidswitch",
        "contextweb",
        "mgid.com",
        "admob.",
        "ads.yahoo",
        "ads.twitter",
        "popads.net",
        "adclick",
        "banner-ads",
        "adservice",
        "adserver",
    )
}
