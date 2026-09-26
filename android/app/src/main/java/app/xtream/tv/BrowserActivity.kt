package app.xtream.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var chrome: View
    private lateinit var download: Button
    private lateinit var gate: View
    private lateinit var loadRing: View
    private lateinit var titleView: TextView
    private lateinit var save: Button
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var homeHost: String = ""
    @Volatile
    private var pageHost: String = ""
    private var pendingVideo: Pair<String, String?>? = null
    private var pendingMime: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        web = findViewById(R.id.web)
        chrome = findViewById(R.id.chrome)
        download = findViewById(R.id.download)
        gate = findViewById(R.id.gate)
        loadRing = findViewById(R.id.load_ring)
        titleView = findViewById(R.id.page_title)
        save = findViewById(R.id.save)
        val block = findViewById<Button>(R.id.block)
        val rotate = findViewById<Button>(R.id.rotate)
        val home = findViewById<Button>(R.id.home)
        val root = findViewById<FrameLayout>(R.id.root)
        val isTv = resources.getBoolean(R.bool.is_television)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.userAgentString = CHROME_AGENT
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
        web.setDownloadListener { url, agent, _, mime, _ -> saveVideoFile(url, agent, mime) }
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
                showDownload()
            }

            override fun onHideCustomView() {
                val view = customView ?: return
                (view.parent as? ViewGroup)?.removeView(view)
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                hideDownload()
                chrome.visibility = View.VISIBLE
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
        }
        web.webChromeClient = chromeClient
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return if (AdBlock.blocked(this@BrowserActivity, request.url, pageHost, request.isForMainFrame, request.requestHeaders)) {
                    AdBlock.emptyResponse(request.url)
                } else {
                    null
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (AdBlock.isUnsafe(request.url) || AdBlock.isDownload(request.url)) return true
                if (request.isForMainFrame && !sameSite(request.url.host) && AdBlock.isAd(this@BrowserActivity, request.url)) {
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                val uri = Uri.parse(url)
                val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
                if (host.isNotBlank() && !AdBlock.isAd(this@BrowserActivity, uri)) pageHost = host
                if (!sameSite(uri.host) && AdBlock.isAd(this@BrowserActivity, uri) && view.canGoBack()) {
                    view.stopLoading()
                    view.goBack()
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
        findViewById<Button>(R.id.video).setOnClickListener { saveCurrentVideo() }
        download.setOnClickListener { saveCurrentVideo() }
        home.setOnKeyListener { _, keyCode, event ->
            event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && web.requestFocus()
        }

        val start = intent.getStringExtra(EXTRA_URL)
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
    }

    private fun showDownload() {
        download.visibility = View.VISIBLE
        download.bringToFront()
    }

    private fun hideDownload() {
        download.visibility = View.GONE
    }

    private fun saveCurrentVideo() {
        web.evaluateJavascript(CURRENT_SRC) { raw ->
            val url = raw?.trim()?.removeSurrounding("\"")?.replace("\\/", "/")?.replace("\\\"", "\"").orEmpty()
            saveVideoFile(url, CHROME_AGENT, null)
        }
    }

    private fun saveVideoFile(raw: String, userAgent: String?, mime: String?) {
        val name = AdBlock.videoName(raw, mime)
        if (name == null) {
            android.widget.Toast.makeText(this, R.string.not_video, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingVideo = raw to userAgent
            pendingMime = mime
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 21)
            return
        }
        val manager = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
        val request = android.app.DownloadManager.Request(Uri.parse(raw))
            .setTitle(name)
            .setMimeType(mime?.takeIf { it.startsWith("video/") } ?: "video/mp4")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
        val agent = userAgent?.ifBlank { CHROME_AGENT } ?: CHROME_AGENT
        request.addRequestHeader("User-Agent", agent)
        android.webkit.CookieManager.getInstance().getCookie(raw)?.let { request.addRequestHeader("Cookie", it) }
        try {
            manager.enqueue(request)
            android.widget.Toast.makeText(this, getString(R.string.saving, name), android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, R.string.not_video, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val pending = pendingVideo ?: return
            saveVideoFile(pending.first, pending.second, pendingMime)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && web.scrollY == 0 && web.hasFocus()) {
            findViewById<Button>(R.id.home).requestFocus()
            return true
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

    private fun exitVideo() {
        if (customView != null) {
            (web.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }
        chrome.visibility = View.VISIBLE
        hideDownload()
        if (!resources.getBoolean(R.bool.is_television)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private inner class VideoBridge {
        @JavascriptInterface
        fun adsOn(): Boolean = AdBlock.enabled(this@BrowserActivity)

        @JavascriptInterface
        fun download(raw: String?) {
            val url = raw.orEmpty()
            runOnUiThread { saveVideoFile(url, CHROME_AGENT, null) }
        }

        @JavascriptInterface
        fun onVideoPlay() {
            runOnUiThread {
                chrome.visibility = View.GONE
                if (customView != null) showDownload()
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
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val CHROME_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
        private const val PAGE_HOOK = """
            (function(){
              if (window.__xtreamHook) return;
              window.__xtreamHook = true;
              var ads = true;
              try { ads = Xtream.adsOn(); } catch (e) {}
              window.__xtreamAds = ads;
              window.__xtreamIsAd = isAd;
              if (ads) {
                window.open = function(){ return null; };
              }
              var keys = ['doubleclick','googlesyndication','googleadservices','googletagservices','popads','popcash','exoclick','exosrv','trafficjunky','juicyads','adsterra','hilltopads','clickadu','monetag','propellerads','outbrain','taboola','revcontent','adnxs','adservice','adserver','adskeeper','magsrv','realsrv','tsyndicate','trafficstars','onclickads','popunder','clickaine','galaksion','admaven','highrevenue','pagead','securepubads','fundingchoices','imasdk','gampad','pubads','fwmrm','springserve','stickyadstv','lkqd','spotx','mgid.com'];
              function isAd(src){
                if (!src) return false;
                src = String(src).toLowerCase();
                for (var i = 0; i < keys.length; i++) {
                  if (src.indexOf(keys[i]) !== -1) return true;
                }
                return false;
              }
              function skipAd(v) {
                if (!v || v.__xtreamHold) return !!(v && v.__xtreamHold);
                var src = v.currentSrc || v.src || '';
                if (!isAd(src)) return false;
                v.__xtreamHold = true;
                try { v.muted = true; } catch (e) {}
                try { v.pause(); } catch (e) {}
                try {
                  if (v.__xtreamSrc) {
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
                  } else if (v.duration && isFinite(v.duration)) {
                    v.currentTime = v.duration;
                  }
                } catch (e3) {}
                setTimeout(function(){ v.__xtreamHold = false; }, 800);
                return true;
              }
              function hook(v){
                if (!v || v.__xtream) return;
                v.__xtream = true;
                var userSeek = false;
                v.addEventListener('pointerdown', function(){ userSeek = true; setTimeout(function(){ userSeek = false; }, 1200); });
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
                v.addEventListener('seeked', function(){
                  if (window.__xtreamAds === false || userSeek || v.__xtreamHold) return;
                  if (isAd(v.currentSrc || v.src || '')) {
                    skipAd(v);
                    return;
                  }
                  if ((v.__xtreamPos || 0) > 3 && v.currentTime < 1) {
                    v.__xtreamHold = true;
                    try { v.currentTime = v.__xtreamPos; } catch (e) {}
                    setTimeout(function(){ v.__xtreamHold = false; }, 500);
                  }
                });
                v.addEventListener('play', function(){
                  if (window.__xtreamAds !== false && skipAd(v)) return;
                  var box = v.getBoundingClientRect();
                  if (box.width < 200 && box.height < 120) return;
                  try {
                    if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
                    else if (v.requestFullscreen) v.requestFullscreen();
                  } catch (e) {}
                  try { Xtream.onVideoPlay(); } catch (e) {}
                  placeDownload();
                });
                v.addEventListener('ended', function(){
                  if (window.__xtreamAds !== false && isAd(v.currentSrc || v.src || '')) return;
                  try { Xtream.onVideoEnd(); } catch (e) {}
                });
              }
              function placeDownload(){
                if (!document.documentElement) return;
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
                var btn = document.getElementById('xtream-dl');
                if (!best) {
                  if (btn) btn.style.display = 'none';
                  return;
                }
                window.__xtreamVideo = best;
                if (!btn) {
                  btn = document.createElement('button');
                  btn.id = 'xtream-dl';
                  btn.type = 'button';
                  btn.textContent = 'Download';
                  btn.style.cssText = 'position:fixed;z-index:2147483646;border:0;border-radius:14px;background:#3ecbff;color:#041018;font:700 16px sans-serif;padding:12px 18px;min-height:48px;min-width:48px;';
                  btn.addEventListener('click', function(ev){
                    ev.preventDefault();
                    ev.stopPropagation();
                    var src = '';
                    try { src = window.__xtreamVideo.currentSrc || window.__xtreamVideo.src || ''; } catch (e) {}
                    try { Xtream.download(src); } catch (e2) {}
                  });
                  document.documentElement.appendChild(btn);
                }
                var box = best.getBoundingClientRect();
                btn.style.display = 'block';
                var width = btn.offsetWidth || 128;
                btn.style.left = Math.max(12, box.right - width - 16) + 'px';
                btn.style.top = Math.max(12, box.bottom - 64) + 'px';
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
                hidePlayerAds();
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
                if (!document.documentElement || window.__xtreamObs) return;
                window.__xtreamObs = new MutationObserver(function(){ scan(); hideAds(); });
                window.__xtreamObs.observe(document.documentElement, {childList:true, subtree:true});
              }
              boot();
              document.addEventListener('DOMContentLoaded', boot);
              setInterval(function(){ scan(); hideAds(); placeDownload(); }, 1000);
            })();
        """
        private const val CURRENT_SRC = """
            (function(){
              var best = '';
              var area = 0;
              var list = document.querySelectorAll('video');
              for (var i = 0; i < list.length; i++) {
                var v = list[i];
                var src = v.currentSrc || v.src || '';
                if (!src) continue;
                var box = v.getBoundingClientRect();
                var size = box.width * box.height;
                if (size >= area) { area = size; best = src; }
              }
              return best;
            })()
        """
    }
}
