package com.micflow.android

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** MicFlow's online part, the same Supabase project as the Mac app. */
object BackendConfig {
    const val URL = "https://igieggvaaxcrbepvpxmi.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_ldSK8ZCk-4bo2crFSp2OQw__AnZ6SxZ"
    /** Where Google sends you back to MicFlow (already allowed in Supabase for the Mac app). */
    const val CALLBACK = "micflow://auth-callback"
    const val CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
}

class BackendError(message: String, val status: Int = 0) : Exception(message)

/** Requests to Supabase (REST, auth, storage), off the main thread. */
object Backend {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun request(method: String, path: String, query: List<Pair<String, String>> = emptyList(), json: Any? = null,
                        data: ByteArray? = null, contentType: String? = null, token: String?, prefer: String? = null): String =
        withContext(Dispatchers.IO) {
            val url = Uri.parse(BackendConfig.URL + path).buildUpon().apply {
                query.forEach { (name, value) -> appendQueryParameter(name, value) }
            }.build().toString()
            val body = when {
                json != null -> json.toString().toRequestBody("application/json".toMediaType())
                data != null -> data.toRequestBody((contentType ?: "application/octet-stream").toMediaType())
                method == "POST" || method == "PATCH" -> "".toRequestBody(null)
                else -> null
            }
            val request = Request.Builder().url(url)
                .header("apikey", BackendConfig.PUBLISHABLE_KEY)
                .header("Authorization", "Bearer ${token ?: BackendConfig.PUBLISHABLE_KEY}")
                .apply { if (prefer != null) header("Prefer", prefer) }
                .method(method, body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = runCatching { JSONObject(text) }.getOrNull()?.let {
                        it.optString("msg").ifEmpty { it.optString("message") }.ifEmpty { it.optString("error_description") }.ifEmpty { it.optString("error") }
                    }.orEmpty()
                    throw BackendError(reason.ifEmpty { "The server answered ${response.code}." }, response.code)
                }
                text
            }
        }
}

/**
 * Small secrets (the API key, the sign-in session) kept in the app's private storage, locked with a
 * key held by Android's Keystore, so they can't be read even from a backup or a copied file.
 */
object SecureStore {
    private const val ALIAS = "micflow-secrets"
    private const val FILE = "micflow-secure"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    fun save(context: Context, name: String, value: String?) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (value == null) { prefs.edit().remove(name).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context, name: String): String? {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
        }.getOrNull()
    }

    fun has(context: Context, name: String) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).contains(name)
}

/**
 * Locks the API key before it's saved to the account, exactly like the Mac app's KeyVault: AES-GCM
 * with a key made (HKDF-SHA256) from a secret inside the app and the person's account id. So a key
 * saved on the Mac opens here, and the other way round. Never change the two parts.
 */
object KeyVault {
    private val partA = intArrayOf(0xEF, 0x38, 0x5C, 0x41, 0x37, 0xBA, 0xB7, 0x49, 0xEE, 0x46, 0x9B, 0xEA, 0x57, 0x9B, 0xC3, 0x33,
                                   0x4E, 0x2D, 0x1A, 0x42, 0xA2, 0x04, 0xAA, 0xC4, 0xF8, 0x8B, 0xED, 0xAF, 0x78, 0xC4, 0xD0, 0x1C)
    private val partB = intArrayOf(0xDA, 0xD9, 0x57, 0xBF, 0x4A, 0x67, 0x9C, 0xB6, 0xA4, 0x8F, 0x20, 0xAD, 0x15, 0x4D, 0xE9, 0x0A,
                                   0x91, 0x7A, 0x82, 0x77, 0xAE, 0xB6, 0x8F, 0xBE, 0x3A, 0xD9, 0x11, 0x54, 0xE7, 0x80, 0xE8, 0x6D)
    private const val PREFIX = "mf1:"

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** HKDF-SHA256 (RFC 5869), 32 bytes, as CryptoKit's HKDF<SHA256>.deriveKey does. */
    private fun key(user: String): SecretKeySpec {
        val secret = ByteArray(32) { (partA[it] xor partB[it]).toByte() }
        val prk = hmac(user.toByteArray(), secret)
        val okm = hmac(prk, "MicFlow API key v1".toByteArray() + byteArrayOf(1))
        return SecretKeySpec(okm, "AES")
    }

    fun seal(apiKey: String, user: String): String? = runCatching {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(user), GCMParameterSpec(128, nonce)) }
        PREFIX + Base64.encodeToString(nonce + cipher.doFinal(apiKey.toByteArray()), Base64.NO_WRAP)
    }.getOrNull()

    /** The key from a sealed copy; a plain key (from an older Mac version) comes back as it is. */
    fun open(stored: String, user: String): String? {
        if (!stored.startsWith(PREFIX)) return stored.ifEmpty { null }
        return runCatching {
            val bytes = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(user), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
        }.getOrNull()
    }
}

/** The signed-in Google account (through Supabase), like the Mac's Account. */
class Account(private val context: Context) {
    data class User(val id: String, val email: String?, val name: String?, val avatar: String?)
    private data class Session(val access: String, val refresh: String, val expiresAt: Long, val user: User)

    var user by mutableStateOf<User?>(null)
        private set
    var isWorking by mutableStateOf(false)
        private set
    /** Why the last sign-in didn't finish, in plain words. */
    var message by mutableStateOf<String?>(null)
    var isAdmin by mutableStateOf(false)
        private set

