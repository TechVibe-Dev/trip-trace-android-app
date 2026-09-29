package com.techvibedev.triptrace.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// One of four base shapes, combined with mirroring/rotation, covers
// Google's full Maneuver enum without a distinct icon per value — cheaper
// to build and maintain than ~15 separate icons, and any maneuver Google
// adds later that isn't explicitly mapped below still gets a sensible
// fallback (a straight arrow) instead of nothing at all.
private enum class ManeuverShape { STRAIGHT, TURN, UTURN, ROUNDABOUT }

private data class ManeuverIconSpec(
    val shape: ManeuverShape,
    val mirrored: Boolean = false,
    val rotationDegrees: Float = 0f,
)

private fun maneuverIconSpec(maneuver: String): ManeuverIconSpec = when (maneuver) {
    "TURN_SLIGHT_LEFT" -> ManeuverIconSpec(ManeuverShape.TURN, mirrored = true, rotationDegrees = -28f)
    "TURN_LEFT" -> ManeuverIconSpec(ManeuverShape.TURN, mirrored = true)
    "TURN_SHARP_LEFT" -> ManeuverIconSpec(ManeuverShape.TURN, mirrored = true, rotationDegrees = 28f)
    "TURN_SLIGHT_RIGHT" -> ManeuverIconSpec(ManeuverShape.TURN, rotationDegrees = -28f)
    "TURN_RIGHT" -> ManeuverIconSpec(ManeuverShape.TURN)
    "TURN_SHARP_RIGHT" -> ManeuverIconSpec(ManeuverShape.TURN, rotationDegrees = 28f)
    "UTURN_LEFT" -> ManeuverIconSpec(ManeuverShape.UTURN, mirrored = true)
    "UTURN_RIGHT" -> ManeuverIconSpec(ManeuverShape.UTURN)
    "ROUNDABOUT_LEFT" -> ManeuverIconSpec(ManeuverShape.ROUNDABOUT, mirrored = true)
    "ROUNDABOUT_RIGHT" -> ManeuverIconSpec(ManeuverShape.ROUNDABOUT)
    // Merging/forking/ramping left or right reads close enough to a
    // moderate turn in the same direction at a glance, at driving
    // distance — a dedicated shape per case wouldn't read as meaningfully
    // different in a small card icon.
    "MERGE", "FORK_LEFT", "RAMP_LEFT" -> ManeuverIconSpec(ManeuverShape.TURN, mirrored = true, rotationDegrees = -45f)
    "FORK_RIGHT", "RAMP_RIGHT" -> ManeuverIconSpec(ManeuverShape.TURN, rotationDegrees = -45f)
    // STRAIGHT and anything not explicitly listed above (e.g. a Maneuver
    // value Google adds later) — a straight arrow is the safe generic
    // fallback: never actively misleading, at worst just uninformative.
    else -> ManeuverIconSpec(ManeuverShape.STRAIGHT)
}

@Composable
fun ManeuverIcon(
    maneuver: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 34.dp,
    color: Color = Color.White,
) {
    val spec = remember(maneuver) { maneuverIconSpec(maneuver) }
    Canvas(modifier = modifier.size(iconSize)) {
        val stroke = Stroke(
            width = size.minDimension * 0.11f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        rotate(degrees = spec.rotationDegrees) {
            scale(scaleX = if (spec.mirrored) -1f else 1f, scaleY = 1f) {
                val w = size.width
                val h = size.height

                if (spec.shape == ManeuverShape.ROUNDABOUT) {
                    drawArc(
                        color = color,
                        startAngle = -20f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(w * 0.22f, h * 0.22f),
                        size = Size(w * 0.56f, h * 0.56f),
                        style = stroke,
                    )
                    val arrowhead = Path().apply {
                        moveTo(w * 0.78f, h * 0.28f)
                        lineTo(w * 0.9f, h * 0.22f)
                        lineTo(w * 0.86f, h * 0.36f)
                    }
                    drawPath(arrowhead, color = color, style = stroke)
                    return@scale
                }

                val path = Path()
                when (spec.shape) {
                    ManeuverShape.STRAIGHT -> {
                        path.moveTo(w * 0.5f, h * 0.85f)
                        path.lineTo(w * 0.5f, h * 0.2f)
                        path.moveTo(w * 0.3f, h * 0.42f)
                        path.lineTo(w * 0.5f, h * 0.15f)
                        path.lineTo(w * 0.7f, h * 0.42f)
                    }
                    ManeuverShape.TURN -> {
                        // A hook: comes up from the bottom, curves toward
                        // the top-right, ends in an arrowhead.
                        path.moveTo(w * 0.32f, h * 0.85f)
                        path.lineTo(w * 0.32f, h * 0.5f)
                        path.cubicTo(w * 0.32f, h * 0.22f, w * 0.5f, h * 0.18f, w * 0.72f, h * 0.2f)
                        path.moveTo(w * 0.55f, h * 0.06f)
                        path.lineTo(w * 0.78f, h * 0.2f)
                        path.lineTo(w * 0.55f, h * 0.34f)
                    }
                    ManeuverShape.UTURN -> {
                        path.moveTo(w * 0.68f, h * 0.85f)
                        path.lineTo(w * 0.68f, h * 0.42f)
                        path.cubicTo(w * 0.68f, h * 0.16f, w * 0.32f, h * 0.16f, w * 0.32f, h * 0.42f)
                        path.lineTo(w * 0.32f, h * 0.62f)
                        path.moveTo(w * 0.18f, h * 0.48f)
                        path.lineTo(w * 0.32f, h * 0.68f)
                        path.lineTo(w * 0.46f, h * 0.48f)
                    }
                    ManeuverShape.ROUNDABOUT -> Unit // handled above
                }
                drawPath(path, color = color, style = stroke)
            }
        }
    }
}
