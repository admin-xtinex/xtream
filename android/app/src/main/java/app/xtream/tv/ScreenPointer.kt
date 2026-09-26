package app.xtream.tv

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout

/** D-pad pointer shared by every screen except the video player. */
class ScreenPointer(private val activity: Activity) {
    private val layer = PointerView(activity)
    private val step = 28f * activity.resources.displayMetrics.density
    private val edgeBand = 40f * activity.resources.displayMetrics.density
    private var enabled = NavMode.isOn(activity)
    private var suppressed = false
    private var x = 0f
    private var y = 0f
    private var placed = false
    private var refreshButton: (() -> Unit)? = null

    /** Window y where content stops being hidden behind a toolbar drawn over it. */
    var topInset: () -> Int = { 0 }

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
        if (scrollAtEdge(dx, dy)) return
        slide(dx, dy)
    }

    private fun slide(dx: Float, dy: Float) {
        val area = contentArea()
        x = (x + dx).coerceIn(area[0], area[2])
        y = (y + dy).coerceIn(area[1], area[3])
        refresh()
    }

    /** Left, top, right, bottom of the app's own content, in pointer-layer coordinates. */
    private fun contentArea(): FloatArray {
        val content = activity.findViewById<View>(android.R.id.content)
        if (content == null || content.width == 0 || content.height == 0) {
            return floatArrayOf(0f, 0f, layer.width.toFloat().coerceAtLeast(1f), layer.height.toFloat().coerceAtLeast(1f))
        }
        val box = IntArray(2)
        val origin = IntArray(2)
        content.getLocationInWindow(box)
        layer.getLocationInWindow(origin)
        val left = (box[0] - origin[0]).toFloat()
        val top = (box[1] - origin[1]).toFloat()
        return floatArrayOf(left, top, left + content.width - 1, top + content.height - 1)
    }

    /**
     * Pushing the pointer into the edge of something scrollable scrolls it instead,
     * so long pages and side columns can be read in Cursor mode. The pointer only
     * keeps moving once there is nothing left to scroll that way.
     */
    private fun scrollAtEdge(dx: Float, dy: Float): Boolean {
        val origin = IntArray(2)
        layer.getLocationInWindow(origin)
        val wx = x + origin[0]
        val wy = y + origin[1]
        val target = scrollableAt(activity.window.decorView, wx, wy, dx, dy) ?: return false
        val box = IntArray(2)
        target.getLocationInWindow(box)
        val top = maxOf(box[1], topInset()).toFloat()
        val band = edgeBand
        val atEdge = (dy < 0 && wy <= top + band) ||
            (dy > 0 && wy >= box[1] + target.height - band) ||
            (dx < 0 && wx <= box[0] + band) ||
            (dx > 0 && wx >= box[0] + target.width - band)
        if (!atEdge) return false
        if (target is WebView) {
            val fx = ((wx - box[0]) / target.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            val fy = ((wy - box[1]) / target.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            val sx = if (dx < 0) -1 else if (dx > 0) 1 else 0
            val sy = if (dy < 0) -1 else if (dy > 0) 1 else 0
            target.evaluateJavascript("($WEB_SCROLL)($fx,$fy,$sx,$sy)") { result ->
                if (result == "true") return@evaluateJavascript
                val direction = if (sx < 0 || sy < 0) -1 else 1
                val native = if (sy != 0) target.canScrollVertically(direction) else target.canScrollHorizontally(direction)
                when {
                    !native -> slide(dx, dy)
                    sy != 0 -> target.scrollBy(0, (target.height * 0.35f * direction).toInt())
                    else -> target.scrollBy((target.width * 0.35f * direction).toInt(), 0)
                }
            }
            return true
        }
        val direction = if (dx < 0 || dy < 0) -1 else 1
        if (dy != 0f) {
            target.scrollBy(0, (target.height * 0.35f * direction).toInt())
        } else {
            target.scrollBy((target.width * 0.35f * direction).toInt(), 0)
        }
        return true
    }

    /**
     * Follows the views under the point the way a touch would (topmost first) and
     * returns the deepest one on that path that can scroll the requested way.
     */
    private fun scrollableAt(view: View, wx: Float, wy: Float, dx: Float, dy: Float): View? {
        if (view === layer || view.visibility != View.VISIBLE) return null
        val box = IntArray(2)
        view.getLocationInWindow(box)
        if (wx < box[0] || wx >= box[0] + view.width || wy < box[1] || wy >= box[1] + view.height) return null
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child === layer || child.visibility != View.VISIBLE) continue
                val cb = IntArray(2)
                child.getLocationInWindow(cb)
                val hit = wx >= cb[0] && wx < cb[0] + child.width && wy >= cb[1] && wy < cb[1] + child.height
                if (!hit) continue
                scrollableAt(child, wx, wy, dx, dy)?.let { return it }
                break
            }
        }
        if (view is WebView) return view
        val direction = if (dx < 0 || dy < 0) -1 else 1
        val scrolls = if (dy != 0f) view.canScrollVertically(direction) else view.canScrollHorizontally(direction)
        return if (scrolls) view else null
    }

    private fun click() {
        placeIfNeeded()
        layer.visibility = View.INVISIBLE
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
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

    private companion object {
        /** Scrolls the column under (fx, fy), falling back to the page; answers whether anything moved. */
        const val WEB_SCROLL = """function(fx,fy,sx,sy){
          var w=window.innerWidth||document.documentElement.clientWidth;
          var h=window.innerHeight||document.documentElement.clientHeight;
          var x=Math.min(Math.max(fx*w,1),w-2),y=Math.min(Math.max(fy*h,1),h-2);
          var dx=Math.round(sx*w*0.35),dy=Math.round(sy*h*0.35);
          function room(e){
            if(!e||e.nodeType!==1)return false;
            var s=getComputedStyle(e);
            if(sy){
              if(e.scrollHeight<=e.clientHeight+1||!/(auto|scroll|overlay)/.test(s.overflowY))return false;
              return sy<0?e.scrollTop>0:e.scrollTop+e.clientHeight<e.scrollHeight-1;
            }
            if(e.scrollWidth<=e.clientWidth+1||!/(auto|scroll|overlay)/.test(s.overflowX))return false;
            return sx<0?e.scrollLeft>0:e.scrollLeft+e.clientWidth<e.scrollWidth-1;
          }
          function push(e){
            var t=e.scrollTop,l=e.scrollLeft;
            e.scrollTop=t+dy;e.scrollLeft=l+dx;
            return e.scrollTop!==t||e.scrollLeft!==l;
          }
          function chain(px,py){
            for(var e=document.elementFromPoint(px,py);e&&e!==document.documentElement;e=e.parentElement){
              if(e!==document.body&&room(e)&&push(e))return true;
            }
            return false;
          }
          if(chain(x,y))return true;
          var p=document.scrollingElement||document.documentElement;
          var t=p.scrollTop,l=p.scrollLeft;
          window.scrollTo(window.pageXOffset+dx,window.pageYOffset+dy);
          if(p.scrollTop!==t||p.scrollLeft!==l)return true;
          if(document.body&&room(document.body)&&push(document.body))return true;
          if(chain(x,h/2)||chain(w/2,h/2))return true;
          var best=null,area=0,all=document.querySelectorAll('div,main,section,article,ul');
          for(var i=0;i<all.length;i++){
            var r=all[i].getBoundingClientRect(),a=r.width*r.height;
            if(a>area&&r.bottom>0&&r.top<h&&room(all[i])){best=all[i];area=a;}
          }
          return !!(best&&push(best));
        }"""

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
