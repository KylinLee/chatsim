package io.github.kylinlee.chatsim.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import io.github.kylinlee.chatsim.common.ROLE_ID
import io.github.kylinlee.chatsim.navigation.DialpadRoute
import io.github.kylinlee.chatsim.navigation.ThreadRoute
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.ui.theme.SmsMessengerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var roleRepository: RoleRepository

    private val pendingRoute = mutableStateOf<NavKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRoute.value = parseRoute(intent)

        setContent {
            SmsMessengerTheme {
                ChatSimApp(
                    pendingRoute = pendingRoute.value,
                    onPendingRouteHandled = { pendingRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = parseRoute(intent)
    }

    /** 外部号码：`tel:` 打开拨号盘并预填号码；短信类 scheme 打开对应会话。 */
    private fun parseRoute(intent: Intent?): NavKey? {
        val data = intent?.data ?: return null
        val scheme = data.scheme?.lowercase() ?: return null

        return when (scheme) {
            "tel" -> Uri.decode(data.schemeSpecificPart)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { DialpadRoute(it) }

            "sms", "smsto", "mms", "mmsto" -> {
                val number = Uri.decode(data.schemeSpecificPart)
                    .substringBefore('?')
                    .trim()
                if (number.isBlank()) {
                    null
                } else {
                    val roleId = intent.getLongExtra(ROLE_ID, 0L).takeIf { it > 0 }
                        ?: roleRepository.getActiveRole()?.id
                        ?: 0L
                    ThreadRoute(roleId = roleId, peerNumber = number)
                }
            }

            else -> null
        }
    }
}
