package app.pact.android.net

import android.content.Context
import app.pact.android.BuildConfig
import app.pact.android.model.SeatCredentials
import app.pact.android.model.SeatView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Process-wide singleton tying together the live PactClient, the current
 * seat credentials, and a thin persistence layer (so the shield service and
 * the push receiver can act on the same pact the UI is in).
 *
 * Sessions are ephemeral by design — we persist only enough to reconnect and
 * to let a background push act. Cleared on leave / broken.
 */
object PactRepository {
    val client: PactClient by lazy { PactClient(Config.relayBaseUrl) }

    private val _credentials = MutableStateFlow<SeatCredentials?>(null)
    val credentials: StateFlow<SeatCredentials?> = _credentials.asStateFlow()

    val view: StateFlow<SeatView?> get() = client.view
    val status get() = client.status

    private const val PREFS = "pact_session"
    private const val KEY_CREDS = "credentials"

    private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun start(context: Context, credentials: SeatCredentials) {
        _credentials.value = credentials
        persist(context, credentials)
        client.connect(credentials)
    }

    /** Reconnect to a persisted session (e.g. after a process restart). */
    fun restore(context: Context): SeatCredentials? {
        if (_credentials.value != null) return _credentials.value
        val raw = prefs(context).getString(KEY_CREDS, null) ?: return null
        val creds = runCatching { storeJson.decodeFromString<SeatCredentials>(raw) }.getOrNull()
            ?: return null
        _credentials.value = creds
        client.connect(creds)
        return creds
    }

    fun clear(context: Context) {
        client.disconnect()
        _credentials.value = null
        prefs(context).edit().remove(KEY_CREDS).apply()
    }

    private fun persist(context: Context, creds: SeatCredentials) {
        prefs(context).edit()
            .putString(KEY_CREDS, storeJson.encodeToString(creds))
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Build-time + runtime configuration. Relay URL comes from BuildConfig. */
object Config {
    val relayBaseUrl: String get() = BuildConfig.RELAY_BASE_URL
}
