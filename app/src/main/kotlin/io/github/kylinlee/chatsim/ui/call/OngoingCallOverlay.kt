package io.github.kylinlee.chatsim.ui.call

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.telecom.Call
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.common.extensions.getCallDuration
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.legacy.CallManager
import io.github.kylinlee.chatsim.data.legacy.CallManagerListener
import io.github.kylinlee.chatsim.data.legacy.getCallContact
import io.github.kylinlee.chatsim.data.model.AudioRoute
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.ui.common.formatDuration
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.ui.theme.SmsMessengerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 通话进行中、用户离开通话界面时显示的悬浮窗。 */
object CallOverlayController {
    private var overlay: OngoingCallOverlay? = null
    private var callScreenVisible = false
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
        if (overlay == null) {
            overlay = OngoingCallOverlay(context.applicationContext)
        }
        refresh()
    }

    fun detach() {
        overlay?.release()
        overlay = null
        callScreenVisible = false
        appContext = null
    }

    fun onCallStateChanged() = refresh()

    fun onCallScreenVisibilityChanged(visible: Boolean) {
        callScreenVisible = visible
        refresh()
    }

    private fun refresh() {
        val current = overlay ?: return
        val enabled = appContext?.config?.callOverlay ?: true
        if (!enabled || callScreenVisible || !current.hasActiveCall()) {
            current.hide()
        } else {
            current.show()
        }
    }
}

class OngoingCallOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val stateHolder = CallOverlayStateHolder(context)
    private val lifecycleOwner = OverlayLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var view: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var snapJob: Job? = null

    init {
        stateHolder.start()
        scope.launch {
            AppEventBus.eventsOfType<AppEvent.CallOverlayChanged>().collect {
                CallOverlayController.onCallStateChanged()
            }
        }
    }

    fun hasActiveCall(): Boolean = when (CallManager.getState()) {
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE, Call.STATE_HOLDING -> true
        else -> false
    }

    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) return

        val composeView = ComposeView(context).apply {
            setContent {
                SmsMessengerTheme {
                    val state by stateHolder.state.collectAsStateWithLifecycle()
                    CallOverlayContent(state = state)
                }
            }
        }

        val container = OverlayTouchLayout(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            onTap = { openCallScreen() }
            onDragStart = { snapJob?.cancel() }
            onDrag = { dx, dy -> moveBy(dx, dy) }
            onDragEnd = { snapToEdge() }
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0f
        }

        layoutParams = params
        lifecycleOwner.resume()
        val added = runCatching { windowManager.addView(container, params) }.isSuccess
        if (!added) {
            lifecycleOwner.pause()
            layoutParams = null
            return
        }
        view = container

        container.doOnLayout {
            if (view !== container) return@doOnLayout
            positionInitially(container)
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        snapJob?.cancel()
        lifecycleOwner.pause()
        runCatching { windowManager.removeView(current) }
    }

    fun release() {
        hide()
        stateHolder.stop()
        scope.cancel()
        lifecycleOwner.destroy()
    }

    private fun positionInitially(container: View) {
        val params = layoutParams ?: return
        val metrics = context.resources.displayMetrics
        val margin = marginPx()
        val maxX = (metrics.widthPixels - container.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - container.height).coerceAtLeast(0)
        val savedX = context.config.callOverlayX
        val savedY = context.config.callOverlayY
        params.x = if (savedX >= 0) {
            savedX.coerceIn(0, maxX)
        } else {
            ((metrics.widthPixels - container.width) / 2).coerceAtLeast(margin)
        }
        params.y = if (savedY >= 0) {
            savedY.coerceIn(0, maxY)
        } else {
            (metrics.heightPixels - container.height - margin).coerceAtLeast(margin)
        }
        params.alpha = 1f
        runCatching { windowManager.updateViewLayout(container, params) }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val current = view ?: return
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - current.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - current.height).coerceAtLeast(0)
        params.x = (params.x + dx.toInt()).coerceIn(0, maxX)
        params.y = (params.y + dy.toInt()).coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(current, params) }
    }

    private fun snapToEdge() {
        val params = layoutParams ?: return
        val current = view ?: return
        val metrics = context.resources.displayMetrics
        val margin = marginPx()
        val targetX = if (params.x + current.width / 2 < metrics.widthPixels / 2) {
            margin
        } else {
            (metrics.widthPixels - current.width - margin).coerceAtLeast(margin)
        }
        context.config.callOverlayX = targetX
        context.config.callOverlayY = params.y
        animateX(params, current, targetX)
    }

    private fun animateX(params: WindowManager.LayoutParams, current: View, targetX: Int) {
        snapJob?.cancel()
        snapJob = scope.launch {
            val startX = params.x
            val distance = targetX - startX
            if (distance == 0) return@launch

            val duration = 200L
            val startTime = System.currentTimeMillis()
            while (true) {
                val fraction = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                val eased = 1f - (1f - fraction) * (1f - fraction)
                params.x = startX + (distance * eased).toInt()
                if (view !== current) break
                runCatching { windowManager.updateViewLayout(current, params) }
                if (fraction >= 1f) break
                delay(16L)
            }
        }
    }

    private fun marginPx(): Int = (8 * context.resources.displayMetrics.density).toInt()

    private fun openCallScreen() {
        runCatching {
            context.startActivity(CallActivity.getStartIntent(context))
        }
    }
}

