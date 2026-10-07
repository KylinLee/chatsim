package io.github.kylinlee.chatsim.ui.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.data.legacy.NoCall
import io.github.kylinlee.chatsim.ui.theme.SmsMessengerTheme
import io.github.kylinlee.chatsim.viewmodel.CallViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CallActivity : ComponentActivity() {
    private val viewModel: CallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        addLockScreenFlags()

        setContent {
            SmsMessengerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                CallScreen(
                    state = state,
                    swipeToAnswer = config.swipeToAnswer,
                    overlayEnabled = config.callOverlay,
                    onMinimize = { finish() },
                    onAccept = viewModel::accept,
                    onReject = viewModel::reject,
                    onEndCall = viewModel::disconnect,
                    onToggleMute = viewModel::toggleMute,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onToggleHold = { viewModel.toggleHold() },
                    onDtmf = viewModel::keypad,
                    onSwap = viewModel::swap,
                    onMerge = viewModel::merge,
                )

                LaunchedEffect(state.phoneState) {
                    if (state.phoneState is NoCall) {
                        finish()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CallOverlayController.onCallScreenVisibilityChanged(true)
    }

    override fun onPause() {
        CallOverlayController.onCallScreenVisibilityChanged(false)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun addLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    companion object {
        fun getStartIntent(context: Context): Intent {
            return Intent(context, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        }
    }
}
