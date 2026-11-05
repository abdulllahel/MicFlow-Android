package com.micflow.android

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Every dictation on this phone: when, what was typed, and how long you spoke. Kept only on the
 * phone, for Home's numbers and history (the same numbers as the Mac's Home).
 */
class DictationLog(context: Context) {
    data class Entry(val id: String, val date: Instant, val text: String, val words: Int, val seconds: Double) {
        val day: LocalDate get() = date.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    companion object {
        /** A typical typing speed, to work out the time voice typing saved. */
        const val TYPING_WPM = 40.0
        private const val LIMIT = 5000
        fun wordCount(text: String) = text.split(Regex("\\s+")).count { it.isNotEmpty() }
    }

    private val file = File(context.filesDir, "dictations.json")
    val entries = mutableStateListOf<Entry>()

    init {
        runCatching {
            JSONArray(file.readText()).objects().forEach {
                entries += Entry(it.getString("id"), Instant.ofEpochMilli(it.getLong("date")), it.getString("text"),
                                 it.getInt("words"), it.getDouble("seconds"))
            }
        }
    }

    fun add(text: String, seconds: Double) {
        val words = wordCount(text)
        if (words == 0) return
        entries += Entry(UUID.randomUUID().toString(), Instant.now(), text, words, seconds)
        if (entries.size > LIMIT) entries.removeRange(0, entries.size - LIMIT)
        save()
    }

    fun clear() { entries.clear(); save() }

    private fun save() {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("id", it.id).put("date", it.date.toEpochMilli()).put("text", it.text)
                                     .put("words", it.words).put("seconds", it.seconds)) }
        runCatching { file.writeText(array.toString()) }
    }

    // MARK: Numbers for Home

    val totalWords get() = entries.sumOf { it.words }

    fun words(on: LocalDate) = entries.filter { it.day == on }.sumOf { it.words }

    /** Words per minute of speaking, over every dictation. */
    val averageWPM: Int get() {
        val minutes = entries.sumOf { it.seconds } / 60
        return if (minutes > 0) Math.round(totalWords / minutes).toInt() else 0
    }

    /** Minutes saved compared with typing the same words. */
    val minutesSaved: Int get() {
        val speaking = entries.sumOf { it.seconds } / 60
        return maxOf(0, Math.round(totalWords / TYPING_WPM - speaking).toInt())
    }

    /** Days in a row, up to today (or yesterday, if nothing yet today), with at least one dictation. */
    val streak: Int get() {
        val days = entries.map { it.day }.toSet()
        var day = LocalDate.now()
        if (day !in days) day = day.minusDays(1)
        var count = 0
        while (day in days) { count++; day = day.minusDays(1) }
        return count
    }

    /** Words per day for the last seven days, oldest first. */
    val lastSevenDays: List<Pair<LocalDate, Int>> get() {
        val today = LocalDate.now()
        return (6 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to words(it) } }
    }

    /** The share of words in Bengali script, from 0 to 1. */
    val bengaliShare: Double get() {
        var bengali = 0
        var all = 0
        entries.forEach { entry ->
            entry.text.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
                all++
                if (word.any { it.code in 0x0980..0x09FF }) bengali++
            }
        }
        return if (all > 0) bengali.toDouble() / all else 0.0
    }
}
