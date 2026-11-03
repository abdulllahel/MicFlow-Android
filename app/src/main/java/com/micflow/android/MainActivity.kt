package com.micflow.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * MicFlow's setup screen: three steps (API key, microphone, turn on the bubble), each showing
 * whether it's done, and a box to try voice typing in.
 */
class MainActivity : Activity() {
    private val forest = Color.parseColor("#2B5036")
    private val card = Color.parseColor("#335E41")
    private val cream = Color.parseColor("#FBEBC9")
    private val pink = Color.parseColor("#F66399")
    private val yellow = Color.parseColor("#FFB413")
    private val ink = Color.parseColor("#141414")

    private lateinit var keyStatus: TextView
    private lateinit var keyField: EditText
    private lateinit var micStatus: TextView
    private lateinit var serviceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = forest
        window.navigationBarColor = forest
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(28))
        }

        // The yellow hero, like the Mac app.
        column.addView(card(yellow) {
            addView(text("MicFlow", 30f, ink, bold = true))
            addView(text("Voice typing anywhere on your phone, with no word limit. Speak English, Bengali or both.", 15f, ink))
        })

        column.addView(card(card) {
            addView(text("1  Your API key", 17f, Color.WHITE, bold = true))
            keyStatus = text("", 14f, cream)
            addView(keyStatus)
            keyField = EditText(this@MainActivity).apply {
                hint = "Paste your key here"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setTextColor(Color.WHITE)
                setHintTextColor(Color.argb(140, 255, 255, 255))
            }
            addView(keyField)
            addView(row(
                button("Save key", cream, ink) {
                    val key = keyField.text.toString()
                    if (key.isNotBlank()) { Settings.saveApiKey(this@MainActivity, key); keyField.setText(""); refresh() }
                },
                button("Get a free key", Color.TRANSPARENT, cream) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                },
            ))
        })

        column.addView(card(card) {
            addView(text("2  Allow the microphone", 17f, Color.WHITE, bold = true))
            micStatus = text("", 14f, cream)
            addView(micStatus)
            addView(button("Allow microphone", cream, ink) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            })
        })

        column.addView(card(card) {
            addView(text("3  Turn on the MicFlow bubble", 17f, Color.WHITE, bold = true))
            serviceStatus = text("", 14f, cream)
            addView(serviceStatus)
            addView(text("In the list that opens, tap Installed apps (or Downloaded apps) › MicFlow voice typing, and turn it on. " +
                "If Android says it's a restricted setting: open Settings › Apps › MicFlow, tap ⋮ at the top right, choose " +
                "Allow restricted settings, and try again.", 13f, Color.argb(200, 255, 255, 255)))
            addView(row(
                button("Open Accessibility settings", cream, ink) { startActivity(Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS)) },
                button("App settings", Color.TRANSPARENT, cream) {
                    startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                },
            ))
        })

        column.addView(card(card) {
            addView(text("Bubble size and look", 17f, Color.WHITE, bold = true))
            addView(slider("Size", Settings.MIN_SIZE, Settings.MAX_SIZE, Settings.size(this@MainActivity),
                format = { "$it" }) { Settings.saveSize(this@MainActivity, it) })
            val opacity = (Settings.opacity(this@MainActivity) * 100).toInt()
            addView(slider("See-through", (Settings.MIN_OPACITY * 100).toInt(), 100, opacity,
                format = { if (it >= 100) "off (solid)" else "${100 - it}%" }) { Settings.saveOpacity(this@MainActivity, it / 100f) })
            addView(text("Changes show on the bubble right away. Drag the bubble to move it: it stays where you put it, in every app.",
                13f, Color.argb(200, 255, 255, 255)))
            addView(Switch(this@MainActivity).apply {
                text = "Polish after speaking"
                setTextColor(cream)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                isChecked = Settings.polish(this@MainActivity)
                setPadding(0, dp(12), 0, 0)
                setOnCheckedChangeListener { _, on -> Settings.savePolish(this@MainActivity, on) }
            })
            addView(text("Your words appear as you speak; a second after you stop, they're swapped for a cleaned-up version " +
                "(punctuation, spelling, no \"um\"). Same words, same language.", 13f, Color.argb(200, 255, 255, 255)))
            addView(text("Stop listening after silence", 14f, cream).apply { setPadding(0, dp(12), 0, dp(2)) })
            addView(autoStopChoices())
            addView(text("Or tap the bubble again to stop at any time.", 13f, Color.argb(200, 255, 255, 255)))
            addView(button("Put the bubble back next to text fields", Color.TRANSPARENT, cream) {
                Settings.resetPosition(this@MainActivity)
            })
        })

        column.addView(card(Color.WHITE) {
            addView(text("Try it here", 17f, ink, bold = true))
            addView(text("Tap the box, then the green mic bubble, and speak: your words appear in the box as you talk. Stop talking and it stops by itself, or tap the bubble again.", 13f, Color.DKGRAY))
            addView(EditText(this@MainActivity).apply {
                hint = "Your words will appear here"
                minLines = 3
                gravity = Gravity.TOP
                setTextColor(ink)
            })
        })

        val scroll = ScrollView(this).apply { setBackgroundColor(forest); addView(column) }
        // Android draws apps under the status and navigation bars: keep the content clear of them.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                view.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun refresh() {
        val hasKey = !Settings.apiKey(this).isNullOrBlank()
        keyStatus.text = if (hasKey) "✓ Key saved on this phone." else "Needed for voice typing. It's free."
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.text = if (mic) "✓ Allowed." else "MicFlow needs to hear you."
        serviceStatus.text = if (serviceOn()) "✓ On. Tap any text field to see the bubble." else "Off."
        serviceStatus.setTextColor(if (serviceOn()) cream else pink)
    }

    /** Read from Android's list of turned-on accessibility services, which apps can always see. */
    private fun serviceOn(): Boolean =
        SystemSettings.Secure.getString(contentResolver, SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.startsWith("$packageName/") }

    // MARK: Building blocks

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun card(color: Int, content: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(12) }
        content()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(title: String, fill: Int, textColor: Int, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setTextColor(textColor)
        background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(99).toFloat()
            if (fill == Color.TRANSPARENT) setStroke(dp(1), Color.argb(90, 255, 255, 255))
        }
        setPadding(dp(16), 0, dp(16), 0)
        setOnClickListener { action() }
    }

    /** Pills for the silence choices; the chosen one is cream. */
    private fun autoStopChoices(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
        fun build() {
            row.removeAllViews()
            val chosen = Settings.autoStopSeconds(this)
            Settings.AUTO_STOP_CHOICES.forEachIndexed { index, seconds ->
                val selected = seconds == chosen
                val title = if (seconds == 0) "Never" else "$seconds s"
                row.addView(button(title, if (selected) cream else Color.TRANSPARENT, if (selected) ink else cream) {
                    Settings.saveAutoStopSeconds(this, seconds)
                    build()
                }.apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (index > 0) leftMargin = dp(6)
                })
            }
        }
        build()
        return row
    }

    /** A label, its current value, and a slider. */
    private fun slider(label: String, min: Int, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit): View {
        val title = text("", 14f, cream)
        fun show(v: Int) { title.text = "$label: ${format(v)}" }
        show(value)
        val bar = SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    show(progress)
                    if (fromUser) onChange(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(2))
            addView(title)
            addView(bar)
        }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, 0)
        views.forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
    }
}
