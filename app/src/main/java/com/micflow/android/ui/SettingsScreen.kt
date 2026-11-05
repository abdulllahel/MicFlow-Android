package com.micflow.android.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.micflow.android.FeatureRequest
import com.micflow.android.MicFlowApp
import com.micflow.android.R
import com.micflow.android.RequestPlatform
import com.micflow.android.Settings
import com.micflow.android.SiteContent
import com.micflow.android.Updater
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Settings: the Mac's General, Feature Requests, What's New and About tabs. */
@Composable
fun SettingsScreen(app: MicFlowApp, tab: String, select: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HeroCard("Settings", "MicFlow on your phone", "Your account, updates, what's new, and ideas for MicFlow.", icon = Icons.Filled.Shield)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("General", "Feature Requests", "What's New", "About").forEach { Pill(it, tab == it) { select(it) } }
        }
        when (tab) {
            "General" -> GeneralTab(app)
            "Feature Requests" -> RequestsTab(app)
            "What's New" -> WhatsNewTab(app)
            else -> AboutTab(app)
        }
    }
}

@Composable
private fun GeneralTab(app: MicFlowApp) {
    val context = LocalContext.current
    val account = app.account
    val updater = app.updater
    @Suppress("UNUSED_VARIABLE") val revision = app.revision
    FeatureCard(Icons.Filled.Person, "Account", account.user?.email ?: "Not signed in") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(account.user?.avatar, null, modifier = Modifier.size(40.dp).clip(CircleShape).background(Palette.cream))
            Column(Modifier.weight(1f)) {
                Text(account.user?.name ?: "Signed in", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                account.user?.email?.let { Text(it, color = Palette.greyOnDark, fontSize = 12.sp) }
            }
        }
        Note("Your instructions, words, mix-ups, clean-up setting and a locked copy of your API key are saved to your account, so they come back on any phone or Mac you sign in on.")
        Note("Logging out pauses voice typing and removes your key from this phone. It comes back when you log in again.")
        PillButton("Log out", Icons.AutoMirrored.Filled.Logout, PillStyle.Outline) { account.signOut() }
    }
    FeatureCard(Icons.Filled.SystemUpdate, "Updates", "You have MicFlow ${updater.currentVersion}") {
        when (val state = updater.state) {
            Updater.State.Idle -> PillButton("Check for updates", Icons.Filled.Refresh) { updater.check() }
            Updater.State.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(Palette.cream, 16.dp); Text("Checking…", color = Palette.cream, fontSize = 14.sp)
            }
            Updater.State.UpToDate -> {
                Note("MicFlow is up to date.")
                PillButton("Check again", Icons.Filled.Refresh, PillStyle.Outline) { updater.check() }
            }
            is Updater.State.Available -> {
                Text("MicFlow ${state.version} is ready", color = Palette.white, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!updater.canInstall()) {
                    Note("Android first asks you to let MicFlow install updates: tap Update now, turn on \"Allow from this source\", then come back and tap Update now again.")
                }
                PillButton("Update now", Icons.Filled.SystemUpdate, PillStyle.Cream) {
                    if (updater.canInstall()) updater.install(state)
                    else context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                      Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Note("Android asks you to confirm the install. Your settings, words and key stay as they are.")
            }
            is Updater.State.Downloading -> Text("Downloading… ${state.percent}%", color = Palette.cream, fontSize = 14.sp)
            is Updater.State.Failed -> {
                Note(state.message, isError = true)
                PillButton("Try again", Icons.Filled.Refresh, PillStyle.Outline) { updater.check() }
            }
        }
    }
    val dark = Settings.prefs(context).getBoolean(Settings.DARK, false)
    FeatureCard(Icons.Filled.DarkMode, "Dark frame", "Forest green is the default", dark, { Settings.put(context, Settings.DARK, it) }) {}
    FeatureCard(Icons.Filled.Shield, "Privacy", "What stays on this phone") {
        Note("Your dictation history and statistics stay on this phone. Your API key is kept locked on the phone and in your account; nobody can read it from the database.")
    }
}

// MARK: - Feature requests

