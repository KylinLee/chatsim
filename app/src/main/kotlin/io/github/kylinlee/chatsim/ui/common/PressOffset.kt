package io.github.kylinlee.chatsim.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Tracks the position of the last pointer down so context menus can pop up at the press location. */
@Composable
fun Modifier.trackPressOffset(onPressOffset: (Offset) -> Unit): Modifier {
    val currentOnPressOffset by rememberUpdatedState(onPressOffset)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            currentOnPressOffset(down.position)
        }
    }
}

/**
 * A dropdown menu anchored at [pressOffset] inside the parent layout, so it pops up right at the
 * press position instead of at the parent's edge.
 */
@Composable
fun PressAnchorMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    pressOffset: Offset,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(pressOffset.x.roundToInt(), pressOffset.y.roundToInt()) }
            .size(1.dp),
    ) {
        AppMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = DpOffset.Zero,
            content = content,
        )
    }
}
