package com.micflow.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The app: one account, one key setup, one dictation log, shared by the screens and the bubble. */
class MicFlowApp : Application() {
    companion object {
        lateinit var shared: MicFlowApp
            private set
    }

    val context: Context get() = this
    lateinit var account: Account
        private set
    lateinit var keySetup: KeySetupFlow
        private set
    lateinit var log: DictationLog
        private set
    lateinit var site: SiteContentStore
        private set
    lateinit var requests: FeatureRequestStore
        private set
    lateinit var updater: Updater
        private set

    /** The screen in front, for opening Google's sign-in. */
    var activity: Activity? = null

    /** Bumped whenever a setting or the key changes, so screens read them again. */
    var revision by mutableIntStateOf(0)
        private set
    var hasKeyState by mutableStateOf(false)
        private set
    var keyRefusedState by mutableStateOf(false)
        private set

    private var syncJob: Job? = null
    private var keyRemovedByYou = false
    private var restoring = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        revision++
        refreshKeyState()
        if (key != null && key !in setOf("bubbleX", "bubbleY", "keyChanged")) scheduleSync()
    }

    override fun onCreate() {
        super.onCreate()
        shared = this
        account = Account(this)
        keySetup = KeySetupFlow(this)
        log = DictationLog(this)
        site = SiteContentStore(this)
        requests = FeatureRequestStore(account)
        updater = Updater(this)
        refreshKeyState()
        Settings.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        account.onSignedIn = { token, setUpKey -> signedIn(token, setUpKey) }
        account.onSignedOut = {
            keySetup.reset()
            // The key leaves this phone; the account keeps its locked copy for the next sign-in.
            Settings.saveApiKey(this, null)
            Settings.setKeyRefused(this, false)
            refreshKeyState()
        }
        appScope.launch { account.refreshAdmin() }
        site.refresh()
    }

    fun refreshKeyState() {
        hasKeyState = Settings.apiKey(this) != null
        keyRefusedState = Settings.keyRefused(this)
    }

    fun hasKey() = Settings.apiKey(this) != null

    /** Voice typing (both models) needs a sign-in; Model 1 also needs a key that works. */
    val needsSetup: Boolean
        get() = !account.isSignedIn || (Settings.engine(this) == Settings.Engine.MODEL1 && (!hasKeyState || keyRefusedState))

    // MARK: Sign-in

    fun signIn(setUpKey: Boolean) {
        val front = activity ?: return
        account.signIn(front, setUpKey)
    }

    /** Right after a Google sign-in: bring back the account's setup first, then make a key only if there's none. */
    private fun signedIn(googleToken: String?, setUpKey: Boolean) {
        appScope.launch {
            restoring = true
            if (setUpKey) keySetup.showStep("Bringing back your settings…")
            val saved = runCatching { AccountSync.load(this@MicFlowApp, account) }.getOrNull()
            // The key saved in the account wins: it's the one bound to this Google account (a key
            // left on the phone by MicFlow 1.0 was never linked to an account).
            val accountKey = saved?.apiKey
            if (accountKey != null && accountKey != Settings.apiKey(this@MicFlowApp)) {
                Settings.saveApiKey(this@MicFlowApp, accountKey)
                Settings.setKeyRefused(this@MicFlowApp, false)
            }
            restoring = false
            refreshKeyState()
            val needsKey = !hasKey() || Settings.keyRefused(this@MicFlowApp)
            if (setUpKey && needsKey) keySetup.received(googleToken)
            else if (setUpKey) keySetup.finishedWithSavedKey()
            syncNow()
        }
    }

    /** Checks a key and saves it; returns why it was refused, or null when it's saved. */
    suspend fun saveKeyChecked(key: String): String? {
        val trimmed = key.trim()
        var problem: String? = null
        for (attempt in 0 until 4) {
            problem = KeyCheck.problem(trimmed) ?: break
            if (problem == FREE_PLAN_OFF) break
            if (attempt < 3) delay(3000)
        }
        if (problem != null) return problem
        Settings.saveApiKey(this, trimmed)
        Settings.setKeyRefused(this, false)
        keyRemovedByYou = false
        refreshKeyState()
        syncNow()
        return null
    }

    fun afterKeySaved() = syncNow()

    fun removeKeyByYou() {
        Settings.saveApiKey(this, null)
        keyRemovedByYou = true
        refreshKeyState()
        syncNow()
    }

    /** The bubble heard Google refuse the key for a turned-off free plan. */
    fun keyWasRefused() {
        Settings.setKeyRefused(this, true)
        refreshKeyState()
    }

    // MARK: Account sync

    private fun scheduleSync() {
        if (!account.isSignedIn || restoring) return
        syncJob?.cancel()
        syncJob = appScope.launch {
            delay(4000)
            push()
        }
    }

    private fun syncNow() {
        syncJob?.cancel()
        syncJob = appScope.launch { push() }
    }

    private suspend fun push() {
        if (!account.isSignedIn || restoring) return
        val key = Settings.apiKey(this)
        val upload = when {
            key != null -> AccountSync.KeyUpload.Set(key)
            keyRemovedByYou -> AccountSync.KeyUpload.Clear
            else -> AccountSync.KeyUpload.Keep
        }
        runCatching { AccountSync.save(this, account, upload) }
            .onFailure { Log.i(MicFlowService.TAG, "Saving to the account failed: ${it.message}") }
    }

    fun openUrl(uri: Uri) {
        val front = activity
        if (front != null) CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(front, uri)
        else startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
