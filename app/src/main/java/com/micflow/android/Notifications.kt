package com.micflow.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Notifications for a new What's New post and a new MicFlow version, checked in the background about
 * every hour (Android decides the exact time). Tapping a post opens it; "Update now" downloads the new
 * version and opens Android's installer, which always asks you to confirm once.
 */
object MicFlowNotifications {
    private const val CHANNEL_POSTS = "posts"
    private const val CHANNEL_UPDATES = "updates"
    private const val SEEN_POSTS = "notifiedPosts"
    private const val SEEN_VERSION = "notifiedVersion"
    const val ACTION_UPDATE = "com.micflow.android.UPDATE_NOW"
    const val EXTRA_OPEN = "open"

    fun schedule(context: Context) {
        createChannels(context)
        val request = PeriodicWorkRequestBuilder<CheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("micflow-check", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_POSTS, "What's New", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "New posts from MicFlow" })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, "Updates", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "A new version of MicFlow is ready" })
    }

    fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Checks once: new posts and a new version. The first check only remembers what's there. */
    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        val prefs = Settings.prefs(context)
        val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        fun get(url: String) = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()

        get(SiteContent.RAW + "site.json?t=${System.currentTimeMillis()}")?.let(SiteContent::parse)?.let { site ->
            val seen = prefs.getStringSet(SEEN_POSTS, null)
            val ids = site.posts.map { it.id }.toSet()
            if (seen != null) site.posts.filter { it.id !in seen }.take(3).forEach { post ->
                show(context, CHANNEL_POSTS, post.id.hashCode(), "New in MicFlow: ${post.title}", post.body.take(200),
                     open = "whatsnew", update = false)
            }
            prefs.edit().putStringSet(SEEN_POSTS, (seen.orEmpty() + ids)).apply()
        }

        get("https://api.github.com/repos/abdulllahel/MicFlow-Android/releases/latest")?.let { text ->
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return@let
            val version = json.optString("tag_name").removePrefix("v")
            val hasApk = json.optJSONArray("assets")?.objects().orEmpty().any { it.optString("name").endsWith(".apk") }
            if (hasApk && Updater.isNewer(version, BuildConfig.VERSION_NAME) && prefs.getString(SEEN_VERSION, null) != version) {
                prefs.edit().putString(SEEN_VERSION, version).apply()
                show(context, CHANNEL_UPDATES, 1, "MicFlow $version is ready", "Tap Update now. Your settings, words and key stay as they are.",
                     open = "settings", update = true)
            }
        }
    }

    private fun show(context: Context, channel: String, id: Int, title: String, text: String, open: String, update: Boolean) {
        if (!allowed(context)) return
        val openIntent = PendingIntent.getActivity(context, id,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, open).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
        if (update) {
            // Opens MicFlow, which downloads the update and shows Android's installer (Android only
            // lets an app on screen open the installer).
            val updateIntent = PendingIntent.getActivity(context, 2,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, "update").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, "Update now", updateIntent)
        }
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}

class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        MicFlowNotifications.check(applicationContext)
        return Result.success()
    }
}

