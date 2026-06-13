package app.pact.android.shield

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.pact.android.MainActivity
import app.pact.android.R
import app.pact.android.push.PactNotifications
import app.pact.android.ui.theme.isReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that enforces the shield while a pact is locked. It polls
 * the foreground app via UsageStatsManager and, for any non-allowlisted app,
 * raises a full-screen overlay showing the night screen with "Ask the table".
 *
 * Hard rules (architecture + Play policy):
 *  - The emergency dialer, phone, and in-call UI are NEVER overlaid.
 *  - During this seat's own pass / emergency, the overlay is cleared.
 *  - On leave / broken, the service stops entirely.
 *
 * The relay remains authoritative: ShieldController feeds lock/release/stop here
 * off live WS state and push; this service does not interpret pact rules.
 */
class ShieldService : LifecycleService() {

    private val tag = "ShieldService"

    private lateinit var monitor: ForegroundAppMonitor
    private lateinit var overlay: ShieldOverlay
    private var allowlist: Set<String> = emptySet()

    private var pollJob: Job? = null

    /** True when this seat is locked (should shield). False during a pass. */
    @Volatile private var shielding = false
    @Volatile private var pactId: String? = null

    override fun onCreate() {
        super.onCreate()
        monitor = ForegroundAppMonitor(this)
        overlay = ShieldOverlay(this)
        allowlist = ShieldAllowlist.build(this)
        PactNotifications.ensureChannels(this)
        startForegroundShield()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_LOCK -> {
                pactId = intent.getStringExtra(EXTRA_PACT_ID) ?: pactId
                shielding = true
                ensurePolling()
            }
            ACTION_RELEASE -> {
                // This seat got a pass / emergency: clear the overlay for me.
                shielding = false
                overlay.hide()
            }
            ACTION_STOP -> {
                stopShield()
                return START_NOT_STICKY
            }
            else -> { /* recreated by the system: keep current state */ }
        }
        // STICKY so the shield survives transient process death during a pact.
        return START_STICKY
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = lifecycleScope.launch {
            while (isActive) {
                tick()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun tick() {
        if (!shielding) {
            if (overlay.isShown) overlay.hide()
            return
        }
        // If usage access was revoked mid-pact, we can't see the foreground app.
        // Fail open (don't overlay blindly) — the table still sees the heartbeat
        // drop on the relay. Visibility over prevention.
        if (!hasUsageAccess(this)) {
            if (overlay.isShown) overlay.hide()
            return
        }
        val pkg = monitor.currentForegroundPackage() ?: return

        // NEVER overlay the dialer / in-call / SOS / our own app / launcher.
        val allowed = pkg in allowlist
        if (allowed) {
            if (overlay.isShown) overlay.hide()
        } else {
            if (!overlay.isShown) {
                overlay.show(
                    reducedMotion = isReducedMotion(this),
                    onAsk = { openAppForAsk() },
                )
            }
        }
    }

    private fun openAppForAsk() {
        // Route the ask through the app's own night/ask flow over the live socket.
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_ASK, true)
        }
        runCatching { startActivity(intent) }
    }

    private fun stopShield() {
        shielding = false
        pollJob?.cancel()
        pollJob = null
        overlay.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        overlay.destroy()
        super.onDestroy()
    }

    private fun startForegroundShield() {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, PactNotifications.CHANNEL_SHIELD)
            .setSmallIcon(R.drawable.ic_pact_flame)
            .setContentTitle(getString(R.string.shield_notif_title))
            .setContentText(getString(R.string.shield_notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SHIELD_NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(SHIELD_NOTIFICATION_ID, notif)
        }
    }

    companion object {
        const val ACTION_LOCK = "app.pact.android.shield.LOCK"
        const val ACTION_RELEASE = "app.pact.android.shield.RELEASE"
        const val ACTION_STOP = "app.pact.android.shield.STOP"
        const val EXTRA_PACT_ID = "pactId"

        private const val SHIELD_NOTIFICATION_ID = 7301
        private const val POLL_INTERVAL_MS = 800L

        fun hasUsageAccess(context: Context): Boolean {
            return try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
                    as android.app.AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName,
                    )
                }
                mode == android.app.AppOpsManager.MODE_ALLOWED
            } catch (_: Throwable) {
                false
            }
        }

        fun hasOverlayPermission(context: Context): Boolean =
            Settings.canDrawOverlays(context)
    }
}
