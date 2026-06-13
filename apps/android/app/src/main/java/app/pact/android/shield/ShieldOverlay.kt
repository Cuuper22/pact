package app.pact.android.shield

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.pact.android.MainActivity
import app.pact.android.ui.screens.ShieldOverlayContent
import app.pact.android.ui.theme.PactTheme

/**
 * Manages the full-screen overlay window drawn by the shield. It hosts a
 * Compose tree (the night-screen look + an "Ask the table" button) in a
 * TYPE_APPLICATION_OVERLAY window. Because the window is detached from any
 * Activity, we provide our own Lifecycle / SavedState / ViewModelStore owners so
 * Compose can run inside it.
 */
class ShieldOverlay(private val context: Context) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private var composeView: ComposeView? = null
    private var shown = false

    /** The reason input the shield posts when the user asks from the overlay. */
    private val askInProgress = mutableStateOf(false)

    fun show(reducedMotion: Boolean, onAsk: () -> Unit) {
        if (shown) return
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ShieldOverlay)
            setViewTreeViewModelStoreOwner(this@ShieldOverlay)
            setViewTreeSavedStateRegistryOwner(this@ShieldOverlay)
            setContent {
                PactTheme(darkTheme = true) {
                    ShieldOverlayContent(
                        reducedMotion = reducedMotion,
                        onAsk = onAsk,
                        onOpenApp = { openApp() },
                    )
                }
            }
        }
        composeView = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Not focusable so it never steals the dialer; but it covers the
            // screen so the temptation surface is replaced by the night screen.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            wm.addView(view, params)
            shown = true
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } catch (_: Throwable) {
            // Overlay permission may have been revoked mid-pact; fail soft.
            composeView = null
        }
    }

    fun hide() {
        if (!shown) return
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        composeView?.let { runCatching { wm.removeView(it) } }
        composeView = null
        shown = false
    }

    fun destroy() {
        hide()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    val isShown: Boolean get() = shown

    /** Bring the Pact app forward (its own night screen) — used by overlay buttons. */
    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { context.startActivity(intent) }
    }
}
