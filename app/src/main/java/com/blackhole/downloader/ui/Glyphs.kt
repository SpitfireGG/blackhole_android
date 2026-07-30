package com.blackhole.downloader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackhole.downloader.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Every icon in the app is drawn here rather than pulled from a font pack, so the
 * line weights stay consistent with the hairline circle on the home screen.
 */

@Composable
fun InfoGlyph(size: Dp = 34.dp, tint: Color = Ink.TextSecondary) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val stroke = r * 0.075f
        drawCircle(tint, radius = r - stroke, style = Stroke(width = stroke))
        // dot
        drawCircle(tint, radius = r * 0.075f, center = center.copy(y = center.y - r * 0.36f))
        // stem
        drawLine(
            tint,
            start = center.copy(y = center.y - r * 0.14f),
            end = center.copy(y = center.y + r * 0.40f),
            strokeWidth = stroke * 1.6f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun NoAdsGlyph(size: Dp = 34.dp, tint: Color = Ink.TextPrimary) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val stroke = r * 0.075f
        drawCircle(tint, radius = r - stroke, style = Stroke(width = stroke))

        // "ADS" reduced to three uprights — legible at 34dp, unlike real letterforms.
        val glyphWidth = r * 0.95f
        val step = glyphWidth / 2f
        val top = center.y - r * 0.26f
        val bottom = center.y + r * 0.26f
        for (i in 0..2) {
            val x = center.x - glyphWidth / 2f + step * i
            drawLine(
                tint,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = stroke * 1.4f,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            tint,
            start = Offset(center.x - glyphWidth / 2f, (top + bottom) / 2f),
            end = Offset(center.x + glyphWidth / 2f, (top + bottom) / 2f),
            strokeWidth = stroke * 1.4f
        )

        // the slash
        val d = r * 0.72f
        drawLine(
            tint,
            start = Offset(center.x - d, center.y + d),
            end = Offset(center.x + d, center.y - d),
            strokeWidth = stroke * 1.9f,
            cap = StrokeCap.Round
        )
    }
}

/** The curved return arrow from the videos screen. */
@Composable
fun BackGlyph(size: Dp = 34.dp, tint: Color = Ink.Amber) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f

        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.30f)
            lineTo(w * 0.70f, h * 0.30f)
            cubicTo(w * 0.95f, h * 0.30f, w * 0.95f, h * 0.72f, w * 0.70f, h * 0.72f)
            lineTo(w * 0.16f, h * 0.72f)
        }
        drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // arrow head
        val head = Path().apply {
            moveTo(w * 0.34f, h * 0.54f)
            lineTo(w * 0.14f, h * 0.72f)
            lineTo(w * 0.34f, h * 0.90f)
        }
        drawPath(head, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

/** Two interlocking cogs, matching the settings affordance on the list screen. */
@Composable
fun GearsGlyph(size: Dp = 32.dp, tint: Color = Ink.Green) {
    Canvas(Modifier.size(size)) {
        drawCog(centerX = this.size.width * 0.36f, centerY = this.size.height * 0.38f,
            radius = this.size.minDimension * 0.26f, teeth = 8, tint = tint)
        drawCog(centerX = this.size.width * 0.70f, centerY = this.size.height * 0.68f,
            radius = this.size.minDimension * 0.20f, teeth = 7, tint = tint)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCog(
    centerX: Float,
    centerY: Float,
    radius: Float,
    teeth: Int,
    tint: Color
) {
    val c = Offset(centerX, centerY)
    drawCircle(tint, radius = radius * 0.72f, center = c)
    drawCircle(Ink.BackgroundTop, radius = radius * 0.30f, center = c)
    val toothLength = radius * 0.42f
    val toothWidth = radius * 0.30f
    repeat(teeth) { i ->
        val angle = (2 * PI * i / teeth).toFloat()
        val inner = Offset(
            c.x + cos(angle) * radius * 0.62f,
            c.y + sin(angle) * radius * 0.62f
        )
        val outer = Offset(
            c.x + cos(angle) * (radius * 0.62f + toothLength),
            c.y + sin(angle) * (radius * 0.62f + toothLength)
        )
        drawLine(tint, inner, outer, strokeWidth = toothWidth, cap = StrokeCap.Round)
    }
}

/** The 2x2 ring cluster used as each row's overflow control. */
@Composable
fun FourDotsGlyph(size: Dp = 30.dp, tint: Color = Ink.Amber) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension * 0.19f
        val gap = this.size.minDimension * 0.27f
        val stroke = r * 0.55f
        listOf(
            Offset(center.x - gap, center.y - gap),
            Offset(center.x + gap, center.y - gap),
            Offset(center.x - gap, center.y + gap),
            Offset(center.x + gap, center.y + gap)
        ).forEach { drawCircle(tint, radius = r - stroke / 2, center = it, style = Stroke(stroke)) }
    }
}

/**
 * The videos affordance: a phyllotaxis spiral of dots. Golden-angle placement means
 * no two rings line up, which reads as "scattered matter" rather than a grid.
 */
@Composable
fun DotClusterGlyph(size: Dp = 96.dp, tint: Color = Ink.Amber, dots: Int = 34) {
    Canvas(Modifier.size(size)) {
        val maxR = this.size.minDimension * 0.44f
        val goldenAngle = 2.399963f
        repeat(dots) { i ->
            val t = (i + 0.5f) / dots
            val radius = maxR * sqrt(t)
            val angle = i * goldenAngle
            val dotRadius = this.size.minDimension * (0.020f + 0.028f * (1f - t))
            drawCircle(
                color = tint,
                radius = dotRadius,
                center = Offset(
                    center.x + cos(angle) * radius,
                    center.y + sin(angle) * radius
                )
            )
        }
    }
}

@Composable
fun CheckGlyph(size: Dp = 22.dp, tint: Color = Ink.Green) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.20f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.82f, h * 0.26f)
        }
        drawPath(path, tint, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round))
    }
}

@Composable
fun CrossGlyph(size: Dp = 22.dp, tint: Color = Ink.TextSecondary) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val s = w * 0.10f
        drawLine(tint, Offset(w * 0.26f, w * 0.26f), Offset(w * 0.74f, w * 0.74f), s, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.74f, w * 0.26f), Offset(w * 0.26f, w * 0.74f), s, StrokeCap.Round)
    }
}

@Composable
fun PlayGlyph(size: Dp = 26.dp, tint: Color = Ink.TextPrimary) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply {
            moveTo(w * 0.34f, w * 0.24f)
            lineTo(w * 0.78f, w * 0.50f)
            lineTo(w * 0.34f, w * 0.76f)
            close()
        }
        drawPath(path, tint)
    }
}

/** Dashed ring used behind the void while a download is resolving. */
fun dashEffect(on: Float, off: Float) = PathEffect.dashPathEffect(floatArrayOf(on, off), 0f)

internal fun Size.shortest(): Float = minDimension
