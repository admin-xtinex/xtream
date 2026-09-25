package app.xtream.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val address = findViewById<EditText>(R.id.address)
        val notice = findViewById<TextView>(R.id.notice)
        val go = findViewById<Button>(R.id.go)
        findViewById<Button>(R.id.bookmarks).setOnClickListener {
            startActivity(Intent(this, BookmarksActivity::class.java))
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
        listOf(
            "Wikipedia" to "https://www.wikipedia.org",
            "Internet Archive" to "https://archive.org",
            "NASA" to "https://www.nasa.gov",
            "Example" to "https://example.com",
        ).forEach { (label, url) ->
            shortcuts.addView(tile(label) { open(url, label) })
        }
        address.requestFocus()
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
        return Button(this).apply {
            text = label
            textSize = 18f
            isAllCaps = false
            setTextColor(0xFFF4F7FF.toInt())
            setBackgroundResource(R.drawable.bg_tile)
            minHeight = dp(72)
            minWidth = dp(180)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gap.marginEnd = dp(12)
            layoutParams = gap
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