@Composable
private fun RequestsTab(app: MicFlowApp) {
    val store = app.requests
    var open by remember { mutableStateOf<FeatureRequest?>(null) }
    var composing by remember { mutableStateOf(false) }
    // Android's requests first, or every request while Android has none; the tabs switch.
    var chosen by remember { mutableStateOf<RequestPlatform?>(null) }
    var choseAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { store.refresh() }
    val selected = open?.let { o -> store.requests.firstOrNull { it.id == o.id } ?: o }
    val platform = when {
        choseAll -> null
        chosen != null -> chosen
        store.requests.any { it.platform == RequestPlatform.current } -> RequestPlatform.current
        else -> null
    }
    val shown = if (platform == null) store.requests else store.requests.filter { it.platform == platform }
    when {
        composing -> ComposeRequest(app) { composing = false }
        selected != null -> RequestDetail(app, selected) { open = null }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DarkCard {
                CardHeader(Icons.Filled.NewReleases, "Feature Requests", "Vote for the ideas you want")
                PillButton("Post an idea", Icons.Filled.Add, PillStyle.Cream) { composing = true }
                store.error?.let { Note(it, isError = true) }
                if (store.isLoading && store.requests.isEmpty()) Spinner(Palette.cream, 18.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RequestPlatform.entries.forEach { p ->
                        Pill("${p.title} · ${store.requests.count { it.platform == p }}", platform == p) { chosen = p; choseAll = false }
                    }
                    Pill("All · ${store.requests.size}", platform == null) { choseAll = true }
                }
                if (shown.isEmpty() && !store.isLoading) Note(if (platform == null) "No requests yet. Be the first to ask for something!"
                                                              else "No ${platform.title} requests yet.")
            }
            shown.forEach { request -> RequestRow(app, request) { open = request; store.loadComments(request) } }
        }
    }
}

