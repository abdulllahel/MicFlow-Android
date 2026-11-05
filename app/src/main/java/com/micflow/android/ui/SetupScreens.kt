package com.micflow.android.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micflow.android.KeySetupFlow
import com.micflow.android.KeySetupFlow.Stage
import com.micflow.android.MicFlowApp
import com.micflow.android.R
import com.micflow.android.Settings
import com.micflow.android.hideProvider
import kotlinx.coroutines.launch

/**
 * The first screen until voice typing is ready: signed out, one heading, one line and Google's big
 * "Sign in with Google" button (which also makes the key). Progress and any problem show under it;
 * pasting your own key appears only once Google wouldn't make one.
 */
@Composable
fun LoginScreen(app: MicFlowApp, standalone: Boolean = true) {
    val flow = app.keySetup
    val account = app.account
    val signedIn = account.isSignedIn
    @Suppress("UNUSED_VARIABLE") val revision = app.revision
    val refused = app.keyRefusedState
    val showPaste = signedIn && (flow.hasFailed || refused)
    val showButton = flow.stage == Stage.Idle || flow.hasFailed
    Column(
        if (standalone) Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(12.dp)
        else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = if (flow.stage == Stage.Idle && !showPaste)
                (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - if (standalone) 90 else 170).dp else 0.dp)
                .clip(RoundedCornerShape(28.dp)).background(Palette.yellow).padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            PageSticker(Icons.Filled.GraphicEq)
            Text(
                when {
                    refused -> "Your key needs replacing"
                    signedIn -> "One more step: your voice key"
                    else -> "Login with Google to start using voice typing"
                },
                style = display(34.sp), color = Palette.ink, textAlign = TextAlign.Center,
            )
            Text(
                when {
                    refused -> "Google stopped accepting the key from your Google account. Set it up again, maybe with a Google account you use regularly, or paste your own key."
                    signedIn -> "You're signed in. Let MicFlow make your free voice key with Google; it takes about a minute."
                    else -> "Logging in turns on voice typing and saves all your preferences, instructions and settings to your account. Your API key is made for you automatically, so you don't have to create one."
                },
                color = Palette.ink.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            if (showButton) {
                GoogleSignInButton(title = if (signedIn) "Continue with Google" else "Sign in with Google", working = account.isWorking) {
                    // A working key on this phone, or Model 2: a plain sign-in is enough.
                    if (!signedIn && app.hasKey() && !refused) app.signIn(setUpKey = false) else flow.begin()
                }
            }
            account.message?.takeIf { flow.stage == Stage.Idle }?.let {
                Text(it, color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                     modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.6f)).padding(12.dp))
            }
        }
        if (flow.stage != Stage.Idle) DarkCard { KeySetupStatus(flow) }
        if (showPaste) {
            FeatureCard(Icons.Filled.Key, "Or paste your own key", "You're signed in; only the key is missing") {
                PasteKey(app)
                Note("Your key is kept on this phone, locked, and your account keeps a locked (encrypted) copy that only MicFlow, signed in as you, can open.")
            }
        }
    }
    if (flow.showGuide) GoogleKeyGuide(flow)
}

/** Where the key setup stands. */
@Composable
fun KeySetupStatus(flow: KeySetupFlow) {
    when (val stage = flow.stage) {
        Stage.Idle -> Unit
        is Stage.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spinner(Palette.cream, 18.dp)
            Text(stage.text, color = Palette.cream, fontSize = 14.sp)
        }
        Stage.Done -> Note("Voice typing is ready. Your key is saved on this phone and, locked, in your account, so it comes back wherever you sign in.")
        is Stage.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Note("Couldn't set up the key automatically: ${stage.text}", isError = true)
            PillButton("Try again", Icons.Filled.Refresh, PillStyle.Outline) { flow.begin() }
        }
        Stage.MissingPermission -> InfoPanel(Icons.Filled.CheckBox, "The Google Cloud box wasn't ticked",
            "MicFlow needs that permission to make your key. Tap Try again, tick the box shown here on Google's screen, then tap Continue.") {
            GuideImage(R.drawable.guide_tick, 180)
            PillButton("Try again", Icons.Filled.Refresh, PillStyle.Ink) { flow.continueToGoogle() }
        }
        Stage.FreePlanOff -> InfoPanel(Icons.Filled.PersonOff, "This Google account can't get a voice key yet",
            "Google decides which accounts get the free key, partly from how the account has been used (Google's own note is below). It isn't something you did wrong.") {
            GuideImage(R.drawable.guide_google_note, 110)
            Text("What we found", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.ink)
            Text("Some of our Google accounts that hadn't been used for a long time couldn't get a key. After we used them for a day (Gmail, YouTube, search), Google let them make one.",
                 fontSize = 13.sp, color = Palette.ink.copy(alpha = 0.78f), lineHeight = 18.sp)
            Text("What to do", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.ink)
            listOf("Use this Google account as usual for a while, then try again in a few hours or tomorrow.",
                   "Or try now with a Google account you use every day.",
                   "Or paste your own key (below), if you have one.").forEach {
                Text("•  $it", fontSize = 13.sp, color = Palette.ink.copy(alpha = 0.78f), lineHeight = 18.sp)
            }
            flow.failureDetail?.let { Text("Google said: ${hideProvider(it)}", fontSize = 11.sp, color = Palette.ink.copy(alpha = 0.5f)) }
            PillButton("Try another Google account", Icons.Filled.People, PillStyle.Ink) { flow.continueToGoogle() }
        }
        Stage.NeedsTerms -> TermsPanel(flow)
    }
}

