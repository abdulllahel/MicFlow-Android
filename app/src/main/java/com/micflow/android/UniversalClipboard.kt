package com.micflow.android

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Universal Clipboard, the same as the Mac's: text and images copied on one device appear on your
 * others. Encrypted on the device with a key made from the pairing code (which never leaves your
 * devices); the server only holds an item while it's delivered.
 */
object UniversalClipboard {
    private const val MAX_IMAGE = 5_000_000
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    var status by mutableStateOf("")
    var code by mutableStateOf<String?>(null)
        private set

    private fun prefs(c: Context) = Settings.prefs(c)
    fun enabled(c: Context) = prefs(c).getBoolean("clipboardEnabled", true)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("clipboardEnabled", on).apply()
    private fun device(c: Context) = prefs(c).getString("clipboardDevice", null)
        ?: UUID.randomUUID().toString().also { prefs(c).edit().putString("clipboardDevice", it).apply() }

    fun load(c: Context) {
        code = prefs(c).getString("clipboardCode", null) ?: newCode().also { prefs(c).edit().putString("clipboardCode", it).apply() }
    }

    private fun newCode(): String {
        val r = SecureRandom()
        return (0 until 16).map { ALPHABET[r.nextInt(ALPHABET.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
    }

    private fun normalize(s: String) = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** Uses another device's code; false when it isn't a valid code. */
    fun useCode(c: Context, text: String): Boolean {
        val clean = normalize(text)
        if (clean.length != 16 || clean.any { it !in ALPHABET }) return false
        code = clean.chunked(4).joinToString("-")
        prefs(c).edit().putString("clipboardCode", code).apply()
        return true
    }

    private fun hmac(key: ByteArray, data: ByteArray) = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    /** HKDF-SHA256, the same as CryptoKit's on the Mac. */
    private fun key(user: String): SecretKeySpec? {
        val code = code ?: return null
        val prk = hmac(user.toByteArray(), normalize(code).toByteArray())
        val okm = hmac(prk, "MicFlow clipboard v1".toByteArray() + byteArrayOf(1))
        return SecretKeySpec(okm.copyOf(32), "AES")
    }

    private fun seal(plain: ByteArray, key: SecretKeySpec): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
        return nonce + cipher.doFinal(plain)
    }

    private fun open(sealed: ByteArray, key: SecretKeySpec): ByteArray? = runCatching {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }
            .doFinal(sealed, 12, sealed.size - 12)
    }.getOrNull()

    private fun iso(date: Date) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)

    // MARK: Sending

