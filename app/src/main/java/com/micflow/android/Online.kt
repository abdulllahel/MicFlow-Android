package com.micflow.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()

private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
    runCatching { http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
}

// MARK: - What's New and About

/** The About page and the posts, published by the admin Mac app in the public MicFlow repository. */
data class SiteContent(val about: About, val posts: List<Post>) {
    data class About(val name: String, val role: String, val bio: String, val photo: String?, val linkedin: String,
                     val facebook: String, val whatsapp: String)
    data class Post(val id: String, val title: String, val body: String, val image: String?, val pdf: String?, val date: Instant)

    companion object {
        val builtIn = SiteContent(About(
            name = "Abdullah Al Mamun", role = "Senior UX Designer at Musemind",
            bio = "Hi, I'm Abdullah. I design products from brief to launch. I'm a Senior UX Designer at Musemind, and before that I led the UX team at Ostad, where the products I designed reached about 200,000 users. I've also built SaaS products on my own, like Mocku, an AI mock interview app.\nI made MicFlow for my own work and share it free with friends, family and anyone who finds it useful.\nI'm open to feature requests. Tell me what would make MicFlow better for you.",
            photo = null, linkedin = "https://www.linkedin.com/in/abdullahell/", facebook = "https://www.facebook.com/eabdullahel",
            whatsapp = "8801779053845"), emptyList())

        fun parse(text: String): SiteContent? = runCatching {
            val json = JSONObject(text)
            val a = json.getJSONObject("about")
            SiteContent(
                About(a.optString("name"), a.optString("role"), a.optString("bio"), a.optString("photo").ifEmpty { null },
                      a.optString("linkedin"), a.optString("facebook"), a.optString("whatsapp")),
                json.optJSONArray("posts")?.objects().orEmpty().map {
                    Post(it.optString("id"), it.optString("title"), it.optString("body"), it.optString("image").ifEmpty { null },
                         it.optString("pdf").ifEmpty { null }, runCatching { Instant.parse(it.optString("date")) }.getOrDefault(Instant.EPOCH))
                }.sortedByDescending { it.date })
        }.getOrNull()

        const val RAW = "https://raw.githubusercontent.com/abdulllahel/MicFlow/main/content/"
        fun media(name: String) = RAW + "media/" + name
    }
}

class SiteContentStore(private val context: Context) {
    private val cache = File(context.filesDir, "site.json")
    var content by mutableStateOf(runCatching { SiteContent.parse(cache.readText()) }.getOrNull() ?: SiteContent.builtIn)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun refresh() {
        if (isLoading) return
        isLoading = true
        appScope.launch {
            fetch(SiteContent.RAW + "site.json?t=${System.currentTimeMillis()}")?.let { text ->
                SiteContent.parse(text)?.let { content = it; runCatching { cache.writeText(text) } }
            }
            isLoading = false
        }
    }
}

// MARK: - Feature requests

data class FeatureRequest(val id: String, val authorID: String?, val title: String, val body: String, val images: List<String>,
                          val authorName: String?, val authorAvatar: String?, val createdAt: Instant,
                          val upvotes: Int, val downvotes: Int, val comments: Int, val platform: RequestPlatform)

/** Which MicFlow app a request is for (same values as the Mac app's). */
enum class RequestPlatform(val title: String) {
    MAC("Mac"), ANDROID("Android"), WINDOWS("Windows");
    val key get() = name.lowercase()
    companion object {
        /** This app's platform. */
        val current = ANDROID
        /** Requests from before platforms were all made on a Mac. */
        fun from(value: String?) = entries.firstOrNull { it.key == value } ?: MAC
    }
}

data class RequestComment(val id: String, val authorID: String?, val body: String, val authorName: String?,
                          val authorAvatar: String?, val createdAt: Instant)

/** The same feature requests as the Mac's: vote, comment, post (with pictures), delete your own. */
class FeatureRequestStore(private val account: Account) {
    var requests by mutableStateOf<List<FeatureRequest>>(emptyList())
        private set
    val myVotes = mutableStateMapOf<String, Int>()
    val comments = mutableStateMapOf<String, List<RequestComment>>()
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    private fun date(text: String): Instant = runCatching { Instant.parse(text.replace(Regex("\\.\\d+"), "").let { if (it.endsWith("Z") || it.contains("+")) it.replace("+00:00", "Z") else it + "Z" }) }
        .getOrDefault(Instant.EPOCH)

