package io.github.kylinlee.chatsim.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.domain.rule.user.CallKind
import io.github.kylinlee.chatsim.domain.rule.user.RULE_RECORD_ANY
import io.github.kylinlee.chatsim.domain.rule.user.RuleCondition
import io.github.kylinlee.chatsim.domain.rule.user.RuleConnector
import io.github.kylinlee.chatsim.domain.rule.user.RuleObject
import io.github.kylinlee.chatsim.domain.rule.user.RuleOperator
import io.github.kylinlee.chatsim.domain.rule.user.RuleValueType
import io.github.kylinlee.chatsim.domain.rule.user.SmsDirection
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.domain.rule.user.valueType
import io.github.kylinlee.chatsim.navigation.UserRuleEditRoute
import io.github.kylinlee.chatsim.ui.common.AppSelectableMenuItem
import io.github.kylinlee.chatsim.ui.common.DAY_MILLIS
import io.github.kylinlee.chatsim.ui.common.appMenuContainerColor
import io.github.kylinlee.chatsim.ui.common.appMenuContainerShape
import io.github.kylinlee.chatsim.ui.common.appMenuContentPadding
import io.github.kylinlee.chatsim.ui.common.formatTimestamp
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.viewmodel.UserRuleEditState
import io.github.kylinlee.chatsim.viewmodel.UserRuleEditViewModel
import java.util.Calendar

private val AndOrGroupWidth = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRuleEditScreen(
    route: UserRuleEditRoute,
    onBack: () -> Unit,
    viewModel: UserRuleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    LaunchedEffect(route.ruleId) {
        viewModel.load(route.ruleId)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (route.ruleId == 0L) {
                                io.github.kylinlee.chatsim.R.string.add_rule
                            } else {
                                io.github.kylinlee.chatsim.R.string.edit_rule
                            }
                        )
                    )
                },
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
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .width(24.dp),
                        )
                    } else {
                        TextButton(
                            onClick = viewModel::save,
                            enabled = state.canSave,
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    Text(
                        text = stringResource(io.github.kylinlee.chatsim.R.string.rule_action),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserRuleAction.entries.forEach { action ->
                            FilterChip(
                                selected = state.action == action,
                                onClick = { viewModel.setAction(action) },
                                enabled = action != UserRuleAction.TRASH || state.trashEnabled,
                                label = { Text(ruleActionLabel(action)) },
                            )
                        }
                    }
                    if (!state.trashEnabled) {
                        Text(
                            text = stringResource(io.github.kylinlee.chatsim.R.string.rule_trash_disabled),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                TagSelector(state = state, viewModel = viewModel)
            }

            item {
                Text(
                    text = stringResource(io.github.kylinlee.chatsim.R.string.rule_conditions),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            item {
                ConditionsEditor(
                    conditions = state.conditions,
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSelector(
    state: UserRuleEditState,
    viewModel: UserRuleEditViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    Column {
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.rule_tags),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableTags.forEach { tag ->
                FilterChip(
                    selected = tag.name in state.selectedTags,
                    onClick = { viewModel.toggleTag(tag.name) },
                    label = { Text("#${tag.name}") },
                )
            }
            FilterChip(
                selected = false,
                onClick = { showAddDialog = true },
                label = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_new_tag)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_add),
                        contentDescription = null,
                    )
                },
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_new_tag)) },
            text = {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTag(newTag)
                        newTag = ""
                        showAddDialog = false
                    },
                    enabled = newTag.isNotBlank(),
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** 条件区：条件组件依次排列，组件之间不留空隙。 */
@Composable
private fun ConditionsEditor(
    conditions: List<RuleCondition>,
    state: UserRuleEditState,
    viewModel: UserRuleEditViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        conditions.forEachIndexed { index, condition ->
            ConditionRow(
                index = index,
                condition = condition,
                canRemove = conditions.size > 1,
                state = state,
                onObjectChange = { viewModel.setObject(index, it) },
                onOperatorChange = { viewModel.setOperator(index, it) },
                onValueChange = { viewModel.setValue(index, it) },
                onConnectorChange = { connector ->
                    if (condition.connector == null && index == conditions.lastIndex) {
                        viewModel.appendCondition(index, connector)
                    } else {
                        viewModel.setConnector(index, connector)
                    }
                },
                onRemove = { viewModel.removeCondition(index) },
            )
        }
    }
}

@Composable
private fun ConditionRow(
    index: Int,
    condition: RuleCondition,
    canRemove: Boolean,
    state: UserRuleEditState,
    onObjectChange: (RuleObject) -> Unit,
    onOperatorChange: (RuleOperator) -> Unit,
    onValueChange: (String) -> Unit,
    onConnectorChange: (RuleConnector) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_header, index + 1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (canRemove) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.delete))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FilledDropdownField(
                label = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_subject),
                value = ruleObjectLabel(condition.field),
                options = RuleObject.entries.map { it to ruleObjectLabel(it) },
                selectedOption = condition.field,
                onSelect = onObjectChange,
                modifier = Modifier.weight(1f),
            )
            FilledDropdownField(
                label = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_operator),
                value = ruleOperatorLabel(condition.operator),
                options = condition.field.allowedOperators().map { it to ruleOperatorLabel(it) },
                selectedOption = condition.operator,
                onSelect = onOperatorChange,
                modifier = Modifier.weight(1f),
            )
            RuleValueInput(
                condition = condition,
                state = state,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            AndOrGroup(
                connector = condition.connector,
                onSelect = onConnectorChange,
                modifier = Modifier.width(AndOrGroupWidth),
            )
        }
    }
}

