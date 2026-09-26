package app.xtream.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BookmarksActivity : Activity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_BOOKMARKS = "bookmarks"
        const val MODE_HISTORY = "history"
    }

    private var mode = MODE_BOOKMARKS
    private lateinit var pointer: ScreenPointer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_BOOKMARKS
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
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
        val list = findViewById<LinearLayout>(R.id.list)
        val empty = findViewById<TextView>(R.id.empty)
        val titleView = findViewById<TextView>(R.id.title)
        val clearAll = findViewById<Button>(R.id.clear_all)

        val isHistory = mode == MODE_HISTORY
        titleView.text = getString(if (isHistory) R.string.history else R.string.bookmarks)
        empty.text = getString(if (isHistory) R.string.empty_history else R.string.empty_bookmarks)

        list.removeAllViews()
        val items = if (isHistory) Library.history(this) else Library.bookmarks(this)
        empty.visibility = if (items.isEmpty()) TextView.VISIBLE else TextView.GONE

        if (items.isNotEmpty()) {
            clearAll.visibility = TextView.VISIBLE
            clearAll.text = getString(if (isHistory) R.string.clear_history else R.string.remove)
            clearAll.setOnClickListener {
                if (isHistory) Library.clearHistory(this) else Library.clearBookmarks(this)
                onResume()
            }
        } else {
            clearAll.visibility = TextView.GONE
        }

        val itemMinH = resources.getDimensionPixelSize(R.dimen.tile_min_height)

        items.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val gap = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gap.bottomMargin = dp(8)
                layoutParams = gap
            }

            val host = Uri.parse(entry.url).host?.removePrefix("www.") ?: entry.url
            val open = Button(this).apply {
                text = "${entry.title}\n$host"
                textSize = 15f
                isAllCaps = false
                setTextColor(0xFFF4F7FF.toInt())
                setBackgroundResource(R.drawable.bg_tile)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minHeight = itemMinH
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    startActivity(
                        Intent(this@BookmarksActivity, BrowserActivity::class.java)
                            .putExtra(BrowserActivity.EXTRA_URL, entry.url)
                    )
                }
            }

            val remove = Button(this).apply {
                text = getString(R.string.remove)
                textSize = 14f
                isAllCaps = false
                setTextColor(0xFFFF6B9D.toInt())
                setBackgroundResource(R.drawable.bg_tile)
                minHeight = itemMinH
                val gap = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gap.marginStart = dp(8)
                layoutParams = gap
                setOnClickListener {
                    if (isHistory) {
                        Library.removeHistory(this@BookmarksActivity, entry.url)
                    } else {
                        Library.removeBookmark(this@BookmarksActivity, entry.url)
                    }
                    onResume()
                }
            }

            row.addView(open)
            row.addView(remove)
            list.addView(row)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
