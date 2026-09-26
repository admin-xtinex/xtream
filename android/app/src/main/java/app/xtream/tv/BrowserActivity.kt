package app.xtream.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var chrome: View
    private lateinit var gate: View
    private lateinit var loadRing: View
    private lateinit var titleView: TextView
    private lateinit var save: Button
    private lateinit var progress: ProgressBar
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var homeHost: String = ""
    @Volatile
    private var pageHost: String = ""
    private var isVideoFullscreen: Boolean = false
    private lateinit var pointer: ScreenPointer
    private var webGone = false
    private var feedback: android.widget.Toast? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (isVideoFullscreen && (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                setImmersiveFullscreen(true)
            }
        }
        setContentView(R.layout.activity_browser)
        web = findViewById(R.id.web)
        chrome = findViewById(R.id.chrome)
        gate = findViewById(R.id.gate)
        loadRing = findViewById(R.id.load_ring)
        titleView = findViewById(R.id.page_title)
        save = findViewById(R.id.save)
        progress = findViewById(R.id.progress)
        val block = findViewById<Button>(R.id.block)
        val rotate = findViewById<Button>(R.id.rotate)
        val home = findViewById<Button>(R.id.home)
        val root = findViewById<FrameLayout>(R.id.root)
        val isTv = resources.getBoolean(R.bool.is_television)

        // Remote inspection exposes cookies and signed-in sessions, so only debug builds allow it.
        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        // A phone identity gets phone players that start at 360p, so the TV presents as desktop Chrome.
        web.settings.userAgentString = if (isTv) desktopAgent() else CHROME_AGENT
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.setSupportZoom(true)
        web.settings.builtInZoomControls = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        @Suppress("DEPRECATION")
        web.settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        web.settings.allowUniversalAccessFromFileURLs = false
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            web.settings.safeBrowsingEnabled = true
        }
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setGeolocationEnabled(false)
        web.settings.setSupportMultipleWindows(true)
        web.setDownloadListener { url, _, _, _, _ ->
            Log.d("Xtream", "Download ignored (downloads not supported): $url")
        }
        web.isLongClickable = false
        web.setOnLongClickListener { true }
        val cookies = android.webkit.CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)
        web.setBackgroundColor(0xFF02030A.toInt())
        web.addJavascriptInterface(VideoBridge(), "Xtream")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, PAGE_HOOK, setOf("*"))
        }

        val chromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                chrome.visibility = View.GONE
                setImmersiveFullscreen(true)
                if (!isTv) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                if (view != null) {
                    root.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                pointer.setSuppressed(true)
            }

            override fun onHideCustomView() {
                val view = customView ?: return
                (view.parent as? ViewGroup)?.removeView(view)
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                chrome.visibility = View.VISIBLE
                setImmersiveFullscreen(false)
                pointer.setSuppressed(false)
                if (!isTv) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                titleView.text = title?.ifBlank { web.url } ?: web.url
                refreshSave()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?,
            ): Boolean {
                Log.d("XtreamAdBlock", "POPUP WINDOW BLOCKED: ${view?.url}")
                return false
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progress.visibility = View.VISIBLE
                    progress.progress = newProgress
                } else {
                    progress.visibility = View.GONE
                }
            }
        }
        web.webChromeClient = chromeClient
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                val blocked = AdBlock.blocked(this@BrowserActivity, url, pageHost, request.isForMainFrame, request.requestHeaders)
                if (blocked) {
                    Log.d("XtreamAdBlock", "BLOCKED: $url")
                    return AdBlock.emptyResponse(url)
                }

                val host = url.host?.lowercase().orEmpty()
                val path = url.path?.lowercase().orEmpty()

                val streamProxy = host.contains("playit")
                val playlistProxy = path.contains("master.txt") || path.contains("master.m3u8") || path.contains("/m3/") || path.endsWith(".m3u8")
                // The app refetches these itself, so a page must never point it at the TV's own network.
                if ((streamProxy || playlistProxy) && !LocalNetwork.isPublic(url.toString())) {
                    Log.d("XtreamAdBlock", "LOCAL ADDRESS NOT PROXIED: $url")
                    return null
                }

                if (streamProxy) {
                    val streamResp = handlePlayitStream(url, request.requestHeaders)
                    if (streamResp != null) return streamResp
                }

                if (playlistProxy) {
                    val healed = handleHlsPlaylist(url, request.requestHeaders)
                    if (healed != null) return healed
                }

                Log.v("XtreamAdBlock", "ALLOWED: $url")
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (AdBlock.isUnsafe(url) || AdBlock.isDownload(url)) {
                    Log.d("XtreamAdBlock", "OVERRIDE BLOCKED (unsafe/download): $url")
                    return true
                }
                if (AdBlock.isAd(this@BrowserActivity, url)) {
                    Log.d("XtreamAdBlock", "OVERRIDE BLOCKED (ad url): $url")
                    return true
                }
                val targetHost = url.host?.lowercase()?.removePrefix("www.").orEmpty()
                if (request.isForMainFrame && !sameSite(targetHost)) {
                    if (!request.hasGesture()) {
                        Log.d("XtreamAdBlock", "OVERRIDE BLOCKED (unsolicited redirect without gesture): $url")
                        return true
                    }
                    if (AdBlock.isAd(this@BrowserActivity, url)) {
                        Log.d("XtreamAdBlock", "OVERRIDE BLOCKED (cross-site ad navigation): $url")
                        return true
                    }
                }
                return false
            }

            @android.annotation.TargetApi(26)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // A crashed or killed page engine must not take the whole app down with it.
                Log.w("Xtream", "Page renderer gone (crash=${detail.didCrash()})")
                (view.parent as? ViewGroup)?.removeView(view)
                webGone = true
                view.destroy()
                recreate()
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                val uri = Uri.parse(url)
                val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
                if (host.isNotBlank() && !AdBlock.isAd(this@BrowserActivity, uri)) pageHost = host
                if (!sameSite(uri.host) && AdBlock.isAd(this@BrowserActivity, uri)) {
                    Log.d("XtreamAdBlock", "PAGE STARTED BLOCKED (ad host): $uri")
                    view.stopLoading()
                    if (view.canGoBack()) view.goBack()
                    return
                }
                view.evaluateJavascript(PAGE_HOOK, null)
                showLoad()
            }

            override fun onPageFinished(view: WebView, url: String) {
                hideLoad()
                val title = view.title?.ifBlank { url } ?: url
                titleView.text = title
                Library.visit(this@BrowserActivity, url, title)
                refreshSave()
                val ads = if (AdBlock.enabled(this@BrowserActivity)) "true" else "false"
                view.evaluateJavascript("window.__xtreamAds=$ads;$PAGE_HOOK", null)
            }
        }

        home.setOnClickListener { finish() }
        block.setOnClickListener {
            AdBlock.setEnabled(this, !AdBlock.enabled(this))
            refreshBlock(block)
            web.reload()
        }
        refreshBlock(block)
        if (!isTv) {
            rotate.visibility = View.VISIBLE
            rotate.setOnClickListener {
                val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                requestedOrientation = if (portrait) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
        save.setOnClickListener {
            val url = web.url ?: return@setOnClickListener
            val title = web.title?.ifBlank { url } ?: url
            Library.toggleBookmark(this, url, title)
            refreshSave()
        }
        val optionsBtn = findViewById<Button>(R.id.video)
        optionsBtn.text = getString(R.string.video)
        optionsBtn.setOnClickListener {
            showPlayerOptionsMenu()
        }
        val dpadDownToWeb = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                web.requestFocus()
                true
            } else {
                false
            }
        }
        home.setOnKeyListener(dpadDownToWeb)
        rotate.setOnKeyListener(dpadDownToWeb)
        block.setOnKeyListener(dpadDownToWeb)
        optionsBtn.setOnKeyListener(dpadDownToWeb)
        save.setOnKeyListener(dpadDownToWeb)
        pointer = ScreenPointer(this)
        pointer.topInset = {
            val bar = chrome.parent as View
            if (chrome.visibility == View.VISIBLE) {
                val box = IntArray(2)
                bar.getLocationInWindow(box)
                box[1] + bar.height
            } else {
                0
            }
        }
        pointer.bind(findViewById(R.id.nav_mode))
        pointer.attach()

        // Other apps can open this screen, so accept only a plain http(s) address.
        val start = (intent.getStringExtra(EXTRA_URL) ?: intent.dataString)
            ?.takeIf { Uri.parse(it).scheme?.lowercase() in setOf("http", "https") && !Uri.parse(it).host.isNullOrBlank() }
        if (start.isNullOrBlank()) {
            finish()
            return
        }
        homeHost = Uri.parse(start).host?.lowercase()?.removePrefix("www.").orEmpty()
        pageHost = homeHost
        titleView.text = start
        web.loadUrl(start)
        web.requestFocus()
    }

    /** Desktop Chrome identity carrying the real engine version. */
    private fun desktopAgent(): String {
        val engine = try {
            Regex("Chrome/[0-9.]+").find(android.webkit.WebSettings.getDefaultUserAgent(this))?.value
        } catch (_: Exception) {
            null
        } ?: "Chrome/131.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $engine Safari/537.36"
    }

    private fun showLoad() {
        gate.visibility = View.VISIBLE
        loadRing.startAnimation(AnimationUtils.loadAnimation(this, R.anim.spin))
    }

    private fun hideLoad() {
        loadRing.clearAnimation()
        if (::gate.isInitialized) {
            gate.visibility = View.GONE
            if (::web.isInitialized) web.requestFocus()
        }
    }

    private fun sameSite(host: String?): Boolean {
        val name = host?.lowercase()?.removePrefix("www.") ?: return false
        val home = homeHost
        if (home.isEmpty()) return true
        return name == home || name.endsWith(".$home") || home.endsWith(".$name")
    }

    private fun refreshBlock(block: Button) {
        val on = AdBlock.enabled(this)
        block.text = getString(if (on) R.string.blocking else R.string.ads)
        block.setBackgroundResource(if (on) R.drawable.bg_go else R.drawable.bg_tile)
        block.setTextColor(if (on) 0xFF041018.toInt() else 0xFFF4F7FF.toInt())
    }

    private fun refreshSave() {
        val url = web.url
        val saved = url != null && Library.isBookmarked(this, url)
        save.text = getString(if (saved) R.string.saved else R.string.save)
        save.setBackgroundResource(if (saved) R.drawable.bg_go else R.drawable.bg_tile)
        save.setTextColor(if (saved) 0xFF041018.toInt() else 0xFFF4F7FF.toInt())
    }

    /** Sends a command to the page's video, including one nested in frames. */
    private fun player(cmd: String, arg: String = "null") {
        web.evaluateJavascript("window.__xtreamPlayer && window.__xtreamPlayer('$cmd', $arg)", null)
    }

    private fun flash(text: String) {
        feedback?.cancel()
        feedback = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun showPlayerOptionsMenu() {
        val items = arrayOf(
            "⏯ Play / Pause",
            "⏪ Rewind 10s",
            "⏩ Forward 10s",
            "⏭ Next Episode / Server",
            "📺 Video Quality: ${VideoQuality.label(this)}",
            "⚡ Playback Speed",
            "📐 Aspect Ratio",
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Player Options")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> player("toggle")
                    1 -> {
                        player("seek", "-10")
                        flash("⏪ 10s")
                    }
                    2 -> {
                        player("seek", "10")
                        flash("⏩ 10s")
                    }
                    3 -> {
                        player("next")
                        flash("⏭ Next episode / server")
                    }
                    4 -> showQualityDialog()
                    5 -> showSpeedDialog()
                    6 -> showAspectDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showQualityDialog() {
        val labels = VideoQuality.labels
        val values = VideoQuality.values
        val current = values.indexOf(VideoQuality.get(this)).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Video Quality")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val q = values[which]
                VideoQuality.set(this, q)
                player("quality", "'$q'")
                flash("Quality: ${labels[which]}")
                dialog.dismiss()
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
        val values = arrayOf("0.75", "1.0", "1.25", "1.5", "2.0")
        android.app.AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds) { _, which ->
                player("speed", values[which])
                flash("Speed: ${speeds[which]}")
            }
            .show()
    }

    private fun showAspectDialog() {
        val aspects = arrayOf("Fit (Contain)", "Fill (Cover)", "Stretch")
        val values = arrayOf("contain", "cover", "fill")
        android.app.AlertDialog.Builder(this)
            .setTitle("Aspect Ratio")
            .setItems(aspects) { _, which ->
                player("fit", "'${values[which]}'")
                flash("Aspect: ${aspects[which]}")
            }
            .show()
    }

    /**
     * While a video plays, the remote drives the player directly: the page's own
     * controls can't be reached with arrows, and in fullscreen only the video shows.
     */
    private fun handlePlayerKey(event: KeyEvent): Boolean {
        if (customView == null && !isVideoFullscreen) return false
        if (chrome.visibility == View.VISIBLE && chrome.findFocus() != null) return false
        val code = event.keyCode
        val keys = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_DOWN
        if (!keys) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Holding an arrow speeds seeking up: 10s steps, then 30s.
                val step = if (event.repeatCount >= SEEK_FAST_REPEATS) 30 else 10
                val back = code == KeyEvent.KEYCODE_DPAD_LEFT
                player("seek", if (back) "-$step" else "$step")
                flash(if (back) "⏪ ${step}s" else "⏩ ${step}s")
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (event.repeatCount == 0) showPlayerOptionsMenu()
            else -> if (event.repeatCount == 0) player("toggle")
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::pointer.isInitialized) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (isVideoFullscreen || customView != null) showPlayerOptionsMenu() else pointer.toggle()
            return true
        }
        if (pointer.handle(event)) return true
        if (handlePlayerKey(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            val atTop = web.scrollY <= 8
            // Holding Up always reaches the top bar, even when a video frame or a
            // page column has trapped the remote's focus.
            val held = event.repeatCount >= UP_HOLD_REPEATS
            if (chrome.visibility != View.VISIBLE || (web.hasFocus() && (atTop || held))) {
                revealChrome()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun revealChrome() {
        // A video that played inside the page (not a real fullscreen player) must
        // hand the screen back, or the Cursor switch and MENU stay locked out.
        if (customView == null && isVideoFullscreen) exitVideo()
        chrome.visibility = View.VISIBLE
        (chrome.parent as View).bringToFront()
        findViewById<Button>(R.id.home).requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player("toggle")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player("seek", "10")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player("seek", "-10")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                player("next")
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (isVideoFullscreen || customView != null) {
                    showPlayerOptionsMenu()
                } else {
                    pointer.toggle()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Activity back is the TV remote Back key")
    override fun onBackPressed() {
        if (customView != null || chrome.visibility != View.VISIBLE) {
            exitVideo()
            return
        }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && (customView != null || isVideoFullscreen)) {
            setImmersiveFullscreen(true)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (isVideoFullscreen) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setImmersiveFullscreen(enable: Boolean) {
        isVideoFullscreen = enable
        val window = this.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, !enable)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enable) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun handlePlayitStream(url: Uri, reqHeaders: Map<String, String>): WebResourceResponse? {
        var rawUrl = url.toString()
        if (rawUrl.contains("playit15.xyz")) {
            rawUrl = rawUrl.replace("playit15.xyz", "playit11.xyz")
        }
        val targetUri = Uri.parse(rawUrl)
        val origHost = targetUri.host ?: "playit11.xyz"
        val healthyNodes = listOf("playit11.xyz", "playit12.xyz", "playit13.xyz", "playit14.xyz")
        val candidateHosts = (listOf(origHost) + healthyNodes).distinct()

        for (node in candidateHosts) {
            val candidateUrl = rawUrl.replace(origHost, node)
            try {
                val conn = (URL(candidateUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 12000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    reqHeaders.forEach { (k, v) ->
                        if (!k.equals("Host", true)) setRequestProperty(k, v)
                    }
                    if (reqHeaders.none { it.key.equals("Referer", true) }) {
                        setRequestProperty("Referer", "https://ogoplayer.xyz/")
                    }
                    if (reqHeaders.none { it.key.equals("Origin", true) }) {
                        setRequestProperty("Origin", "https://ogoplayer.xyz")
                    }
                }
                val code = conn.responseCode
                if (code in 200..299 && LocalNetwork.isPublic(conn.url.toString())) {
                    val respHeaders = mutableMapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS, HEAD",
                        "Access-Control-Allow-Headers" to "*",
                        "Content-Type" to (conn.contentType ?: "video/mp2t")
                    )
                    val mime = if (targetUri.path?.endsWith(".html") == true) "video/mp2t" else (conn.contentType ?: "video/mp2t")
                    return WebResourceResponse(
                        mime,
                        null,
                        200,
                        "OK",
                        respHeaders,
                        conn.inputStream
                    )
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun handleHlsPlaylist(url: Uri, reqHeaders: Map<String, String>): WebResourceResponse? {
        return try {
            val conn = (URL(url.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                requestMethod = "GET"
                instanceFollowRedirects = true
                reqHeaders.forEach { (k, v) ->
                    if (!k.equals("Host", true)) setRequestProperty(k, v)
                }
                if (reqHeaders.none { it.key.equals("Referer", true) }) {
                    setRequestProperty("Referer", "https://ogoplayer.xyz/")
                }
                if (reqHeaders.none { it.key.equals("Origin", true) }) {
                    setRequestProperty("Origin", "https://ogoplayer.xyz")
                }
            }
            val code = conn.responseCode
            if (code in 200..299 && LocalNetwork.isPublic(conn.url.toString())) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val healed = text.replace("playit15.xyz", "playit11.xyz")
                val respHeaders = mutableMapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Access-Control-Allow-Methods" to "GET, POST, OPTIONS, HEAD",
                    "Access-Control-Allow-Headers" to "*",
                    "Content-Type" to (conn.contentType ?: "application/vnd.apple.mpegurl")
                )
                WebResourceResponse(
                    "application/vnd.apple.mpegurl",
                    "UTF-8",
                    200,
                    "OK",
                    respHeaders,
                    ByteArrayInputStream(healed.toByteArray(Charsets.UTF_8))
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun exitVideo() {
        setImmersiveFullscreen(false)
        if (::pointer.isInitialized) pointer.setSuppressed(false)
        if (customView != null) {
            (web.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }
        chrome.visibility = View.VISIBLE
        if (!resources.getBoolean(R.bool.is_television)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private inner class VideoBridge {
        @JavascriptInterface
        fun adsOn(): Boolean = AdBlock.enabled(this@BrowserActivity)

        @JavascriptInterface
        fun quality(): String = VideoQuality.get(this@BrowserActivity)

        @JavascriptInterface
        fun setQuality(value: String) {
            if (value in VideoQuality.values) VideoQuality.set(this@BrowserActivity, value)
        }

        @JavascriptInterface
        fun onVideoPlay() {
            runOnUiThread {
                // Already showing the player: a resume or seek is not a new start.
                if (customView != null || isVideoFullscreen) return@runOnUiThread
                chrome.visibility = View.GONE
                setImmersiveFullscreen(true)
                if (::pointer.isInitialized) pointer.setSuppressed(true)
                if (!resources.getBoolean(R.bool.is_television)) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }

        @JavascriptInterface
        fun onVideoEnd() {
            runOnUiThread { exitVideo() }
        }
    }

    override fun onDestroy() {
        if (!webGone) {
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val UP_HOLD_REPEATS = 6
        private const val SEEK_FAST_REPEATS = 4
        private const val CHROME_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
        private const val PAGE_HOOK = """
            (function(){
              if (window.__xtreamHook) return;
              window.__xtreamHook = true;
              // --- Player commands from the remote: reach the video however deep its frame is ---
              (function(){
                function localVideo(){
                  try { return window.__xtreamVideo && window.__xtreamVideo.isConnected ? window.__xtreamVideo : getBestVideo(); } catch (e) { return null; }
                }
                function jw(){
                  try {
                    if (typeof window.jwplayer !== 'function') return null;
                    var p = window.jwplayer();
                    return p && p.getState ? p : null;
                  } catch (e) { return null; }
                }
                function clickNext(){
                  var list = document.querySelectorAll('[data-server], .server, .btn-server, a[href*="server"], .episode, .next-episode');
                  for (var i = 0; i < list.length; i++) {
                    var t = (list[i].textContent || '').toLowerCase();
                    if (!list[i].classList.contains('active') && (t.indexOf('server') >= 0 || t.indexOf('next') >= 0 || t.indexOf('part') >= 0 || t.indexOf('episode') >= 0)) {
                      list[i].click();
                      return true;
                    }
                  }
                  return false;
                }
                function run(cmd, arg){
                  var v = localVideo();
                  var p = jw();
                  if (cmd === 'toggle') {
                    if (p) {
                      if (p.getState() === 'playing' || p.getState() === 'buffering') p.pause(); else p.play();
                    } else if (v) {
                      if (v.paused) { var r = v.play(); if (r && r.catch) r.catch(function(){}); } else v.pause();
                    }
                  } else if (cmd === 'seek') {
                    if (p && p.getPosition) {
                      p.seek(Math.max(0, p.getPosition() + arg));
                    } else if (v) {
                      var end = isFinite(v.duration) ? v.duration : v.currentTime + Math.abs(arg);
                      v.currentTime = Math.min(end, Math.max(0, v.currentTime + arg));
                    }
                  } else if (cmd === 'next') {
                    if (!clickNext() && v) v.currentTime = Math.min(v.duration || 0, v.currentTime + 60) || v.currentTime;
                  } else if (cmd === 'speed') {
                    if (p && p.setPlaybackRate) p.setPlaybackRate(arg);
                    if (v) v.playbackRate = arg;
                  } else if (cmd === 'fit') {
                    if (v) v.style.objectFit = arg;
                  } else if (cmd === 'quality') {
                    if (window.__xtreamApplyQuality) window.__xtreamApplyQuality(arg);
                    return;
                  }
                }
                window.__xtreamPlayer = function(cmd, arg){
                  try { run(cmd, arg); } catch (e) {}
                  var frames = document.querySelectorAll('iframe');
                  for (var i = 0; i < frames.length; i++) {
                    try { frames[i].contentWindow.postMessage({ action: 'xtream-player', cmd: cmd, arg: arg }, '*'); } catch (e) {}
                  }
                };
                window.addEventListener('message', function(ev){
                  if (ev && ev.data && ev.data.action === 'xtream-player') window.__xtreamPlayer(ev.data.cmd, ev.data.arg);
                });
              })();
              // --- Video quality: aim high instead of the player's cautious low start ---
              (function(){
                var q = 'best';
                try { q = String(Xtream.quality()); } catch (e) {}
                window.__xtreamQuality = q;
                var hlsList = [];
                function target(){
                  var v = window.__xtreamQuality;
                  if (v === 'auto') return 0;
                  if (v === 'best') return 100000;
                  var n = parseInt(v, 10);
                  return isNaN(n) ? 0 : n;
                }
                function heightOf(level){
                  if (!level) return 0;
                  if (level.height) return level.height;
                  var m = String(level.label || level.name || '').match(/(\d{3,4})\s*p/i);
                  if (m) return parseInt(m[1], 10);
                  if (/4k|uhd/i.test(String(level.label || ''))) return 2160;
                  return 0;
                }
                // Tallest level at or under the wanted height, else the smallest one.
                function pick(levels){
                  var want = target();
                  if (!want || !levels || !levels.length) return -1;
                  var best = -1, bestH = -1, low = -1, lowH = 1e9;
                  for (var i = 0; i < levels.length; i++) {
                    var h = heightOf(levels[i]);
                    if (!h) continue;
                    if (h <= want && h > bestH) { best = i; bestH = h; }
                    if (h < lowH) { low = i; lowH = h; }
                  }
                  return best >= 0 ? best : low;
                }
                // With Best, never let adaptive playback fall under 1080p when the stream has it.
                function floorIndex(levels, top){
                  var topH = heightOf(levels[top]);
                  var want = Math.min(topH, 1080);
                  var idx = top, idxH = topH;
                  for (var i = 0; i < levels.length; i++) {
                    var h = heightOf(levels[i]);
                    if (h >= want && h < idxH) { idx = i; idxH = h; }
                  }
                  return idx;
                }
                function applyHls(h){
                  try {
                    var levels = h.levels || [];
                    var idx = pick(levels);
                    if (idx < 0) {
                      h.config.minAutoBitrate = 0;
                      h.currentLevel = -1;
                      return;
                    }
                    h.autoLevelCapping = -1;
                    if (window.__xtreamQuality === 'best') {
                      var floor = levels[floorIndex(levels, idx)];
                      h.config.minAutoBitrate = floor && floor.bitrate ? floor.bitrate - 1 : 0;
                      h.currentLevel = -1;
                      h.nextLevel = idx;
                    } else {
                      h.config.minAutoBitrate = 0;
                      h.currentLevel = idx;
                    }
                  } catch (e) {}
                }
                function tuneHls(H){
                  if (!H || typeof H !== 'function' || H.__xtreamTuned || typeof Reflect === 'undefined') return H;
                  var W = function(cfg){
                    cfg = cfg || {};
                    if (target()) {
                      cfg.capLevelToPlayerSize = false;
                      if (!(cfg.abrEwmaDefaultEstimate > 8000000)) cfg.abrEwmaDefaultEstimate = 8000000;
                      cfg.testBandwidth = false;
                    }
                    var h = Reflect.construct(H, [cfg], new.target || W);
                    hlsList.push(h);
                    try {
                      var ev = (H.Events && H.Events.MANIFEST_PARSED) || 'hlsManifestParsed';
                      h.on(ev, function(){ applyHls(h); });
                    } catch (e) {}
                    return h;
                  };
                  W.prototype = H.prototype;
                  try { Object.setPrototypeOf(W, H); } catch (e) {}
                  W.__xtreamTuned = true;
                  return W;
                }
                try {
                  var cur = tuneHls(window.Hls);
                  Object.defineProperty(window, 'Hls', {
                    configurable: true,
                    enumerable: true,
                    get: function(){ return cur; },
                    set: function(v){ cur = tuneHls(v); }
                  });
                } catch (e) {}
                function applyJw(){
                  if (typeof window.jwplayer !== 'function') return;
                  try {
                    var jw = window.jwplayer();
                    if (!jw || !jw.getQualityLevels) return;
                    if (!jw.__xtreamQ) {
                      jw.__xtreamQ = true;
                      try { jw.on('levels', applyJw); } catch (e) {}
                    }
                    var levels = jw.getQualityLevels() || [];
                    var idx = pick(levels);
                    if (idx < 0) {
                      for (var i = 0; i < levels.length; i++) {
                        if (/auto/i.test(String(levels[i].label || ''))) idx = i;
                      }
                    }
                    if (idx >= 0 && jw.getCurrentQuality() !== idx) jw.setCurrentQuality(idx);
                  } catch (e) {}
                }
                function applyVideoJs(){
                  if (!window.videojs || !window.videojs.getPlayers) return;
                  try {
                    var players = window.videojs.getPlayers();
                    Object.keys(players).forEach(function(k){
                      var p = players[k];
                      if (!p || !p.qualityLevels) return;
                      var ql = p.qualityLevels();
                      var list = [];
                      for (var i = 0; i < ql.length; i++) list.push(ql[i]);
                      var idx = pick(list);
                      var floor = idx >= 0 && window.__xtreamQuality === 'best' ? heightOf(list[floorIndex(list, idx)]) : 0;
                      list.forEach(function(l, i){
                        var h = heightOf(l);
                        if (idx < 0) l.enabled = true;
                        else if (floor) l.enabled = h >= floor && h <= heightOf(list[idx]);
                        else l.enabled = i === idx;
                      });
                    });
                  } catch (e) {}
                }
                function applyYouTube(){
                  var mp = document.getElementById('movie_player');
                  if (!mp || !mp.getAvailableQualityLevels || !mp.setPlaybackQualityRange) return;
                  try {
                    var names = mp.getAvailableQualityLevels() || [];
                    var sizes = { highres: 4320, hd2160: 2160, hd1440: 1440, hd1080: 1080, hd720: 720, large: 480, medium: 360, small: 240, tiny: 144 };
                    var levels = names.map(function(n){ return { height: sizes[n] || 0, name: n }; });
                    var idx = pick(levels);
                    if (idx < 0) mp.setPlaybackQualityRange('auto', 'auto');
                    else mp.setPlaybackQualityRange(names[idx], names[idx]);
                  } catch (e) {}
                }
                window.__xtreamApplyQuality = function(next){
                  if (next) window.__xtreamQuality = String(next);
                  hlsList.forEach(applyHls);
                  applyJw();
                  applyVideoJs();
                  applyYouTube();
                  if (next) {
                    document.querySelectorAll('iframe').forEach(function(f){
                      try { f.contentWindow.postMessage({ action: 'xtream-quality', quality: String(next) }, '*'); } catch (e) {}
                    });
                  }
                };
                window.addEventListener('message', function(ev){
                  if (ev && ev.data && ev.data.action === 'xtream-quality') window.__xtreamApplyQuality(ev.data.quality);
                });
                // Players build their level lists a moment after playback starts.
                window.__xtreamQualitySoon = function(){
                  [300, 1500, 4000].forEach(function(ms){ setTimeout(function(){ window.__xtreamApplyQuality(); }, ms); });
                };
              })();
              // --- Cursor mode helpers ---
              window.__xtreamShowCursor = function(x, y) {
                var c = document.getElementById('__xtream_cur');
                if (!c) {
                  c = document.createElement('div');
                  c.id = '__xtream_cur';
                  c.style.cssText = 'position:fixed;width:26px;height:26px;border-radius:50%;' +
                    'background:rgba(255,120,0,0.85);border:3px solid #fff;z-index:2147483647;' +
                    'pointer-events:none;transform:translate(-50%,-50%);' +
                    'box-shadow:0 0 10px rgba(0,0,0,0.6),0 0 0 2px rgba(255,120,0,0.4);' +
                    'transition:left 0.06s ease,top 0.06s ease;';
                  document.body.appendChild(c);
                }
                c.style.left = x + 'px';
                c.style.top = y + 'px';
                c.style.display = 'block';
              };
              window.__xtreamHideCursor = function() {
                var c = document.getElementById('__xtream_cur');
                if (c) c.style.display = 'none';
              };
              window.__xtreamClickAt = function(x, y) {
                var c = document.getElementById('__xtream_cur');
                if (c) { c.style.display = 'none'; }
                var el = document.elementFromPoint(x, y);
                if (c) { c.style.display = 'block'; }
                if (!el) return;
                // Try clicking the element and its closest interactive ancestor
                var target = el.closest('a,button,input,select,textarea,[onclick],[role="button"]') || el;
                target.click();
                target.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,clientX:x,clientY:y}));
              };
              var ads = true;
              try { ads = Xtream.adsOn(); } catch (e) {}
              window.__xtreamAds = ads;
              window.__xtreamIsAd = isAd;
              if (ads) {
                window.open = function(){ return null; };
                try {
                  var adCss = document.createElement('style');
                  adCss.id = 'xtream-adblock-css';
                  adCss.textContent = `
                    .rek, .rek_close, .rek_counter, .pppx, #adStop, [id*="adStop"],
                    [class*="rek_"], [id*="rek_"], [class^="rek"], [id^="rek"],
                    .ad-overlay, .ad-counter, [class*="ad-countdown"], [id*="ad-countdown"],
                    [class*="ad-notice"], [class*="adNotice"], .jw-ad, .jw-skip,
                    .ima-ad-container, .videoAdUi, .vjs-ad-overlay {
                      display: none !important;
                      visibility: hidden !important;
                      opacity: 0 !important;
                      pointer-events: none !important;
                      position: absolute !important;
                      top: -9999px !important;
                      left: -9999px !important;
                      width: 0 !important;
                      height: 0 !important;
                      z-index: -99999 !important;
                    }
                  `;
                  var mountCss = function() {
                    var r = document.head || document.documentElement;
                    if (r && !document.getElementById('xtream-adblock-css')) r.appendChild(adCss);
                  };
                  mountCss();
                  document.addEventListener('DOMContentLoaded', mountCss);
                } catch(e) {}
              }
              var keys = [
                'doubleclick','googlesyndication','googleadservices','googletagservices',
                'popads','popcash','exoclick','exosrv','trafficjunky','juicyads','adsterra',
                'hilltopads','clickadu','monetag','propellerads','outbrain','taboola','revcontent',
                'adnxs','adservice','adserver','adskeeper','magsrv','realsrv','tsyndicate',
                'trafficstars','onclickads','popunder','clickaine','galaksion','admaven',
                'highrevenue','pagead','securepubads','fundingchoices','imasdk','gampad',
                'pubads','fwmrm','springserve','stickyadstv','lkqd','spotx','mgid.com',
                'createlouisville','bakestubborn','show-sb','show-creative','flushpersist',
                'storageimagedisplay','spendsdetachment','interstitial','center_banner',
                'gambling','whos.amung.us','histats','alwingulla','deloton','onclickprediction',
                'pxf.gif','/sspi/','propush','adcash','popmyads'
              ];
              function isAd(src){
                if (!src) return false;
                src = String(src).toLowerCase();
                if (src.indexOf('blob:') === 0 || src.indexOf('playit') !== -1 || src.indexOf('master.txt') !== -1 || src.indexOf('master.m3u8') !== -1 || src.indexOf('ogoplayer.xyz') !== -1) return false;
                for (var i = 0; i < keys.length; i++) {
                  if (src.indexOf(keys[i]) !== -1) return true;
                }
                return false;
              }
              window.addEventListener('error', function(e) {
                if (e && e.message && (e.message.indexOf('label') !== -1 || e.message.indexOf('getVisualQuality') !== -1)) {
                  e.preventDefault();
                  e.stopPropagation();
                  return true;
                }
              }, true);
              if (ads) {
                document.addEventListener('click', function(e){
                  var target = e.target;
                  while (target && target !== document.body && target !== document.documentElement) {
                    if (target.tagName === 'A') {
                      var href = target.href || target.getAttribute('href') || '';
                      if (href && isAd(href)) {
                        e.preventDefault();
                        e.stopPropagation();
                        target.remove();
                        return false;
                      }
                    }
                    target = target.parentElement;
                  }
                }, true);
              }
              function skipAd(v) {
                if (!v || v.__xtreamHold) return !!(v && v.__xtreamHold);
                var src = v.currentSrc || v.src || '';
                if (!isAd(src)) return false;
                v.__xtreamHold = true;
                try { v.muted = true; } catch (e) {}
                try { v.pause(); } catch (e) {}
                try {
                  if (v.__xtreamSrc && v.__xtreamSrc !== src) {
                    var pos = v.__xtreamPos || 0;
                    var back = v.__xtreamSrc;
                    var resume = function() {
                      v.removeEventListener('loadeddata', resume);
                      try {
                        if (pos > 1) v.currentTime = pos;
                        var play = v.play();
                        if (play && play.catch) play.catch(function(){});
                      } catch (e2) {}
                    };
                    v.addEventListener('loadeddata', resume);
                    v.src = back;
                  }
                } catch (e3) {}
                setTimeout(function(){ v.__xtreamHold = false; }, 800);
                return true;
              }
              function hook(v){
                if (!v || v.__xtream) return;
                v.__xtream = true;
                v.addEventListener('loadstart', function(){ skipAd(v); });
                v.addEventListener('timeupdate', function(){
                  var src = v.currentSrc || v.src || '';
                  if (isAd(src)) {
                    skipAd(v);
                    return;
                  }
                  if (v.currentTime > 1) {
                    v.__xtreamPos = v.currentTime;
                    if (v.currentSrc) v.__xtreamSrc = v.currentSrc;
                  }
                });
                v.addEventListener('loadedmetadata', function(){
                  if (window.__xtreamQualitySoon) window.__xtreamQualitySoon();
                });
                v.addEventListener('play', function(){
                  if (window.__xtreamQualitySoon) window.__xtreamQualitySoon();
                  if (window.__xtreamAds !== false && skipAd(v)) return;
                  var box = v.getBoundingClientRect();
                  if (box.width < 200 && box.height < 120) return;
                  // Resuming, seeking or a new source must not restart the fullscreen hand-off.
                  var already = document.fullscreenElement || document.webkitFullscreenElement;
                  if (!already) {
                    try {
                      if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
                      else if (v.requestFullscreen) v.requestFullscreen();
                    } catch (e) {}
                  }
                  try { Xtream.onVideoPlay(); } catch (e) {}
                  injectXtreamPlayerOSD();
                });
                v.addEventListener('ended', function(){
                  if (window.__xtreamAds !== false && isAd(v.currentSrc || v.src || '')) return;
                  try { Xtream.onVideoEnd(); } catch (e) {}
                });
              }
              function formatClock(total){
                if (!isFinite(total) || total < 0) return '00:00';
                var s = Math.floor(total);
                var h = Math.floor(s / 3600);
                var m = Math.floor((s % 3600) / 60);
                var sec = s % 60;
                var ss = (sec < 10 ? '0' : '') + sec;
                var mm = (m < 10 ? '0' : '') + m;
                if (h > 0) return h + ':' + mm + ':' + ss;
                return mm + ':' + ss;
              }

              function getBestVideo(){
                var best = null;
                var area = 0;
                document.querySelectorAll('video').forEach(function(v){
                  var src = v.currentSrc || v.src || '';
                  if (isAd(src)) return;
                  var box = v.getBoundingClientRect();
                  var size = box.width * box.height;
                  if (box.width > 160 && box.height > 90 && size > area) {
                    area = size;
                    best = v;
                  }
                });
                return best;
              }

              var osdTimer = null;
              function showOSD(){
                var osd = document.getElementById('xtream-player-osd');
                if (!osd) return;
                osd.classList.add('xt-visible');
                clearTimeout(osdTimer);
                osdTimer = setTimeout(function(){
                  var modal = document.getElementById('xt-modal');
                  if (!modal || !modal.classList.contains('xt-open')) {
                    osd.classList.remove('xt-visible');
                  }
                }, 3500);
              }

              function injectXtreamPlayerOSD(){
                var oldBtn = document.getElementById('xtream-dl');
                if (oldBtn) oldBtn.remove();
                if (!document.body) return;

                var best = getBestVideo();
                var osd = document.getElementById('xtream-player-osd');
                if (!best) {
                  if (osd) osd.style.display = 'none';
                  return;
                }
                window.__xtreamVideo = best;

                if (!document.getElementById('xtream-osd-style')) {
                  var st = document.createElement('style');
                  st.id = 'xtream-osd-style';
                  st.textContent = `
                    #xtream-player-osd {
                      position: fixed;
                      bottom: 0;
                      left: 0;
                      right: 0;
                      background: linear-gradient(0deg, rgba(2, 4, 12, 0.96) 0%, rgba(2, 4, 12, 0.75) 60%, transparent 100%);
                      backdrop-filter: blur(12px);
                      -webkit-backdrop-filter: blur(12px);
                      padding: 12px 24px 16px 24px;
                      display: flex;
                      flex-direction: column;
                      gap: 8px;
                      z-index: 2147483645;
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                      color: #F4F7FF;
                      opacity: 0;
                      pointer-events: none;
                      transform: translateY(8px);
                      transition: opacity 0.25s ease, transform 0.25s ease;
                      box-sizing: border-box;
                    }
                    #xtream-player-osd.xt-visible {
                      opacity: 1;
                      pointer-events: auto;
                      transform: translateY(0);
                    }
                    .xt-seek-row {
                      display: flex;
                      align-items: center;
                      gap: 12px;
                      width: 100%;
                    }
                    .xt-time {
                      font-size: 13px;
                      font-weight: 700;
                      color: #73D8FF;
                      min-width: 48px;
                      font-family: monospace;
                    }
                    .xt-time-dur {
                      color: #8E9BAE;
                    }
                    .xt-slider {
                      flex: 1;
                      -webkit-appearance: none;
                      appearance: none;
                      height: 6px;
                      border-radius: 3px;
                      background: rgba(255, 255, 255, 0.25);
                      outline: none;
                      cursor: pointer;
                      transition: height 0.15s ease;
                    }
                    .xt-slider:hover, .xt-slider:focus {
                      height: 8px;
                      box-shadow: 0 0 10px rgba(62, 203, 255, 0.6);
                    }
                    .xt-slider::-webkit-slider-thumb {
                      -webkit-appearance: none;
                      appearance: none;
                      width: 16px;
                      height: 16px;
                      border-radius: 50%;
                      background: #3ECBFF;
                      box-shadow: 0 0 8px rgba(62, 203, 255, 0.8);
                      cursor: pointer;
                    }
                    .xt-ctrl-row {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      width: 100%;
                    }
                    .xt-group {
                      display: flex;
                      align-items: center;
                      gap: 8px;
                    }
                    .xt-btn {
                      background: rgba(255, 255, 255, 0.1);
                      border: 1px solid rgba(255, 255, 255, 0.18);
                      color: #F4F7FF;
                      border-radius: 10px;
                      padding: 8px 14px;
                      font-size: 13px;
                      font-weight: 600;
                      cursor: pointer;
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      gap: 6px;
                      transition: all 0.2s ease;
                      outline: none;
                      min-height: 40px;
                    }
                    .xt-btn:hover, .xt-btn:focus {
                      background: #3ECBFF;
                      color: #041018;
                      border-color: #3ECBFF;
                      box-shadow: 0 0 12px rgba(62, 203, 255, 0.6);
                      transform: scale(1.03);
                    }
                    .xt-btn-main {
                      background: #3ECBFF;
                      color: #041018;
                      border-color: #3ECBFF;
                      font-weight: 700;
                      font-size: 14px;
                      padding: 8px 20px;
                    }
                    .xt-btn-main:hover, .xt-btn-main:focus {
                      background: #73D8FF;
                      box-shadow: 0 0 18px rgba(62, 203, 255, 0.85);
                    }
                    #xt-modal {
                      position: fixed;
                      bottom: 84px;
                      right: 24px;
                      width: 290px;
                      background: rgba(8, 12, 26, 0.97);
                      backdrop-filter: blur(20px);
                      -webkit-backdrop-filter: blur(20px);
                      border: 1px solid rgba(62, 203, 255, 0.35);
                      border-radius: 16px;
                      padding: 16px;
                      display: none;
                      flex-direction: column;
                      gap: 12px;
                      box-shadow: 0 16px 48px rgba(0, 0, 0, 0.9);
                      z-index: 2147483647;
                      color: #F4F7FF;
                    }
                    #xt-modal.xt-open {
                      display: flex;
                    }
                    .xt-modal-head {
                      display: flex;
                      justify-content: space-between;
                      align-items: center;
                      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                      padding-bottom: 8px;
                    }
                    .xt-modal-title {
                      color: #3ECBFF;
                      font-size: 14px;
                      font-weight: 700;
                    }
                    .xt-modal-close {
                      background: transparent;
                      border: 0;
                      color: #8E9BAE;
                      font-size: 16px;
                      cursor: pointer;
                      padding: 2px 6px;
                      border-radius: 6px;
                    }
                    .xt-modal-close:hover, .xt-modal-close:focus {
                      color: #F4F7FF;
                      background: rgba(255, 255, 255, 0.1);
                    }
                    .xt-sec-title {
                      font-size: 11px;
                      font-weight: 700;
                      text-transform: uppercase;
                      letter-spacing: 0.08em;
                      color: #8E9BAE;
                      margin-bottom: 4px;
                    }
                    .xt-pills {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 6px;
                    }
                    .xt-pill {
                      background: rgba(255, 255, 255, 0.08);
                      border: 1px solid rgba(255, 255, 255, 0.14);
                      border-radius: 8px;
                      padding: 6px 12px;
                      font-size: 12px;
                      font-weight: 600;
                      color: #DDE5F0;
                      cursor: pointer;
                      outline: none;
                      transition: all 0.15s ease;
                    }
                    .xt-pill:hover, .xt-pill:focus, .xt-pill.active {
                      background: #3ECBFF;
                      color: #041018;
                      border-color: #3ECBFF;
                      box-shadow: 0 0 10px rgba(62, 203, 255, 0.5);
                    }
                  `;
                  document.head.appendChild(st);
                }

                if (!osd) {
                  osd = document.createElement('div');
                  osd.id = 'xtream-player-osd';
                  osd.innerHTML = `
                    <div class="xt-seek-row">
                      <span id="xt-cur" class="xt-time">00:00</span>
                      <input type="range" id="xt-seek" class="xt-slider" min="0" max="1000" value="0" />
                      <span id="xt-dur" class="xt-time xt-time-dur">00:00</span>
                    </div>
                    <div class="xt-ctrl-row">
                      <div class="xt-group">
                        <button id="xt-rw" class="xt-btn" title="Rewind 10s">↺ 10s</button>
                        <button id="xt-play" class="xt-btn xt-btn-main" title="Play/Pause">⏸ Pause</button>
                        <button id="xt-ff" class="xt-btn" title="Forward 10s">10s ↻</button>
                        <button id="xt-next" class="xt-btn" title="Next Episode / Server">⏭ Next</button>
                      </div>
                      <div class="xt-group">
                        <button id="xt-fs" class="xt-btn" title="Fullscreen">⛶ Fullscreen</button>
                        <button id="xt-opt" class="xt-btn" title="Settings / Options">⚙️ Options</button>
                      </div>
                    </div>
                  `;
                  document.documentElement.appendChild(osd);

                  var modal = document.createElement('div');
                  modal.id = 'xt-modal';
                  modal.innerHTML = `
                    <div class="xt-modal-head">
                      <span class="xt-modal-title">⚙️ Player Options &amp; Quality</span>
                      <button id="xt-close-modal" class="xt-modal-close">✕</button>
                    </div>
                    <div>
                      <div class="xt-sec-title">Video Quality:</div>
                      <div class="xt-pills" id="xt-q-pills">
                        <button class="xt-pill" data-q="best">Best</button>
                        <button class="xt-pill" data-q="1080">1080p</button>
                        <button class="xt-pill" data-q="720">720p</button>
                        <button class="xt-pill" data-q="480">480p</button>
                        <button class="xt-pill" data-q="360">360p</button>
                        <button class="xt-pill" data-q="auto">Auto</button>
                      </div>
                    </div>
                    <div>
                      <div class="xt-sec-title">Playback Speed:</div>
                      <div class="xt-pills" id="xt-spd-pills">
                        <button class="xt-pill" data-spd="0.75">0.75x</button>
                        <button class="xt-pill active" data-spd="1.0">1.0x</button>
                        <button class="xt-pill" data-spd="1.25">1.25x</button>
                        <button class="xt-pill" data-spd="1.5">1.5x</button>
                        <button class="xt-pill" data-spd="2.0">2.0x</button>
                      </div>
                    </div>
                    <div>
                      <div class="xt-sec-title">Aspect Fit:</div>
                      <div class="xt-pills" id="xt-fit-pills">
                        <button class="xt-pill active" data-fit="contain">Fit</button>
                        <button class="xt-pill" data-fit="cover">Fill</button>
                        <button class="xt-pill" data-fit="fill">Stretch</button>
                      </div>
                    </div>
                  `;
                  document.documentElement.appendChild(modal);

                  var playBtn = document.getElementById('xt-play');
                  var rwBtn = document.getElementById('xt-rw');
                  var ffBtn = document.getElementById('xt-ff');
                  var nextBtn = document.getElementById('xt-next');
                  var fsBtn = document.getElementById('xt-fs');
                  var optBtn = document.getElementById('xt-opt');
                  var seek = document.getElementById('xt-seek');
                  var closeBtn = document.getElementById('xt-close-modal');

                  window.__xtreamTogglePlay = function(){
                    window.__xtreamPlayer('toggle');
                    showOSD();
                  };

                  window.__xtreamNudge = function(delta){
                    window.__xtreamPlayer('seek', delta);
                    showOSD();
                  };

                  window.__xtreamNext = function(){
                    window.__xtreamPlayer('next');
                    showOSD();
                  };

                  window.__xtreamToggleOptions = function(){
                    if (modal) {
                      modal.classList.toggle('xt-open');
                      showOSD();
                    }
                    document.querySelectorAll('iframe').forEach(function(f){
                      try { f.contentWindow.postMessage('xtream-toggle-options', '*'); } catch(e){}
                    });
                  };

                  window.__xtreamSetQuality = function(q){
                    try {
                      if (window.jwplayer) {
                        var jw = window.jwplayer();
                        var qualities = jw.getQualityLevels ? jw.getQualityLevels() : [];
                        for (var qi = 0; qi < qualities.length; qi++) {
                          if (q === 'auto' && (qualities[qi].label || '').toLowerCase().includes('auto')) {
                            jw.setCurrentQuality(qi); break;
                          } else if ((qualities[qi].label || '').includes(q)) {
                            jw.setCurrentQuality(qi); break;
                          }
                        }
                      }
                    } catch(e1) {}
                    try {
                      var qBtns = document.querySelectorAll('[data-quality], .quality-btn, .quality');
                      qBtns.forEach(function(qb){
                        if ((qb.textContent || '').includes(q)) qb.click();
                      });
                    } catch(e2) {}
                    document.querySelectorAll('iframe').forEach(function(f){
                      try { f.contentWindow.postMessage({action:'xtream-set-quality', quality:q}, '*'); } catch(e){}
                    });
                    showOSD();
                  };

                  window.addEventListener('message', function(ev){
                    if (!ev || !ev.data) return;
                    if (ev.data === 'xtream-toggle-options') {
                      if (modal) { modal.classList.toggle('xt-open'); showOSD(); }
                    } else if (ev.data && ev.data.action === 'xtream-set-quality') {
                      window.__xtreamSetQuality(ev.data.quality);
                    }
                  });

                  playBtn.addEventListener('click', function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    window.__xtreamTogglePlay();
                  });

                  rwBtn.addEventListener('click', function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    window.__xtreamNudge(-10);
                  });

                  ffBtn.addEventListener('click', function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    window.__xtreamNudge(10);
                  });

                  nextBtn.addEventListener('click', function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    window.__xtreamNext();
                  });

                  fsBtn.addEventListener('click', function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    var v = window.__xtreamVideo;
                    if (!v) return;
                    try {
                      if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
                      else if (v.requestFullscreen) v.requestFullscreen();
                      else if (document.documentElement.requestFullscreen) document.documentElement.requestFullscreen();
                    } catch(err) {}
                    showOSD();
                  });

                  optBtn.addEventListener('click', function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    window.__xtreamToggleOptions();
                  });

                  closeBtn.addEventListener('click', function(e){
                    e.preventDefault();
                    e.stopPropagation();
                    modal.classList.remove('xt-open');
                  });

                  var isDraggingSeek = false;
                  seek.addEventListener('pointerdown', function(){ isDraggingSeek = true; });
                  seek.addEventListener('touchstart', function(){ isDraggingSeek = true; }, {passive: true});
                  seek.addEventListener('mousedown', function(){ isDraggingSeek = true; });
                  seek.addEventListener('change', function(){ isDraggingSeek = false; });
                  seek.addEventListener('pointerup', function(){ isDraggingSeek = false; });
                  seek.addEventListener('mouseup', function(){ isDraggingSeek = false; });
                  seek.addEventListener('touchend', function(){ isDraggingSeek = false; });

                  seek.addEventListener('input', function(){
                    var v = window.__xtreamVideo || getBestVideo();
                    if (!v || !v.duration) return;
                    var targetSec = (seek.value / 1000) * v.duration;
                    try {
                      if (window.jwplayer && typeof window.jwplayer === 'function') {
                        var jw = window.jwplayer();
                        if (jw && typeof jw.seek === 'function') {
                          jw.seek(targetSec);
                        } else {
                          v.currentTime = targetSec;
                        }
                      } else {
                        v.currentTime = targetSec;
                      }
                    } catch(e) {
                      v.currentTime = targetSec;
                    }
                    var curSpan = document.getElementById('xt-cur');
                    if (curSpan) curSpan.textContent = formatClock(targetSec);
                    showOSD();
                  });

                  document.querySelectorAll('#xt-q-pills .xt-pill').forEach(function(pill){
                    if (pill.getAttribute('data-q') === window.__xtreamQuality) pill.classList.add('active');
                    pill.addEventListener('click', function(e){
                      e.preventDefault();
                      e.stopPropagation();
                      document.querySelectorAll('#xt-q-pills .xt-pill').forEach(function(p){ p.classList.remove('active'); });
                      pill.classList.add('active');
                      var q = pill.getAttribute('data-q');
                      try { Xtream.setQuality(q); } catch (e0) {}
                      window.__xtreamPlayer('quality', q);
                      showOSD();
                    });
                  });

                  document.querySelectorAll('#xt-spd-pills .xt-pill').forEach(function(pill){
                    pill.addEventListener('click', function(e){
                      e.preventDefault();
                      e.stopPropagation();
                      document.querySelectorAll('#xt-spd-pills .xt-pill').forEach(function(p){ p.classList.remove('active'); });
                      pill.classList.add('active');
                      var spd = parseFloat(pill.getAttribute('data-spd')) || 1.0;
                      window.__xtreamPlayer('speed', spd);
                      showOSD();
                    });
                  });

                  document.querySelectorAll('#xt-fit-pills .xt-pill').forEach(function(pill){
                    pill.addEventListener('click', function(e){
                      e.preventDefault();
                      e.stopPropagation();
                      document.querySelectorAll('#xt-fit-pills .xt-pill').forEach(function(p){ p.classList.remove('active'); });
                      pill.classList.add('active');
                      var fit = pill.getAttribute('data-fit') || 'contain';
                      window.__xtreamPlayer('fit', fit);
                      showOSD();
                    });
                  });

                  ['mousemove', 'pointerdown', 'touchstart', 'keydown'].forEach(function(evt){
                    document.addEventListener(evt, showOSD, {passive: true});
                  });
                }

                osd.style.display = 'flex';

                var curSpan = document.getElementById('xt-cur');
                var durSpan = document.getElementById('xt-dur');
                var seekInput = document.getElementById('xt-seek');
                var playButton = document.getElementById('xt-play');

                if (curSpan) curSpan.textContent = formatClock(best.currentTime || 0);
                if (durSpan) durSpan.textContent = formatClock(best.duration || 0);
                if (seekInput && best.duration && !isDraggingSeek) {
                  seekInput.value = Math.floor(((best.currentTime || 0) / best.duration) * 1000);
                }
                if (playButton) {
                  playButton.textContent = best.paused ? '▶ Play' : '⏸ Pause';
                }
              }
              function scan(){
                if (!document.body) return;
                document.querySelectorAll('video').forEach(hook);
              }
              function hidePlayerAds(){
                if (window.__xtreamAds === false || !document.body) return;
                var sel = '.ima-ad-container,.videoAdUi,.jw-ad,.jw-skip,.vjs-ad-overlay,[class*="ad-container"],[id*="ad-container"],[class*="adContainer"]';
                document.querySelectorAll(sel).forEach(function(node){
                  if (node.closest && node.closest('video')) return;
                  var videos = node.querySelectorAll ? node.querySelectorAll('video') : [];
                  for (var i = 0; i < videos.length; i++) {
                    var box = videos[i].getBoundingClientRect();
                    if (box.width > 200 && box.height > 120 && !isAd(videos[i].currentSrc || videos[i].src || '')) return;
                  }
                  node.remove();
                });
              }
              function hideAds(){
                if (window.__xtreamAds === false) return;
                if (!document.body) return;
                document.querySelectorAll('iframe,ins').forEach(function(node){
                  if (node.closest && node.closest('video')) return;
                  var src = node.src || node.getAttribute('src') || '';
                  if (isAd(src)) node.remove();
                });
                var badSelectors = [
                  '.ima-ad-container', '.videoAdUi', '.jw-ad', '.jw-skip', '.vjs-ad-overlay',
                  '[class*="ad-container"]', '[id*="ad-container"]', '[class*="adContainer"]',
                  '[id*="ad-banner"]', '[class*="ad-banner"]', '[id*="banner-ad"]', '[class*="banner-ad"]',
                  '[id*="interstitial"]', '[class*="interstitial"]', '[id*="center_banner"]', '[class*="center_banner"]',
                  '[class*="popunder"]', '[id*="popunder"]', '[class*="floating-banner"]',
                  '.rek', '.rek_close', '.rek_counter', '.pppx', '#adStop', '[id*="adStop"]',
                  '[class*="rek"]', '[id*="rek"]', '.ad-overlay', '.ad-counter',
                  '[class*="ad-countdown"]', '[id*="ad-countdown"]', '[class*="countdown"]',
                  '[class*="ad-notice"]', '[id*="ad-notice"]', '[class*="ad_overlay"]'
                ];
                badSelectors.forEach(function(sel){
                  try {
                    document.querySelectorAll(sel).forEach(function(el){
                      if (el.closest && el.closest('video')) return;
                      var v = el.querySelector ? el.querySelector('video') : null;
                      if (!v) el.remove();
                    });
                  } catch(e) {}
                });
                try {
                  var vw = window.innerWidth || 1920;
                  var vh = window.innerHeight || 1080;
                  document.querySelectorAll('div, a, section').forEach(function(el){
                    if (el.id === 'xtream-dl' || el.tagName === 'VIDEO') return;
                    if (el.querySelector && el.querySelector('video')) return;
                    var style = window.getComputedStyle(el);
                    if (!style) return;
                    if (style.position === 'fixed' || style.position === 'absolute') {
                      var z = parseInt(style.zIndex, 10);
                      var op = parseFloat(style.opacity);
                      var box = el.getBoundingClientRect();
                      if (box.width >= vw * 0.75 && box.height >= vh * 0.75) {
                        if (op < 0.1 || style.visibility === 'hidden' || z > 500) {
                          if (!el.querySelector('h1, h2, h3, p, main, article, input, form')) {
                            el.remove();
                          }
                        }
                      }
                    }
                  });
                } catch(e2) {}
                hidePlayerAds();
              }
              function purgeAdTextOverlays(){
                if (window.__xtreamAds === false || !document.body) return;
                try {
                  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                  var node;
                  var toRemove = [];
                  while (node = walker.nextNode()) {
                    var txt = (node.nodeValue || '').trim().toLowerCase();
                    if (txt && (txt.includes('closed in') || txt.includes('will be closed') || txt.includes('close in') || txt.includes('ads will') || txt.includes('skip ad in') || txt.includes('seconds remaining'))) {
                      var parent = node.parentElement;
                      if (parent && parent.tagName !== 'BODY' && parent.tagName !== 'HTML') {
                        var box = parent.closest ? parent.closest('.rek, [class*="rek"], div, section') : parent;
                        if (box && !box.querySelector('video') && box.tagName !== 'BODY' && box.tagName !== 'HTML') {
                          toRemove.push(box);
                        } else if (parent && !parent.querySelector('video') && parent.tagName !== 'BODY' && parent.tagName !== 'HTML') {
                          toRemove.push(parent);
                        }
                      }
                    }
                  }
                  toRemove.forEach(function(el){ try { el.remove(); } catch(e){} });
                } catch(e) {}
                try {
                  var pbo = document.querySelector('.play-button-outer');
                  if (pbo) {
                    var rek = document.querySelector('.rek, .rek_counter, .rek_close, .pppx');
                    if (rek) rek.remove();
                  }
                } catch(e) {}
              }
              window.__xtreamHide = hideAds;
              window.__xtreamAdCount = function(){
                if (window.__xtreamAds === false || !document.body) return 0;
                var n = 0;
                document.querySelectorAll('iframe,ins').forEach(function(node){
                  if (node.closest && node.closest('video')) return;
                  var src = node.src || node.getAttribute('src') || '';
                  if (isAd(src)) n++;
                });
                return n;
              };
              function boot(){
                scan();
                hideAds();
                purgeAdTextOverlays();
                if (!document.documentElement || window.__xtreamObs) return;
                window.__xtreamObs = new MutationObserver(function(){ scan(); hideAds(); purgeAdTextOverlays(); });
                window.__xtreamObs.observe(document.documentElement, {childList:true, subtree:true});
              }
              boot();
              document.addEventListener('DOMContentLoaded', boot);
              setInterval(function(){ scan(); hideAds(); purgeAdTextOverlays(); injectXtreamPlayerOSD(); }, 800);
            })();
        """
    }
}
