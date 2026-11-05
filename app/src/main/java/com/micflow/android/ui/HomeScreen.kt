package com.micflow.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micflow.android.DictationLog
import com.micflow.android.MicFlowApp
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.sin

private fun Int.formatted(): String = NumberFormat.getIntegerInstance().format(this)

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MicFlow", text))
}

/** Home: the same numbers and cards as the Mac's Home, from dictations on this phone only. */
@Composable
fun HomeScreen(app: MicFlowApp, openVoice: () -> Unit) {
    val log = app.log
    var listMode by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TodayCard(log) { listMode = true }
        ActivityCard(log)
        LanguagesCard(log, openVoice)
        DictationsPanel(log, listMode, { listMode = it }, openVoice)
        WeeklyCard(log)
        NumbersCard(log)
    }
}

/** The one yellow card: today's words, with a sticker. */
@Composable
private fun TodayCard(log: DictationLog, showHistory: () -> Unit) {
    val context = LocalContext.current
    val words = log.words(LocalDate.now())
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.yellow).padding(18.dp),
               verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today · " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")), color = Palette.ink,
                     fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Chip("${log.averageWPM} WPM")
            }
            Text(if (words == 0) "Tap the mic bubble\nand start talking" else "${words.formatted()} words\ndictated today",
                 style = display(27.sp), color = Palette.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (copied) "Copied" else "Copy last", color = Palette.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                     modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.orange)
                         .clickable(enabled = log.entries.isNotEmpty()) { log.entries.lastOrNull()?.let { copy(context, it.text); copied = true } }
                         .padding(horizontal = 14.dp, vertical = 8.dp))
                Text("History", color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = showHistory))
            }
        }
        // The pink mic sticker breaking out of the corner.
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = 6.dp)) {
            Box(Modifier.rotate(-10f).size(66.dp, 58.dp).clip(RoundedCornerShape(14.dp)).background(Palette.pink)
                    .border(3.dp, Palette.white, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, null, tint = Palette.cream, modifier = Modifier.size(26.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-8).dp).size(24.dp).clip(CircleShape).background(Palette.yellow)
                    .border(1.5.dp, Palette.ink, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.GraphicEq, null, tint = Palette.ink, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** The month as day circles; days with dictations are pink, today has a ring. */
@Composable
private fun ActivityCard(log: DictationLog) {
    var offset by remember { mutableIntStateOf(0) }
    val month = YearMonth.now().plusMonths(offset.toLong())
    val dictated = log.entries.map { it.day }.toSet()
    val today = LocalDate.now()
    val lead = (month.atDay(1).dayOfWeek.value - 1) // Monday first
    DarkCard {
        CardHeader(Icons.Filled.Bolt, "Activity", "Days you dictated")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("This month", offset == 0) { offset = 0 }
            Pill("Last month", offset == -1) { offset = -1 }
        }
        val cells = listOf("M", "T", "W", "T", "F", "S", "S").map { "w$it" } + List(lead) { "b" } + (1..month.lengthOfMonth()).map { "d$it" }
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { cell ->
                    Box(Modifier.weight(1f).padding(2.5.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        when {
                            cell.startsWith("w") -> Text(cell.drop(1), color = Palette.greyOnDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            cell == "b" -> Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                drawCircle(Color.White.copy(alpha = 0.4f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                            }
                            else -> {
                                val number = cell.drop(1).toInt()
                                val day = month.atDay(number)
                                val active = day in dictated
                                Box(Modifier.fillMaxWidth().aspectRatio(1f)
                                        .then(if (day == today) Modifier.border(2.dp, Palette.white, CircleShape).padding(3.dp) else Modifier)
                                        .clip(CircleShape).background(if (active) Palette.pink else Palette.cream),
                                    contentAlignment = Alignment.Center) {
                                    Text("$number", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                         color = if (active) Palette.white else Palette.ink.copy(alpha = if (day.isAfter(today)) 0.4f else 1f))
                                }
                            }
                        }
                    }
                }
                repeat(7 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Palette.white, modifier = Modifier.clickable { offset-- })
            Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${month.year}", color = Palette.white,
                 fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Palette.white.copy(alpha = if (offset < 0) 1f else 0.3f),
                 modifier = Modifier.clickable { offset = minOf(0, offset + 1) })
        }
    }
}

/** English and Bengali, with pink word-share badges. */
@Composable
private fun LanguagesCard(log: DictationLog, openVoice: () -> Unit) {
    val share = log.bengaliShare
    val any = log.totalWords > 0
    DarkCard {
        Text("Languages", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        LanguageRow("English", any && share < 1, if (any) 1 - share else 0.0)
        LanguageRow("বাংলা Bengali", share > 0, share)
        Row(Modifier.clickable(onClick = openVoice), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Add, null, tint = Palette.white, modifier = Modifier.size(16.dp))
            Text("Add a word", color = Palette.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LanguageRow(title: String, used: Boolean, percent: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (used) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
             tint = if (used) Palette.white else Palette.greyOnDark, modifier = Modifier.size(18.dp))
        Text(title, color = Palette.white, fontSize = 14.sp)
        Text("${Math.round(percent * 100)}%", color = Palette.white, fontSize = 10.sp, fontWeight = FontWeight.Bold,
             modifier = Modifier.clip(CircleShape).background(Palette.pink).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** The white panel: the last four days as columns of coloured cards, or a searchable list. */
@Composable
private fun DictationsPanel(log: DictationLog, listMode: Boolean, setList: (Boolean) -> Unit, openVoice: () -> Unit) {
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.white).padding(18.dp),
           verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM, yyyy")), style = display(28.sp), color = Palette.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.clip(CircleShape).background(Palette.lightField).padding(3.dp)) {
                Pill("Cards", !listMode, onDark = false) { setList(false) }
                Pill("List", listMode, onDark = false) { setList(true) }
            }
            Box(Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).background(Palette.yellow).clickable(onClick = openVoice).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Add, null, tint = Palette.ink, modifier = Modifier.size(16.dp))
                Text("Add word", color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Search, null, tint = Palette.greyOnLight, modifier = Modifier.size(18.dp))
            PillField(search, { search = it }, "Search your dictations…", onDark = false, modifier = Modifier.weight(1f))
        }
        val all = log.entries.reversed()
        if (listMode || search.isNotEmpty()) {
            val matches = if (search.isEmpty()) all else all.filter { it.text.contains(search, ignoreCase = true) }
            if (matches.isEmpty()) Text("No dictations match.", color = Palette.greyOnLight, fontSize = 13.sp)
            matches.take(200).forEach { ListRow(it) }
        } else {
            DayColumns(log)
        }
    }
}

@Composable
private fun DayColumns(log: DictationLog) {
    val today = LocalDate.now()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        (0..3).forEach { back ->
            val day = today.minusDays(back.toLong())
            val entries = log.entries.filter { it.day == day }.reversed()
            Column(Modifier.width(190.dp).padding(horizontal = 8.dp)
                       .then(if (back > 0) Modifier.drawBehind {
                           drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, 0f), Offset(0f, size.height), 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                       } else Modifier),
                   verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(start = 6.dp)) {
                    Text("${day.dayOfMonth}", style = display(24.sp), color = Palette.ink)
                    Text("/" + day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = Palette.greyOnLight, fontSize = 13.sp)
                    if (back == 0) Text("Today", color = Palette.white, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(Palette.pink).padding(horizontal = 7.dp, vertical = 2.dp))
                }
                if (entries.isEmpty()) EmptySlot(back == 0)
                else entries.take(8).forEachIndexed { index, entry -> DictationCard(entry, (index + back) % 5) }
            }
        }
    }
}

