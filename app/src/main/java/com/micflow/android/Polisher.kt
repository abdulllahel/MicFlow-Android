package com.micflow.android

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cleans up dictated text after you stop speaking, with a fast text model: punctuation, capitals,
 * spelling, obvious grammar, and no filler words or false starts. It keeps your words, meaning and
 * language. [done] gets the polished text, or null if it failed or was too slow (the raw text then
 * stays). Runs on the main thread.
 */
object Polisher {
    /**
     * Both are asked at once, with thinking kept minimal, and the first answer wins: their speed
     * varies a lot with how busy the service is (from under a second to several).
     */
    /** The fastest text model, also used to check that a new key works. */
    const val MODEL = "gemini-3.5-flash-lite"
    private val MODELS = listOf(MODEL, "gemini-3.5-flash")
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())

    /** What MicFlow always tells the model, then your mix-ups and your own instructions (same as the Mac). */
    fun instructions(mixUps: List<MixUp>, yours: String): String {
        var text = """You correct the text of a voice dictation. The speaker is from Bangladesh and talks in English, Bengali, or both mixed in one sentence. Speech recognition often mishears their accent, so some words come out as other words that sound alike.

Do this:
- Read the whole text and work out what the speaker meant. Where a word doesn't fit the sentence but a similar-sounding word does, replace it. For example "I want to it food" -> "I want to eat food", "Please send me the fail" -> "Please send me the file".
- Fix punctuation, capital letters, spelling and obvious grammar mistakes.
- Remove filler words (um, uh, er), false starts and accidentally repeated words.

Strict limits:
- Change only single words or short phrases. Never rewrite, reorder, merge or split sentences, and never change what a sentence means.
- If you're not sure a word is wrong, leave it as it is.
- Keep each language as spoken: Bengali stays in Bengali script, English stays English. Never translate.
- Don't summarize, shorten, add anything, or answer questions in the text: it's a message the speaker is writing to someone else.
- If the text is already fine, return it unchanged."""
        if (mixUps.isNotEmpty()) {
            val list = mixUps.joinToString("\n") { m -> "- \"${m.meant}\"${if (m.meaning.isEmpty()) "" else " (${m.meaning})"} can come out as \"${m.heard}\"." }
            text += "\n\nThe speaker pronounces some words so that they come out as another word:\n$list\nReplace such a word only when the sentence clearly means the other one; if both readings make sense, leave it."
        }
        val own = yours.trim()
        if (own.isNotEmpty()) text += "\n\nThe speaker's own instructions. Follow them, except where they conflict with the strict limits above:\n${own.take(2000)}"
        return text + "\n\nAnswer at once, without deliberating: speed matters. Reply with the corrected text only."
    }

    fun polish(key: String, text: String, instructions: String, done: (String?) -> Unit) {
        var answered = false
        var failures = 0
        val calls = mutableListOf<Call>()
        MODELS.forEach { model ->
            calls += ask(key, text, instructions, model) { polished ->
                if (answered) return@ask
                if (polished != null) {
                    answered = true
                    calls.forEach { it.cancel() }
                    done(polished)
                } else if (++failures == MODELS.size) {
                    done(null)
                }
            }
        }
    }

    /** One model's answer, or null; [result] runs on the main thread. */
    private fun ask(key: String, text: String, instructions: String, model: String, result: (String?) -> Unit): Call {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("generationConfig", JSONObject()
                .put("maxOutputTokens", 2048)
                .put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal")))
        val request = Request.Builder()
            .url("$BASE/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val started = System.currentTimeMillis()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = fail("failed: ${e.message}")

            override fun onResponse(call: Call, response: Response) {
                val polished = response.use { r ->
                    if (!r.isSuccessful) null else parse(r.body?.string().orEmpty())
                }
                if (polished.isNullOrBlank()) return fail("HTTP ${response.code}")
                Log.i(MicFlowService.TAG, "Polished in ${System.currentTimeMillis() - started} ms with $model")
                main.post { result(polished) }
            }

            private fun fail(reason: String) {
                if (!call.isCanceled()) Log.w(MicFlowService.TAG, "Polishing with $model $reason")
                main.post { result(null) }
            }
        })
        return call
    }

    private fun parse(json: String): String? = try {
        val parts = JSONObject(json).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
    } catch (_: Exception) {
        null
    }
}
