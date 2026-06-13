package app.pact.android.shield

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.pact.android.model.SeatView

/**
 * Translates the relay's view + push transitions into shield service commands.
 * The relay is authoritative; this never decides pact state, it only maps a
 * known view/transition onto "shield on / off for me / stop".
 *
 * | view / push kind            | shield action                       |
 * | night (locked, not me-pass) | start service, overlay non-allowed  |
 * | pass / grant / emergency    | clear overlay for me (this seat)    |
 * | night after pass / relock   | resume overlaying                   |
 * | broken / leave / none       | stop the service entirely           |
 */
object ShieldController {
    private val tag = "ShieldController"

    fun onView(context: Context, pactId: String, view: SeatView) {
        when (view) {
            is SeatView.Night,
            is SeatView.Ask,
            is SeatView.Waiting -> {
                // Locked and shielded: night, or asking/voting while still locked.
                applyLock(context, pactId)
            }
            is SeatView.Pass -> {
                // This seat is unshielded until remainMs expires.
                releaseForMe(context)
            }
            is SeatView.Broken,
            is SeatView.None -> {
                stop(context)
            }
            is SeatView.Join,
            is SeatView.LobbyHost,
            is SeatView.LobbyWait -> {
                // Pre-lock: no shield yet.
                stop(context)
            }
        }
    }

    fun applyLock(context: Context, pactId: String) {
        Log.d(tag, "applyLock pact=$pactId")
        send(context, ShieldService.ACTION_LOCK) { it.putExtra(ShieldService.EXTRA_PACT_ID, pactId) }
    }

    fun releaseForMe(context: Context) {
        Log.d(tag, "releaseForMe")
        send(context, ShieldService.ACTION_RELEASE)
    }

    fun stop(context: Context) {
        Log.d(tag, "stop")
        val intent = Intent(context, ShieldService::class.java).apply {
            action = ShieldService.ACTION_STOP
        }
        // Stopping is fine even if not started; service handles it.
        runCatching { context.startService(intent) }
    }

    private inline fun send(
        context: Context,
        action: String,
        configure: (Intent) -> Unit = {},
    ) {
        val intent = Intent(context, ShieldService::class.java).apply {
            this.action = action
            configure(this)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (t: Throwable) {
            Log.w(tag, "failed to start shield service: ${t.message}")
        }
    }
}
