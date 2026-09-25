package app.xtream.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BookmarksActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val list = findViewById<LinearLayout>(R.id.list)
        val empty = findViewById<TextView>(R.id.empty)
        list.removeAllViews()
        val items = Library.bookmarks(this)
        empty.visibility = if (items.isEmpty()) TextView.VISIBLE else TextView.GONE
        items.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                gap.bottomMargin = dp(10)
                layoutParams = gap
            }
            val open = Button(this).apply {
                text = entry.title
                textSize = 18f
                isAllCaps = false
                setTextColor(0xFFF4F7FF.toInt())
                setBackgroundResource(R.drawable.bg_tile)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minHeight = dp(64)
                setPadding(dp(18), dp(8), dp(18), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    startActivity(Intent(this@BookmarksActivity, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, entry.url))
                }
            }
            val remove = Button(this).apply {
                text = getString(R.string.remove)
                textSize = 16f
                isAllCaps = false
                setTextColor(0xFFF4F7FF.toInt())
                setBackgroundResource(R.drawable.bg_tile)
                minHeight = dp(64)
                val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                gap.marginStart = dp(10)
                layoutParams = gap
                setOnClickListener {
                    Library.removeBookmark(this@BookmarksActivity, entry.url)
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
