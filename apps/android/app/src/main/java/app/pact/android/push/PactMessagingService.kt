package app.pact.android.push

import android.util.Log
import app.pact.android.shield.ShieldController
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Guarded FCM receiver. It extends FirebaseMessagingService so it's wired in
 * the manifest, but the whole app builds and launches with no google-services
 * config — in that case the service simply never receives a message.
 *
 * Handles data-only messages: { pactId, kind, title, body }.
 *  - kind=ask    -> post an actionable Allow / Not now notification.
 *  - other kinds -> drive the shield transition (lock / grant / relock / leave …).
 */
class PactMessagingService : FirebaseMessagingService() {

    private val tag = "PactMessaging"

    override fun onNewToken(token: String) {
        Log.i(tag, "onNewToken")
        try {
            PushTokens.onNewToken(token)
        } catch (t: Throwable) {
            Log.w(tag, "onNewToken handling failed: ${t.message}")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val pactId = data["pactId"] ?: run {
            Log.w(tag, "push without pactId; ignoring")
            return
        }
        val kind = data["kind"].orEmpty()
        val title = data["title"]
        val body = data["body"]
        Log.i(tag, "push kind=$kind pact=$pactId")

        try {
            when (kind) {
                "ask" -> {
                    val reason = data["reason"] ?: body ?: "No reason"
                    PactNotifications.showAsk(
                        context = applicationContext,
                        pactId = pactId,
                        title = title ?: "Someone's asking the table",
                        reason = reason,
                    )
                }
                "lock" -> ShieldController.applyLock(applicationContext, pactId)
                "grant", "emergency" -> ShieldController.releaseForMe(applicationContext)
                "relock" -> ShieldController.applyLock(applicationContext, pactId)
                "deny" -> {
                    // No state change to the shield; surface a quiet nudge.
                    PactNotifications.showInfo(applicationContext, title ?: "Not now", body)
                }
                "leave" -> ShieldController.stop(applicationContext)
                else -> Log.d(tag, "unhandled kind=$kind")
            }
        } catch (t: Throwable) {
            Log.w(tag, "onMessageReceived handling failed: ${t.message}")
        }
    }
}
