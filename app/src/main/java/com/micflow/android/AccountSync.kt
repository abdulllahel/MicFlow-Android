package com.micflow.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The account's copy of your voice typing setup, shared with the Mac app: the API key (locked, see
 * [KeyVault]), your words, mix-ups and instructions, and clean-up on or off. The phone's own
 * settings (bubble, silence timer, which Model) are saved under "android" in the same settings list,
 * so they come back after a reinstall; the Mac's settings in that list are left exactly as they are.
 */
object AccountSync {
    data class Saved(val apiKey: String?, val hadSettings: Boolean)

    /** Settings the Mac and the phone share (same names as the Mac's). */
    private const val SHARED_POLISH = "voiceTypingPolish"
    private const val SHARED_FIX = "voiceTypingFixMixUps"

    /** What the phone keeps under settings.android. */
    private val phoneKeys = listOf(Settings.SIZE, Settings.OPACITY, Settings.AUTO_STOP, Settings.ENGINE, Settings.MODEL2_LANGUAGE,
                                   Settings.DARK, Settings.SKIP_GUIDE)

    /** Brings the account's setup onto the phone. Words and mix-ups are added to the phone's own. */
    suspend fun load(context: Context, account: Account): Saved? {
        val token = account.token() ?: return null
        val id = account.user?.id ?: return null
        val rows = JSONArray(Backend.request("GET", "/rest/v1/profiles", listOf(
            "id" to "eq.$id", "select" to "instructions,instructions_enabled,my_words,mix_ups,api_key,settings"), token = token))
        if (rows.length() == 0) return Saved(null, false)
        val row = rows.getJSONObject(0)
        val instructions = row.optString("instructions")
        if (instructions.isNotEmpty()) Settings.put(context, Settings.INSTRUCTIONS, instructions)
        if (row.has("instructions_enabled")) Settings.put(context, Settings.INSTRUCTIONS_ON, row.optBoolean("instructions_enabled", true))
        row.optJSONArray("my_words")?.strings()?.let { words ->
            val mine = Settings.words(context).toMutableList()
            words.filter { w -> mine.none { it.equals(w, ignoreCase = true) } }.forEach { mine += it }
            Settings.saveWords(context, mine)
        }
        row.optJSONArray("mix_ups")?.objects()?.map(MixUp::from)?.let { theirs ->
            val mine = Settings.mixUps(context).toMutableList()
            theirs.filter { t -> mine.none { it.heard == t.heard && it.meant == t.meant } }.forEach { mine += it }
            Settings.saveMixUps(context, mine)
        }
        val settings = row.optJSONObject("settings")
        settings?.let {
            if (it.has(SHARED_POLISH)) Settings.put(context, Settings.POLISH, it.optBoolean(SHARED_POLISH, true))
            if (it.has(SHARED_FIX)) Settings.put(context, Settings.FIX_MIXUPS, it.optBoolean(SHARED_FIX, true))
            it.optJSONObject("android")?.let { phone ->
                phoneKeys.filter(phone::has).forEach { key ->
                    when (val value = phone.get(key)) {
                        is Boolean -> Settings.put(context, key, value)
                        is Int -> Settings.put(context, key, value)
                        is Double -> Settings.put(context, key, if (key == Settings.OPACITY) value.toFloat() else value.toInt())
                        is String -> Settings.put(context, key, value)
                    }
                }
            }
        }
        val key = row.optString("api_key").takeIf { it.isNotEmpty() && it != "null" }?.let { KeyVault.open(it, id) }
        return Saved(key, settings?.has("android") == true)
    }

    /** What to do with the account's copy of the key. */
    sealed class KeyUpload { data class Set(val key: String) : KeyUpload(); object Clear : KeyUpload(); object Keep : KeyUpload() }

    /** Saves the phone's setup to the account, keeping every setting the Mac saved. */
    suspend fun save(context: Context, account: Account, key: KeyUpload) {
        val token = account.token() ?: return
        val user = account.user ?: return
        val existing = runCatching {
            JSONArray(Backend.request("GET", "/rest/v1/profiles", listOf("id" to "eq.${user.id}", "select" to "settings"), token = token))
                .optJSONObject(0)?.optJSONObject("settings")
        }.getOrNull() ?: JSONObject()
        val phone = JSONObject()
        val prefs = Settings.prefs(context)
        phoneKeys.forEach { name -> prefs.all[name]?.let { phone.put(name, it) } }
        existing.put("android", phone)
        existing.put(SHARED_POLISH, Settings.polish(context))
        existing.put(SHARED_FIX, Settings.fixMixUps(context))
        val row = JSONObject()
            .put("id", user.id).put("email", user.email).put("name", user.name)
            .put("instructions", Settings.instructions(context))
            .put("instructions_enabled", Settings.instructionsEnabled(context))
            .put("my_words", JSONArray(Settings.words(context)))
            .put("mix_ups", JSONArray(Settings.mixUps(context).map { it.json() }))
            .put("settings", existing)
            .put("updated_at", java.time.Instant.now().toString())
        when (key) {
            is KeyUpload.Set -> row.put("api_key", KeyVault.seal(key.key, user.id) ?: JSONObject.NULL)
            KeyUpload.Clear -> row.put("api_key", JSONObject.NULL)
            KeyUpload.Keep -> Unit
        }
        Backend.request("POST", "/rest/v1/profiles", json = JSONArray().put(row), token = token,
                        prefer = "resolution=merge-duplicates,return=minimal")
    }
}
