package com.micflow.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/** Your word library: words that were heard are written your way ("figma" becomes "Figma"), as on the Mac. */
object MyWords {
    fun spell(text: String, words: List<String>): String = words.fold(text) { current, word ->
        val letters = word.filter { !it.isWhitespace() && it != '-' }
        if (letters.isEmpty()) return@fold current
        val pattern = "(?<![\\p{L}\\p{N}])" + letters.map { Regex.escape(it.toString()) }.joinToString("[\\s\\-]?") + "(?![\\p{L}\\p{N}])"
        runCatching { current.replace(Regex(pattern, RegexOption.IGNORE_CASE), Regex.escapeReplacement(word)) }.getOrDefault(current)
    }
}

/**
 * Model 2 on Android: the phone's own speech recognition. Free and needs no key, but it hears one
 * language at a time (chosen in the app). It stops after each pause, so it's started again until
 * MicFlow's own silence timer (or a tap) ends the dictation. Runs on the main thread.
 */
class Model2Listener(
    private val context: Context,
    private val language: String,
    private val onText: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var finals = ""
    private var interim = ""
    private var running = false

    val text: String get() = listOf(finals, interim).filter { it.isNotBlank() }.joinToString(" ").trim()

    companion object {
        fun isAvailable(context: Context) = SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun start(): Boolean {
        if (!isAvailable(context)) return false
        running = true
        listen()
        return true
    }

    private fun listen() {
        recognizer?.destroy()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            // Roughly -2 (quiet) to 10 dB (loud): turned into the bubble's 0 to 1 sound level.
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f) * 0.04f)
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                interim = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onText(text)
            }
            override fun onResults(results: Bundle?) {
                val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (best.isNotBlank()) finals = listOf(finals, best).filter { it.isNotBlank() }.joinToString(" ")
                interim = ""
                onText(text)
                if (running) listen()
            }
            override fun onError(error: Int) {
                Log.i(MicFlowService.TAG, "Model 2 error $error")
                when (error) {
                    // A pause with nothing new: keep listening until MicFlow's silence timer ends it.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (running) listen()
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Allow the microphone for MicFlow first (open the MicFlow app).")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        fail("Model 2 needs the internet for this language. Check your connection.")
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        fail("This phone's speech recognition doesn't have this language yet. Pick English, or use Model 1.")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> fail("The phone's speech recognition is busy. Try again in a moment.")
                    else -> if (running) fail("Model 2 stopped (error $error). Try again, or use Model 1.")
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        })
    }

    private fun fail(message: String) {
        if (!running) return
        running = false
        onError(message)
    }

    fun stop() {
        running = false
        recognizer?.let { runCatching { it.stopListening(); it.destroy() } }
        recognizer = null
    }
}
