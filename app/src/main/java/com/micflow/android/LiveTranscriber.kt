package com.micflow.android

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Live speech-to-text over a WebSocket, the same setup as MicFlow on the Mac: English and Bengali
 * (mixed is fine), "smart" mode that drops filler words and adds punctuation. Audio streams while
 * you speak, and [text] is always the words so far; there's no waiting at the end.
 *
 * Callbacks run on the main thread.
 */
class LiveTranscriber(key: String, private val onText: (String) -> Unit, private val onError: (String) -> Unit) {
    companion object {
        private const val MODEL = "gemini-3.5-transcribe-live"
        private const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    private val main = Handler(Looper.getMainLooper())
    private val socket: WebSocket
    private val waiting = mutableListOf<String>()
    private var ready = false
    private var closed = false
    private var failed: String? = null

    // Finished segments, and the current guess at the one being spoken.
    private var finals = ""
    private var interim = ""

    val text: String get() = spaceSentences(join(finals, interim)).trim()

    init {
        val request = Request.Builder().url("$ENDPOINT?key=$key").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { handle(text) }
            }

            // The service sends its JSON replies as binary frames.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                main.post { handle(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { fail(describe(t, response)) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (!closed && code != 1000) fail(if (reason.isNotBlank()) reason else "The connection closed ($code).") }
            }
        })
        // Only English and Bengali. With no list, Bengali after English can come back as Hindi.
        val transcription = JSONObject()
            .put("languageCodes", JSONArray(listOf("en-US", "bn-BD")))
            .put("mode", "SMART")
        val setup = JSONObject().put("setup", JSONObject()
            .put("model", "models/$MODEL")
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray(listOf("TEXT"))))
            .put("inputAudioTranscription", transcription))
        socket.send(setup.toString())
    }

    /** Called from the audio thread. */
    fun append(pcm: ByteArray, level: Float) {
        main.post {
            if (closed) return@post
            val audio = JSONObject()
                .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
                .put("mimeType", "audio/pcm;rate=16000")
            send(JSONObject().put("realtimeInput", JSONObject().put("audio", audio)).toString())
        }
    }

    fun cancel() = close()

    private fun send(message: String) {
        if (ready) socket.send(message) else waiting.add(message)
    }

    private fun handle(message: String) {
        // Updates can still arrive after recording stopped; they must not show up anywhere.
        if (closed) return
        val json = try { JSONObject(message) } catch (_: Exception) { return }
        if (json.has("setupComplete")) {
            Log.i(MicFlowService.TAG, "Speech service ready")
            ready = true
            waiting.forEach { socket.send(it) }
            waiting.clear()
        }
        json.optJSONObject("error")?.let { fail(it.optString("message", "The speech service returned an error.")) }
        val content = json.optJSONObject("serverContent") ?: return
        content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
            finals = join(finals, it)
            interim = ""
            onText(text)
        }
        content.optJSONObject("interimInputTranscription")?.let {
            interim = it.optString("text")
            onText(text)
        }
    }

    private fun fail(message: String) {
        Log.w(MicFlowService.TAG, "Speech service problem: $message")
        if (failed == null) {
            failed = message
            onError(hideProvider(message))
        }
    }

    /**
     * MicFlow doesn't say which company's models it uses. The service's own messages (a refused key,
     * a used-up quota) can name it, so those names are taken out before they're shown.
     */
    private fun hideProvider(message: String): String = listOf(
        "generativelanguage\\.googleapis\\.com" to "the service",
        "(Google )?AI Studio" to "your provider's dashboard",
        "Generative Language API" to "the API",
        "Gemini API" to "the API",
        "gemini-[0-9a-z.\\-]+" to "the model",
        "Gemini" to "the model",
        "Google Cloud" to "the provider",
        "Google" to "the provider",
    ).fold(message) { text, (pattern, name) -> text.replace(Regex(pattern, RegexOption.IGNORE_CASE), name) }

    private fun close() {
        if (closed) return
        closed = true
        socket.close(1000, null)
    }

    private fun describe(t: Throwable, response: Response?): String = when (response?.code) {
        400, 401, 403 -> "Your API key wasn't accepted. Check it in the MicFlow app."
        else -> "Couldn't reach the speech service: ${t.message ?: "no connection"}"
    }

    /** Joins pieces of text with a space, unless one is already there. */
    private fun join(a: String, b: String): String = when {
        a.isEmpty() || b.isEmpty() -> a + b
        a.last().isWhitespace() || b.first().isWhitespace() -> a + b
        else -> "$a $b"
    }

    /** Segments can arrive glued together ("help.But"): a space after a sentence end before a capital or Bengali. */
    private fun spaceSentences(text: String) = text.replace(Regex("(?<=[.?!।])(?=[A-Z\\u0980-\\u09FF])"), " ")
}