    /** Reads the clipboard (only works while a MicFlow screen has focus) and sends it. */
    fun sendCurrent(c: Context) {
        val clip = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return
        val item = clip.getItemAt(0) ?: return
        val uri = item.uri
        val image = if (uri != null && c.contentResolver.getType(uri)?.startsWith("image/") == true)
            runCatching { c.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let(::shrink) else null
        if (image != null) appScope.launch { send(c, "image", image) }
        else item.coerceToText(c)?.toString()?.takeIf { it.isNotEmpty() }?.let { appScope.launch { send(c, "text", it.toByteArray()) } }
    }

    private fun shrink(bytes: ByteArray): ByteArray? {
        if (bytes.size <= MAX_IMAGE) return bytes
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        repeat(5) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            if (out.size() <= MAX_IMAGE) return out.toByteArray()
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.7).toInt(), (bitmap.height * 0.7).toInt(), true)
        }
        return null
    }

    private suspend fun send(c: Context, kind: String, plain: ByteArray) {
        val account = MicFlowApp.shared.account
        if (!enabled(c)) return
        val user = account.user?.id ?: return toast(c, "Sign in to MicFlow to share your clipboard")
        if (code == null) load(c)
        val key = key(user) ?: return
        val token = runCatching { account.token() }.getOrNull() ?: return
        runCatching {
            Backend.request("DELETE", "/rest/v1/clipboard_items", listOf("created_at" to "lt.${iso(Date(System.currentTimeMillis() - 300_000))}"), token = token)
        }
        runCatching {
            Backend.request("POST", "/rest/v1/clipboard_items", json = JSONObject()
                .put("device", device(c)).put("device_name", Build.MODEL).put("kind", kind)
                .put("data", Base64.encodeToString(seal(plain, key), Base64.NO_WRAP)), token = token)
        }.onSuccess { status = "Sent to your devices"; toast(c, "Sent to your other devices") }
         .onFailure { status = "Couldn't send: ${it.message}" }
    }

    private fun toast(c: Context, text: String) = appScope.launch { Toast.makeText(c, text, Toast.LENGTH_SHORT).show() }.let { }

    // MARK: Receiving (runs inside the accessibility service, which stays alive in the background)

    private var listenJob: Job? = null
    private var socket: WebSocket? = null
    private var lastSeen = iso(Date())
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()

    fun startListening(c: Context) {
        load(c)
        listenJob?.cancel()
        listenJob = appScope.launch {
            while (true) {
                val account = MicFlowApp.shared.account
                val user = account.user?.id
                val token = runCatching { account.token() }.getOrNull()
                if (enabled(c) && user != null && token != null) {
                    // Also checks every 10 seconds, in case the live connection misses an item.
                    fetchNew(c, token, user)
                    if (socket == null) connect(c, token, user)
                } else if (user == null) status = "Sign in to use Universal Clipboard"
                delay(10_000)
            }
        }
    }

    fun stopListening() { listenJob?.cancel(); socket?.close(1000, null); socket = null }

    private fun connect(c: Context, token: String, user: String) {
        val url = BackendConfig.URL.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=${BackendConfig.PUBLISHABLE_KEY}&vsn=1.0.0"
        val topic = "realtime:clipboard-$user"
        fun msg(topic: String, event: String, payload: JSONObject) =
            JSONObject().put("topic", topic).put("event", event).put("payload", payload).put("ref", UUID.randomUUID().toString()).toString()
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            var beat: Job? = null
            override fun onOpen(ws: WebSocket, response: Response) {
                val change = JSONObject().put("event", "INSERT").put("schema", "public").put("table", "clipboard_items").put("filter", "user_id=eq.$user")
                ws.send(msg(topic, "phx_join", JSONObject().put("config", JSONObject().put("postgres_changes", JSONArray().put(change))).put("access_token", token)))
                beat = appScope.launch {
                    while (true) {
                        delay(25_000)
                        ws.send(msg("phoenix", "heartbeat", JSONObject()))
                        runCatching { MicFlowApp.shared.account.token() }.getOrNull()?.let { ws.send(msg(topic, "access_token", JSONObject().put("access_token", it))) }
                    }
                }
            }
            override fun onMessage(ws: WebSocket, text: String) {
                if (text.contains("phx_reply") && text.contains("\"error\"")) status = "Live connection refused: " + text.take(160)
                else if (text.contains("phx_reply") && status.isEmpty()) status = "Connected"
                if (text.contains("\"postgres_changes\"")) appScope.launch {
                    runCatching { MicFlowApp.shared.account.token() }.getOrNull()?.let { fetchNew(c, it, user) }
                }
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { beat?.cancel(); if (socket == ws) socket = null }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { status = "Connection failed: ${t.message}"; beat?.cancel(); if (socket == ws) socket = null }
        })
    }

    private suspend fun fetchNew(c: Context, token: String, user: String) {
        val text = runCatching {
            Backend.request("GET", "/rest/v1/clipboard_items", listOf("select" to "device_name,kind,data,created_at",
                "device" to "neq.${device(c)}", "created_at" to "gt.$lastSeen", "order" to "created_at.desc", "limit" to "1"), token = token)
        }.onFailure { status = "Couldn't check: ${it.message}" }.getOrNull() ?: return
        val row = runCatching { JSONArray(text).getJSONObject(0) }.getOrNull() ?: return
        lastSeen = row.getString("created_at")
        val key = key(user) ?: return
        val plain = open(Base64.decode(row.getString("data"), Base64.DEFAULT), key)
            ?: run { status = "An item couldn't be opened: check both devices use the same code"; return }
        val manager = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (row.getString("kind") == "image") {
            val file = File(c.cacheDir, "clipboard/clip-${System.currentTimeMillis()}.img").apply { parentFile?.mkdirs(); parentFile?.listFiles()?.forEach { it.delete() }; writeBytes(plain) }
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", file)
            manager.setPrimaryClip(ClipData("MicFlow", arrayOf(if (plain.size > 3 && plain[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"), ClipData.Item(uri)))
        } else manager.setPrimaryClip(ClipData.newPlainText("MicFlow", String(plain)))
        val from = row.optString("device_name").ifEmpty { "your Mac" }
        status = "Received from $from"
        Toast.makeText(c, "Copied from $from", Toast.LENGTH_SHORT).show()
    }
}

/** An invisible screen that reads the clipboard (Android only allows that while an app has focus), sends it and closes. */
class ClipboardSendActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        UniversalClipboard.sendCurrent(this)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

/** The Quick Settings tile: one tap sends what you copied. */
class ClipboardTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, ClipboardSendActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}
