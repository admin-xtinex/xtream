package app.xtream.tv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

class BrowserActivity : Activity() {
    private lateinit var web: GeckoView
    private lateinit var chrome: View
    private lateinit var titleView: TextView
    private lateinit var save: Button
    private var session: GeckoSession? = null
    private var pageUrl: String? = null
    private var pageTitle: String? = null
    private var canGoBack = false
    private var fullScreen = false
    private var pendingVideo: Pair<String, String?>? = null
    private var pendingMime: String? = null

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
        val isTv = resources.getBoolean(R.bool.is_television)

        home.setOnClickListener { finish() }
        block.setOnClickListener {
            AdBlock.setEnabled(this, !AdBlock.enabled(this))
            refreshBlock(block)
            pageUrl?.let { openSession(it) }
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
            val url = pageUrl ?: return@setOnClickListener
            val title = pageTitle?.ifBlank { url } ?: url
            Library.toggleBookmark(this, url, title)
            refreshSave()
        }
        findViewById<Button>(R.id.video).setOnClickListener {
            saveVideoFile(pageUrl.orEmpty(), null, null)
        }
        home.setOnKeyListener { _, keyCode, event ->
            event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && web.requestFocus()
        }

        val start = intent.getStringExtra(EXTRA_URL)
        if (start.isNullOrBlank()) {
            finish()
            return
        }
        titleView.text = start
        openSession(start)
        web.requestFocus()
    }

    private fun openSession(url: String) {
        val next = GeckoSession(
            GeckoSessionSettings.Builder()
                .useTrackingProtection(AdBlock.enabled(this))
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .allowJavascript(true)
                .suspendMediaWhenInactive(false)
                .build(),
        )
        next.navigationDelegate = navigation
        next.progressDelegate = progress
        next.contentDelegate = content
        next.open(Engine.runtime(this))
        web.releaseSession()?.close()
        web.setSession(next)
        session = next
        next.loadUri(url)
    }

    private val navigation = object : GeckoSession.NavigationDelegate {
        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny> {
            val uri = Uri.parse(request.uri)
            val allow = !AdBlock.isUnsafe(uri) && !AdBlock.isDownload(uri)
            return GeckoResult.fromValue(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
        }

        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            pageUrl = url
            runOnUiThread {
                if (pageTitle.isNullOrBlank()) titleView.text = url
                refreshSave()
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            this@BrowserActivity.canGoBack = canGoBack
        }
    }

    private val progress = object : GeckoSession.ProgressDelegate {
        override fun onPageStop(session: GeckoSession, success: Boolean) {
            val url = pageUrl ?: return
            val title = pageTitle?.ifBlank { url } ?: url
            runOnUiThread {
                titleView.text = title
                Library.visit(this@BrowserActivity, url, title)
                refreshSave()
            }
        }
    }

    private val content = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            pageTitle = title
            runOnUiThread {
                titleView.text = title?.ifBlank { pageUrl } ?: pageUrl
                refreshSave()
            }
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            runOnUiThread {
                this@BrowserActivity.fullScreen = fullScreen
                chrome.visibility = if (fullScreen) View.GONE else View.VISIBLE
                if (!resources.getBoolean(R.bool.is_television)) {
                    requestedOrientation = if (fullScreen) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    }
                }
            }
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            val mime = response.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
            val uri = response.uri
            response.body?.close()
            runOnUiThread { saveVideoFile(uri, null, mime) }
        }
    }

    private fun refreshBlock(block: Button) {
        val on = AdBlock.enabled(this)
        block.text = getString(if (on) R.string.blocking else R.string.ads)
        block.setBackgroundResource(if (on) R.drawable.bg_go else R.drawable.bg_tile)
        block.setTextColor(if (on) 0xFF041018.toInt() else 0xFFF4F7FF.toInt())
    }

    private fun refreshSave() {
        val url = pageUrl
        val saved = url != null && Library.isBookmarked(this, url)
        save.text = getString(if (saved) R.string.saved else R.string.save)
    }

    private fun saveVideoFile(raw: String, userAgent: String?, mime: String?) {
        val name = AdBlock.videoName(raw, mime)
        if (name == null) {
            Toast.makeText(this, R.string.not_video, Toast.LENGTH_SHORT).show()
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
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        try {
            manager.enqueue(request)
            Toast.makeText(this, getString(R.string.saving, name), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.not_video, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val pending = pendingVideo ?: return
            saveVideoFile(pending.first, pending.second, pendingMime)
        }
    }

    @Deprecated("Activity back is the TV remote Back key")
    override fun onBackPressed() {
        if (fullScreen) {
            session?.exitFullScreen()
            return
        }
        if (canGoBack) session?.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.releaseSession()?.close()
        session = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
