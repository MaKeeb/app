package com.makeeb.android

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.makeeb.shared.keyboard.KeyboardPorts
import com.makeeb.shared.keyboard.KeyboardSession
import com.makeeb.shared.surface.KeyboardSurface
import com.makeeb.platform.clipboard.AndroidSystemClipboard
import com.makeeb.platform.feedback.AudioManagerSoundFeedback
import com.makeeb.platform.feedback.ViewHapticFeedback
import com.makeeb.platform.host.ImeServiceKeyboardHost
import com.makeeb.platform.host.InputConnectionTextHost
import com.makeeb.platform.host.toEditorAttributes
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

/**
 * The Android keyboard. An IME is a Service, not an Activity, so it provides the lifecycle,
 * ViewModel store and saved-state owners Compose expects, and installs them on the IME window.
 */
class MaKeebInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    KoinComponent {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = MainScope()
    private var inputView: View? = null
    private val navigationBarInset = mutableIntStateOf(0)
    private val textHost = InputConnectionTextHost { currentInputConnection }
    private lateinit var keyboardHost: ImeServiceKeyboardHost
    private lateinit var session: KeyboardSession

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        moveLifecycleTo(Lifecycle.State.CREATED)
        keyboardHost = ImeServiceKeyboardHost(this)
        val ports = KeyboardPorts(
            clipboard = AndroidSystemClipboard(this),
            haptics = ViewHapticFeedback { inputView },
            sound = AudioManagerSoundFeedback(this),
        )
        session = get { parametersOf(ports, scope) }
        session.density = resources.displayMetrics.density
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            // The IME window is edge-to-edge (targetSdk 35+) and the system draws its back and
            // keyboard-switcher buttons over the bottom of it. Those buttons are reported as
            // tappableElement insets (48dp on a Pixel with gesture navigation), while
            // navigationBars only covers the 24dp gesture handle: pad for the larger of the two.
            ViewCompat.setOnApplyWindowInsetsListener(decor) { view, insets ->
                navigationBarInset.intValue = insets.getInsets(SYSTEM_BUTTON_INSETS).bottom
                ViewCompat.onApplyWindowInsets(view, insets)
            }
            decor.rootWindowInsets?.let { raw ->
                navigationBarInset.intValue = WindowInsetsCompat.toWindowInsetsCompat(raw, decor)
                    .getInsets(SYSTEM_BUTTON_INSETS).bottom
            }
        }
        return ComposeView(this).apply {
            setContent {
                val bottomInset = with(LocalDensity.current) { navigationBarInset.intValue.toDp() }
                KeyboardSurface(session, bottomInset = bottomInset, screenHeight = LocalConfiguration.current.screenHeightDp.dp)
            }
        }.also { inputView = it }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        session.start(textHost, keyboardHost, info.toEditorAttributes())
        moveLifecycleTo(Lifecycle.State.RESUMED)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        session.stop()
        moveLifecycleTo(Lifecycle.State.STARTED)
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        session.engine.onExternalChange()
    }

    /** Never take over the screen with the extract UI in landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        // super.onDestroy() finishes the input view, which calls back into onFinishInputView, so
        // the lifecycle may only reach DESTROYED after it returns. Switching to another keyboard
        // destroys the service, so this path runs on every switch.
        super.onDestroy()
        moveLifecycleTo(Lifecycle.State.DESTROYED)
        viewModelStore.clear()
        scope.cancel()
        inputView = null
    }

    private companion object {
        /** Combined types report the larger inset per side. */
        val SYSTEM_BUTTON_INSETS = WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.tappableElement()
    }

    /** LifecycleRegistry throws on any move out of DESTROYED; late framework callbacks are ignored. */
    private fun moveLifecycleTo(state: Lifecycle.State) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.currentState = state
    }
}
