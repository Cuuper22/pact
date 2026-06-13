package app.pact.android.net

import android.util.Log
import app.pact.android.model.ActionBody
import app.pact.android.model.ActionResponse
import app.pact.android.model.ClientAction
import app.pact.android.model.CreatePactBody
import app.pact.android.model.JoinPactBody
import app.pact.android.model.PactJson
import app.pact.android.model.Platform
import app.pact.android.model.SeatCredentials
import app.pact.android.model.SeatView
import app.pact.android.model.ServerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Connection lifecycle, surfaced to the UI for a tiny "reconnecting…" hint. */
enum class WsStatus { Idle, Connecting, Open, Reconnecting, Closed }

/**
 * The single networking surface. REST calls are suspend functions over OkHttp;
 * the live screen is a StateFlow<SeatView?> fed by a self-healing WebSocket.
 *
 * The relay is authoritative: we render whatever view it sends and never run
 * engine logic locally. Client-side countdowns (remainMs/cooldownMs) are for
 * smoothness only and are corrected by the next `state` frame.
 */
class PactClient(
    private val baseUrl: String,
) {
    private val tag = "PactClient"

    private val json = PactJson.instance
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // WS frames can be quiet for a long time; no read timeout on the socket
        // is handled by app-level pings below.
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _view = MutableStateFlow<SeatView?>(null)
    val view: StateFlow<SeatView?> = _view.asStateFlow()

    private val _status = MutableStateFlow(WsStatus.Idle)
    val status: StateFlow<WsStatus> = _status.asStateFlow()

    /** Server clock skew estimate (serverTime - localTime), for absolute deadlines. */
    @Volatile var serverClockSkewMs: Long = 0L
        private set

    private var webSocket: WebSocket? = null
    private var wsLoop: Job? = null
    private var creds: SeatCredentials? = null

    // ---- REST ----

    /** POST /pacts — start a pact. Host is seated and joined immediately. */
    suspend fun createPact(
        hostName: String,
        passMinutes: Int,
        stakes: String,
        pushToken: String?,
    ): SeatCredentials {
        val body = CreatePactBody(
            hostName = hostName,
            passMinutes = passMinutes,
            stakes = stakes,
            pushToken = pushToken,
            platform = if (pushToken != null) Platform.ANDROID else null,
        )
        return post("/pacts", json.encodeToString(body), SeatCredentials.serializer())
    }

    /** POST /pacts/:code/join — join by table code. */
    suspend fun joinPact(
        code: String,
        name: String,
        pushToken: String?,
    ): SeatCredentials {
        val body = JoinPactBody(
            name = name,
            pushToken = pushToken,
            platform = if (pushToken != null) Platform.ANDROID else null,
        )
        val path = "/pacts/${code.trim().uppercase()}/join"
        return post(path, json.encodeToString(body), SeatCredentials.serializer())
    }

    /**
     * POST /pacts/:pactId/actions — act without a live socket (e.g. a push
     * notification action handler). Returns the resulting view, or null on 409.
     */
    suspend fun postAction(
        pactId: String,
        seatId: String,
        token: String,
        action: ClientAction,
    ): SeatView? {
        val body = ActionBody(seatId, token, action)
        val resp = post("/pacts/$pactId/actions", json.encodeToString(body), ActionResponse.serializer())
        return resp.view
    }

    private suspend fun <T> post(
        path: String,
        jsonBody: String,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .post(jsonBody.toRequestBody(jsonMedia))
                .build()
            val call = http.newCall(req)
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    response.use {
                        val text = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            cont.resumeWithException(
                                PactHttpException(it.code, text),
                            )
                            return
                        }
                        try {
                            cont.resume(json.decodeFromString(deserializer, text))
                        } catch (t: Throwable) {
                            cont.resumeWithException(t)
                        }
                    }
                }
            })
        }

    // ---- WebSocket ----

    /** Connect (or reconnect) the live screen for these credentials. */
    fun connect(credentials: SeatCredentials) {
        if (creds?.wsUrl == credentials.wsUrl && wsLoop?.isActive == true) return
        creds = credentials
        wsLoop?.cancel()
        wsLoop = scope.launch { runSocketLoop(credentials) }
    }

    /** Tear down the socket; UI navigation away from a pact calls this. */
    fun disconnect() {
        wsLoop?.cancel()
        wsLoop = null
        runCatching { webSocket?.close(1000, "client closing") }
        webSocket = null
        _status.value = WsStatus.Closed
    }

    /** Send a client action over the socket. No-op (returns false) if not open. */
    fun send(action: ClientAction): Boolean {
        val ws = webSocket ?: return false
        return ws.send(json.encodeToString(ClientAction.serializer(), action))
    }

    fun ask(reason: String?) = send(ClientAction.AskAction(reason?.ifBlank { null }))
    fun vote(allow: Boolean) = send(ClientAction.Vote(allow))
    fun lock() = send(ClientAction.Lock)
    fun emergency() = send(ClientAction.Emergency)
    fun leave() = send(ClientAction.Leave)

    private suspend fun runSocketLoop(credentials: SeatCredentials) {
        var attempt = 0
        while (scope.isActiveLoop()) {
            _status.value = if (attempt == 0) WsStatus.Connecting else WsStatus.Reconnecting
            val closed = try {
                openOnce(credentials)
            } catch (t: Throwable) {
                Log.w(tag, "ws error: ${t.message}")
                true
            }
            if (!scope.isActiveLoop()) break
            if (closed) {
                attempt++
                val backoff = backoffMs(attempt)
                Log.d(tag, "ws reconnect in ${backoff}ms (attempt $attempt)")
                delay(backoff)
            } else {
                attempt = 0
            }
        }
    }

    /** Opens one socket and suspends until it closes/fails. Returns true if it should reconnect. */
    private suspend fun openOnce(credentials: SeatCredentials): Boolean =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(credentials.wsUrl).build()
            val listener = object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    webSocket = ws
                    _status.value = WsStatus.Open
                    // Optional re-sync hello, harmless if the relay ignores it.
                    ws.send(json.encodeToString(
                        ClientAction.serializer(),
                        ClientAction.Hello(credentials.seatId, credentials.token),
                    ))
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleFrame(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    runCatching { ws.close(1000, null) }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    if (cont.isActive) cont.resume(code != 1000)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(tag, "ws failure: ${t.message}")
                    if (cont.isActive) cont.resume(true)
                }
            }
            val ws = http.newWebSocket(req, listener)
            cont.invokeOnCancellation { runCatching { ws.close(1000, "cancelled") } }
        }

    private fun handleFrame(text: String) {
        val frame = try {
            json.decodeFromString<ServerFrame>(text)
        } catch (t: Throwable) {
            Log.w(tag, "bad frame: ${t.message}")
            return
        }
        when (frame) {
            is ServerFrame.Welcome -> {
                serverClockSkewMs = frame.serverTime - System.currentTimeMillis()
                _view.value = frame.view
            }
            is ServerFrame.State -> {
                serverClockSkewMs = frame.serverTime - System.currentTimeMillis()
                _view.value = frame.view
            }
            is ServerFrame.Pong -> {
                serverClockSkewMs = frame.serverTime - System.currentTimeMillis()
            }
            is ServerFrame.Error -> Log.w(tag, "relay error ${frame.code}: ${frame.message}")
        }
    }

    /** Lets the shield/push layer push a view in without owning the socket. */
    fun overrideView(view: SeatView) {
        _view.value = view
    }

    private fun CoroutineScope.isActiveLoop() = wsLoop?.isActive == true

    companion object {
        /** Capped exponential backoff with a floor; jitter avoids thundering herds. */
        fun backoffMs(attempt: Int): Long {
            val base = (500L * (1L shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(15_000L)
            val jitter = (0..250).random().toLong()
            return base + jitter
        }
    }
}

class PactHttpException(val code: Int, val bodyText: String) :
    IOException("HTTP $code: $bodyText") {
    val isConflict get() = code == 409
    val isNotFound get() = code == 404
    val isUnauthorized get() = code == 401
}
