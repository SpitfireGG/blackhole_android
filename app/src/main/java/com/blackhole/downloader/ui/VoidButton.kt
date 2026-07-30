package com.blackhole.downloader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackhole.downloader.ui.theme.Ink

/**
 * The signature element. At rest it's an event horizon: a hairline ring around a
 * faintly lit sphere. While a download runs, the ring becomes the progress track and
 * a slow accretion sweep orbits it. Everything else on the screen stays still.
 */
@Composable
fun VoidButton(
    diameter: Dp,
    progress: Float?,
    working: Boolean,
    enabled: Boolean = true,
    contentDescription: String,
    onClick: () -> Unit
) {
    val label = contentDescription
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val transition = rememberInfiniteTransition(label = "void")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(
        modifier = Modifier
            .size(diameter)
            .semantics { this.contentDescription = label }
            .selectable(
                selected = working,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        val outerRadius = size.minDimension / 2f
        val ringWidth = outerRadius * 0.022f
        val squash = if (pressed) 0.972f else 1f
        val radius = outerRadius * squash

        // The sphere: brightest slightly above centre, so it reads as lit from the front.
        val coreCenter = Offset(center.x, center.y - radius * 0.06f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Ink.VoidCore.copy(alpha = if (working) 0.95f else 0.78f),
                    Ink.VoidEdge,
                    Color.Black
                ),
                center = coreCenter,
                radius = radius * (if (working) breathe else 1f)
            ),
            radius = radius - ringWidth,
            center = center
        )

        // Base ring — a sweep gradient gives the highlight that runs down the left edge.
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Ink.Ring.copy(alpha = 0.95f),
                    Ink.Ring.copy(alpha = 0.35f),
                    Ink.Ring.copy(alpha = 0.75f),
                    Ink.Ring.copy(alpha = 0.30f),
                    Ink.Ring.copy(alpha = 0.95f)
                ),
                center = center
            ),
            radius = radius - ringWidth / 2f,
            style = Stroke(width = ringWidth),
            center = center
        )

        if (working) {
            val trackRadius = radius - ringWidth * 3.2f
            val arcSize = Size(trackRadius * 2, trackRadius * 2)
            val topLeft = Offset(center.x - trackRadius, center.y - trackRadius)

            if (progress != null && progress > 0.5f) {
                drawArc(
                    color = Ink.Amber,
                    startAngle = -90f,
                    sweepAngle = 360f * (progress / 100f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = ringWidth * 2.4f, cap = StrokeCap.Round)
                )
            } else {
                // Indeterminate: a short arc orbiting the horizon.
                rotate(degrees = sweep, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                Ink.Amber.copy(alpha = 0.15f),
                                Ink.Amber
                            ),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 78f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = ringWidth * 2.4f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
