package io.github.kylinlee.chatsim.data.legacy

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import io.github.kylinlee.chatsim.common.extensions.telecomManager
import io.github.kylinlee.chatsim.domain.OutgoingRoute
import io.github.kylinlee.chatsim.repository.SimRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val simRepository: SimRepository,
) {
    @SuppressLint("MissingPermission")
    fun placeCall(route: OutgoingRoute) {
        val trimmedNumber = route.number.trim()
        if (trimmedNumber.isEmpty()) return

        val uri = Uri.fromParts("tel", trimmedNumber, null)
        val bundle = Bundle().apply {
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, false)
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)

            val handle = simRepository.getPhoneAccountHandleForSubscription(route.subscriptionId)
                ?: simRepository.getCustomCallSim(trimmedNumber)
                ?: context.telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            if (handle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
        }

        runCatching {
            context.telecomManager.placeCall(uri, bundle)
        }
    }
}
