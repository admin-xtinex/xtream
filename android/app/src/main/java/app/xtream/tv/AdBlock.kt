package app.xtream.tv

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

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

    fun blocked(
        context: Context,
        url: Uri,
        pageHost: String,
        mainFrame: Boolean,
        headers: Map<String, String> = emptyMap(),
    ): Boolean {
        if (isUnsafe(url) || isDownload(url)) return true
        if (!enabled(context)) return false
        if (isAd(context, url)) return true
        return false
    }

    /** JVM-safe check used by the pre-build test. Media is allowed. Downloads and ad hosts are not. */
    fun wouldBlock(enabled: Boolean, raw: String, mainFrame: Boolean, accept: String = ""): Boolean {
        val uri = try {
            java.net.URI(raw)
        } catch (_: Exception) {
            return true
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return true
        val path = uri.path?.lowercase().orEmpty()
        if (extension(path) in downloads) return true
        if (!enabled) return false
        if (mainFrame) return false
        val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
        if (needles.any { host.contains(it) }) return true
        return false
    }

    fun isUnsafe(url: Uri): Boolean {
        val scheme = url.scheme?.lowercase()
        return scheme != "http" && scheme != "https"
    }

    fun isDownload(url: Uri): Boolean {
        val path = url.path?.lowercase()?.substringBefore('?') ?: return false
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "")
        return ext in downloads
    }

    /** Direct video file only. Playlists, blobs, and every other format are refused. */
    fun videoName(raw: String, mime: String? = null): String? {
        val uri = try {
            java.net.URI(raw)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val type = mime?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        if (type.isNotEmpty() && type != "application/octet-stream" && type !in videoMimes) return null
        val ext = extension(uri.path.orEmpty())
        if (ext.isNotEmpty() && ext !in videoExt) return null
        val fileExt = when {
            ext in videoExt -> ext
            type == "video/webm" -> "webm"
            type == "video/x-matroska" || type == "video/matroska" -> "mkv"
            type == "video/quicktime" -> "mov"
            type == "video/3gpp" || type == "video/3gpp2" -> "3gp"
            type == "video/x-msvideo" || type == "video/avi" -> "avi"
            type == "video/mp4" -> "mp4"
            else -> return null
        }
        val base = uri.path?.substringAfterLast('/')?.substringBefore('?').orEmpty()
        val clean = base.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(80)
        return if (clean.substringAfterLast('.', "") in videoExt) clean else "video.$fileExt"
    }

    private fun extension(path: String): String {
        val name = path.substringBefore('?').substringAfterLast('/')
        return name.substringAfterLast('.', "")
    }

    private fun isMedia(url: Uri, headers: Map<String, String>): Boolean {
        val accept = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value?.lowercase().orEmpty()
        if (accept.contains("mpegurl") || accept.contains("audio/") || accept.contains("video/")) return true
        val path = (url.path ?: "").lowercase()
        if (path.contains(".m3u8") || path.contains(".mpd") || path.contains("/hls/") || path.contains("/dash/")) return true
        val ext = path.substringBefore('?').substringAfterLast('.', "")
        return ext in media
    }

    private fun sameSite(host: String, pageHost: String): Boolean {
        if (pageHost.isBlank()) return true
        return host == pageHost || host.endsWith(".$pageHost") || pageHost.endsWith(".$host")
    }

    private fun isAllowedCdn(host: String): Boolean = cdn.any { host == it || host.endsWith(".$it") || host.contains(it) }

    fun emptyResponse(url: Uri? = null): WebResourceResponse {
        val raw = url?.toString()?.lowercase().orEmpty()
        val vast = listOf("vast", "gampad", "imasdk", "adtag", "vast.xml").any { raw.contains(it) }
        val body = if (vast) "<VAST version=\"3.0\"></VAST>" else ""
        val type = if (vast) "text/xml" else "text/plain"
        val headers = HashMap<String, String>()
        headers["Content-Type"] = type
        headers["Cache-Control"] = "no-store"
        headers["Access-Control-Allow-Origin"] = "*"
        return WebResourceResponse(
            type,
            "utf-8",
            200,
            "OK",
            headers,
            ByteArrayInputStream(body.toByteArray()),
        )
    }

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
        "pubads",
        "gampad",
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

    private val media = setOf(
        "m3u8", "mpd", "ts", "m4s", "mp4", "webm", "mkv", "mov", "m4v",
        "mp3", "aac", "m4a", "ogg", "opus", "vtt", "srt",
    )

    private val downloads = setOf(
        "apk", "xapk", "aab", "zip", "rar", "7z", "exe", "msi", "dmg", "pkg",
        "iso", "torrent", "bin", "deb", "rpm", "gz", "tgz", "tar", "xz", "crx",
        "bat", "cmd", "sh", "jar", "cab",
    )

    private val videoExt = setOf("mp4", "webm", "mkv", "m4v", "mov", "3gp", "avi")

    private val videoMimes = setOf(
        "video/mp4",
        "video/webm",
        "video/x-matroska",
        "video/matroska",
        "video/quicktime",
        "video/3gpp",
        "video/3gpp2",
        "video/x-m4v",
        "video/x-msvideo",
        "video/avi",
    )

    private val cdn = listOf(
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com",
        "gvt1.com",
        "youtube.com",
        "vimeo.com",
        "vimeocdn.com",
        "dailymotion.com",
        "dmcdn.net",
        "jwpcdn.com",
        "jwplayer.com",
        "jwpltx.com",
        "bitmovin.com",
        "mux.com",
        "cloudfront.net",
        "akamaihd.net",
        "akamaized.net",
        "fastly.net",
        "gstatic.com",
        "googleapis.com",
    )
}
