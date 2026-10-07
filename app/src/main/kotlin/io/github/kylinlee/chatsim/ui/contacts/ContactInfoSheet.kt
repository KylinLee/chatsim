package io.github.kylinlee.chatsim.ui.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import io.github.kylinlee.chatsim.common.extensions.toast
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.R
import kotlinx.coroutines.launch

/** The drag handle area and the bottom system inset, which are part of the total sheet height. */
private val SheetChromeHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactInfoSheet(
    contact: Contact,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDial: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val name = contact.displayName()
    val minSheetHeight = (LocalConfiguration.current.screenHeightDp.dp / 3 - SheetChromeHeight).coerceAtLeast(0.dp)

    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        context.toast(io.github.kylinlee.chatsim.R.string.copied_to_clipboard)
    }

    fun hide() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minSheetHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConversationAvatar(
                    title = name,
                    photoUri = contact.photoUri,
                    size = 48.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(onClick = {}, onLongClick = { copy(name) })
                        .padding(vertical = 8.dp),
                )
                IconButton(onClick = { hide(); onEdit() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_edit),
                        contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.edit),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            contact.phoneNumbers.forEach { phone ->
                ContactInfoRow(
                    icon = painterResource(R.drawable.ic_symbol_call),
                    text = phone.value,
                    onClick = { hide(); onDial(phone.value) },
                    onLongClick = { copy(phone.value) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 36.dp))
            }
            contact.emails.forEach { email ->
                ContactInfoRow(
                    icon = painterResource(R.drawable.ic_symbol_mail),
                    text = email.value,
                    onClick = { hide(); sendEmail(context, email.value) },
                    onLongClick = { copy(email.value) },
                )
            }

            val company = contact.getFullCompany()
            if (company.isNotBlank()) {
                ContactInfoRow(
                    icon = painterResource(R.drawable.ic_symbol_domain),
                    text = company,
                    onLongClick = { copy(company) },
                )
            }
            contact.addresses.forEach { address ->
                if (address.value.isNotBlank()) {
                    ContactInfoRow(
                        icon = painterResource(R.drawable.ic_symbol_location_on),
                        text = address.value,
                        onLongClick = { copy(address.value) },
                    )
                }
            }
            contact.websites.forEach { website ->
                if (website.isNotBlank()) {
                    ContactInfoRow(
                        icon = painterResource(R.drawable.ic_symbol_language),
                        text = website,
                        onLongClick = { copy(website) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactInfoRow(
    icon: Painter,
    text: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = { onLongClick?.invoke() },
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun sendEmail(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
    runCatching { context.startActivity(intent) }
}
