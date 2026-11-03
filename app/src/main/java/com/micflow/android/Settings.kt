package com.micflow.android

import android.content.Context
import android.content.SharedPreferences

/** The API key and the bubble's position, size and see-through level, kept in the app's private storage. */
object Settings {
    const val DEFAULT_SIZE = 46
    const val MIN_SIZE = 32
    const val MAX_SIZE = 72
    const val DEFAULT_OPACITY = 1f
    const val MIN_OPACITY = 0.3f

    private const val FILE = "micflow"
    private const val KEY = "apiKey"
    private const val X = "bubbleX"
    private const val Y = "bubbleY"
    private const val SIZE = "bubbleSize"
    private const val OPACITY = "bubbleOpacity"
    private const val AUTO_STOP = "autoStopSeconds"
    private const val POLISH = "polish"
    /** The choices offered in the app; 0 means only stop when the bubble is tapped. */
    val AUTO_STOP_CHOICES = listOf(3, 5, 8, 0)

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String? = prefs(context).getString(KEY, null)
    fun saveApiKey(context: Context, key: String) = prefs(context).edit().putString(KEY, key.trim()).apply()

    /** Once the bubble has been dragged, it stays where it was put, in every app. */
    fun hasCustomPosition(context: Context) = prefs(context).contains(X)
    fun position(context: Context) = prefs(context).getInt(X, 0) to prefs(context).getInt(Y, 0)
    fun savePosition(context: Context, x: Int, y: Int) = prefs(context).edit().putInt(X, x).putInt(Y, y).apply()
    fun resetPosition(context: Context) = prefs(context).edit().remove(X).remove(Y).apply()

    /** The mic button's diameter, in dp. */
    fun size(context: Context) = prefs(context).getInt(SIZE, DEFAULT_SIZE)
    fun saveSize(context: Context, dp: Int) = prefs(context).edit().putInt(SIZE, dp).apply()

    /** Seconds of silence before listening stops by itself; 0 = only when the bubble is tapped. */
    fun autoStopSeconds(context: Context) = prefs(context).getInt(AUTO_STOP, 5)
    fun saveAutoStopSeconds(context: Context, seconds: Int) = prefs(context).edit().putInt(AUTO_STOP, seconds).apply()

    /** Whether to swap the raw words for a polished version after you stop speaking. */
    fun polish(context: Context) = prefs(context).getBoolean(POLISH, true)
    fun savePolish(context: Context, on: Boolean) = prefs(context).edit().putBoolean(POLISH, on).apply()

    /** 1 is solid; lower is more see-through. */
    fun opacity(context: Context) = prefs(context).getFloat(OPACITY, DEFAULT_OPACITY)
    fun saveOpacity(context: Context, value: Float) = prefs(context).edit().putFloat(OPACITY, value).apply()
}
