package com.micflow.android.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micflow.android.MicFlowService
import com.micflow.android.R

/** What voice typing needs from Android, read again each time MicFlow comes to the front. */
class PermissionState(val microphone: Boolean, val accessibility: Boolean, val requestMicrophone: () -> Unit) {
    val allGood get() = microphone && accessibility

    companion object {
        fun read(context: Context, requestMicrophone: () -> Unit): PermissionState {
            val mic = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val name = ComponentName(context, MicFlowService::class.java).flattenToString()
            val enabled = SystemSettings.Secure.getString(context.contentResolver, SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val on = enabled.split(':').any { it.equals(name, ignoreCase = true) }
            return PermissionState(mic, on, requestMicrophone)
        }

        fun openAccessibility(context: Context) =
            context.startActivity(Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        fun openAppInfo(context: Context) = context.startActivity(
            Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * The permissions voice typing needs, each with a picture guide: Android adds extra steps for apps
 * installed from outside the Play Store, and people get stuck on them without seeing what to tap.
 */
@Composable
fun PermissionsCard(state: PermissionState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DarkCard {
        CardHeader(Icons.Filled.Lock, "Allow voice typing", "Two permissions, only once")
        PermissionStep(1, Icons.Filled.Mic, "Allow the microphone", "So MicFlow can hear you while you dictate.", state.microphone) {
            PillButton("Allow the microphone", Icons.Filled.Mic, PillStyle.Cream) { state.requestMicrophone() }
            Note("If Android doesn't ask any more: Settings › Apps › MicFlow › Permissions › Microphone › Allow only while using the app.")
        }
        PermissionStep(2, Icons.Filled.Accessibility, "Turn on MicFlow in Accessibility",
                       "So the mic bubble can appear beside text boxes and type your words in them.", state.accessibility) {
            GuideSteps(listOf(
                R.drawable.guide_a11y_1 to "Open Accessibility, then tap Downloaded apps (or Installed apps) › MicFlow.",
                R.drawable.guide_a11y_2 to "Turn on Use MicFlow, then tap Allow.",
            ))
            PillButton("Open Accessibility", Icons.Filled.OpenInNew, PillStyle.Cream) { PermissionState.openAccessibility(context) }
            if (Build.VERSION.SDK_INT >= 33) RestrictedHelp(context)
        }
    }
}

/** Android 13+ greys out Accessibility for apps installed from a download, until you allow it. */
@Composable
private fun RestrictedHelp(context: Context) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.cream).padding(12.dp),
           verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Is MicFlow greyed out, or does it say \"Restricted setting\"?", color = Palette.ink, fontSize = 13.sp,
                 fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Palette.ink,
                 modifier = Modifier.plainClick { open = !open })
        }
        if (open) {
            Text("Android does this for apps installed from a download instead of the Play Store. Allow it once:",
                 color = Palette.ink.copy(alpha = 0.75f), fontSize = 13.sp)
            GuideSteps(listOf(
                R.drawable.guide_restricted_1 to "Open MicFlow's App info and tap ⋮ at the top right.",
            ), onLight = true)
            Text("2.  Tap Allow restricted settings, and confirm with your PIN or fingerprint.", color = Palette.ink, fontSize = 13.sp)
            Text("3.  Go back to Accessibility and turn on MicFlow voice typing.", color = Palette.ink, fontSize = 13.sp)
            PillButton("Open MicFlow's App info", Icons.Filled.OpenInNew, PillStyle.Ink) { PermissionState.openAppInfo(context) }
        }
    }
}

@Composable
private fun PermissionStep(number: Int, icon: ImageVector, title: String, detail: String, done: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp),
           verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) Palette.mintText else Palette.orange), contentAlignment = Alignment.Center) {
                if (done) Icon(Icons.Filled.Check, null, tint = Palette.white, modifier = Modifier.size(16.dp))
                else Text("$number", color = Palette.white, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (done) "Done" else detail, color = if (done) Palette.mint else Palette.greyOnDark, fontSize = 12.5.sp)
            }
            Icon(icon, null, tint = Palette.cream, modifier = Modifier.size(18.dp))
        }
        if (!done) content()
    }
}

@Composable
fun GuideSteps(steps: List<Pair<Int, String>>, onLight: Boolean = false) {
    steps.forEachIndexed { index, (image, text) ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${index + 1}.", color = if (onLight) Palette.ink else Palette.cream, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(text, color = if (onLight) Palette.ink else Palette.white, fontSize = 13.sp)
        }
        GuideImage(image, 300)
    }
}