@Composable
private fun RequestRow(app: MicFlowApp, request: FeatureRequest, onOpen: () -> Unit) {
    DarkCard(Modifier.clickable(onClick = onOpen)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VoteButtons(app, request)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlatformTag(request.platform)
                Text(request.title, color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (request.body.isNotEmpty()) Text(request.body, color = Palette.greyOnDark, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ChatBubbleOutline, null, tint = Palette.greyOnDark, modifier = Modifier.size(14.dp))
                    Text("${request.comments} · ${request.authorName ?: "MicFlow user"}", color = Palette.greyOnDark, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VoteButtons(app: MicFlowApp, request: FeatureRequest) {
    val mine = app.requests.myVotes[request.id]
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Filled.ArrowUpward, "Vote up", tint = if (mine == 1) Palette.mint else Palette.white,
             modifier = Modifier.size(30.dp).clip(CircleShape).background(if (mine == 1) Palette.mintText else Color.White.copy(alpha = 0.08f))
                 .clickable { app.requests.vote(request, 1) }.padding(6.dp))
        Text("${request.upvotes - request.downvotes}", color = Palette.white, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Icon(Icons.Filled.ArrowDownward, "Vote down", tint = Palette.white,
             modifier = Modifier.size(30.dp).clip(CircleShape).background(if (mine == -1) Palette.pink else Color.White.copy(alpha = 0.08f))
                 .clickable { app.requests.vote(request, -1) }.padding(6.dp))
    }
}

@Composable
private fun RequestDetail(app: MicFlowApp, request: FeatureRequest, back: () -> Unit) {
    val store = app.requests
    val scope = rememberCoroutineScope()
    var comment by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Back", Icons.AutoMirrored.Filled.ArrowBack, PillStyle.Outline, onClick = back)
        DarkCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VoteButtons(app, request)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlatformTag(request.platform)
                    Text(request.title, color = Palette.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${request.authorName ?: "MicFlow user"} · " + request.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                         color = Palette.greyOnDark, fontSize = 12.sp)
                }
            }
            if (request.body.isNotEmpty()) Text(request.body, color = Palette.white, fontSize = 14.sp, lineHeight = 20.sp)
            request.images.forEach { path ->
                AsyncImage(store.imageUrl(path), null, contentScale = ContentScale.FillWidth,
                           modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
            }
            if (store.canDelete(request.authorID)) {
                PillButton("Delete", Icons.Filled.Delete, PillStyle.Outline) { scope.launch { if (store.delete(request)) back() } }
            }
            store.error?.let { Note(it, isError = true) }
        }
        DarkCard {
            Text("Comments", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            store.comments[request.id].orEmpty().forEach { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(c.authorAvatar, null, modifier = Modifier.size(30.dp).clip(CircleShape).background(Palette.cream))
                    Column(Modifier.weight(1f)) {
                        Text(c.authorName ?: "MicFlow user", color = Palette.white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(c.body, color = Palette.white.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                    if (store.canDelete(c.authorID)) Icon(Icons.Filled.Delete, "Delete", tint = Palette.greyOnDark,
                                                          modifier = Modifier.size(18.dp).clickable { scope.launch { store.deleteComment(c, request) } })
                }
            }
            PillField(comment, { comment = it }, "Write a comment…", singleLine = false, modifier = Modifier.fillMaxWidth())
            PillButton("Comment", style = PillStyle.Cream, enabled = comment.isNotBlank()) {
                scope.launch { if (store.addComment(comment, request)) comment = "" }
            }
        }
    }
}

@Composable
private fun ComposeRequest(app: MicFlowApp, done: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var platform by remember { mutableStateOf(RequestPlatform.current) }
    val images = remember { mutableStateListOf<Bitmap>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        uris.take(6 - images.size).forEach { uri ->
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = minOf(1f, 1600f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }.getOrNull()?.let { images += it }
        }
    }
    DarkCard {
        PillButton("Back", Icons.AutoMirrored.Filled.ArrowBack, PillStyle.Outline, onClick = done)
        Text("Post an idea", style = display(26.sp), color = Palette.white)
        Text("For which app?", color = Palette.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RequestPlatform.entries.forEach { p -> Pill(p.title, platform == p) { platform = p } }
        }
        PillField(title, { title = it }, "Title (at least 4 letters)", modifier = Modifier.fillMaxWidth())
        PillField(body, { body = it }, "Describe it…", singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            images.forEach { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))) }
        }
        PillButton("Add pictures (${images.size}/6)", Icons.Filled.Image, PillStyle.Outline, enabled = images.size < 6) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        app.requests.error?.let { Note(it, isError = true) }
        PillButton(if (posting) "Posting…" else "Post", style = PillStyle.Cream, enabled = !posting && title.trim().length >= 4) {
            posting = true
            scope.launch {
                val jpegs = images.map { bmp -> ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray() }
                val ok = app.requests.post(title.trim(), body.trim(), jpegs, platform)
                posting = false
                if (ok) done()
            }
        }
    }
}

// MARK: - What's New and About

@Composable
private fun WhatsNewTab(app: MicFlowApp) {
    val posts = app.site.content.posts
    LaunchedEffect(Unit) { app.site.refresh() }
    if (posts.isEmpty()) DarkCard { Note("No news yet.") }
    posts.forEach { post ->
        DarkCard {
            Text(post.date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM yyyy")), color = Palette.greyOnDark, fontSize = 12.sp)
            Text(post.title, color = Palette.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            post.image?.let { AsyncImage(SiteContent.media(it), null, contentScale = ContentScale.FillWidth,
                                         modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) }
            Text(post.body, color = Palette.white.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
            post.pdf?.let { pdf -> PillButton("Open the PDF", style = PillStyle.Outline) { app.openUrl(Uri.parse(SiteContent.media(pdf))) } }
        }
    }
}

@Composable
private fun AboutTab(app: MicFlowApp) {
    val about = app.site.content.about
    DarkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (about.photo != null) AsyncImage(SiteContent.media(about.photo), null, contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(72.dp).clip(CircleShape))
            else Image(painterResource(R.drawable.profile), null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape))
            Column {
                Text(about.name, color = Palette.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(about.role, color = Palette.greyOnDark, fontSize = 13.sp)
            }
        }
        Text(about.bio, color = Palette.white.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (about.linkedin.isNotEmpty()) PillButton("LinkedIn", style = PillStyle.Cream) { app.openUrl(Uri.parse(about.linkedin)) }
            if (about.facebook.isNotEmpty()) PillButton("Facebook", style = PillStyle.Cream) { app.openUrl(Uri.parse(about.facebook)) }
            if (about.whatsapp.isNotEmpty()) PillButton("WhatsApp", style = PillStyle.Cream) { app.openUrl(Uri.parse("https://wa.me/${about.whatsapp}")) }
        }
    }
    DarkCard {
        CardHeader(Icons.Filled.Info, "MicFlow for Android", "Version ${app.updater.currentVersion}")
        Note("Free voice typing in English and Bengali, in any app.")
    }
}

/** The small platform tag on a request: Mac purple, Android green, Windows blue. */
@Composable
private fun PlatformTag(platform: RequestPlatform) {
    val color = when (platform) {
        RequestPlatform.MAC -> Palette.lavender
        RequestPlatform.ANDROID -> Palette.mintText
        RequestPlatform.WINDOWS -> Color(0xFF3B82F6)
    }
    Text(platform.title, color = Palette.white, fontSize = 10.sp, fontWeight = FontWeight.Bold,
         modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 3.dp))
}