@Composable
private fun CallOverlayContent(state: CallOverlayState) {
    val hasName = state.callerName.isNotBlank() && state.callerName != state.callerNumber
    val primaryText = if (hasName) {
        state.callerName
    } else {
        state.callerNumber.ifBlank { stringResource(R.string.unknown) }
    }

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationAvatar(
                title = primaryText,
                photoUri = state.callerPhotoUri,
                size = 28.dp,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                if (hasName && state.callerNumber.isNotBlank()) {
                    Text(
                        text = state.callerNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatDuration(state.durationSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

data class CallOverlayState(
    val callerName: String = "",
    val callerNumber: String = "",
    val callerPhotoUri: String = "",
    val durationSeconds: Int = 0,
)

private class CallOverlayStateHolder(private val context: Context) : CallManagerListener {
    private val _state = MutableStateFlow(CallOverlayState())
    val state: StateFlow<CallOverlayState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var durationJob: Job? = null

    fun start() {
        CallManager.addListener(this)
        refreshCallerInfo(CallManager.getPrimaryCall())
        updateState()
    }

    fun stop() {
        CallManager.removeListener(this)
        durationJob?.cancel()
        scope.cancel()
    }

    override fun onStateChanged() = updateState()

    override fun onAudioStateChanged(audioState: AudioRoute) = Unit

    override fun onPrimaryCallChanged(call: Call) {
        refreshCallerInfo(call)
        updateState()
    }

    private fun updateState() {
        updateDurationTicker(CallManager.getState())
    }

    private fun updateDurationTicker(callState: Int?) {
        if (callState == Call.STATE_ACTIVE) {
            if (durationJob?.isActive != true) {
                durationJob = scope.launch {
                    while (isActive) {
                        delay(1000L)
                        val fromConnect = CallManager.getPrimaryCall()?.getCallDuration() ?: 0
                        val next = if (fromConnect > 0) fromConnect else _state.value.durationSeconds + 1
                        _state.value = _state.value.copy(durationSeconds = next)
                    }
                }
            }
        } else {
            durationJob?.cancel()
            durationJob = null

            if (callState == null || callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
                _state.value = _state.value.copy(durationSeconds = 0)
            }
        }
    }

    private fun refreshCallerInfo(call: Call?) {
        getCallContact(context, call) { callContact ->
            _state.value = _state.value.copy(
                callerName = callContact.name,
                callerNumber = callContact.number,
                callerPhotoUri = callContact.photoUri,
            )
        }
    }
}

/** 拦截全部触摸：点击打开通话界面，长按后拖动移动悬浮窗。 */
private class OverlayTouchLayout(context: Context) : FrameLayout(context) {
    var onTap: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var longPressed = false
    private val longPressRunnable = Runnable { longPressed = true }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                longPressed = false
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && SystemClock.uptimeMillis() - event.downTime > ViewConfiguration.getLongPressTimeout()) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                    onDragStart?.invoke()
                }
                if (dragging) {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    onDrag?.invoke(dx, dy)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    onDragEnd?.invoke()
                } else if (!longPressed) {
                    onTap?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    onDragEnd?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun pause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
