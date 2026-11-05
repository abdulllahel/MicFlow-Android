package com.micflow.android

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Checks a key before it's saved: Google accepts it, and its free plan is on (same as the Mac). */
object KeyCheck {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    /** Null when the key works; otherwise why not, in plain words. */
    suspend fun problem(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val list = Request.Builder().url("$BASE/models?pageSize=1").header("x-goog-api-key", key).build()
            client.newCall(list).execute().use { r ->
                if (!r.isSuccessful) {
                    val message = runCatching { JSONObject(r.body?.string().orEmpty()).getJSONObject("error").optString("message") }.getOrNull()
                    return@withContext hideProvider(message?.ifEmpty { null } ?: "This key wasn't accepted.")
                }
            }
            // One tiny request: a key whose account has the free plan off lists models but refuses this.
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Hi")))))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 1))
            val use = Request.Builder().url("$BASE/models/${Polisher.MODEL}:generateContent").header("x-goog-api-key", key)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(use).execute().use { r ->
                if (r.code == 403 && isFreePlanOff(r.body?.string().orEmpty())) return@withContext FREE_PLAN_OFF
            }
            null
        } catch (e: IOException) {
            "MicFlow couldn't reach the provider. Check your internet connection."
        }
    }
}

/**
 * "Sign in with Google" when there's no key yet: Google's sign-in, then the key, and (when Google
 * asks) the terms, which MicFlow keeps checking by itself. The same steps and states as the Mac's.
 */
class KeySetupFlow(private val app: MicFlowApp) {
    sealed class Stage {
        object Idle : Stage()
        data class Working(val text: String) : Stage()
        object NeedsTerms : Stage()
        object MissingPermission : Stage()
        data class Failed(val text: String) : Stage()
        /** Google won't make (or allow) a key for this account right now. */
        object FreePlanOff : Stage()
        object Done : Stage()
    }

    var stage by mutableStateOf<Stage>(Stage.Idle)
        private set
    var hasFailed by mutableStateOf(false)
        private set
    var failureDetail by mutableStateOf<String?>(null)
        private set
    var showGuide by mutableStateOf(false)
    var email by mutableStateOf<String?>(null)
        private set
    var isChecking by mutableStateOf(false)
        private set
    var isWatching by mutableStateOf(false)
        private set
    var termsID by mutableStateOf<String?>(null)
        private set
    var acceptedTerms by mutableIntStateOf(0)
        private set
    var justAccepted by mutableStateOf(false)
        private set

    private var token: String? = null
    private var tokenExpires = 0L
    private var project: String? = null
    private var termsUrl: Uri = KeyProvisioner.termsPage
    private var watch: Job? = null
    private var wakeNow = false

    private fun set(newStage: Stage) {
        stage = newStage
        when (newStage) {
            is Stage.Failed, Stage.FreePlanOff, Stage.MissingPermission -> hasFailed = true
            Stage.Done -> hasFailed = false
            else -> Unit
        }
    }

    val hasValidToken get() = token != null && tokenExpires - System.currentTimeMillis() > 60_000

    /** The button: the guide first (unless turned off), then Google. */
    fun begin() {
        if (Settings.prefs(app.context).getBoolean(Settings.SKIP_GUIDE, false)) continueToGoogle() else showGuide = true
    }

    fun continueToGoogle() {
        showGuide = false
        stop()
        set(Stage.Idle)
        app.signIn(setUpKey = true)
    }

    fun showStep(text: String) { stop(); set(Stage.Working(text)) }

    fun finishedWithSavedKey() { stop(); set(Stage.Done) }

    fun reset() { stop(); set(Stage.Idle); justAccepted = false }

    /** The sign-in finished; [googleToken] is null when Google didn't hand over access. */
    fun received(googleToken: String?) {
        stop()
        if (googleToken == null) { set(Stage.Failed("Google didn't give MicFlow access to set up the key. Try again.")); return }
        token = googleToken
        tokenExpires = System.currentTimeMillis() + 50 * 60_000
        project = null
        termsID = null
        acceptedTerms = 0
        appScope.launch { firstRun() }
    }

