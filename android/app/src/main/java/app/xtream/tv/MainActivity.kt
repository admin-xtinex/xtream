package app.xtream.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var pointer: ScreenPointer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val address = findViewById<EditText>(R.id.address)
        val notice = findViewById<TextView>(R.id.notice)
        val go = findViewById<Button>(R.id.go)
        findViewById<Button>(R.id.bookmarks).setOnClickListener {
            startActivity(
                Intent(this, BookmarksActivity::class.java)
                    .putExtra(BookmarksActivity.EXTRA_MODE, BookmarksActivity.MODE_BOOKMARKS)
            )
        }
        findViewById<Button>(R.id.history).setOnClickListener {
            startActivity(
                Intent(this, BookmarksActivity::class.java)
                    .putExtra(BookmarksActivity.EXTRA_MODE, BookmarksActivity.MODE_HISTORY)
            )
        }
        val submit = {
            val url = Urls.resolve(address.text.toString())
            if (url == null) {
                notice.text = getString(R.string.bad_address)
                notice.visibility = TextView.VISIBLE
            } else {
                notice.visibility = TextView.GONE
                open(url, address.text.toString().trim())
            }
        }
        go.setOnClickListener { submit() }
        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        val shortcuts = findViewById<LinearLayout>(R.id.shortcuts)
        val links = Suggested.load(this).ifEmpty {
            listOf(
                Entry("https://www.youtube.com", "YouTube"),
                Entry("https://www.wikipedia.org", "Wikipedia"),
                Entry("https://archive.org", "Internet Archive"),
                Entry("https://www.nasa.gov", "NASA"),
                Entry("https://www.twitch.tv", "Twitch"),
                Entry("https://www.reddit.com", "Reddit"),
            )
        }
        links.forEach { entry ->
            shortcuts.addView(tile(entry.title) { open(entry.url, entry.title) })
        }
        address.requestFocus()
        pointer = ScreenPointer(this)
        pointer.bind(findViewById(R.id.nav_mode))
        pointer.attach()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::pointer.isInitialized && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
            pointer.toggle()
            return true
        }
        if (::pointer.isInitialized && pointer.handle(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        val row = findViewById<LinearLayout>(R.id.recent)
        val label = findViewById<TextView>(R.id.recent_label)
        row.removeAllViews()
        val recent = Library.history(this)
        label.visibility = if (recent.isEmpty()) TextView.GONE else TextView.VISIBLE
        recent.forEach { entry ->
            row.addView(tile(entry.title) { open(entry.url, entry.title) })
        }
    }

    private fun open(url: String, title: String) {
        Library.visit(this, url, title)
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    private fun tile(label: String, onClick: () -> Unit): Button {
        val minW = resources.getDimensionPixelSize(R.dimen.tile_min_width)
        val minH = resources.getDimensionPixelSize(R.dimen.tile_min_height)
        return Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(0xFFF4F7FF.toInt())
            setBackgroundResource(R.drawable.bg_tile)
            minHeight = minH
            minWidth = minW
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gap.marginEnd = dp(10)
            layoutParams = gap
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
