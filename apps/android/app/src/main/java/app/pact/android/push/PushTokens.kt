package app.pact.android.push

import android.content.Context
import android.util.Log
import app.pact.android.model.ClientAction
import app.pact.android.model.Platform
import app.pact.android.net.PactRepository

/**
 * Holds the current FCM token, if Firebase is configured. Everything here is
 * defensive: with no google-services.json the Firebase calls throw / no-op and
 * [current] stays null, which is a perfectly valid pact (push fields are
 * optional in the protocol).
 */
object PushTokens {
    private const val TAG = "PushTokens"

    @Volatile
    var current: String? = null
        private set

    /** Attempt to fetch the FCM token. Safe to call when Firebase is absent. */
    fun warmUp(context: Context) {
        if (!FirebaseGuard.isAvailable(context)) {
            Log.i(TAG, "Firebase not configured; running without push.")
            return
        }
        try {
            // Reflective-free but guarded: the class is only touched when present.
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        current = task.result
                        Log.i(TAG, "FCM token acquired.")
                        pushTokenToRelay()
                    } else {
                        Log.w(TAG, "FCM token fetch failed: ${task.exception?.message}")
                    }
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase token fetch threw; continuing without push: ${t.message}")
        }
    }

    fun onNewToken(token: String) {
        current = token
        pushTokenToRelay()
    }

    /** If we're already in a pact, register the token over the live socket. */
    private fun pushTokenToRelay() {
        val token = current ?: return
        if (PactRepository.credentials.value == null) return
        PactRepository.client.send(ClientAction.SetPush(token, Platform.ANDROID))
    }
}
