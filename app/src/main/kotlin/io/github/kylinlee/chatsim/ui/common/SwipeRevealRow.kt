package io.github.kylinlee.chatsim.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Width of a single action button revealed by a left swipe. */
val SwipeActionWidth = 72.dp

val SwipePinColor = Color(0xFF4FC3F7)
val SwipeReadColor = Color(0xFFFFB74D)
val SwipeDeleteColor = Color(0xFFE57373)
val SwipeActionContentColor = Color(0xFFFFFFFF)

/**
 * A row that reveals [actions] on its right side when swiped left, QQ style. Only one row should be
 * revealed at a time; the caller owns that state via [revealed] / [onRevealedChange].
 */
@Composable
fun SwipeRevealRow(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    actionsWidth: Dp,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val revealPx = with(LocalDensity.current) { actionsWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(revealed, revealPx) {
        offsetX.animateTo(if (revealed) -revealPx else 0f, tween(200))
    }

    Box(modifier = modifier.clipToBounds()) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
            content = actions,
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        val open = when {
                            velocity < -600f -> true
                            velocity > 600f -> false
                            else -> offsetX.value <= -revealPx / 2f
                        }
                        onRevealedChange(open)
                        offsetX.animateTo(if (open) -revealPx else 0f, tween(200))
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
fun RowScope.SwipeActionButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = SwipeActionContentColor,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(SwipeActionWidth)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
