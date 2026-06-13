package app.pact.android.ui.nav

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pact.android.model.SeatView
import app.pact.android.ui.FlowState
import app.pact.android.ui.PactViewModel
import app.pact.android.ui.screens.AskScreen
import app.pact.android.ui.screens.BrokenScreen
import app.pact.android.ui.screens.HomeScreen
import app.pact.android.ui.screens.JoinScreen
import app.pact.android.ui.screens.LobbyHostScreen
import app.pact.android.ui.screens.LobbyWaitScreen
import app.pact.android.ui.screens.NightScreen
import app.pact.android.ui.screens.PassScreen
import app.pact.android.ui.screens.PermissionsScreen
import app.pact.android.ui.screens.StartScreen
import app.pact.android.ui.screens.WaitingScreen

object Routes {
    const val HOME = "home"
    const val PERMISSIONS = "permissions"
    const val START = "start"
    const val JOIN = "join"
    const val JOIN_WITH_CODE = "join?code={code}"
}

/**
 * Two regimes:
 *  - No session: a small pre-pact nav graph (home / permissions / start / join).
 *  - In a session: the screen is driven entirely by the relay's SeatView. We
 *    crossfade between view screens (reduced-motion-friendly, 200ms).
 */
@Composable
fun PactNavHost(
    viewModel: PactViewModel,
    view: SeatView?,
    hasSession: Boolean,
    flowState: FlowState,
    reconnecting: Boolean,
    deepLinkJoinCode: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    if (hasSession && view != null && view !is SeatView.None) {
        InSessionRouter(viewModel, view, reconnecting)
        return
    }

    val navController = rememberNavController()

    // A pact:// deep link routes straight to Join, pre-filling the code.
    LaunchedEffect(deepLinkJoinCode) {
        if (deepLinkJoinCode != null) {
            navController.navigate("join?code=$deepLinkJoinCode")
            onDeepLinkConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStart = { navController.navigate(Routes.START) },
                onJoin = { navController.navigate(Routes.JOIN) },
                onPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.START) {
            StartScreen(
                flowState = flowState,
                onCreate = { name, pass, stakes -> viewModel.createPact(name, pass, stakes) },
                onClearError = viewModel::clearFlowError,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.JOIN) {
            JoinScreen(
                prefillCode = null,
                flowState = flowState,
                onJoin = { code, name -> viewModel.joinPact(code, name) },
                onClearError = viewModel::clearFlowError,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.JOIN_WITH_CODE,
            arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            JoinScreen(
                prefillCode = entry.arguments?.getString("code"),
                flowState = flowState,
                onJoin = { code, name -> viewModel.joinPact(code, name) },
                onClearError = viewModel::clearFlowError,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun InSessionRouter(
    viewModel: PactViewModel,
    view: SeatView,
    reconnecting: Boolean,
) {
    Crossfade(targetState = view::class, animationSpec = tween(200), label = "session") { _ ->
        when (val v = view) {
            is SeatView.Join -> JoinScreen(
                prefillCode = v.code,
                flowState = FlowState.Idle,
                onJoin = { code, name -> viewModel.joinPact(code, name) },
                onClearError = viewModel::clearFlowError,
                onBack = { viewModel.endSession() },
            )
            is SeatView.LobbyHost -> LobbyHostScreen(
                view = v,
                reconnecting = reconnecting,
                onLock = viewModel::lock,
                onLeave = viewModel::leave,
            )
            is SeatView.LobbyWait -> LobbyWaitScreen(
                view = v,
                reconnecting = reconnecting,
                onLeave = viewModel::leave,
            )
            is SeatView.Night -> NightScreen(
                view = v,
                reconnecting = reconnecting,
                onAsk = { reason -> viewModel.ask(reason) },
                onEmergency = viewModel::emergency,
                onLeave = viewModel::leave,
            )
            is SeatView.Ask -> AskScreen(
                view = v,
                onVote = viewModel::vote,
                onLeave = viewModel::leave,
            )
            is SeatView.Waiting -> WaitingScreen(
                view = v,
                onLeave = viewModel::leave,
            )
            is SeatView.Pass -> PassScreen(
                view = v,
                onLeave = viewModel::leave,
            )
            is SeatView.Broken -> BrokenScreen(
                view = v,
                onDone = viewModel::endSession,
            )
            is SeatView.None -> { /* handled by the outer regime */ }
        }
    }
}