/** A guide picture in a white rounded frame. */
@Composable
fun GuideImage(id: Int, maxHeight: Int) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(6.dp),
        contentAlignment = Alignment.Center) {
        Image(painterResource(id), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight.dp))
    }
}

/** Google's terms as connected steps: ① Google Cloud access → ② Google APIs terms → ③ the voice model's terms. */
@Composable
private fun TermsPanel(flow: KeySetupFlow) {
    val current = when (flow.termsID) { "universal" -> 2; "cloud" -> 0; else -> 3 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (flow.justAccepted) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.mint).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, null, tint = Palette.mintText, modifier = Modifier.size(18.dp))
                Text("Step 2 is done. MicFlow opened step 3 for you: it looks almost the same, but it's a different page. Tap Accept there too.",
                     color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        InfoPanel(Icons.Filled.FindInPage, "Almost done: accept Google's terms",
                  "Google asks every account to accept its terms once before it makes a key. Do the steps in order; MicFlow notices each one by itself.") {
            Column {
                TermsStep(1, "Allow Google Cloud access", "The box you ticked on Google's screen", TermsState.Done("Done"), false, flow)
                TermsStep(2, if (current == 0) "Google Cloud Terms of Service" else "Google APIs Terms of Service",
                          if (current == 0) "console.cloud.google.com" else "…/terms/universal",
                          if (current == 3) TermsState.Done(if (flow.acceptedTerms > 0) "Accepted" else "Already accepted") else TermsState.Now,
                          current == 0, flow)
                if (current != 0) TermsStep(3, "The voice model's terms", "…/terms/generative-language-api",
                                            if (current == 3) TermsState.Now else TermsState.Next, true, flow)
            }
            Text("Steps 2 and 3 look almost the same. The page's address shows which one you're on.", fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.6f))
            flow.email?.let { Text("The pages open in your Google account $it.", fontSize = 13.sp, color = Palette.ink) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.1f)))
            Text("Does it say \"already been accepted\" but MicFlow still waits?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.ink)
            Text("Then the page opened in a different Google account. Tap your picture at the top right of the page, switch to ${flow.email ?: "the account you signed in with"}, and tap Accept there.",
                 fontSize = 13.sp, color = Palette.ink.copy(alpha = 0.7f), lineHeight = 18.sp)
            GuideImage(R.drawable.guide_other_account, 80)
        }
    }
}

private sealed class TermsState { data class Done(val label: String) : TermsState(); object Now : TermsState(); object Next : TermsState() }

