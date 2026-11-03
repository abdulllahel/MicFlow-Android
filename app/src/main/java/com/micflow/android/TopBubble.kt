package com.micflow.android

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * A small chat bubble at the top of the screen, two lines at most. Shows the words as you speak
 * when an app doesn't let MicFlow write into its text field, and short messages when something
 * goes wrong.
 */
class TopBubble(private val context: Context, private val windows: WindowManager) {
    private val view = TextView(context).apply {
        setTextColor(Color.parseColor("#FBEBC9"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.START
        val pad = dp(14)
        setPadding(pad, dp(10), pad, dp(10))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#F02B5036"))
            cornerRadius = dp(18).toFloat()
        }
        elevation = dp(6).toFloat()
    }
    private var shown = false
    private val hide = Runnable { hide() }

    fun show(text: String, seconds: Int = 0) {
        view.removeCallbacks(hide)
        view.text = text
        if (!shown) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP
                horizontalMargin = 0.04f
                y = dp(48)
            }
            windows.addView(view, params)
            shown = true
        }
        if (seconds > 0) view.postDelayed(hide, seconds * 1000L)
    }

    fun hide() {
        view.removeCallbacks(hide)
        if (shown) {
            windows.removeView(view)
            shown = false
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