    private suspend fun firstRun() {
        val current = token ?: return
        set(Stage.Working("Checking what Google allowed…"))
        val info = KeyProvisioner.tokenInfo(current)
        if (info != null) {
            email = info.email ?: app.account.user?.email
            info.expiresIn?.let { tokenExpires = System.currentTimeMillis() + it * 1000 }
            if (BackendConfig.CLOUD_SCOPE !in info.scopes) { set(Stage.MissingPermission); return }
        } else {
            email = app.account.user?.email
        }
        attempt()
    }

    /** One try at making the key. Returns whether to keep checking. */
    private suspend fun attempt(): Boolean {
        val current = token
        if (current == null || !hasValidToken) { stop(); return false }
        isChecking = true
        try {
            val showProgress = stage != Stage.NeedsTerms
            val key = KeyProvisioner.createKey(current, project) { step -> if (showProgress) appScope.launch { set(Stage.Working(step)) } }
            set(Stage.Working("Checking your new key…"))
            val problem = app.saveKeyChecked(key)
            if (problem != null) {
                stop()
                set(if (problem == FREE_PLAN_OFF) Stage.FreePlanOff else Stage.Failed("The new key didn't work yet ($problem). Try again in a minute."))
                return false
            }
            set(Stage.Done)
            stop()
            app.afterKeySaved()
            return false
        } catch (failure: KeyProvisioner.Failure) {
            if (failure.needsTerms) {
                project = failure.project ?: project
                termsUrl = failure.fix ?: KeyProvisioner.termsPage
                val id = failure.termsID ?: "cloud"
                val previous = termsID
                if (stage == Stage.NeedsTerms && previous != null && previous != id) {
                    // Step 2 went through and Google wants step 3: open it at once (they look alike).
                    acceptedTerms += 1
                    termsID = id
                    justAccepted = true
                    app.openUrl(termsPage)
                }
                termsID = id
                if (stage != Stage.NeedsTerms) { set(Stage.NeedsTerms); startWatching() }
                return true
            }
            failureDetail = failure.message
            set(Stage.FreePlanOff)
            stop()
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (stage == Stage.NeedsTerms) return true
            set(Stage.Failed("MicFlow couldn't reach Google. Check your internet connection and try again."))
            stop()
            return false
        } catch (e: Exception) {
            failureDetail = e.message
            set(Stage.FreePlanOff)
            stop()
            return false
        } finally {
            isChecking = false
        }
    }

    // MARK: Terms

    val termsPage: Uri get() = KeyProvisioner.page(termsUrl, email)

    fun openTerms() {
        justAccepted = false
        app.openUrl(termsPage)
        if (!isWatching) startWatching()
    }

    /** "I've accepted": check right now. */
    fun confirmAccepted() {
        justAccepted = false
        if (!hasValidToken) { continueToGoogle(); return }
        if (isWatching) wakeNow = true else startWatching(immediately = true)
    }

    /** Coming back to MicFlow from the browser checks straight away. */
    fun appResumed() { if (isWatching) wakeNow = true }

    /** Every 5 s for 2 minutes, then every 20 s, for 15 minutes in all. */
    private fun startWatching(immediately: Boolean = false) {
        watch?.cancel()
        isWatching = true
        val started = System.currentTimeMillis()
        val hadKey = app.hasKey()
        watch = appScope.launch {
            var first = true
            while (true) {
                if (!hadKey && app.hasKey()) { set(Stage.Idle); stop(); return@launch }
                val elapsed = System.currentTimeMillis() - started
                if (elapsed > 15 * 60_000) break
                if (!(first && immediately)) {
                    val until = System.currentTimeMillis() + if (elapsed < 120_000) 5_000 else 20_000
                    while (System.currentTimeMillis() < until && !wakeNow) delay(250)
                }
                wakeNow = false
                first = false
                if (!attempt()) return@launch
            }
            isWatching = false
        }
    }

    private fun stop() {
        watch?.cancel()
        watch = null
        isWatching = false
    }
}
