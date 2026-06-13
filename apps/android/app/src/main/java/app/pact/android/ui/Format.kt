package app.pact.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.max

/** mm:ss for a clock-style present-time read by the whole table. */
fun formatClock(ms: Long): String {
    val totalSeconds = max(0L, ms / 1000L)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

/** Short "1:23" remaining label for passes / asks. */
fun formatRemaining(ms: Long): String {
    val totalSeconds = max(0L, (ms + 999) / 1000L) // round up so it doesn't show 0:00 early
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

/**
 * A smooth client-side countdown anchored on an absolute end time. The relay's
 * next `state` frame is still truth; this just animates between frames.
 *
 * @param remainMs the remaining ms from the latest server view
 * @return remaining ms, ticking down locally, never below 0
 */
@Composable
fun rememberCountdown(remainMs: Long): Long {
    // Anchor to a local end-time whenever the server value changes.
    val endAt = remember(remainMs) { System.currentTimeMillis() + remainMs.coerceAtLeast(0) }
    var now by remember(remainMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(remainMs) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= endAt) break
            delay(200)
        }
        now = endAt
    }
    return (endAt - now).coerceAtLeast(0)
}

/**
 * A smooth client-side stopwatch anchored on present-time. Counts up from the
 * server's presentMs at the moment it was received.
 */
@Composable
fun rememberPresentClock(presentMs: Long): Long {
    val anchor = remember(presentMs) { System.currentTimeMillis() - presentMs }
    var now by remember(presentMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(presentMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    return (now - anchor).coerceAtLeast(0)
}
