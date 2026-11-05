package com.micflow.android

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Makes a voice-typing API key in the user's own Google account, the same way as the Mac app: an
 * existing project with the model's API on, else an earlier MicFlow project, else a new one; then the
 * API is switched on and a key limited to that API is made.
 */
object KeyProvisioner {
    class Failure(message: String, val fix: Uri? = null, val needsTerms: Boolean = false,
                  val termsID: String? = null, var project: String? = null) : Exception(message)

    data class TokenInfo(val email: String?, val scopes: Set<String>, val expiresIn: Long?)

    private const val SERVICE = "generativelanguage.googleapis.com"
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    /** The Cloud page for one set of Google's terms ("universal" first for accounts new to Cloud). */
    fun terms(id: String): Uri {
        val safe = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "generative-language-api" }
        return Uri.parse("https://console.cloud.google.com/terms/$safe")
    }

    val termsPage: Uri get() = terms("generative-language-api")

    /** A Google page opened in the given account (when the browser is signed in to several). */
    fun page(url: Uri, email: String?): Uri =
        if (email.isNullOrEmpty()) url else url.buildUpon().appendQueryParameter("authuser", email).build()

    /** What Google's token allows (sent in the body, never in the address). */
    suspend fun tokenInfo(token: String): TokenInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("https://oauth2.googleapis.com/tokeninfo")
                .post(FormBody.Builder().add("access_token", token).build()).build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val json = JSONObject(r.body?.string().orEmpty())
                TokenInfo(json.optString("email").ifEmpty { null },
                          json.optString("scope").split(" ").filter { it.isNotEmpty() }.toSet(),
                          json.optString("expires_in").toLongOrNull())
            }
        }.getOrNull()
    }

    /** With [known] (from a try that stopped at the terms), only that project is tried again. */
    suspend fun createKey(token: String, known: String?, progress: (String) -> Unit): String {
        if (known != null) {
            enable(known, token)
            progress("Creating your key…")
            return makeKey(known, token)
        }
        progress("Finding your project…")
        val projects = listProjects(token)
        for (project in projects) {
            if (serviceIsEnabled(project, token)) {
                progress("Creating your key…")
                return makeKey(project, token)
            }
        }
        val project = reuseOrCreate(projects, token, progress)
        progress("Switching on voice typing for your project…")
        enable(project, token)
        progress("Creating your key…")
        return makeKey(project, token)
    }

    private suspend fun listProjects(token: String): List<String> {
        val list = call("GET", "https://cloudresourcemanager.googleapis.com/v1/projects?pageSize=200", token)
        val projects = list.optJSONArray("projects")?.objects().orEmpty().filter { it.optString("lifecycleState") == "ACTIVE" }
        fun rank(p: JSONObject): Int {
            val id = p.optString("projectId")
            return when {
                id.startsWith("gen-lang-client-") -> 0
                id.startsWith("micflow-") || p.optString("name") == "MicFlow" -> 1
                else -> 2
            }
        }
        return projects.sortedBy(::rank).map { it.optString("projectId") }.filter { it.isNotEmpty() }.take(10)
    }

    private suspend fun serviceIsEnabled(project: String, token: String) = runCatching {
        call("GET", "https://serviceusage.googleapis.com/v1/projects/$project/services/$SERVICE", token).optString("state") == "ENABLED"
    }.getOrDefault(false)

    private suspend fun reuseOrCreate(projects: List<String>, token: String, progress: (String) -> Unit): String {
        projects.firstOrNull { it.startsWith("micflow-") }?.let { return it }
        progress("Creating a project for MicFlow…")
        val id = "micflow-" + UUID.randomUUID().toString().replace("-", "").take(10)
        val operation = call("POST", "https://cloudresourcemanager.googleapis.com/v3/projects", token,
                             JSONObject().put("projectId", id).put("displayName", "MicFlow"))
        wait(operation, "https://cloudresourcemanager.googleapis.com/v3/", token)
        return id
    }

    private suspend fun enable(project: String, token: String) {
        try {
            val operation = call("POST", "https://serviceusage.googleapis.com/v1/projects/$project/services/$SERVICE:enable", token, JSONObject())
            wait(operation, "https://serviceusage.googleapis.com/v1/", token)
        } catch (failure: Failure) {
            if (failure.needsTerms) failure.project = project
            throw failure
        }
    }

    private suspend fun makeKey(project: String, token: String): String {
        val base = "https://apikeys.googleapis.com/v2/"
        val operation = call("POST", "${base}projects/$project/locations/global/keys", token, JSONObject()
            .put("displayName", "MicFlow")
            .put("restrictions", JSONObject().put("apiTargets", org.json.JSONArray().put(JSONObject().put("service", SERVICE)))))
        val done = wait(operation, base, token)
        val name = done.optJSONObject("response")?.optString("name")?.ifEmpty { null }
            ?: throw Failure("The key was made but its name didn't come back. Try again.")
        val key = call("GET", "$base$name/keyString", token).optString("keyString")
        if (key.isEmpty()) throw Failure("The new key couldn't be read. Try again.")
        return key
    }

    /** Polls a long-running operation until it's done (about a minute at most). */
    private suspend fun wait(operation: JSONObject, base: String, token: String): JSONObject {
        var current = operation
        repeat(60) {
            if (current.optBoolean("done")) {
                current.optJSONObject("error")?.let { throw Failure(hideProvider(it.optString("message", "Setting up the key didn't work."))) }
                return current
            }
            val name = current.optString("name").ifEmpty { return current }
            delay(1000)
            current = call("GET", base + name, token)
        }
        throw Failure("Setting up the key is taking too long. Try again in a minute.")
    }

    private suspend fun call(method: String, url: String, token: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType())
                ?: if (method == "POST") "".toRequestBody(null) else null)
            .build()
        val (status, text) = try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw e
        }
        val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        if (status !in 200..299) {
            val error = json.optJSONObject("error")
            val message = error?.optString("message").orEmpty().ifEmpty { "HTTP $status" }
            val path = Uri.parse(url).path.orEmpty()
            Log.i(MicFlowService.TAG, "Key setup: $method $path -> HTTP $status: ${text.take(1500)}")
            val details = error?.optJSONArray("details")?.objects().orEmpty()
            val reasons = details.mapNotNull { it.optString("reason").ifEmpty { null } }
            if ("UREQ_TOS_NOT_ACCEPTED" in reasons || message.contains("generative-language-api")) {
                val id = details.flatMap { it.optJSONArray("violations")?.objects().orEmpty() }
                    .mapNotNull { Uri.parse("https://x/" + it.optString("subject")).getQueryParameter("tos_id") }
                    .firstOrNull { it.isNotEmpty() } ?: "generative-language-api"
                throw Failure("One more click: accept Google's terms on Google's page.", terms(id), needsTerms = true, termsID = id)
            }
            if (message.lowercase().contains("terms of service") && path.contains("cloudresourcemanager")) {
                throw Failure("One more click: accept Google Cloud's terms.", Uri.parse("https://console.cloud.google.com/"),
                              needsTerms = true, termsID = "cloud")
            }
            throw Failure(hideProvider(message))
        }
        json
    }
}

