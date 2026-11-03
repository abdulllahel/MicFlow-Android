package com.micflow.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout

/**
 * The floating mic, Gboard-style: one round button that stays the same size. Green with a mic when
 * idle; pink with a live sound wave while listening. Tap to start, tap again to stop. Its size and
 * see-through level come from the app's settings; drag it to move it.
 */
class BubbleView(context: Context) : FrameLayout(context) {
    enum class State { IDLE, RECORDING }

    var onTap: () -> Unit = {}

    private val button = MicButton(context)

    /** Drag the button to move the bubble. */
    val dragHandle: View get() = button

    /** The button's diameter, in dp. */
    var sizeDp = Settings.DEFAULT_SIZE
        set(value) {
            field = value
            button.layoutParams = LayoutParams(dp(value), dp(value))
            requestLayout()
        }

    var state = State.IDLE
        set(value) {
            field = value
            button.listening = value == State.RECORDING
        }

    init {
        button.setOnClickListener { onTap() }
        addView(button, LayoutParams(dp(sizeDp), dp(sizeDp)))
    }

    /** A thin spinning ring while the words are being polished. */
    var polishing: Boolean
        get() = button.polishing
        set(value) { button.polishing = value }

    fun addLevel(level: Float) = button.add(level)

    /** A brief flash, when something went wrong. */
    fun flash() = button.flash()

    fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class MicButton(context: Context) : View(context) {
        companion object {
            private val FOREST = Color.parseColor("#2B5036")
            private val CREAM = Color.parseColor("#FBEBC9")
            private val PINK = Color.parseColor("#F66399")
            private val ERROR = Color.parseColor("#B3141414")
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        /** Recent loudness, 0 to 1, one per bar. */
        private val levels = FloatArray(5)
        private var flashing = false

        var listening = false
            set(value) {
                field = value
                levels.fill(0f)
                invalidate()
            }

        var polishing = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            elevation = resources.displayMetrics.density * 4
            isClickable = true
        }

        fun add(rms: Float) {
            System.arraycopy(levels, 1, levels, 0, levels.size - 1)
            // A decibel scale, like the ear's: silence 0, loud speech 1. Phone mics read quiet.
            val db = 20 * kotlin.math.log10(maxOf(rms, 1e-6f))
            levels[levels.size - 1] = ((db + 60f) / 40f).coerceIn(0f, 1f)
            invalidate()
        }

        fun flash() {
            flashing = true
            invalidate()
            postDelayed({ flashing = false; invalidate() }, 900)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val u = minOf(w, h) / 24f
            paint.style = Paint.Style.FILL
            paint.color = when {
                flashing -> ERROR
                listening -> PINK
                else -> FOREST
            }
            canvas.drawCircle(w / 2, h / 2, minOf(w, h) / 2, paint)
            if (polishing && !listening) {
                // A cream arc going round the edge, a turn every second.
                val angle = (System.currentTimeMillis() % 1000) * 360f / 1000f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f * u
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = CREAM
                val inset = 1.2f * u
                canvas.drawArc(RectF(inset, inset, w - inset, h - inset), angle, 100f, false, paint)
                paint.style = Paint.Style.FILL
                postInvalidateOnAnimation()
            }
            paint.color = if (listening) Color.WHITE else CREAM
            if (listening) {
                // Five bars, the middle ones taller, moving with your voice.
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 2.2f * u
                val shape = floatArrayOf(0.55f, 0.85f, 1f, 0.85f, 0.55f)
                for (i in levels.indices) {
                    val x = w / 2 + (i - 2) * 3.6f * u
                    val half = (1.2f + levels[i] * 7f * shape[i]) * u
                    canvas.drawLine(x, h / 2 - half, x, h / 2 + half, paint)
                }
            } else {
                canvas.drawRoundRect(RectF(w / 2 - 3.2f * u, h / 2 - 7.5f * u, w / 2 + 3.2f * u, h / 2 + 2.5f * u), 3.2f * u, 3.2f * u, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.8f * u
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(RectF(w / 2 - 5.6f * u, h / 2 - 4f * u, w / 2 + 5.6f * u, h / 2 + 5f * u), 0f, 180f, false, paint)
                canvas.drawLine(w / 2, h / 2 + 5f * u, w / 2, h / 2 + 7.5f * u, paint)
            }
        }
    }
}
