package com.blackhole.downloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The palette is lifted straight off the reference screens: near-black, one amber, one green. */
object Ink {
    val BackgroundTop = Color(0xFF141416)
    val BackgroundMid = Color(0xFF0C0C0D)
    val BackgroundBottom = Color(0xFF000000)

    val Surface = Color(0xFF141415)
    val Divider = Color(0xFF232325)

    val TextPrimary = Color(0xFFEDEDED)
    val TextSecondary = Color(0xFF8B8B8E)
    val TextTertiary = Color(0xFF5A5A5D)

    val Amber = Color(0xFFEFA860)
    val AmberDim = Color(0xFF8A5F33)
    val Green = Color(0xFF6FBF4E)
    val Red = Color(0xFFD9634F)

    val Ring = Color(0xFFC9C9C9)
    val VoidCore = Color(0xFF2E2B27)
    val VoidEdge = Color(0xFF0E0E0F)
}

private val BlackholeColors = darkColorScheme(
    primary = Ink.Amber,
    onPrimary = Color.Black,
    secondary = Ink.Green,
    background = Ink.BackgroundMid,
    onBackground = Ink.TextPrimary,
    surface = Ink.Surface,
    onSurface = Ink.TextPrimary,
    error = Ink.Red
)

/** Wide tracking on the wordmark is the whole identity; everything else stays quiet. */
val WordmarkStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    fontSize = 19.sp,
    letterSpacing = 9.sp,
    color = Ink.TextPrimary
)

val LabelStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    letterSpacing = 4.sp
)

private val BlackholeType = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
)

@Composable
fun BlackholeTheme(content: @Composable () -> Unit) {
    // Always dark, regardless of system setting. A light Blackhole is a contradiction.
    MaterialTheme(
        colorScheme = BlackholeColors,
        typography = BlackholeType,
        content = content
    )
}
