package app.pact.android.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pact.android.model.SeatCredentials
import app.pact.android.model.SeatView
import app.pact.android.net.PactHttpException
import app.pact.android.net.PactRepository
import app.pact.android.net.WsStatus
import app.pact.android.push.PushTokens
import app.pact.android.shield.ShieldController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the create/join calls are doing, for button states + error copy. */
sealed interface FlowState {
    data object Idle : FlowState
    data object Working : FlowState
    data class Error(val message: String) : FlowState
}

/**
 * The single screen-facing state holder. It owns the create/join lifecycle and
 * mirrors the live SeatView from the relay. The relay is authoritative: this VM
 * never computes pact state, it only renders the view and sends actions.
 */
class PactViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PactRepository
    private val client get() = repo.client

    val view: StateFlow<SeatView?> = repo.view
    val status: StateFlow<WsStatus> = repo.status
    val credentials: StateFlow<SeatCredentials?> = repo.credentials

    private val _flow = MutableStateFlow<FlowState>(FlowState.Idle)
    val flow: StateFlow<FlowState> = _flow.asStateFlow()

    /** True once the user has progressed past the simple "no session" landing. */
    private val _hasSession = MutableStateFlow(false)

    init {
        // Reconnect to any persisted session on process start.
        repo.restore(context())
        if (repo.credentials.value != null) _hasSession.value = true

        // Drive the shield off the live view + credentials, server state as truth.
        viewModelScope.launch {
            combine(repo.view, repo.credentials) { v, c -> v to c }
                .collect { (v, c) ->
                    if (v != null && c != null) {
                        ShieldController.onView(context(), c.pactId, v)
                    }
                }
        }
    }

    private fun context(): Context = getApplication<Application>().applicationContext

    val isReconnecting: StateFlow<Boolean> = status
        .map { it == WsStatus.Reconnecting || it == WsStatus.Connecting }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun createPact(hostName: String, passMinutes: Int, stakes: String) {
        if (_flow.value is FlowState.Working) return
        _flow.value = FlowState.Working
        viewModelScope.launch {
            try {
                val token = PushTokens.current
                val creds = client.createPact(hostName.trim(), passMinutes, stakes.trim(), token)
                repo.start(context(), creds)
                _hasSession.value = true
                _flow.value = FlowState.Idle
            } catch (t: Throwable) {
                _flow.value = FlowState.Error(humanError(t, joining = false))
            }
        }
    }

    fun joinPact(code: String, name: String) {
        if (_flow.value is FlowState.Working) return
        _flow.value = FlowState.Working
        viewModelScope.launch {
            try {
                val token = PushTokens.current
                val creds = client.joinPact(code.trim(), name.trim(), token)
                repo.start(context(), creds)
                _hasSession.value = true
                _flow.value = FlowState.Idle
            } catch (t: Throwable) {
                _flow.value = FlowState.Error(humanError(t, joining = true))
            }
        }
    }

    fun clearFlowError() {
        if (_flow.value is FlowState.Error) _flow.value = FlowState.Idle
    }

    // ---- WS actions ----
    fun lock() = client.lock()
    fun ask(reason: String?) = client.ask(reason)
    fun vote(allow: Boolean) = client.vote(allow)
    fun emergency() = client.emergency()

    fun leave() {
        client.leave()
        // The relay will send a `broken`/`none` view; the shield stops on that.
    }

    /** Called from the Recap "Done" — fully end the local session. */
    fun endSession() {
        ShieldController.stop(context())
        repo.clear(context())
        _hasSession.value = false
    }

    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    private fun humanError(t: Throwable, joining: Boolean): String = when (t) {
        is PactHttpException -> when {
            t.isNotFound -> "That table code wasn't found. Check it and try again."
            t.isConflict && joining -> "This table has already locked. Ask the host to start a new one."
            t.isUnauthorized -> "This session is no longer valid."
            else -> "The relay rejected that (HTTP ${t.code})."
        }
        else -> "Couldn't reach the relay. Check your connection and that the relay is running."
    }
}