    val isSignedIn get() = user != null

    /** Called after a sign-in: Google's token (when key setup was asked for) and whether it was. */
    var onSignedIn: (String?, Boolean) -> Unit = { _, _ -> }
    var onSignedOut: () -> Unit = {}

    private var session: Session? = null
        set(value) {
            field = value
            user = value?.user
            SecureStore.save(context, "session", value?.let { encode(it) })
        }

    private var verifier: String? = null
    private var pendingKeySetup = false

    init {
        SecureStore.load(context, "session")?.let { decode(it) }?.let { session = it }
    }

    suspend fun refreshAdmin() {
        val token = runCatching { token() }.getOrNull() ?: run { isAdmin = false; return }
        isAdmin = runCatching { Backend.request("POST", "/rest/v1/rpc/is_admin", json = JSONObject(), token = token).trim() == "true" }
            .getOrDefault(false)
    }

    /** A valid access token, refreshed when it's about to run out. */
    suspend fun token(): String? {
        val current = session ?: return null
        if (current.expiresAt - System.currentTimeMillis() > 60_000) return current.access
        return try {
            val data = Backend.request("POST", "/auth/v1/token", listOf("grant_type" to "refresh_token"),
                                       json = JSONObject().put("refresh_token", current.refresh), token = null)
            session = parse(data)
            session?.access
        } catch (e: BackendError) {
            if (e.status == 400 || e.status == 401) {
                session = null
                throw BackendError("You were signed out. Please sign in again.")
            }
            throw e
        }
    }

    /**
     * Opens Google's sign-in in a browser tab (PKCE). With [setUpKey], Google is also asked for
     * the permission to make the voice key in the person's own account, and to show its account list.
     */
    fun signIn(activity: android.app.Activity, setUpKey: Boolean) {
        if (isWorking) return
        val code = randomString()
        verifier = code
        pendingKeySetup = setUpKey
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(code.toByteArray()),
                                              Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val uri = Uri.parse(BackendConfig.URL + "/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", BackendConfig.CALLBACK)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .apply {
                if (setUpKey) {
                    appendQueryParameter("scopes", BackendConfig.CLOUD_SCOPE)
                    appendQueryParameter("prompt", "select_account")
                }
            }.build()
        isWorking = true
        message = null
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, uri)
    }

    /** The browser came back to micflow://auth-callback. */
    suspend fun handleCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error") ?: uri.fragment?.let { Uri.parse("x://x?$it").getQueryParameter("error") }
        val pkce = verifier
        if (code == null || pkce == null) {
            isWorking = false
            message = if (error == "access_denied") "Sign-in stopped on Google's screen. Try again: tap Advanced, then \"Go to … (unsafe)\", tick the box and tap Continue."
                      else "Sign-in didn't finish. Check your internet connection and try again."
            return
        }
        try {
            val data = Backend.request("POST", "/auth/v1/token", listOf("grant_type" to "pkce"),
                                       json = JSONObject().put("auth_code", code).put("code_verifier", pkce), token = null)
            session = parse(data)
            verifier = null
            val googleToken = JSONObject(data).optString("provider_token").ifEmpty { null }
            isWorking = false
            refreshAdmin()
            onSignedIn(if (pendingKeySetup) googleToken else null, pendingKeySetup)
        } catch (e: Exception) {
            isWorking = false
            message = e.message
        }
    }

    /** The person came back to the app without finishing in the browser. */
    fun cancelledIfWaiting() {
        if (!isWorking) return
        isWorking = false
        message = "You closed Google's sign-in before it finished. Tap Sign in with Google to try again."
    }

    fun signOut() {
        val token = session?.access
        session = null
        isAdmin = false
        if (token != null) appScope.launch(Dispatchers.IO) {
            runCatching { Backend.request("POST", "/auth/v1/logout", token = token) }
        }
        onSignedOut()
    }

    private fun parse(data: String): Session {
        val json = JSONObject(data)
        val userJSON = json.getJSONObject("user")
        val meta = userJSON.optJSONObject("user_metadata") ?: JSONObject()
        val user = User(
            id = userJSON.getString("id"),
            email = userJSON.optString("email").ifEmpty { null },
            name = meta.optString("full_name").ifEmpty { meta.optString("name") }.ifEmpty { null },
            avatar = meta.optString("avatar_url").ifEmpty { meta.optString("picture") }.ifEmpty { null },
        )
        return Session(json.getString("access_token"), json.getString("refresh_token"),
                       System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000, user)
    }

    private fun encode(s: Session) = JSONObject()
        .put("access", s.access).put("refresh", s.refresh).put("expiresAt", s.expiresAt)
        .put("id", s.user.id).put("email", s.user.email).put("name", s.user.name).put("avatar", s.user.avatar).toString()

    private fun decode(text: String): Session? = runCatching {
        val j = JSONObject(text)
        Session(j.getString("access"), j.getString("refresh"), j.getLong("expiresAt"),
                User(j.getString("id"), j.optString("email").ifEmpty { null }, j.optString("name").ifEmpty { null },
                     j.optString("avatar").ifEmpty { null }))
    }.getOrNull()

    private fun randomString(): String {
        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

/** Work that outlives a screen (saving to the account, logging out on the server). */
val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)

/** JSON helpers. */
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
