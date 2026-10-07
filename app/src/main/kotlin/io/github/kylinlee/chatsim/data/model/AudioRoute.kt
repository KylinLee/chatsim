package io.github.kylinlee.chatsim.data.model

import android.telecom.CallAudioState
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.kylinlee.chatsim.R

enum class AudioRoute(val route: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    SPEAKER(CallAudioState.ROUTE_SPEAKER, R.string.audio_route_speaker, R.drawable.ic_symbol_volume_up),
    EARPIECE(CallAudioState.ROUTE_EARPIECE, R.string.audio_route_earpiece, R.drawable.ic_symbol_volume_down),
    BLUETOOTH(CallAudioState.ROUTE_BLUETOOTH, R.string.audio_route_bluetooth, R.drawable.ic_symbol_bluetooth),
    WIRED_HEADSET(CallAudioState.ROUTE_WIRED_HEADSET, R.string.audio_route_wired_headset, R.drawable.ic_symbol_headset_mic),
    WIRED_OR_EARPIECE(CallAudioState.ROUTE_WIRED_OR_EARPIECE, R.string.audio_route_wired_or_earpiece, R.drawable.ic_symbol_volume_down);

    companion object {
        fun fromRoute(route: Int?) = values().firstOrNull { it.route == route }
    }
}
