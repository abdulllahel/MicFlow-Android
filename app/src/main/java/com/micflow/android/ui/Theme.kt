package com.micflow.android.ui

import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micflow.android.R

/** Colours from MicFlow's style guide (the same as the Mac app). */
object Palette {
    val pink = Color(0xFFF66399)
    val yellow = Color(0xFFFFB413)
    val orange = Color(0xFFF2621F)
    val orangeLight = Color(0xFFFF8A3D)
    val lavender = Color(0xFFB99AF6)
    val cream = Color(0xFFFBEBC9)
    val mint = Color(0xFFDDF3E2)
    val mintText = Color(0xFF2E9E57)
    val ink = Color(0xFF141414)
    val white = Color.White
    val greyOnDark = Color(0xFF9A9A9A)
    val greyOnLight = Color(0xFF8A8A8A)
    val lightField = Color(0xFFF2F2F0)
}

/** The two frames the sun/moon switch swaps between, like the Mac. */
data class HomeTheme(val frame: Color, val card: Color, val selected: Color, val bar: Color, val barTop: Color) {
    companion object {
        val dark = HomeTheme(Color(0xFF141414), Color(0xFF232323), Color(0xFF2E2E2E), Color(0xFF2C2C2C), Color(0xFF3A3A3A))
        val green = HomeTheme(Color(0xFF2B5036), Color(0xFF335E41), Color(0xFF3B6A4A), Color(0xFF2B5036), Color(0xFF44735A))
    }
}

val LocalTheme = staticCompositionLocalOf { HomeTheme.green }

/** The chunky, condensed display type of the Mac app. */
val Display = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

fun display(size: TextUnit) = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = size, lineHeight = size * 1.05f)

/** A dark card on the frame. */
@Composable
fun DarkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalTheme.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(theme.card)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Icon in a cream circle, title, grey subtitle (the Mac's card header). */
@Composable
fun CardHeader(icon: ImageVector, title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Palette.cream), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.pink, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Palette.greyOnDark, fontSize = 12.sp)
        }
        trailing()
    }
}

/** A dark card for one feature, with an optional pink switch; its settings show while it's on. */
@Composable
fun FeatureCard(icon: ImageVector, title: String, subtitle: String? = null, isOn: Boolean? = null,
                onToggle: (Boolean) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    DarkCard {
        CardHeader(icon, title, subtitle) { if (isOn != null) PinkSwitch(isOn, onToggle) }
        if (isOn != false) content()
    }
}

enum class PillStyle { Cream, Orange, Yellow, Outline, Ink, OutlineOnLight }

