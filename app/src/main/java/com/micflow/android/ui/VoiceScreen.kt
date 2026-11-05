package com.micflow.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micflow.android.MicFlowApp
import com.micflow.android.MixUp
import com.micflow.android.Model2Listener
import com.micflow.android.Settings
import com.micflow.android.Settings.Engine

/** The Voice Typing tab: the Mac's Voice page and Preferences in one place, for a phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceScreen(app: MicFlowApp, permissions: PermissionState) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val revision = app.revision
    val engine = Settings.engine(context)
    val ready = permissions.allGood
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HeroCard("Voice Typing", if (ready) "Tap the mic bubble and talk" else "A few permissions first",
                 if (ready) "Tap any text box in any app: the mic bubble appears beside it. Tap it, speak English, Bengali or both, and it types for you. It stops when you stop talking."
                 else "Android needs you to allow these once. Each step below shows exactly what to tap.",
                 chip = if (engine == Engine.MODEL1) "Model 1" else "Model 2", icon = Icons.Filled.GraphicEq)

        if (!ready) PermissionsCard(permissions)

        FeatureCard(Icons.Filled.Mic, "Voice typing", "Typed into the text box you tapped") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Engine.entries.forEach { e -> Pill(e.title, engine == e) { Settings.put(context, Settings.ENGINE, e.name) } }
            }
            if (engine == Engine.MODEL1) {
                Note("Free with your API key. Words appear while you speak; English and Bengali are detected by themselves, even mixed. Filler words are removed and punctuation added.")
            } else {
                Note("Free, and needs no key: it uses this phone's own speech recognition. It hears one language at a time, so pick it here. Text comes with less punctuation.")
                val language = Settings.model2Language(context)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Settings.Language.entries.forEach { l -> Pill(l.title, language == l) { Settings.put(context, Settings.MODEL2_LANGUAGE, l.name) } }
                }
                if (!Model2Listener.isAvailable(context)) Note("This phone has no speech recognition for Model 2. Use Model 1.", isError = true)
            }
        }

        FeatureCard(Icons.Filled.Tune, "The mic bubble", "Its size, see-through level and when it stops") {
            val size = Settings.size(context)
            LabeledSlider("Size", "$size dp", size.toFloat(), Settings.MIN_SIZE.toFloat()..Settings.MAX_SIZE.toFloat()) {
                Settings.put(context, Settings.SIZE, it.toInt())
            }
            val opacity = Settings.opacity(context)
            LabeledSlider("See-through", "${Math.round(opacity * 100)}%", opacity, Settings.MIN_OPACITY..1f) {
                Settings.put(context, Settings.OPACITY, it)
            }
            Text("Stop listening after silence", color = Palette.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val stop = Settings.autoStopSeconds(context)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Settings.AUTO_STOP_CHOICES.forEach { s -> Pill(if (s == 0) "Never" else "$s s", stop == s) { Settings.put(context, Settings.AUTO_STOP, s) } }
            }
            if (Settings.hasCustomPosition(context)) {
                PillButton("Put the bubble back beside the text box", style = PillStyle.Outline) { Settings.resetPosition(context); app.refreshKeyState() }
            }
        }

        val polish = Settings.polish(context)
        FeatureCard(Icons.Filled.AutoFixHigh, "Clean-up", "A moment after your words are typed", polish, { Settings.put(context, Settings.POLISH, it) }) {
            Note("Misheard words are fixed from what the sentence means, punctuation and grammar corrected and filler words removed; sentences are never rewritten. If you've edited the text meanwhile, it stays yours.")
        }

        if (engine == Engine.MODEL1 || polish) KeyCard(app)

        InstructionsCard(context)
        WordsCard(context)
        MixUpsCard(context)

        FeatureCard(Icons.Filled.Keyboard, "Try it here", "Tap the box, then the mic bubble") {
            var text by remember { mutableStateOf("") }
            PillField(text, { text = it }, "Tap here, then tap the mic bubble…", singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
        }
    }
}

@Composable
private fun LabeledSlider(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(title, color = Palette.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = Palette.white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(current, onChange, valueRange = range, colors = SliderDefaults.colors(
            thumbColor = Palette.white, activeTrackColor = Palette.cream, inactiveTrackColor = Color.White.copy(alpha = 0.2f)))
    }
}

@Composable
private fun KeyCard(app: MicFlowApp) {
    val has = app.hasKeyState
    FeatureCard(Icons.Filled.Key, "API key", "Kept locked on this phone and in your account") {
        if (has) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓  Saved", color = Palette.cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillButton("Remove", style = PillStyle.Outline) { app.removeKeyByYou() }
            }
            if (app.keyRefusedState) Note("Google stopped accepting this key (the free plan is off for its Google account). Set up a new one on Home.", isError = true)
        } else {
            Note("No key on this phone yet. Set it up on Home with Google, or paste one here.")
            PasteKey(app)
        }
        Note("On the free plan, the provider of this model may use what you say to improve their products, and people may review it, so don't dictate anything sensitive.")
    }
}

@Composable
private fun InstructionsCard(context: Context) {
    val on = Settings.instructionsEnabled(context)
    var text by remember { mutableStateOf(Settings.instructions(context)) }
    FeatureCard(Icons.Filled.EditNote, "Your instructions", "Tell clean-up who you are and how you write", on, { Settings.put(context, Settings.INSTRUCTIONS_ON, it) }) {
        PillField(text, { text = it; Settings.put(context, Settings.INSTRUCTIONS, it) },
                  "e.g. I'm a UX designer: I talk about Figma, wireframes and user flows.", singleLine = false,
                  modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordsCard(context: Context) {
    val words = Settings.words(context)
    var draft by remember { mutableStateOf("") }
    fun add() {
        val word = draft.trim()
        if (word.isEmpty() || words.any { it.equals(word, ignoreCase = true) }) return
        Settings.saveWords(context, words + word)
        draft = ""
    }
    FeatureCard(Icons.Filled.Spellcheck, "My words", "${words.size} words, always spelled your way") {
        Note("Brand names, jargon, local or made-up words. Add \"Figma\" once, and it's always written \"Figma\".")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillField(draft, { draft = it }, "Add a word", modifier = Modifier.weight(1f))
            PillButton("Add", style = PillStyle.Cream) { add() }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            words.forEach { word ->
                Row(Modifier.clip(CircleShape).background(Palette.cream).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(word, color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.Close, "Remove", tint = Palette.ink, modifier = Modifier.size(16.dp).clickable { Settings.saveWords(context, words - word) })
                }
            }
        }
    }
}

@Composable
private fun MixUpsCard(context: Context) {
    val list = Settings.mixUps(context)
    val fix = Settings.fixMixUps(context)
    var heard by remember { mutableStateOf("") }
    var meant by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    FeatureCard(Icons.Filled.SwapHoriz, "Mix-ups", "Words that come out as other words", fix, { Settings.put(context, Settings.FIX_MIXUPS, it) }) {
        Note("Add \"word → world\", and \"the whole word is watching\" becomes \"world\", while \"spell this word\" stays. Clean-up fixes them.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillField(heard, { heard = it }, "Heard as", modifier = Modifier.weight(1f))
            PillField(meant, { meant = it }, "Meant", modifier = Modifier.weight(1f))
        }
        PillField(meaning, { meaning = it }, "What it means (optional)", modifier = Modifier.fillMaxWidth())
        PillButton("Add mix-up", style = PillStyle.Cream, enabled = heard.isNotBlank() && meant.isNotBlank()) {
            Settings.saveMixUps(context, list + MixUp(heard.trim(), meant.trim(), meaning.trim()))
            heard = ""; meant = ""; meaning = ""
        }
        list.forEach { m ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${m.heard}  →  ${m.meant}", color = Palette.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (m.meaning.isNotEmpty()) Text(m.meaning, color = Palette.greyOnDark, fontSize = 12.sp)
                }
                Icon(Icons.Filled.Close, "Remove", tint = Palette.white, modifier = Modifier.size(18.dp).clickable { Settings.saveMixUps(context, list - m) })
            }
        }
    }
}
