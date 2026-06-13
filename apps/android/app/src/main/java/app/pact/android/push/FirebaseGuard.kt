package app.pact.android.push

import android.content.Context
import android.util.Log

/**
 * Detects whether Firebase has a usable configuration WITHOUT requiring the
 * google-services Gradle plugin or a google-services.json at build time.
 *
 * Without the plugin, the string resources the Firebase SDK auto-initializes
 * from (google_app_id / gcm_defaultSenderId) are absent, so we can detect
 * configuration purely from resources and never crash if it's missing.
 */
object FirebaseGuard {
    private const val TAG = "FirebaseGuard"

    @Volatile private var cached: Boolean? = null

    fun isAvailable(context: Context): Boolean {
        cached?.let { return it }
        val available = try {
            val appId = resString(context, "google_app_id")
            val senderId = resString(context, "gcm_defaultSenderId")
            val apiKey = resString(context, "google_api_key")
            val ok = !appId.isNullOrBlank() && !senderId.isNullOrBlank() && !apiKey.isNullOrBlank()
            if (ok) {
                // Initialize defensively; absence or any failure leaves push off.
                runCatching {
                    com.google.firebase.FirebaseApp.initializeApp(context)
                }.onFailure { Log.w(TAG, "FirebaseApp.initializeApp failed: ${it.message}") }
            }
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase availability check threw: ${t.message}")
            false
        }
        cached = available
        return available
    }

    private fun resString(context: Context, name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) null else runCatching { context.getString(id) }.getOrNull()
    }
}