@Composable
fun PillButton(title: String, icon: ImageVector? = null, style: PillStyle = PillStyle.Cream, enabled: Boolean = true,
               modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background = when (style) {
        PillStyle.Cream -> Palette.cream
        PillStyle.Orange -> Palette.orange
        PillStyle.Yellow -> Palette.yellow
        PillStyle.Outline -> Color.Transparent
        PillStyle.Ink -> Palette.ink
        PillStyle.OutlineOnLight -> Palette.white
    }
    val foreground = when (style) {
        PillStyle.Cream, PillStyle.Yellow, PillStyle.OutlineOnLight -> Palette.ink
        else -> Palette.white
    }
    val border = when (style) {
        PillStyle.Outline -> BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
        PillStyle.OutlineOnLight -> BorderStroke(1.5.dp, Palette.ink.copy(alpha = 0.35f))
        else -> null
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(if (enabled) Modifier else Modifier.padding(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = foreground.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(16.dp))
        Text(title, color = foreground.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** On: pink track. Off: a faint track. */
@Composable
fun PinkSwitch(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(46.dp).height(28.dp)
            .clip(CircleShape)
            .background(if (isOn) Palette.pink else Color.White.copy(alpha = 0.18f))
            .clickable { onToggle(!isOn) }
            .padding(3.dp),
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.white))
    }
}

/** A round pill: filled when selected. */
@Composable
fun Pill(title: String, selected: Boolean, onDark: Boolean = true, onClick: () -> Unit) {
    val background = if (selected) (if (onDark) Palette.cream else Palette.ink) else Color.Transparent
    val foreground = if (selected) (if (onDark) Palette.ink else Palette.white) else (if (onDark) Palette.white else Palette.ink)
    Text(
        title, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .then(if (onDark && !selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Small grey text on a dark card; pink with a mark when it's a problem. */
@Composable
fun Note(text: String, isError: Boolean = false) {
    if (text.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isError) Text("!", color = Palette.pink, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Text(text, color = if (isError) Palette.pink else Palette.greyOnDark, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

/** The yellow hero card at the top of a page. */
@Composable
fun HeroCard(kicker: String, title: String, detail: String? = null, chip: String? = null, icon: ImageVector,
             actions: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.yellow).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kicker, color = Palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (chip != null) Chip(chip)
            }
            Text(title, style = display(28.sp), color = Palette.ink, modifier = Modifier.padding(end = 56.dp))
            if (!detail.isNullOrEmpty()) {
                Text(detail, color = Palette.ink.copy(alpha = 0.75f), fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                     lineHeight = 18.sp, modifier = Modifier.padding(end = 60.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        PageSticker(icon, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp))
    }
}

/** The white chip with a pink dot, top right of the hero. */
@Composable
fun Chip(text: String) {
    Row(
        Modifier.clip(CircleShape).background(Palette.white).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.pink))
        Text(text, color = Palette.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** A tilted pink sticker with a white edge. */
@Composable
fun PageSticker(icon: ImageVector, modifier: Modifier = Modifier, width: Dp = 60.dp, height: Dp = 54.dp) {
    Box(
        modifier
            .rotate(-10f)
            .size(width, height)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.pink)
            .border(3.dp, Palette.white, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Palette.cream, modifier = Modifier.size(24.dp))
    }
}

/** A text field on a dark card, or on a light panel. */
@Composable
fun PillField(value: String, onChange: (String) -> Unit, placeholder: String, secure: Boolean = false, onDark: Boolean = true,
              singleLine: Boolean = true, modifier: Modifier = Modifier) {
    val color = if (onDark) Palette.white else Palette.ink
    Box(
        modifier
            .clip(if (singleLine) CircleShape else RoundedCornerShape(16.dp))
            .background(if (onDark) Color.White.copy(alpha = 0.08f) else Palette.lightField)
            .then(if (onDark) Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), if (singleLine) CircleShape else RoundedCornerShape(16.dp)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = color.copy(alpha = 0.45f), fontSize = 14.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(color = color, fontSize = 14.sp),
            cursorBrush = SolidColor(if (onDark) Palette.cream else Palette.ink),
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            // A multi-line box is tappable all over, not just on its first line.
            modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 64.dp)),
        )
    }
}

/** Google's own light "Sign in with Google" button (Google's branding guidelines). */
@Composable
fun GoogleSignInButton(title: String = "Sign in with Google", large: Boolean = true, working: Boolean = false,
                       modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(if (large) 56.dp else 42.dp)
            .clip(CircleShape)
            .background(Palette.white)
            .border(1.dp, Color(0xFF747775), CircleShape)
            .clickable(enabled = !working, onClick = onClick)
            .padding(horizontal = if (large) 24.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 10.dp),
    ) {
        if (working) Spinner(Palette.ink, if (large) 22.dp else 16.dp)
        else Image(painterResource(R.drawable.google_g), null, Modifier.size(if (large) 22.dp else 18.dp))
        Text(if (working) "Waiting for Google…" else title, color = Color(0xFF1F1F1F),
             fontSize = if (large) 17.sp else 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A clearly visible loading ring. */
@Composable
fun Spinner(color: Color = Palette.ink, size: Dp = 16.dp) {
    androidx.compose.material3.CircularProgressIndicator(color = color, strokeWidth = 2.5.dp, modifier = Modifier.size(size))
}

/** A plain clickable without the ripple tint on coloured surfaces. */
fun Modifier.plainClick(onClick: () -> Unit) = this.clickable(
    interactionSource = MutableInteractionSource(), indication = null, onClick = onClick,
)

/** Light cream panel used inside the setup cards (InfoPanel on the Mac). */
@Composable
fun InfoPanel(icon: ImageVector, title: String, detail: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.cream).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Palette.yellow), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Palette.ink, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = Palette.ink.copy(alpha = 0.7f), fontSize = 12.5.sp, lineHeight = 17.sp)
            }
        }
        content()
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