/** The mint dashed slot. */
@Composable
private fun EmptySlot(today: Boolean) {
    Column(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(16.dp)).background(Palette.mint)
               .drawBehind {
                   drawRoundRect(Palette.mintText.copy(alpha = 0.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f),
                                 style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
               },
           horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MicNone, null, tint = Palette.mintText, modifier = Modifier.size(18.dp))
        }
        Text(if (today) "Tap the bubble\nto dictate" else "No dictations", color = Palette.mintText, fontSize = 13.sp,
             fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** One dictation as a coloured card; tap to copy it again. */
@Composable
private fun DictationCard(entry: DictationLog.Entry, style: Int) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val background = listOf(Palette.orange, Palette.lavender, Palette.pink, Palette.yellow, Palette.cream)[style]
    val foreground = if (style >= 3) Palette.ink else Palette.white
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(background).clickable { copy(context, entry.text); copied = true }) {
        if (style == 0 || style == 2) Texture(if (style == 0) Palette.orange else Palette.pink,
                                              if (style == 0) Palette.orangeLight else Color(0xFFFFB3CF))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (copied) "Copied" else "${entry.date.atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))} · ${entry.words} words",
                 color = foreground.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(entry.text, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Flowing bands like the reference's marbled image cards. */
@Composable
private fun Texture(base: Color, light: Color) {
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        drawRect(base)
        for (band in 0 until 7) {
            val y0 = size.height * band / 6f - 12f
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, y0)
            for (step in 0..24) {
                val x = size.width * step / 24f
                path.lineTo(x, y0 + (sin(step / 3.5 + band) * 14).toFloat())
            }
            drawPath(path, light.copy(alpha = if (band % 2 == 0) 0.85f else 0.45f), style = Stroke(8f))
        }
    }
}

