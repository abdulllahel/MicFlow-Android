package com.micflow.android

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * MicFlow on Android, working like Gboard's voice typing: when a text field is selected, a small mic
 * bubble appears. Tap it and speak: your words are written into the field as you talk. It stops by
 * itself after a few seconds of silence (set in the app), or when you tap it again. Drag the bubble
 * anywhere and it stays there.
 */
class MicFlowService : AccessibilityService() {
    companion object {
        const val TAG = "MicFlow"
        /** RMS above this counts as speech. Phone mics read quiet: normal speech is about 0.015. */
        private const val SPEECH_LEVEL = 0.006f
    }

    private lateinit var windows: WindowManager
    private lateinit var top: TopBubble
    private var bubble: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var shown = false

    private var capture: AudioCapture? = null
    private var transcriber: LiveTranscriber? = null
    private var model2: Model2Listener? = null
    private var live: LiveField? = null
    private val recording get() = capture != null || model2 != null
    /** When this dictation started, for Home's speaking time. */
    private var startedAt = 0L
    /** When the voice was last heard, for stopping after silence. */
    private var lastSpeech = 0L

    /** Size and see-through changes in the app show at once. */
    private val settingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "bubbleSize" || key == "bubbleOpacity") applyLook()
    }

    override fun onServiceConnected() {
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        top = TopBubble(this, windows)
        Settings.prefs(this).registerOnSharedPreferenceChangeListener(settingsChanged)
        if (BuildConfig.DEBUG) registerTestHook()
    }

    /**
     * Debug builds only: `adb shell am broadcast -a com.micflow.android.TEST_TYPE --es text "…"`
     * types the text into the focused field the way a dictation does (growing live text, then the
     * final words), so typing can be tested without speaking.
     */
    private fun registerTestHook() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val text = intent.getStringExtra("text") ?: return
                val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                // What the field says about itself, for checking placeholder text.
                val info = if (node == null) "no focused field" else listOf(
                    "text=[${node.text}]", "hint=[${node.hintText}]", "showingHint=${node.isShowingHintText}",
                    "sel=${node.textSelectionStart},${node.textSelectionEnd}", "class=${node.className}",
                    "id=${node.viewIdResourceName}", "inputType=${node.inputType}", "desc=[${node.contentDescription}]",
                    "editable=${node.isEditable}", "multi=${node.isMultiLine}").joinToString("\n")
                java.io.File(filesDir, "test-field.txt").writeText(info)
                if (intent.getBooleanExtra("inspect", false)) return
                val record = java.io.File(filesDir, "test-field.txt")
                val field = LiveField.start(node) ?: run { record.appendText("\nno field"); return }
                record.appendText("\nwrite: " + field.write(text))
                node.refresh()
                record.appendText("\nafter: text=[${node.text}] sel=${node.textSelectionStart},${node.textSelectionEnd}")
            }
        }
        registerReceiver(receiver, android.content.IntentFilter("com.micflow.android.TEST_TYPE"), RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (recording) return
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        try {
            if (focused != null && focused.isEditable && !focused.isPassword) showBubble(focused) else hideBubble()
            if (BuildConfig.DEBUG) java.io.File(filesDir, "test-bubble.txt")
                .writeText("event=${event.eventType} focused=${focused?.viewIdResourceName} editable=${focused?.isEditable} shown=$shown")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) java.io.File(filesDir, "test-bubble.txt").writeText("error: $e")
        }
    }

    override fun onInterrupt() = cancelRecording()

    override fun onDestroy() {
        Settings.prefs(this).unregisterOnSharedPreferenceChangeListener(settingsChanged)
        cancelRecording()
        hideBubble()
        top.hide()
        super.onDestroy()
    }

    // MARK: Bubble

    private fun showBubble(node: AccessibilityNodeInfo) {
        val view = bubble ?: BubbleView(this).also { created ->
            created.onTap = { if (recording) finishRecording() else startRecording() }
            created.dragHandle.setOnTouchListener(DragListener())
            bubble = created
            applyLook()
        }
        val layout = params ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            params = it
        }
        placeBubble(view, layout, node)
        if (shown) windows.updateViewLayout(view, layout) else {
            windows.addView(view, layout)
            shown = true
        }
    }

    /** Where the user dragged it, if they did; otherwise inside the right end of the field. */
    private fun placeBubble(view: BubbleView, layout: WindowManager.LayoutParams, node: AccessibilityNodeInfo?) {
        if (Settings.hasCustomPosition(this)) {
            val (x, y) = Settings.position(this)
            layout.x = x
            layout.y = y
        } else if (node != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val size = view.dp(view.sizeDp)
            layout.x = (bounds.right - size - view.dp(6)).coerceAtLeast(0)
            layout.y = (bounds.centerY() - size / 2 - statusBarHeight()).coerceAtLeast(0)
        }
    }

    private fun hideBubble() {
        val view = bubble ?: return
        if (shown) {
            windows.removeView(view)
            shown = false
        }
    }

    private fun applyLook() {
        val view = bubble ?: return
        view.sizeDp = Settings.size(this)
        view.alpha = Settings.opacity(this)
        if (shown) params?.let { windows.updateViewLayout(view, it) }
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /** Dragging moves the bubble and remembers where it was put; a tap still presses it. */
    private inner class DragListener : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var originX = 0
        private var originY = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val layout = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    originX = layout.x; originY = layout.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!dragging && (abs(dx) > 12 || abs(dy) > 12)) dragging = true
                    if (dragging) {
                        layout.x = (originX + dx).toInt()
                        layout.y = (originY + dy).toInt()
                        bubble?.let { windows.updateViewLayout(it, layout) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    Settings.savePosition(this@MicFlowService, layout.x, layout.y)
                    Log.i(TAG, "Bubble moved to ${layout.x}, ${layout.y}")
                    dragging = false
                    return true
                }
            }
            return dragging
        }
    }

    // MARK: Recording

    private fun startRecording() {
        val app = MicFlowApp.shared
        // Voice typing (both models) needs a Google sign-in first, as on the Mac.
        if (!app.account.isSignedIn) return problem("Open MicFlow and sign in with Google to use voice typing.")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return problem("Open the MicFlow app and allow the microphone first.")
        }
        val view = bubble ?: return
        val engine = Settings.engine(this)
        val key = Settings.apiKey(this)
        if (engine == Settings.Engine.MODEL1) {
            if (key.isNullOrBlank()) return problem("Voice typing isn't set up yet. Open MicFlow to finish it (about a minute).")
            if (Settings.keyRefused(this)) return problem(FREE_PLAN_OFF)
        } else if (!Model2Listener.isAvailable(this)) {
            return problem("This phone has no speech recognition for Model 2. Use Model 1 in the MicFlow app.")
        }
        live = LiveField.start(findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
        lastSpeech = System.currentTimeMillis()
        startedAt = lastSpeech
        Log.i(TAG, "Recording starts with $engine")
        if (engine == Settings.Engine.MODEL2) {
            val listener = Model2Listener(this, Settings.model2Language(this).code,
                onText = { text -> showLive(text) },
                onLevel = { level -> heard(level) },
                onError = { message -> if (recording) { cancelRecording(); problem(message) } })
            model2 = listener
            if (!listener.start()) { model2 = null; return problem("Model 2 couldn't start. Try again, or use Model 1.") }
            view.state = BubbleView.State.RECORDING
            return
        }
        val transcription = LiveTranscriber(key!!,
            onText = { text -> showLive(text) },
            onError = { message ->
                if (message == FREE_PLAN_OFF) MicFlowApp.shared.keyWasRefused()
                if (recording) { cancelRecording(); problem(message) }
            })
        val mic = AudioCapture { pcm, level ->
            transcription.append(pcm, level)
            view.post { heard(level) }
        }
        if (!mic.start()) {
            transcription.cancel()
            return problem("Couldn't open the microphone. Is another app using it?")
        }
        transcriber = transcription
        capture = mic
        view.state = BubbleView.State.RECORDING
    }

    /** Moves the sound wave, and stops after the chosen seconds of silence, like Gboard. */
    private fun heard(level: Float) {
        if (!recording) return
        bubble?.addLevel(level)
        val now = System.currentTimeMillis()
        if (level > SPEECH_LEVEL) lastSpeech = now
        val seconds = Settings.autoStopSeconds(this)
        if (seconds > 0 && now - lastSpeech > seconds * 1000L) {
            Log.i(TAG, "Stopping after $seconds s of silence")
            finishRecording()
        }
    }

    /** Your words so far: in the text field if it accepts them, otherwise in the top bubble. */
    private fun showLive(text: String) {
        if (!recording) return
        val field = live
        if (field != null && field.write(text)) return
        live = null
        top.show(text)
    }

    /** Stops and keeps exactly what's shown, right away. */
    private fun finishRecording() {
        val heardText = transcriber?.text ?: model2?.text ?: return
        val text = MyWords.spell(heardText, Settings.words(this))
        val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
        val field = live
        stop()
        Log.i(TAG, "Done: ${text.length} characters")
        live = null
        if (text.isBlank()) {
            top.hide()
            return problem("Didn't catch that. Try again a little closer to the mic.")
        }
        // The field didn't take live text: try once more now that typing has stopped.
        val written = field?.takeIf { it.write(text) }
            ?: LiveField.start(findFocus(AccessibilityNodeInfo.FOCUS_INPUT))?.takeIf { it.write(text) }
        if (written == null) {
            MicFlowApp.shared.log.add(text, seconds)
            return problem("Couldn't type in this field. Your words: $text")
        }
        top.hide()
        polish(written, text, seconds)
    }

    /**
     * Swaps the raw words for a polished version once it's back (about a second), but only if the
     * field still holds exactly what MicFlow typed: if you've edited or sent it, it's left alone.
     */
    private fun polish(field: LiveField, raw: String, seconds: Double) {
        val key = Settings.apiKey(this)
        if (!Settings.polish(this) || key.isNullOrBlank()) {
            MicFlowApp.shared.log.add(raw, seconds)
            return
        }
        val view = bubble
        view?.polishing = true
        val instructions = Polisher.instructions(
            if (Settings.fixMixUps(this)) Settings.mixUps(this) else emptyList(),
            if (Settings.instructionsEnabled(this)) Settings.instructions(this) else "")
        Polisher.polish(key, raw, instructions) { answer ->
            view?.polishing = false
            val polished = answer?.let { MyWords.spell(it, Settings.words(this)) }
            when {
                polished == null || polished == raw -> Log.i(TAG, "Kept the raw text")
                field.replace(raw, polished) -> Log.i(TAG, "Replaced with the polished text")
                else -> Log.i(TAG, "The field changed meanwhile; kept it as it is")
            }
            MicFlowApp.shared.log.add(polished ?: raw, seconds)
        }
    }

    /** Stops without changing anything more, when the service is interrupted or the connection fails. */
    private fun cancelRecording() {
        if (!recording) return
        stop()
        live = null
        top.hide()
    }

    private fun stop() {
        capture?.stop()
        capture = null
        transcriber?.cancel()
        transcriber = null
        model2?.stop()
        model2 = null
        val view = bubble ?: return
        view.state = BubbleView.State.IDLE
        params?.let { layout ->
            placeBubble(view, layout, findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
            if (shown) windows.updateViewLayout(view, layout)
        }
    }

    /** Shown at the top of the screen: Android hides pop-up messages from apps in the background. */
    private fun problem(message: String) {
        Log.w(TAG, message)
        bubble?.flash()
        top.show(message, seconds = 5)
    }

    /**
     * The text field being dictated into: what was there before, and where the cursor was. Each
     * update rewrites the field as before-cursor + words + after-cursor, with the cursor after the
     * words, like Gboard's voice typing.
     */
    private class LiveField(
        private val node: AccessibilityNodeInfo,
        private val before: String,
        private val after: String,
    ) {
        companion object {
            /** Each field's hint, remembered from when it had real text (WhatsApp only says it then). */
            private val knownHints = mutableMapOf<String, String>()

            /**
             * Whether the field's "text" is really its grey placeholder. Most apps say so, but some
             * (WhatsApp's "Message") report the placeholder as the text, with no cursor in it.
             */
            private fun showsPlaceholder(node: AccessibilityNodeInfo, text: String): Boolean {
                if (node.isShowingHintText) return true
                val hint = node.hintText?.toString()
                val id = node.viewIdResourceName
                if (!hint.isNullOrEmpty() && id != null) knownHints[id] = hint
                if (text.isEmpty()) return false
                if (text == hint) return true
                // No cursor in a focused field that has "text": it's a placeholder, if it's the hint
                // seen before or a short one-line label (a real text always has a cursor).
                val noCursor = node.textSelectionStart < 0 && node.textSelectionEnd < 0
                return noCursor && (text == knownHints[id] || (text.length <= 40 && '\n' !in text))
            }

            fun start(node: AccessibilityNodeInfo?): LiveField? {
                if (node == null || !node.isEditable) return null
                node.refresh()
                val raw = node.text?.toString().orEmpty()
                val existing = if (showsPlaceholder(node, raw)) "" else raw
                val start = node.textSelectionStart.takeIf { it in 0..existing.length } ?: existing.length
                val end = node.textSelectionEnd.takeIf { it in start..existing.length } ?: start
                // A space between the text already there and the new words.
                val before = existing.substring(0, start).let { if (it.isNotEmpty() && !it.last().isWhitespace()) "$it " else it }
                return LiveField(node, before, existing.substring(end))
            }
        }

        fun write(words: String): Boolean = set(before + words + after, (before + words).length)

        /** Replaces [raw] with [polished], only if the field still reads exactly as MicFlow left it. */
        fun replace(raw: String, polished: String): Boolean {
            node.refresh()
            if (node.text?.toString() != before + raw + after) return false
            return write(polished)
        }

        private fun set(text: String, cursor: Int): Boolean {
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            })
            if (ok) node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            })
            return ok
        }
    }
}
