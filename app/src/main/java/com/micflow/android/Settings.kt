package com.micflow.android

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** A word the speaker says in a way that gets transcribed as another one (same as the Mac). */
data class MixUp(val heard: String, val meant: String, val meaning: String = "") {
    fun json(): JSONObject = JSONObject().put("id", java.util.UUID.randomUUID().toString().uppercase())
        .put("heard", heard).put("meant", meant).put("meaning", meaning)

    companion object {
        fun from(json: JSONObject) = MixUp(json.optString("heard"), json.optString("meant"), json.optString("meaning"))
    }
}

/**
 * Everything MicFlow keeps on the phone: the bubble's look and place, voice typing's options, and
 * the words and mix-ups. The API key is kept locked in [SecureStore], never here.
 */
object Settings {
    const val DEFAULT_SIZE = 46
    const val MIN_SIZE = 32
    const val MAX_SIZE = 72
    const val DEFAULT_OPACITY = 1f
    const val MIN_OPACITY = 0.3f

    private const val FILE = "micflow"
    private const val OLD_KEY = "apiKey"
    private const val X = "bubbleX"
    private const val Y = "bubbleY"
    const val SIZE = "bubbleSize"
    const val OPACITY = "bubbleOpacity"
    const val AUTO_STOP = "autoStopSeconds"
    const val POLISH = "polish"
    const val FIX_MIXUPS = "fixMixUps"
    const val ENGINE = "engine"
    const val MODEL2_LANGUAGE = "model2Language"
    const val WORDS = "myWords"
    const val MIXUPS = "mixUps"
    const val INSTRUCTIONS = "yourInstructions"
    const val INSTRUCTIONS_ON = "instructionsEnabled"
    const val KEY_REFUSED = "keyFreePlanOff"
    const val SKIP_GUIDE = "skipGoogleKeyGuide"
    const val DARK = "homeDarkTheme"

    /** The choices offered in the app; 0 means only stop when the bubble is tapped. */
    val AUTO_STOP_CHOICES = listOf(3, 5, 8, 0)

    enum class Engine(val title: String) { MODEL1("Model 1 (Recommended)"), MODEL2("Model 2") }

    /** Model 2 hears one language at a time. */
    enum class Language(val code: String, val title: String) { ENGLISH("en-US", "English"), BENGALI("bn-BD", "বাংলা Bengali") }

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // MARK: The key (locked in SecureStore)

    fun apiKey(context: Context): String? {
        // Older versions kept the key in plain storage: move it into the locked store once.
        prefs(context).getString(OLD_KEY, null)?.let {
            SecureStore.save(context, "apiKey", it)
            prefs(context).edit().remove(OLD_KEY).apply()
        }
        return SecureStore.load(context, "apiKey")?.takeIf { it.isNotBlank() }
    }

    fun saveApiKey(context: Context, key: String?) {
        SecureStore.save(context, "apiKey", key?.trim())
        // Tells the app's screens to look again.
        prefs(context).edit().putLong("keyChanged", System.currentTimeMillis()).apply()
    }

    // MARK: The bubble

    fun hasCustomPosition(context: Context) = prefs(context).contains(X)
    fun position(context: Context) = prefs(context).getInt(X, 0) to prefs(context).getInt(Y, 0)
    fun savePosition(context: Context, x: Int, y: Int) = prefs(context).edit().putInt(X, x).putInt(Y, y).apply()
    fun resetPosition(context: Context) = prefs(context).edit().remove(X).remove(Y).apply()

    fun size(context: Context) = prefs(context).getInt(SIZE, DEFAULT_SIZE)
    fun opacity(context: Context) = prefs(context).getFloat(OPACITY, DEFAULT_OPACITY)
    fun autoStopSeconds(context: Context) = prefs(context).getInt(AUTO_STOP, 5)

    // MARK: Voice typing

    fun polish(context: Context) = prefs(context).getBoolean(POLISH, true)
    fun fixMixUps(context: Context) = prefs(context).getBoolean(FIX_MIXUPS, true)
    fun engine(context: Context) = runCatching { Engine.valueOf(prefs(context).getString(ENGINE, null) ?: "MODEL1") }.getOrDefault(Engine.MODEL1)
    fun model2Language(context: Context) =
        runCatching { Language.valueOf(prefs(context).getString(MODEL2_LANGUAGE, null) ?: "ENGLISH") }.getOrDefault(Language.ENGLISH)
    fun instructions(context: Context) = prefs(context).getString(INSTRUCTIONS, "").orEmpty()
    fun instructionsEnabled(context: Context) = prefs(context).getBoolean(INSTRUCTIONS_ON, true)

    fun words(context: Context): List<String> =
        runCatching { JSONArray(prefs(context).getString(WORDS, "[]")).strings() }.getOrDefault(emptyList())

    fun saveWords(context: Context, words: List<String>) = prefs(context).edit().putString(WORDS, JSONArray(words).toString()).apply()

    fun mixUps(context: Context): List<MixUp> =
        runCatching { JSONArray(prefs(context).getString(MIXUPS, "[]")).objects().map(MixUp::from) }.getOrDefault(emptyList())

    fun saveMixUps(context: Context, list: List<MixUp>) =
        prefs(context).edit().putString(MIXUPS, JSONArray(list.map { it.json() }).toString()).apply()

    /** Google stopped accepting the saved key (its account's free plan is off). */
    fun keyRefused(context: Context) = prefs(context).getBoolean(KEY_REFUSED, false)
    fun setKeyRefused(context: Context, refused: Boolean) = prefs(context).edit().putBoolean(KEY_REFUSED, refused).apply()

    fun put(context: Context, name: String, value: Any) {
        prefs(context).edit().apply {
            when (value) {
                is Boolean -> putBoolean(name, value)
                is Int -> putInt(name, value)
                is Float -> putFloat(name, value)
                is String -> putString(name, value)
            }
        }.apply()
    }
}
