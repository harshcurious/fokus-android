package dev.fokus.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import dev.fokus.app.R
import dev.fokus.app.timer.TimerEngine
import dev.fokus.app.ui.TimerFace
import dev.fokus.app.ui.dialSize
import kotlinx.coroutines.flow.StateFlow

/**
 * Shows and hides the full-screen break overlay, drawn over every app via
 * [WindowManager] - the Android counterpart of the plasmoid's per-screen fullscreen
 * break windows. Requires the "display over other apps" permission; without it the
 * overlay is silently skipped and the event notifications still fire.
 */
class OverlayController(private val context: Context) {

    interface Actions {
        fun onStart()

        fun onSkip()

        fun onPostpone()
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null

    var stateFlow: StateFlow<TimerEngine.Snapshot>? = null
    var actions: Actions? = null

    fun setVisible(visible: Boolean) {
        if (visible) show() else hide()
    }

    fun show() {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val composeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(overlayLifecycleOwner)
                setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
                setContent {
                    val flow = stateFlow ?: return@setContent
                    val snapshot by flow.collectAsState()
                    BreakOverlayContent(
                        snapshot = snapshot,
                        onStart = { actions?.onStart() },
                        onSkip = { actions?.onSkip() },
                        onPostpone = { actions?.onPostpone() },
                    )
                }
            }
        overlayLifecycleOwner.onAttach()
        windowManager.addView(composeView, layoutParams())
        view = composeView
    }

    fun hide() {
        val v = view ?: return
        windowManager.removeView(v)
        overlayLifecycleOwner.onDetach()
        view = null
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
    }

    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    /** A minimal self-contained owner triple so a [ComposeView] can live outside an activity. */
    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun onAttach() {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDetach() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}

@Composable
private fun BreakOverlayContent(
    snapshot: TimerEngine.Snapshot,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onPostpone: () -> Unit,
) {
    // The plasmoid dims the desktop to ~80%; on a phone the app underneath is usually
    // bright, so the scrim is nearly opaque or white text ghosts through it.
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xF7101216)) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            val caption =
                when {
                    snapshot.overtime -> stringResource(R.string.overlay_caption_overtime)
                    snapshot.phase == TimerEngine.Phase.LONG_BREAK ->
                        stringResource(R.string.overlay_caption_long_break)
                    else -> stringResource(R.string.overlay_caption_short_break)
                }
            TimerFace(
                snapshot = snapshot,
                caption = caption,
                ringColor = Color(0xFF7FD08C),
                trackColor = Color(0x33888888),
                textColor = Color(0xFFF2F4F6),
                dimColor = Color(0xFF9AA0A6),
                modifier = Modifier.dialSize(),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (snapshot.overtime) {
                        Button(onClick = onStart) { Text(stringResource(R.string.action_start_focus)) }
                    } else {
                        OutlinedButton(
                            onClick = onPostpone,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF2F4F6)),
                        ) {
                            Text(stringResource(R.string.action_postpone))
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF2F4F6)),
                        ) {
                            Text(stringResource(R.string.action_skip))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.overlay_home_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0A6),
                )
            }
        }
    }
}
