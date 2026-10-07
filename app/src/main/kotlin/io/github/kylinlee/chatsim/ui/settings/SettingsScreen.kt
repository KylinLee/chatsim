package io.github.kylinlee.chatsim.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.LOCK_SCREEN_NOTHING
import io.github.kylinlee.chatsim.common.LOCK_SCREEN_SENDER
import io.github.kylinlee.chatsim.common.LOCK_SCREEN_SENDER_MESSAGE
import io.github.kylinlee.chatsim.domain.scheduler.RECYCLE_BIN_CLEAN_PERIODS
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class SettingsDialog {
    LockScreenVisibility,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRolesClick: () -> Unit,
    onSimImportClick: () -> Unit,
    onOverlayPermissionClick: () -> Unit,
    onRecycleBinClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var showBaseDatePicker by remember { mutableStateOf(false) }
    var showBaseTimePicker by remember { mutableStateOf(false) }
    var pickedBaseDateMillis by remember { mutableLongStateOf(0L) }

    val exportMessagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMessages(it) } }

    val importMessagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMessages(it) } }

    val exportCallsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportCallHistory(it) } }

    val importCallsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importCallHistory(it) } }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    if (!listDetailLayout) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter),
            ) {
                item { SectionHeader(stringResource(io.github.kylinlee.chatsim.R.string.general)) }
                item {
                    SwitchRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.hide_dialpad_numbers),
                        checked = state.hideDialpadNumbers,
                        onCheckedChange = viewModel::setHideDialpadNumbers,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.dialpad_muted),
                        checked = state.dialpadMuted,
                        onCheckedChange = viewModel::setDialpadMuted,
                    )
                }

                item { SectionHeader(stringResource(io.github.kylinlee.chatsim.R.string.roles)) }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.manage_roles),
                        value = "",
                        onClick = onRolesClick,
                    )
                }

                item { SectionHeader(stringResource(R.string.contacts_tab)) }
                item {
                    SwitchRow(
                        title = stringResource(R.string.start_name_with_surname),
                        checked = state.startNameWithSurname,
                        onCheckedChange = viewModel::setStartNameWithSurname,
                        showDivider = false,
                    )
                }
                item {
                    NameSeparatorRow(
                        selected = state.nameSeparator,
                        onSelect = viewModel::setNameSeparator,
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(
                            io.github.kylinlee.chatsim.R.string.import_contacts_from_sim
                        ),
                        value = "",
                        onClick = onSimImportClick,
                    )
                }

                item { SectionHeader(stringResource(io.github.kylinlee.chatsim.R.string.calls)) }
                item {
                    SwitchRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.swipe_to_answer),
                        checked = state.swipeToAnswer,
                        onCheckedChange = viewModel::setSwipeToAnswer,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.call_overlay),
                        checked = state.callOverlay,
                        onCheckedChange = viewModel::setCallOverlay,
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.call_overlay_permission),
                        value = stringResource(
                            if (state.canDrawOverlays) {
                                io.github.kylinlee.chatsim.R.string.call_overlay_permission_granted
                            } else {
                                io.github.kylinlee.chatsim.R.string.call_overlay_permission_denied
                            }
                        ),
                        onClick = onOverlayPermissionClick,
                    )
                }
                item { SectionHeader(stringResource(io.github.kylinlee.chatsim.R.string.messages)) }
                item {
                    SwitchRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.use_simple_characters),
                        checked = state.useSimpleCharacters,
                        onCheckedChange = viewModel::setUseSimpleCharacters,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.enable_delivery_reports),
                        checked = state.enableDeliveryReports,
                        onCheckedChange = viewModel::setEnableDeliveryReports,
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.lock_screen_visibility),
                        value = lockScreenVisibilityLabel(state.lockScreenVisibilitySetting),
                        onClick = { dialog = SettingsDialog.LockScreenVisibility },
                    )
                }

                item { SectionHeader(stringResource(R.string.recycle_bin)) }
                item {
                    val recycleBinLocked = state.trashRuleCount > 0 || state.recycleBinMessagesCount > 0
                    SwitchRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.use_recycle_bin),
                        checked = state.useRecycleBin,
                        onCheckedChange = viewModel::setUseRecycleBin,
                        enabled = !recycleBinLocked,
                        subtitle = if (recycleBinLocked) {
                            stringResource(io.github.kylinlee.chatsim.R.string.recycle_bin_locked)
                        } else {
                            null
                        },
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(R.string.open_recycle_bin),
                        value = state.recycleBinMessagesCount.toString(),
                        onClick = onRecycleBinClick,
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(R.string.recycle_bin_clean_base_time),
                        value = recycleBinBaseTimeLabel(state.recycleBinCleanBaseTime),
                        onClick = { showBaseDatePicker = true },
                    )
                }
                item {
                    SteppedSliderRow(
                        title = stringResource(R.string.recycle_bin_clean_period),
                        value = recycleBinPeriodLabel(state.recycleBinCleanPeriodIndex),
                        index = state.recycleBinCleanPeriodIndex,
                        onIndexChange = viewModel::setRecycleBinCleanPeriodIndex,
                    )
                }

                item { SectionHeader(stringResource(io.github.kylinlee.chatsim.R.string.import_export)) }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.export_messages),
                        value = "",
                        onClick = { exportMessagesLauncher.launch("messages.json") },
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.import_messages),
                        value = "",
                        onClick = {
                            importMessagesLauncher.launch(
                                arrayOf("application/json", "application/xml", "text/xml")
                            )
                        },
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.export_calls),
                        value = "",
                        onClick = { exportCallsLauncher.launch("call_history.json") },
                    )
                }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.import_calls),
                        value = "",
                        onClick = { importCallsLauncher.launch(arrayOf("application/json")) },
                    )
                }

                item { SectionHeader(stringResource(R.string.about)) }
                item {
                    EntryRow(
                        title = stringResource(io.github.kylinlee.chatsim.R.string.version),
                        value = stringResource(R.string.version_with_code, state.appVersionName, state.appVersionCode),
                        onClick = {},
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.LockScreenVisibility -> RadioOptionDialog(
            title = stringResource(io.github.kylinlee.chatsim.R.string.lock_screen_visibility),
            options = listOf(
                LOCK_SCREEN_SENDER_MESSAGE to stringResource(io.github.kylinlee.chatsim.R.string.sender_and_message),
                LOCK_SCREEN_SENDER to stringResource(io.github.kylinlee.chatsim.R.string.sender_only),
                LOCK_SCREEN_NOTHING to stringResource(R.string.nothing),
            ),
            selected = state.lockScreenVisibilitySetting,
            onSelect = {
                viewModel.setLockScreenVisibilitySetting(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }

    if (showBaseDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.recycleBinCleanBaseTime)
        DatePickerDialog(
            onDismissRequest = { showBaseDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedBaseDateMillis = datePickerState.selectedDateMillis ?: state.recycleBinCleanBaseTime
                        showBaseDatePicker = false
                        showBaseTimePicker = true
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBaseDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showBaseTimePicker) {
        val calendar = remember(pickedBaseDateMillis) {
            Calendar.getInstance().apply { timeInMillis = pickedBaseDateMillis }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showBaseTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = Calendar.getInstance().apply {
                            timeInMillis = pickedBaseDateMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        showBaseTimePicker = false
                        viewModel.setRecycleBinCleanBaseTime(picked.timeInMillis)
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBaseTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

@Composable
private fun lockScreenVisibilityLabel(value: Int): String = stringResource(
    when (value) {
        LOCK_SCREEN_SENDER -> io.github.kylinlee.chatsim.R.string.sender_only
        LOCK_SCREEN_NOTHING -> R.string.nothing
        else -> io.github.kylinlee.chatsim.R.string.sender_and_message
    }
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun NameSeparatorRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.name_separator),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        NameSeparatorChips(
            selected = selected,
            onSelect = onSelect,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun NameSeparatorChips(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        "" to stringResource(io.github.kylinlee.chatsim.R.string.name_separator_none),
        " " to stringResource(io.github.kylinlee.chatsim.R.string.name_separator_space),
        ", " to ", ",
        "-" to "-",
        ". " to ". ",
        "·" to "·",
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = options, key = { it.first }) { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun EntryRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun SteppedSliderRow(
    title: String,
    value: String,
    index: Int,
    onIndexChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onIndexChange(it.roundToInt()) },
            valueRange = 0f..RECYCLE_BIN_CLEAN_PERIODS.lastIndex.toFloat(),
            steps = RECYCLE_BIN_CLEAN_PERIODS.size - 2,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun recycleBinPeriodLabel(index: Int): String = stringResource(
    when (index) {
        0 -> R.string.recycle_bin_period_1_day
        1 -> R.string.recycle_bin_period_3_days
        2 -> R.string.recycle_bin_period_1_week
        3 -> R.string.recycle_bin_period_2_weeks
        4 -> R.string.recycle_bin_period_1_month
        else -> R.string.recycle_bin_period_3_months
    }
)

private fun recycleBinBaseTimeLabel(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

@Composable
private fun <T> RadioOptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
