package app.pact.android.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pact.android.model.ClientAction
import app.pact.android.net.PactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the Allow / Not now taps from an ask notification. It votes over the
 * live socket if one is open, otherwise POSTs to /pacts/:pactId/actions so a
 * locked, backgrounded phone can still vote in one tap.
 */
class AskActionReceiver : BroadcastReceiver() {

    private val tag = "AskActionReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_VOTE) return
        val pactId = intent.getStringExtra(EXTRA_PACT_ID) ?: return
        val allow = intent.getBooleanExtra(EXTRA_ALLOW, false)

        PactNotifications.cancelAsk(context)

        // Prefer the live socket if it's connected; it's instant and ordered.
        if (PactRepository.client.vote(allow)) {
            Log.i(tag, "voted over socket allow=$allow")
            return
        }

        // No socket: REST fallback with the persisted credentials.
        val creds = PactRepository.credentials.value ?: run {
            // Try a restore in case the process was cold-started by the push.
            PactRepository.restore(context)
        }
        if (creds == null) {
            Log.w(tag, "no credentials to vote with")
            return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                PactRepository.client.postAction(
                    pactId = pactId,
                    seatId = creds.seatId,
                    token = creds.token,
                    action = ClientAction.Vote(allow),
                )
                Log.i(tag, "voted over REST allow=$allow")
            } catch (t: Throwable) {
                Log.w(tag, "REST vote failed: ${t.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_VOTE = "app.pact.android.action.VOTE"
        const val EXTRA_PACT_ID = "pactId"
        const val EXTRA_ALLOW = "allow"
    }
}
