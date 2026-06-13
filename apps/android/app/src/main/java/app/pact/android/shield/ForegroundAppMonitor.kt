package app.pact.android.shield

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Reads UsageStatsManager to determine the currently foregrounded package.
 * Requires the PACKAGE_USAGE_STATS special access (granted in Settings).
 *
 * We poll recent MOVE_TO_FOREGROUND events rather than aggregate stats so the
 * read is fresh enough to react quickly when a user opens an app.
 */
class ForegroundAppMonitor(context: Context) {

    private val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** The package most recently moved to the foreground in the last window. */
    fun currentForegroundPackage(windowMs: Long = 10_000L): String? {
        val um = usage ?: return null
        val end = System.currentTimeMillis()
        val begin = end - windowMs
        val events = try {
            um.queryEvents(begin, end)
        } catch (_: Throwable) {
            return null
        }
        var lastPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }
}
