package app.xtream.tv

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout

/** D-pad pointer shared by every screen except the video player. */
class ScreenPointer(private val activity: Activity) {
    private val layer = PointerView(activity)
    private val step = 28f * activity.resources.displayMetrics.density
    private var enabled = NavMode.isOn(activity)
    private var suppressed = false
    private var x = 0f
    private var y = 0f
    private var placed = false
    private var refreshButton: (() -> Unit)? = null

    fun attach() {
        val decor = activity.window.decorView as ViewGroup
        if (layer.parent == null) {
            decor.addView(
                layer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        layer.isClickable = false
        layer.isFocusable = false
        layer.setOnTouchListener { _, _ -> false }
        refresh()
    }

    fun isActive(): Boolean = enabled && !suppressed

    fun setSuppressed(value: Boolean) {
        if (suppressed == value) return
        suppressed = value
        refresh()
    }

    fun toggle() {
        enabled = !enabled
        NavMode.set(activity, enabled)
        if (enabled) placed = false
        refresh()
    }

    fun bind(button: Button) {
        refreshButton = {
            val on = enabled
            button.text = activity.getString(if (on) R.string.remote else R.string.cursor)
            button.visibility = if (suppressed) View.GONE else View.VISIBLE
            button.setBackgroundResource(if (on) R.drawable.bg_go else R.drawable.bg_tile)
            button.setTextColor(if (on) 0xFF041018.toInt() else 0xFFF4F7FF.toInt())
        }
        button.setOnClickListener { toggle() }
        refresh()
    }

    fun handle(event: KeyEvent): Boolean {
        if (!isActive()) return false
        val code = event.keyCode
        val arrows = code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER
        if (!arrows) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        placeIfNeeded()
        val boost = 1f + event.repeatCount.coerceAtMost(10) * 0.4f
        val distance = step * boost
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> move(0f, -distance)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0f, distance)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-distance, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(distance, 0f)
            else -> click()
        }
        return true
    }

    private fun placeIfNeeded() {
        if (placed || layer.width == 0 || layer.height == 0) return
        x = layer.width / 2f
        y = layer.height / 2f
        placed = true
    }

    private fun move(dx: Float, dy: Float) {
        placeIfNeeded()
        val limitX = layer.width.toFloat().coerceAtLeast(1f)
        val limitY = layer.height.toFloat().coerceAtLeast(1f)
        x = (x + dx).coerceIn(0f, limitX)
        y = (y + dy).coerceIn(0f, limitY)
        refresh()
    }

    private fun click() {
        placeIfNeeded()
        layer.visibility = View.INVISIBLE
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_UP, x, y, 0)
        activity.dispatchTouchEvent(down)
        activity.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        layer.visibility = View.VISIBLE
        refresh()
    }

    private fun refresh() {
        if (!placed && layer.width > 0 && layer.height > 0) {
            x = layer.width / 2f
            y = layer.height / 2f
            placed = true
        }
        layer.showPointer = isActive()
        layer.px = x
        layer.py = y
        layer.invalidate()
        refreshButton?.invoke()
    }

    private class PointerView(context: Context) : View(context) {
        var showPointer = false
        var px = 0f
        var py = 0f
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ECBFF.toInt() }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
        }

        init {
            setWillNotDraw(false)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = false

        override fun onDraw(canvas: Canvas) {
            if (!showPointer) return
            val density = resources.displayMetrics.density
            ring.strokeWidth = 3f * density
            canvas.drawCircle(px, py, 7f * density, fill)
            canvas.drawCircle(px, py, 14f * density, ring)
        }
    }
}

private object NavMode {
    private const val PREF = "xtream"
    private const val KEY = "cursor_mode"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