/** 且/或单选：整体圆角方形，两块无间距拼接，Extra small 尺寸，选中只换底色。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AndOrGroup(
    connector: RuleConnector?,
    onSelect: (RuleConnector) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val outline = MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .height(ButtonDefaults.ExtraSmallContainerHeight)
            .clip(shape)
            .border(1.dp, outline, shape),
    ) {
        AndOrItem(
            label = stringResource(io.github.kylinlee.chatsim.R.string.rule_and_button),
            selected = connector == RuleConnector.AND,
            onClick = { onSelect(RuleConnector.AND) },
            modifier = Modifier.weight(1f),
        )
        AndOrItem(
            label = stringResource(io.github.kylinlee.chatsim.R.string.rule_or_button),
            selected = connector == RuleConnector.OR,
            onClick = { onSelect(RuleConnector.OR) },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AndOrItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton }
            .padding(ButtonDefaults.ExtraSmallContentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun filledFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.background,
    unfocusedContainerColor = MaterialTheme.colorScheme.background,
    disabledContainerColor = MaterialTheme.colorScheme.background,
    errorContainerColor = MaterialTheme.colorScheme.background,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
    disabledIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
)

/** 条件区专用 Filled 文本字段：容器色与页面一致，左右内边距收窄以适配小屏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = filledFieldColors()

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(
            minWidth = TextFieldDefaults.MinWidth,
            minHeight = TextFieldDefaults.MinHeight,
        ),
        readOnly = readOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                trailingIcon = trailingIcon,
                colors = colors,
                contentPadding = TextFieldDefaults.contentPaddingWithLabel(start = 8.dp, end = 8.dp),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilledDropdownField(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    selectedOption: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        CompactTextField(
            value = value,
            onValueChange = {},
            label = label,
            readOnly = true,
            trailingIcon = {
                Icon(
                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_drop_down),
                    contentDescription = null,
                )
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = true,
            shape = appMenuContainerShape(),
            containerColor = appMenuContainerColor(),
        ) {
            Column(modifier = Modifier.padding(appMenuContentPadding())) {
                options.forEachIndexed { index, (option, text) ->
                    AppSelectableMenuItem(
                        text = text,
                        selected = option == selectedOption,
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        index = index,
                        count = options.size,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleValueInput(
    condition: RuleCondition,
    state: UserRuleEditState,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (condition.field.valueType(condition.operator)) {
        RuleValueType.TEXT -> CompactTextField(
            value = condition.value,
            onValueChange = onValueChange,
            label = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_value),
            modifier = modifier,
        )

        RuleValueType.ROLE -> FilledDropdownField(
            label = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_value),
            value = state.roles.firstOrNull { it.id.toString() == condition.value }
                ?.let { it.label.ifBlank { it.displayPrefix } }
                ?: stringResource(io.github.kylinlee.chatsim.R.string.rule_object_role),
            options = state.roles.map { it.id.toString() to it.label.ifBlank { it.displayPrefix } },
            selectedOption = condition.value.takeIf { it.isNotBlank() },
            onSelect = onValueChange,
            modifier = modifier,
        )

        RuleValueType.SMS_DIRECTION -> FilledDropdownField(
            label = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_value),
            value = smsRecordValueLabel(condition.value),
            options = listOf(RULE_RECORD_ANY to stringResource(io.github.kylinlee.chatsim.R.string.rule_record_any)) +
                SmsDirection.entries.map { it.name to smsDirectionLabel(it) },
            selectedOption = condition.value,
            onSelect = onValueChange,
            modifier = modifier,
        )

        RuleValueType.CALL_KIND -> FilledDropdownField(
            label = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_value),
            value = callRecordValueLabel(condition.value),
            options = listOf(RULE_RECORD_ANY to stringResource(io.github.kylinlee.chatsim.R.string.rule_record_any)) +
                CallKind.entries.map { it.name to callKindLabel(it) },
            selectedOption = condition.value,
            onSelect = onValueChange,
            modifier = modifier,
        )

        RuleValueType.TIMESTAMP -> AbsoluteTimeField(
            value = condition.value,
            onValueChange = onValueChange,
            modifier = modifier,
        )

        RuleValueType.RELATIVE_DAYS -> RelativeDaysField(
            value = condition.value,
            operator = condition.operator,
            onValueChange = onValueChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun smsRecordValueLabel(value: String): String =
    SmsDirection.entries.firstOrNull { it.name == value }?.let { smsDirectionLabel(it) }
        ?: stringResource(io.github.kylinlee.chatsim.R.string.rule_record_any)

@Composable
private fun callRecordValueLabel(value: String): String =
    CallKind.entries.firstOrNull { it.name == value }?.let { callKindLabel(it) }
        ?: stringResource(io.github.kylinlee.chatsim.R.string.rule_record_any)

/** 「N 天以内 / N 天以前」：界面输入天数，存储为负毫秒偏移。 */
@Composable
private fun RelativeDaysField(
    value: String,
    operator: RuleOperator,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = value.toLongOrNull()?.takeIf { it < 0 }?.let { (-it / DAY_MILLIS).toString() }.orEmpty()

    CompactTextField(
        value = days,
        onValueChange = { text ->
            val digits = text.filter { it.isDigit() }.trimStart('0').take(6)
            onValueChange(if (digits.isEmpty()) "" else (-(digits.toLong() * DAY_MILLIS)).toString())
        },
        label = stringResource(
            if (operator == RuleOperator.BEFORE_DAYS) {
                io.github.kylinlee.chatsim.R.string.rule_relative_days_before
            } else {
                io.github.kylinlee.chatsim.R.string.rule_relative_days_within
            }
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbsoluteTimeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableLongStateOf(0L) }

    val text = value.toLongOrNull()?.let { formatTimestamp(it / 1000) }
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(io.github.kylinlee.chatsim.R.string.rule_pick_time)

    Box(modifier = modifier) {
        CompactTextField(
            value = text,
            onValueChange = {},
            label = stringResource(io.github.kylinlee.chatsim.R.string.rule_condition_value),
            readOnly = true,
            trailingIcon = {
                Icon(
                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_schedule),
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDatePicker = true },
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = pickedDateMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        showTimePicker = false
                        onValueChange(calendar.timeInMillis.toString())
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}