@Composable
private fun TermsStep(number: Int, title: String, address: String, state: TermsState, isLast: Boolean, flow: KeySetupFlow) {
    val done = state is TermsState.Done
    val now = state is TermsState.Now
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) Palette.mintText else if (now) Palette.orange else Color.Black.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center) {
                if (done) Icon(Icons.Filled.Check, null, tint = Palette.white, modifier = Modifier.size(16.dp))
                else Text("$number", color = if (now) Palette.white else Palette.ink.copy(alpha = 0.45f), fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            if (!isLast) Box(Modifier.width(3.dp).weight(1f).background(if (done) Palette.mintText else Color.Black.copy(alpha = 0.12f)))
        }
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.SemiBold,
                         color = Palette.ink.copy(alpha = if (now || done) 1f else 0.5f))
                    Text(address, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Palette.ink.copy(alpha = 0.5f))
                }
                when (state) {
                    is TermsState.Done -> Text(state.label, color = Palette.mintText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    TermsState.Next -> Text("Next", color = Palette.ink.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    TermsState.Now -> Unit
                }
            }
            if (now) {
                if (number == 3) GuideImage(R.drawable.guide_accept, 110)
                else Text("Open the page and tap the blue Accept button.", fontSize = 13.sp, color = Palette.ink.copy(alpha = 0.75f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Open step $number", Icons.Filled.OpenInNew, PillStyle.Ink) { flow.openTerms() }
                    PillButton("I've accepted", Icons.Filled.Check, PillStyle.OutlineOnLight) { flow.confirmAccepted() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (flow.isWatching || flow.isChecking) {
                        Spinner(Palette.ink, 14.dp)
                        Text(if (flow.isChecking) "Checking with Google now…" else "Checking automatically every few seconds",
                             fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.ink)
                    } else {
                        Icon(Icons.Filled.PauseCircle, null, tint = Palette.ink.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        Text(if (flow.hasValidToken) "Automatic checking stopped. Tap I've accepted after accepting."
                             else "Google's access ran out. Tap I've accepted to sign in again.",
                             fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.ink.copy(alpha = 0.75f))
                    }
                }
            }
        }
    }
}

/** The three slides before Google's sign-in, in MicFlow's colours, as a sheet from the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleKeyGuide(flow: KeySetupFlow) {
    val context = MicFlowApp.shared
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    var dontShow by remember { mutableStateOf(Settings.prefs(context).getBoolean(Settings.SKIP_GUIDE, false)) }
    val slides = listOf(
        Triple(R.drawable.guide_advanced, "Tap Advanced, then \"Go to … (unsafe)\"",
               "Google shows this warning because its review of MicFlow isn't finished yet. MicFlow only uses the access to make your key."),
        Triple(R.drawable.guide_tick, "Tick the Google Cloud box",
               "This is the important one. Without the tick MicFlow can't make your key. Then tap Continue."),
        Triple(R.drawable.guide_accept, "Accept Google's terms",
               "One or two pages, only once. MicFlow opens them in the right account and notices when you're done."),
    )
    fun close() { scope.launch { sheet.hide() }.invokeOnCompletion { flow.showGuide = false } }
    ModalBottomSheet(onDismissRequest = { flow.showGuide = false }, sheetState = sheet, containerColor = HomeTheme.green.frame,
                     dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Before you sign in: 3 quick things", color = Palette.cream, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                slides.indices.forEach { dot ->
                    Box(Modifier.padding(start = 6.dp).height(8.dp).width(if (dot == index) 26.dp else 8.dp).clip(CircleShape)
                            .background(if (dot == index) Palette.yellow else Color.White.copy(alpha = 0.25f)))
                }
            }
            AnimatedContent(targetState = index, label = "slide") { shown ->
                val (image, title, detail) = slides[shown]
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box {
                        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(12.dp),
                            contentAlignment = Alignment.Center) {
                            Image(painterResource(image), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
                        }
                        Box(Modifier.padding(start = 0.dp).rotate(-10f).size(52.dp, 46.dp).clip(RoundedCornerShape(12.dp)).background(Palette.pink)
                                .border(3.dp, Palette.white, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text("${shown + 1}", style = display(26.sp), color = Palette.white)
                        }
                    }
                    Text(title, style = display(26.sp), color = Palette.white)
                    Text(detail, color = Palette.white.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = dontShow, onCheckedChange = {
                    dontShow = it
                    Settings.put(context, Settings.SKIP_GUIDE, it)
                }, colors = CheckboxDefaults.colors(checkedColor = Palette.cream, checkmarkColor = Palette.ink, uncheckedColor = Palette.cream))
                Text("Don't show this again", color = Palette.white.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Cancel", style = PillStyle.Outline) { close() }
                if (index > 0) PillButton("Back", Icons.Filled.ChevronLeft, PillStyle.Outline) { index-- }
                Box(Modifier.weight(1f))
                if (index < slides.size - 1) PillButton("Next", Icons.Filled.ChevronRight, PillStyle.Cream) { index++ }
            }
            if (index == slides.size - 1) {
                GoogleSignInButton("Continue with Google", large = true, modifier = Modifier.fillMaxWidth()) {
                    scope.launch { sheet.hide() }.invokeOnCompletion { flow.continueToGoogle() }
                }
            }
        }
    }
}

/** Pasting your own key: open the key page, paste, save (checked first). */
@Composable
fun PasteKey(app: MicFlowApp) {
    var draft by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save() {
        val key = draft.trim()
        if (key.isEmpty() || checking) return
        checking = true
        problem = null
        scope.launch {
            val result = app.saveKeyChecked(key)
            checking = false
            if (result != null) problem = result else draft = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepLine(1, "Open the key page and sign in")
        PillButton("Open the key page", Icons.Filled.OpenInNew, PillStyle.Outline) { app.openUrl(Uri.parse("https://aistudio.google.com/apikey")) }
        StepLine(2, "Tap Create API key, then copy the key")
        StepLine(3, "Paste it here")
        PillField(draft, { draft = it }, "Paste your key", secure = true, modifier = Modifier.fillMaxWidth())
        PillButton(if (checking) "Checking…" else "Save", style = PillStyle.Cream, enabled = !checking && draft.isNotBlank()) { save() }
        if (checking) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spinner(Palette.cream, 14.dp)
            Text("Checking that the key works…", color = Palette.greyOnDark, fontSize = 13.sp)
        }
        problem?.let { Note("That key didn't work: $it", isError = true) }
    }
}

@Composable
fun StepLine(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Palette.cream), contentAlignment = Alignment.Center) {
            Text("$number", color = Palette.ink, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Text(text, color = Palette.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