    fun refresh() {
        if (isLoading) return
        isLoading = true
        appScope.launch {
            try {
                val token = runCatching { account.token() }.getOrNull()
                val rows = JSONArray(Backend.request("GET", "/rest/v1/feature_request_list", listOf(
                    "select" to "*", "order" to "score.desc,created_at.desc", "limit" to "200"), token = token))
                requests = rows.objects().map {
                    FeatureRequest(it.getString("id"), it.optString("author_id").ifEmpty { null }, it.optString("title"), it.optString("body"),
                                   it.optJSONArray("images")?.strings().orEmpty(), it.optString("author_name").ifEmpty { null },
                                   it.optString("author_avatar").ifEmpty { null }, date(it.optString("created_at")),
                                   it.optInt("upvotes"), it.optInt("downvotes"), it.optInt("comments"),
                                   RequestPlatform.from(it.optString("platform").ifEmpty { null }))
                }
                val id = account.user?.id
                if (token != null && id != null) {
                    myVotes.clear()
                    JSONArray(Backend.request("GET", "/rest/v1/votes", listOf("select" to "request_id,value", "voter_id" to "eq.$id"), token = token))
                        .objects().forEach { myVotes[it.getString("request_id")] = it.optInt("value") }
                }
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    /** Up (1) or down (-1); pressing your current vote again takes it back. */
    fun vote(request: FeatureRequest, value: Int) {
        val previous = myVotes[request.id]
        val new = if (previous == value) null else value
        apply(request.id, previous, new)
        appScope.launch {
            try {
                val token = account.token() ?: return@launch
                val voter = account.user?.id ?: return@launch
                if (new != null) Backend.request("POST", "/rest/v1/votes",
                    json = JSONArray().put(JSONObject().put("request_id", request.id).put("voter_id", voter).put("value", new)),
                    token = token, prefer = "resolution=merge-duplicates,return=minimal")
                else Backend.request("DELETE", "/rest/v1/votes", listOf("request_id" to "eq.${request.id}", "voter_id" to "eq.$voter"), token = token)
            } catch (e: Exception) {
                apply(request.id, new, previous)
                error = "Your vote wasn't saved: ${e.message}"
            }
        }
    }

    private fun apply(id: String, old: Int?, new: Int?) {
        if (new == null) myVotes.remove(id) else myVotes[id] = new
        requests = requests.map {
            if (it.id != id) it else it.copy(
                upvotes = it.upvotes - (if (old == 1) 1 else 0) + (if (new == 1) 1 else 0),
                downvotes = it.downvotes - (if (old == -1) 1 else 0) + (if (new == -1) 1 else 0))
        }
    }

    fun loadComments(request: FeatureRequest) {
        appScope.launch {
            runCatching {
                val rows = JSONArray(Backend.request("GET", "/rest/v1/comments", listOf(
                    "select" to "id,author_id,body,author_name,author_avatar,created_at",
                    "request_id" to "eq.${request.id}", "order" to "created_at.asc"), token = runCatching { account.token() }.getOrNull()))
                comments[request.id] = rows.objects().map {
                    RequestComment(it.getString("id"), it.optString("author_id").ifEmpty { null }, it.optString("body"),
                                   it.optString("author_name").ifEmpty { null }, it.optString("author_avatar").ifEmpty { null },
                                   date(it.optString("created_at")))
                }
            }.onFailure { error = it.message }
        }
    }

    suspend fun addComment(text: String, request: FeatureRequest): Boolean {
        val body = text.trim()
        if (body.isEmpty()) return false
        return try {
            val token = account.token() ?: return false
            val user = account.user ?: return false
            Backend.request("POST", "/rest/v1/comments", json = JSONArray().put(JSONObject()
                .put("request_id", request.id).put("body", body).put("author_id", user.id)
                .put("author_name", user.name ?: "MicFlow user").put("author_avatar", user.avatar ?: "")), token = token, prefer = "return=minimal")
            loadComments(request)
            requests = requests.map { if (it.id == request.id) it.copy(comments = it.comments + 1) else it }
            true
        } catch (e: Exception) { error = e.message; false }
    }

    fun canDelete(authorID: String?) = account.isSignedIn && (account.isAdmin || (authorID != null && authorID == account.user?.id))

    suspend fun delete(request: FeatureRequest): Boolean = try {
        val token = account.token() ?: throw BackendError("Sign in first.")
        val rows = JSONArray(Backend.request("DELETE", "/rest/v1/feature_requests", listOf("id" to "eq.${request.id}"), token = token,
                                             prefer = "return=representation"))
        if (rows.length() == 0) { error = "Only the person who posted this, or the admin, can delete it."; false }
        else {
            if (request.images.isNotEmpty()) runCatching {
                Backend.request("DELETE", "/storage/v1/object/feature-images",
                                json = JSONObject().put("prefixes", JSONArray(request.images.map { it.removePrefix("feature-images/") })), token = token)
            }
            requests = requests.filter { it.id != request.id }
            true
        }
    } catch (e: Exception) { error = e.message; false }

    suspend fun deleteComment(comment: RequestComment, request: FeatureRequest) {
        try {
            val token = account.token() ?: return
            val rows = JSONArray(Backend.request("DELETE", "/rest/v1/comments", listOf("id" to "eq.${comment.id}"), token = token,
                                                 prefer = "return=representation"))
            if (rows.length() == 0) { error = "Only the person who wrote this comment, or the admin, can delete it."; return }
            comments[request.id] = comments[request.id].orEmpty().filter { it.id != comment.id }
            requests = requests.map { if (it.id == request.id) it.copy(comments = it.comments - 1) else it }
        } catch (e: Exception) { error = e.message }
    }

    /** Uploads the pictures (JPEG), then posts the request. */
    /** Whether the database knows about platforms yet (Backend/migration-2.7.sql in the Mac repo). */
    private var hasPlatformColumn = true

    suspend fun post(title: String, body: String, images: List<ByteArray>, platform: RequestPlatform = RequestPlatform.current): Boolean = try {
        val token = account.token() ?: throw BackendError("Sign in first.")
        val user = account.user ?: throw BackendError("Sign in first.")
        val paths = images.map { image ->
            val path = "${user.id}/${UUID.randomUUID().toString().lowercase()}.jpg"
            Backend.request("POST", "/storage/v1/object/feature-images/$path", data = image, contentType = "image/jpeg", token = token)
            "feature-images/$path"
        }
        val row = JSONObject()
            .put("title", title).put("body", body).put("images", JSONArray(paths)).put("author_id", user.id)
            .put("author_name", user.name ?: "MicFlow user").put("author_avatar", user.avatar ?: "")
        if (hasPlatformColumn) row.put("platform", platform.key)
        try {
            Backend.request("POST", "/rest/v1/feature_requests", json = JSONArray().put(row), token = token, prefer = "return=minimal")
        } catch (e: BackendError) {
            // An older database without platforms: post without the tag.
            if (!(e.message ?: "").contains("platform")) throw e
            hasPlatformColumn = false
            row.remove("platform")
            Backend.request("POST", "/rest/v1/feature_requests", json = JSONArray().put(row), token = token, prefer = "return=minimal")
        }
        refresh()
        true
    } catch (e: Exception) { error = e.message; false }

    fun imageUrl(path: String) = "${BackendConfig.URL}/storage/v1/object/public/$path"
}

// MARK: - Updates

/**
 * Looks for a newer MicFlow for Android in the MicFlow-Android GitHub releases, downloads the APK and
 * hands it to Android's installer (Android always asks the person to confirm).
 */
class Updater(private val context: Context) {
    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        data class Available(val version: String, val apk: String, val notes: String) : State()
        data class Downloading(val percent: Int) : State()
        data class Failed(val message: String) : State()
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    fun check() {
        if (state is State.Checking || state is State.Downloading) return
        state = State.Checking
        appScope.launch {
            val text = fetch("https://api.github.com/repos/abdulllahel/MicFlow-Android/releases/latest")
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json == null) { state = State.Failed("Couldn't check for updates. Check your internet connection."); return@launch }
            val version = json.optString("tag_name").removePrefix("v")
            val apk = json.optJSONArray("assets")?.objects().orEmpty().firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
            state = if (apk != null && isNewer(version, currentVersion)) State.Available(version, apk, json.optString("body")) else State.UpToDate
        }
    }

    /** Checks, then installs at once if there's a newer version (the notification's Update now). */
    fun checkThen(install: (State.Available) -> Unit) {
        check()
        appScope.launch {
            while (state is State.Checking) kotlinx.coroutines.delay(200)
            (state as? State.Available)?.let { if (canInstall()) install(it) }
        }
    }

    fun install(update: State.Available) {
        state = State.Downloading(0)
        appScope.launch {
            val file = File(context.cacheDir, "updates/MicFlow-${update.version}.apk").apply { parentFile?.mkdirs() }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(update.apk).build()).execute().use { response ->
                        val body = response.body ?: return@use false
                        val total = body.contentLength().coerceAtLeast(1)
                        var done = 0L
                        file.outputStream().use { out ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    out.write(buffer, 0, read)
                                    done += read
                                    val percent = (done * 100 / total).toInt()
                                    launch(Dispatchers.Main) { state = State.Downloading(percent) }
                                }
                            }
                        }
                        response.isSuccessful
                    }
                }.getOrDefault(false)
            }
            if (!ok) { state = State.Failed("The download didn't finish. Try again."); return@launch }
            state = update
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Whether Android lets MicFlow open the installer (Settings › Install unknown apps). */
    fun canInstall() = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    companion object {
        fun isNewer(a: String, than: String): Boolean {
            val x = a.split(".").map { it.toIntOrNull() ?: 0 }
            val y = than.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val p = x.getOrElse(i) { 0 }
                val q = y.getOrElse(i) { 0 }
                if (p != q) return p > q
            }
            return false
        }
    }
}

@Suppress("unused")
private fun Uri.keep() = this
