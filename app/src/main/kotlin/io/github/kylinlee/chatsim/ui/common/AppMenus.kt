package io.github.kylinlee.chatsim.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpOffset

/** 全局菜单统一使用的 Expressive 容器形状（大圆角）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun appMenuContainerShape(): Shape = MenuDefaults.standaloneGroupShape

/** 全局菜单统一使用的 Expressive 容器色。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun appMenuContainerColor(): Color = MenuDefaults.groupStandardContainerColor

/** 全局菜单统一使用的分组内容内边距。 */
fun appMenuContentPadding(): PaddingValues = MenuDefaults.DropdownMenuGroupContentPadding

/** M3 Expressive 竖向菜单容器：统一的分组形状与容器色。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(index = 0, count = 1),
            containerColor = MenuDefaults.groupStandardContainerColor,
            content = content,
        )
    }
}

/** Expressive 菜单项；[index]/[count] 决定分段形状（首/中/尾/独立）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    colors: MenuItemColors = MenuDefaults.itemColors(),
) {
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(text) },
        shape = MenuDefaults.itemShape(index = index, count = count).shape,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        colors = colors,
    )
}

/** 可选中（单选）的 Expressive 菜单项，用于下拉选择器。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSelectableMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    selectedLeadingIcon: @Composable (() -> Unit)? = null,
) {
    DropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = { Text(text) },
        shapes = MenuDefaults.itemShape(index = index, count = count),
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        checkedLeadingIcon = selectedLeadingIcon,
        colors = MenuDefaults.selectableItemColors(),
    )
}
