package app.pact.android

import android.app.Application
import app.pact.android.push.PactNotifications
import app.pact.android.push.PushTokens

class PactApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PactNotifications.ensureChannels(this)
        // Defensive: fetches an FCM token only if Firebase is configured.
        PushTokens.warmUp(this)
    }
}
