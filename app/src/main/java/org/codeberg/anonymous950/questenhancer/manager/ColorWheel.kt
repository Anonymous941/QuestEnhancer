package org.codeberg.anonymous950.questenhancer.manager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * A hue/saturation wheel: hue around the circle, white in the middle, full colour at the
 * edge. Value (brightness) is always full; an LED's brightness is its own setting.
 */
class ColorWheel(context: Context) : View(context) {
    /** Called with the picked colour while the pointer moves on the wheel. */
    var onColor: ((Int) -> Unit)? = null

    private val hsv = floatArrayOf(0f, 0f, 1f)
    private val wheel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        color = Color.BLACK
    }
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    var color: Int
        get() = Color.HSVToColor(hsv)
        set(value) {
            Color.colorToHSV(value, hsv)
            hsv[2] = 1f
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f - ring.strokeWidth * 2
        val hues = IntArray(13) { Color.HSVToColor(floatArrayOf((it * 30f) % 360f, 1f, 1f)) }
        // SweepGradient starts at 3 o'clock and turns clockwise on screen; touch maps the same way.
        val sweep = SweepGradient(cx, cy, hues, null)
        val white = RadialGradient(cx, cy, radius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        wheel.shader = ComposeShader(sweep, white, PorterDuff.Mode.SRC_OVER)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (280 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius, wheel)
        val angle = Math.toRadians(hsv[0].toDouble())
        val r = hsv[1] * radius
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        ring.color = Color.BLACK
        canvas.drawCircle(x, y, ring.strokeWidth * 3, ring)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val dx = event.x - cx
                val dy = event.y - cy
                var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                if (deg < 0) deg += 360f
                hsv[0] = deg
                hsv[1] = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                hsv[2] = 1f
                invalidate()
                onColor?.invoke(color)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