@Composable
private fun ListRow(entry: DictationLog.Entry) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { copy(context, entry.text); copied = true }.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.date.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a")) + " · ${entry.words} words",
                 color = Palette.greyOnLight, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(entry.text, color = Palette.ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (copied) Icons.Filled.CheckBox else Icons.Filled.ContentCopy, null, tint = Palette.greyOnLight, modifier = Modifier.size(16.dp))
    }
}

/** Words per day this week: bars, today in yellow over orange stripes, with a tooltip. */
@Composable
private fun WeeklyCard(log: DictationLog) {
    val theme = LocalTheme.current
    val days = log.lastSevenDays
    val most = maxOf(days.maxOf { it.second }, 10)
    val todayCount = log.entries.count { it.day == LocalDate.now() }
    DarkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weekly Words Overview", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Last 7 days  ${days.sumOf { it.second }.formatted()} words", color = Palette.greyOnDark, fontSize = 12.sp)
            }
            Box(Modifier.size(32.dp).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.BarChart, null, tint = Palette.white, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A3A3A)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Today", color = Palette.greyOnDark, fontSize = 11.sp)
            Legend(Palette.yellow, "Words", days.last().second)
            Legend(Palette.orange, "Dictations", todayCount)
        }
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { index, (day, words) ->
                val isToday = index == days.size - 1
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                       horizontalAlignment = Alignment.CenterHorizontally) {
                    val fraction = maxOf(0.06f, words.toFloat() / most)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction * 0.85f).clip(RoundedCornerShape(8.dp))) {
                        if (isToday) {
                            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                                drawRect(Palette.orange)
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(Palette.orangeLight, Offset(x, size.height), Offset(x + size.height, 0f), 6f)
                                    x += 16f
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(14.dp).background(Palette.yellow))
                        } else {
                            Box(Modifier.fillMaxWidth().fillMaxHeight().background(theme.bar))
                            Box(Modifier.fillMaxWidth().fillMaxHeight(0.45f).background(theme.barTop))
                        }
                    }
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 11.sp,
                         fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, color = if (isToday) Palette.white else Palette.greyOnDark)
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, title: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(title, color = Palette.white, fontSize = 11.sp)
        Text(value.formatted(), color = Palette.white, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** The numbers, as list rows. */
@Composable
private fun NumbersCard(log: DictationLog) {
    val theme = LocalTheme.current
    DarkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your Numbers", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.MoreHoriz, null, tint = Palette.white)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberRow(Icons.Filled.Speed, Palette.pink, "Average speed", "${log.averageWPM}", "WPM", theme.selected)
            NumberRow(Icons.Filled.ShortText, Palette.yellow, "Total words", log.totalWords.formatted(), "", null)
            NumberRow(Icons.Filled.LocalFireDepartment, Palette.orange, "Streak", "${log.streak}", if (log.streak == 1) "day" else "days", null)
            val minutes = log.minutesSaved
            NumberRow(Icons.Filled.HourglassBottom, Palette.lavender, "Time saved",
                      if (minutes < 60) "$minutes min" else "%dh %02dm".format(minutes / 60, minutes % 60), "", null)
        }
    }
}

@Composable
private fun NumberRow(icon: ImageVector, color: Color, title: String, value: String, unit: String, highlight: Color?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(highlight ?: Color.Transparent).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.ink, modifier = Modifier.size(14.dp))
        }
        Text(title, color = Palette.white, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (unit.isNotEmpty()) Text(unit, color = Palette.greyOnDark, fontSize = 12.sp)
    }
}
