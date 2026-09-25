package app.xtream.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
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
    private lateinit var titleView: TextView
    private lateinit var save: Button
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var homeHost: String = ""
    @Volatile
    private var pageHost: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        web = findViewById(R.id.web)
        chrome = findViewById(R.id.chrome)
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
        web.setDownloadListener { _, _, _, _, _ -> }
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
            }

            override fun onHideCustomView() {
                val view = customView ?: return
                (view.parent as? ViewGroup)?.removeView(view)
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
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
                    AdBlock.emptyResponse()
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
            }

            override fun onPageFinished(view: WebView, url: String) {
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
        if (!resources.getBoolean(R.bool.is_television)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private inner class VideoBridge {
        @JavascriptInterface
        fun adsOn(): Boolean = AdBlock.enabled(this@BrowserActivity)

        @JavascriptInterface
        fun onVideoPlay() {
            runOnUiThread {
                chrome.visibility = View.GONE
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
              if (ads) {
                window.open = function(){ return null; };
              }
              var keys = ['doubleclick','googlesyndication','googleadservices','googletagservices','popads','popcash','exoclick','exosrv','trafficjunky','juicyads','adsterra','hilltopads','clickadu','monetag','propellerads','outbrain','taboola','revcontent','adnxs','adservice','adserver','adskeeper','magsrv','realsrv','tsyndicate','trafficstars','onclickads','popunder','clickaine','galaksion','admaven','highrevenue','pagead','securepubads','fundingchoices','imasdk','mgid.com','adsterra','exoclick'];
              function isAd(src){
                if (!src) return false;
                src = String(src).toLowerCase();
                for (var i = 0; i < keys.length; i++) {
                  if (src.indexOf(keys[i]) !== -1) return true;
                }
                return false;
              }
              function hook(v){
                if (!v || v.__xtream) return;
                v.__xtream = true;
                v.addEventListener('play', function(){
                  var box = v.getBoundingClientRect();
                  if (box.width < 200 && box.height < 120) return;
                  try {
                    if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
                    else if (v.requestFullscreen) v.requestFullscreen();
                  } catch (e) {}
                  try { Xtream.onVideoPlay(); } catch (e) {}
                });
                v.addEventListener('ended', function(){
                  try { Xtream.onVideoEnd(); } catch (e) {}
                });
              }
              function scan(){
                if (!document.body) return;
                document.querySelectorAll('video').forEach(hook);
              }
              function hideAds(){
                if (window.__xtreamAds === false) return;
                if (!document.body) return;
                document.querySelectorAll('iframe,ins').forEach(function(node){
                  if (node.closest && node.closest('video')) return;
                  var src = node.src || node.getAttribute('src') || '';
                  if (isAd(src)) node.remove();
                });
              }
              function boot(){
                scan();
                hideAds();
                if (!document.documentElement || window.__xtreamObs) return;
                window.__xtreamObs = new MutationObserver(function(){ scan(); hideAds(); });
                window.__xtreamObs.observe(document.documentElement, {childList:true, subtree:true});
              }
              boot();
              document.addEventListener('DOMContentLoaded', boot);
              setInterval(function(){ scan(); hideAds(); }, 1000);
            })();
        """
    }
}
