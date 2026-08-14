package dev.fokus.app.timer

import java.util.Locale

/**
 * The countdown as MM:SS, with minutes accumulating past an hour. Negative input is a
 * break that ran past its end and is rendered with a leading minus, like the plasmoid.
 */
fun formatCounter(totalSeconds: Long): String {
    val sign = if (totalSeconds < 0) "-" else ""
    val magnitude = kotlin.math.abs(totalSeconds)
    return String.format(Locale.US, "%s%02d:%02d", sign, magnitude / 60, magnitude % 60)
}
