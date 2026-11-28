// Colours, text styles and round-screen layout rules shared by every Stage 3 screen.
// Text sizes stay at or above 12 sp (captions) and 15 sp (rows) for reading at a glance; content
// keeps clear of the round edge with a side inset that grows with the screen (432 to 498 px).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import io.github.shanebl030glitch.rbsbweather.sync.WarningLevel

object Palette {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF1B1F27)
    val CardPressed = Color(0xFF262B35)
    val Divider = Color(0xFF2A2F38)
    val Text = Color(0xFFFFFFFF)
    val TextSoft = Color(0xFFDDE3EA)
    val Quiet = Color(0xFFA9B3C2)
    val Accent = Color(0xFF9FC6FF)
    val Rain = Color(0xFF7FC0FF)
    val Wind = Color(0xFFFFB347)
    val Sample = Color(0xFFFFC94D)
    val Amber = Color(0xFFE7A93B)
    val Expired = Color(0xFFE2453C)
    val MoonTitle = Color(0xFFC8D6FF)

    /** Warning card colours by level, as the phone shows them (background, text). */
    fun warning(level: WarningLevel): Pair<Color, Color> = when (level) {
        WarningLevel.RED -> Color(0xFFD7392F) to Color.White
        WarningLevel.ORANGE -> Color(0xFFF39A2B) to Color(0xFF1B1200)
        WarningLevel.YELLOW -> Color(0xFFF2D23C) to Color(0xFF1B1600)
        WarningLevel.WATCH -> Color(0xFF4F7DBF) to Color.White
        WarningLevel.MINOR -> Color(0xFF3A4352) to Color(0xFFE9EDF3)
        WarningLevel.LIMIT -> Color(0xFF3B4A63) to Color(0xFFE9EDF3)
    }
}

val SkyShadow = Shadow(Color(0xCC000000), Offset(0f, 1.5f), 5f)

object Type {
    val SectionTitle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
    val Row = TextStyle(fontSize = 15.sp, color = Palette.Text)
    val RowBold = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
    val Body = TextStyle(fontSize = 14.sp, color = Palette.TextSoft, lineHeight = 19.sp)
    val Caption = TextStyle(fontSize = 12.sp, color = Palette.Quiet, lineHeight = 16.sp)
    val Big = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
}

/** Round-screen layout: the screen size and a side inset that keeps rows inside the circle. */
object Round {
    @Composable fun screenDp(): Dp = LocalConfiguration.current.screenWidthDp.dp

    /** Side inset for list content: 11% of the screen width each side. */
    @Composable fun side(): Dp = (LocalConfiguration.current.screenWidthDp * 0.11f).dp

    /** Below this width (dp) long names are shortened (432 px screens). */
    const val NARROW_DP = 230

    @Composable fun narrow(): Boolean = LocalConfiguration.current.screenWidthDp < NARROW_DP
}

/** A small rounded label ("SAMPLE DATA", "Old data · from 14:05"). */
@Composable
fun Chip(text: String, background: Color, color: Color, modifier: Modifier = Modifier, bold: Boolean = true) {
    Text(
        text, modifier = modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
        style = TextStyle(fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = color),
        maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
    )
}

/** A centred section heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.SectionTitle, textAlign = TextAlign.Center)
    }
}
