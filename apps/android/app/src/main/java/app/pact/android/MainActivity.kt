package app.pact.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.pact.android.ui.PactViewModel
import app.pact.android.ui.nav.PactNavHost
import app.pact.android.ui.theme.PactTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pendingJoinCode = extractJoinCode(intent)
        // EXTRA_OPEN_ASK is set when launched from the shield overlay's "Ask the
        // table" button; the night/ask flow is reached via the live view, so no
        // extra routing is needed here beyond bringing the app forward.

        setContent {
            PactTheme {
                val view by viewModel.view.collectAsStateWithLifecycle()
                val hasSession by viewModel.hasSession.collectAsStateWithLifecycle()
                val flow by viewModel.flow.collectAsStateWithLifecycle()
                val reconnecting by viewModel.isReconnecting.collectAsStateWithLifecycle()

                val deepLinkCode = remember { mutableStateOf(pendingJoinCode) }

                PactNavHost(
                    viewModel = viewModel,
                    view = view,
                    hasSession = hasSession,
                    flowState = flow,
                    reconnecting = reconnecting,
                    deepLinkJoinCode = deepLinkCode.value,
                    onDeepLinkConsumed = { deepLinkCode.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Deep-link / ask routing on an already-running task is handled by the
        // nav host reading the latest intent; recreate-free for singleTask.
    }

    /** Parses pact://join?code=TBL-XXXX from a VIEW intent. */
    private fun extractJoinCode(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "pact") return null
        return data.getQueryParameter("code")
    }

    companion object {
        const val EXTRA_OPEN_ASK = "app.pact.android.OPEN_ASK"
    }
}