/** MicFlow doesn't say which company's models it uses (same rule as the Mac). */
fun hideProvider(message: String): String {
    if (isFreePlanOff(message)) return FREE_PLAN_OFF
    return listOf(
        "generativelanguage\\.googleapis\\.com" to "the service",
        "(Google )?AI Studio" to "your provider's dashboard",
        "Generative Language API" to "the API",
        "(the )?Gemini API" to "the API",
        "gemini-[0-9a-z.\\-]+" to "the model",
        "Gemini" to "the model",
        "Google Cloud" to "the provider",
        "Google" to "the provider",
    ).fold(message) { text, (pattern, name) -> text.replace(Regex(pattern, RegexOption.IGNORE_CASE), name) }
        .replace(Regex("\\bthe the\\b", RegexOption.IGNORE_CASE), "the")
        .let { if (it.isNotEmpty() && it[0].isLowerCase() && message.firstOrNull()?.isUpperCase() == true) it.replaceFirstChar(Char::uppercase) else it }
}

/** Google's "free tier is not available for this project": its decision for the whole account. */
fun isFreePlanOff(message: String) = message.contains("free tier is not available", ignoreCase = true)

const val FREE_PLAN_OFF = "The provider isn't giving this Google account the free plan right now. Use the account as usual and try again in a few hours or tomorrow, or set up with a Google account you use every day."
