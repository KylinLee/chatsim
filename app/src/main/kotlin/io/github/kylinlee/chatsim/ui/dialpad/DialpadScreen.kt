package io.github.kylinlee.chatsim.ui.dialpad

import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.DIALPAD_TONE_LENGTH_MS
import io.github.kylinlee.chatsim.data.model.DialpadSuggestion
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.navigation.ThreadRoute
import io.github.kylinlee.chatsim.ui.common.AppMenu
import io.github.kylinlee.chatsim.ui.common.AppSelectableMenuItem
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.viewmodel.DialpadViewModel

private const val TONE_VOLUME = 80

private val dialpadKeys = listOf(
    listOf(DialpadKey('1', ""), DialpadKey('2', "ABC"), DialpadKey('3', "DEF")),
    listOf(DialpadKey('4', "GHI"), DialpadKey('5', "JKL"), DialpadKey('6', "MNO")),
    listOf(DialpadKey('7', "PQRS"), DialpadKey('8', "TUV"), DialpadKey('9', "WXYZ")),
    listOf(DialpadKey('*', ""), DialpadKey('0', "+"), DialpadKey('#', "")),
)

private val dialpadTones = mapOf(
    '0' to ToneGenerator.TONE_DTMF_0,
    '1' to ToneGenerator.TONE_DTMF_1,
    '2' to ToneGenerator.TONE_DTMF_2,
    '3' to ToneGenerator.TONE_DTMF_3,
    '4' to ToneGenerator.TONE_DTMF_4,
    '5' to ToneGenerator.TONE_DTMF_5,
    '6' to ToneGenerator.TONE_DTMF_6,
    '7' to ToneGenerator.TONE_DTMF_7,
    '8' to ToneGenerator.TONE_DTMF_8,
    '9' to ToneGenerator.TONE_DTMF_9,
    '*' to ToneGenerator.TONE_DTMF_S,
    '#' to ToneGenerator.TONE_DTMF_P,
)

private data class DialpadKey(val digit: Char, val letters: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(
    onBack: () -> Unit,
    onOpenConversation: (ThreadRoute) -> Unit,
    onCreateContact: (String) -> Unit,
    initialNumber: String = "",
    viewModel: DialpadViewModel = hiltViewModel(),
) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val roleOverride by viewModel.roleOverride.collectAsStateWithLifecycle()
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    var roleMenuExpanded by remember { mutableStateOf(false) }
    val currentRole = roleOverride ?: activeRole ?: roles.firstOrNull()

    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotBlank()) {
            viewModel.setInput(initialNumber)
        }
    }

    val view = LocalView.current
    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, TONE_VOLUME) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    fun feedback(key: Char) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!viewModel.dialpadMuted) {
            dialpadTones[key]?.let { toneGenerator?.startTone(it, DIALPAD_TONE_LENGTH_MS.toInt()) }
        }
    }

    fun press(key: Char) {
        viewModel.append(key.toString())
        feedback(key)
    }

    fun longPress(key: Char) {
        if (key == '0') {
            viewModel.append("+")
            feedback(key)
        }
    }

    fun openConversation(number: String, title: String) {
        val role = currentRole ?: return
        onOpenConversation(
            ThreadRoute(
                roleId = role.id,
                peerNumber = number,
                title = title,
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (roles.isNotEmpty()) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { roleMenuExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = currentRole?.let { it.label.ifBlank { it.displayPrefix } }.orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_symbol_arrow_drop_down),
                                    contentDescription = stringResource(R.string.roles),
                                )
                            }
                            val roleCount = roles.size
                            AppMenu(
                                expanded = roleMenuExpanded,
                                onDismissRequest = { roleMenuExpanded = false },
                            ) {
                                roles.forEachIndexed { index, role ->
                                    AppSelectableMenuItem(
                                        text = role.label.ifBlank { role.displayPrefix },
                                        selected = role.id == currentRole?.id,
                                        onClick = {
                                            roleMenuExpanded = false
                                            viewModel.setRoleOverride(role)
                                        },
                                        index = index,
                                        count = roleCount,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(stringResource(R.string.dialpad))
                    }
                },
                navigationIcon = {
                    if (!listDetailLayout) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = suggestions, key = { "${it.contact.contactId}:${it.phoneNumber.value}" }) { suggestion ->
                        SuggestionRow(
                            suggestion = suggestion,
                            onClick = { viewModel.setInput(suggestion.phoneNumber.value) },
                        )
                    }

                    if (input.isNotBlank() && suggestions.isEmpty()) {
                        item(key = "add_contact") {
                            ListItem(
                                headlineContent = { Text(input) },
                                supportingContent = { Text(stringResource(R.string.new_contact)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_symbol_person_add),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier = Modifier.clickable { onCreateContact(input) },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(48.dp))
                Text(
                    text = if (viewModel.hideDialpadNumbers) "•".repeat(input.length) else input,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                viewModel.backspace()
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                            onLongClick = { viewModel.clear() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_backspace),
                        contentDescription = stringResource(R.string.backspace),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                dialpadKeys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            DialpadKeyButton(
                                key = key,
                                onKey = ::press,
                                onLongKey = if (key.digit == '0') ::longPress else null,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.size(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { openConversation(input, "") },
                    enabled = viewModel.isDialable(input) && currentRole != null,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_add_comment),
                        contentDescription = stringResource(R.string.start_conversation),
                        modifier = Modifier.size(28.dp),
                    )
                }
                FilledIconButton(
                    onClick = { viewModel.placeCall() },
                    enabled = viewModel.isDialable(input),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_call),
                        contentDescription = stringResource(R.string.call_number),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKeyButton(
    key: DialpadKey,
    onKey: (Char) -> Unit,
    onLongKey: ((Char) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionModifier = if (onLongKey != null) {
        Modifier.combinedClickable(
            onClick = { onKey(key.digit) },
            onLongClick = { onLongKey(key.digit) },
        )
    } else {
        Modifier.clickable { onKey(key.digit) }
    }

    Box(
        modifier = modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: DialpadSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = suggestion.contact.displayName()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(
            title = name,
            photoUri = suggestion.contact.photoUri,
            size = 44.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestion.phoneNumber.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}
