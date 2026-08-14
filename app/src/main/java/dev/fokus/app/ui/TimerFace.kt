package dev.fokus.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fokus.app.timer.TimerEngine
import dev.fokus.app.timer.formatCounter

/**
 * The countdown dial shared by the main screen and the break overlay: a progress
 * ring, the remaining time, a status caption, and one dot per focus session of the
 * cycle (the same elements as the plasmoid's overlay).
 */
@Composable
fun TimerFace(
    snapshot: TimerEngine.Snapshot,
    caption: String,
    ringColor: Color,
    trackColor: Color,
    textColor: Color,
    dimColor: Color,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 14.dp,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)
            drawArc(color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * snapshot.progress,
                useCenter = false,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatCounter(snapshot.remainingMs / 1000),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            Text(text = caption, style = MaterialTheme.typography.titleMedium, color = dimColor)
            if (snapshot.sessionsPerCycle > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    repeat(snapshot.sessionsPerCycle) { index ->
                        SessionDot(
                            color =
                                when {
                                    index < snapshot.focusIndex - 1 -> ringColor
                                    index == snapshot.focusIndex - 1 && snapshot.phase != TimerEngine.Phase.IDLE -> textColor
                                    else -> dimColor
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDot(color: Color) {
    Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = color) }
}

/** One modifier to keep the dial square and centered with margins. */
fun Modifier.dialSize(): Modifier = this.fillMaxSize(0.75f).aspectRatio(1f)
