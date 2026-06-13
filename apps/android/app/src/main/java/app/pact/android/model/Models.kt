package app.pact.android.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Wire + domain models, mirroring packages/engine/src/types.ts deliberately.
 * The relay is authoritative; these decode the views it sends and encode the
 * actions we send back.
 */

@Serializable
enum class Platform {
    @SerialName("ios") IOS,
    @SerialName("android") ANDROID,
}

/** A lightweight member descriptor for in-session rosters (MemberView). */
@Serializable
data class MemberView(
    val id: String,
    val name: String,
    val host: Boolean = false,
)

/** A vote line in a tally. [vote] is null while the seat is still deciding. */
@Serializable
data class TallyEntry(
    val seatId: String,
    val name: String,
    val vote: Boolean? = null,
)

/** The end-of-pact summary card. */
@Serializable
data class Recap(
    val presentMs: Long,
    val asks: Int,
    val granted: Int,
    val denied: Int,
    val brokenBy: String? = null,
    val stakes: String,
)

/**
 * SeatView: a discriminated union keyed by "screen". kotlinx.serialization
 * decodes the right subtype from the JSON class discriminator.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("screen")
sealed class SeatView {
    abstract val me: MemberView?

    @Serializable
    @SerialName("none")
    data object None : SeatView() {
        override val me: MemberView? get() = null
    }

    @Serializable
    @SerialName("join")
    data class Join(
        override val me: MemberView,
        val code: String,
    ) : SeatView()

    @Serializable
    @SerialName("lobby-host")
    data class LobbyHost(
        override val me: MemberView,
        val code: String,
        val members: List<MemberView>,
        val canLock: Boolean,
        val stakes: String,
    ) : SeatView()

    @Serializable
    @SerialName("lobby-wait")
    data class LobbyWait(
        override val me: MemberView,
        val members: List<MemberView>,
    ) : SeatView()

    @Serializable
    @SerialName("night")
    data class Night(
        override val me: MemberView,
        val members: List<MemberView>,
        val presentMs: Long,
        val stakes: String,
        val canAsk: Boolean? = null,
        val cooldownMs: Long? = null,
        val notice: String? = null,
        val banner: String? = null,
    ) : SeatView()

    @Serializable
    @SerialName("ask")
    data class Ask(
        override val me: MemberView,
        val members: List<MemberView>,
        val presentMs: Long,
        val stakes: String,
        val asker: String,
        val reason: String,
        val remainMs: Long,
        val tally: List<TallyEntry>,
    ) : SeatView()

    @Serializable
    @SerialName("waiting")
    data class Waiting(
        override val me: MemberView,
        val members: List<MemberView>,
        val presentMs: Long,
        val stakes: String,
        val reason: String,
        val tally: List<TallyEntry>,
        val remainMs: Long,
    ) : SeatView()

    @Serializable
    @SerialName("pass")
    data class Pass(
        override val me: MemberView,
        val members: List<MemberView>,
        val presentMs: Long,
        val stakes: String,
        val remainMs: Long,
        val emergency: Boolean,
    ) : SeatView()

    @Serializable
    @SerialName("broken")
    data class Broken(
        override val me: MemberView,
        val recap: Recap,
    ) : SeatView()
}

/** Returned by POST /pacts and POST /pacts/:code/join. */
@Serializable
data class SeatCredentials(
    val pactId: String,
    val code: String,
    val seatId: String,
    val token: String,
    val wsUrl: String,
    val joinLink: String,
)

// ---- REST request/response bodies ----

@Serializable
data class CreatePactBody(
    val hostName: String,
    val passMinutes: Int = 5,
    val stakes: String = "",
    val pushToken: String? = null,
    val platform: Platform? = null,
)

@Serializable
data class JoinPactBody(
    val name: String,
    val pushToken: String? = null,
    val platform: Platform? = null,
)

/** POST /pacts/:pactId/actions body. */
@Serializable
data class ActionBody(
    val seatId: String,
    val token: String,
    val action: ClientAction,
)

/** Response from POST /pacts/:pactId/actions. */
@Serializable
data class ActionResponse(
    val ok: Boolean,
    val view: SeatView? = null,
)

// ---- Client -> server actions (also the WS client frames) ----

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ClientAction {
    @Serializable @SerialName("ping") data object Ping : ClientAction()

    @Serializable
    @SerialName("hello")
    data class Hello(val seatId: String, val token: String) : ClientAction()

    @Serializable @SerialName("lock") data object Lock : ClientAction()

    @Serializable
    @SerialName("ask")
    data class AskAction(val reason: String? = null) : ClientAction()

    @Serializable
    @SerialName("vote")
    data class Vote(val allow: Boolean) : ClientAction()

    @Serializable @SerialName("emergency") data object Emergency : ClientAction()

    @Serializable @SerialName("leave") data object Leave : ClientAction()

    @Serializable
    @SerialName("setPush")
    data class SetPush(val pushToken: String, val platform: Platform) : ClientAction()
}

// ---- Server -> client WS frames ----

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ServerFrame {
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val pactId: String,
        val serverTime: Long,
        val view: SeatView,
    ) : ServerFrame()

    @Serializable
    @SerialName("state")
    data class State(
        val serverTime: Long,
        val view: SeatView,
    ) : ServerFrame()

    @Serializable
    @SerialName("pong")
    data class Pong(val serverTime: Long) : ServerFrame()

    @Serializable
    @SerialName("error")
    data class Error(val code: String, val message: String) : ServerFrame()
}

/**
 * Shared JSON codec. Discriminators are declared per-hierarchy via
 * @JsonClassDiscriminator ("type" for frames/actions, "screen" for SeatView),
 * so one configured instance decodes nested views correctly.
 */
object PactJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
