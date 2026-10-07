package io.github.kylinlee.chatsim.ui.contacts

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LetterItemHeight = 16.dp
private val LetterItemGap = 2.dp

/**
 * Vertical initial letter bar of the contact list. Tapping a letter scrolls to its group smoothly,
 * dragging over the letters jumps continuously while showing the pressed letter.
 *
 * The bar wraps its content (fixed letter height and gap) and is centered vertically by its parent.
 */
@Composable
fun ContactIndexBar(
    letters: List<Char>,
    activeLetter: Char?,
    onLetterSelected: (letter: Char, animate: Boolean) -> Unit,
    onPressedLetterChange: (Char?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    val density = LocalDensity.current
    val itemPitchPx = with(density) { (LetterItemHeight + LetterItemGap).toPx() }

    fun letterAt(y: Float): Char? {
        if (itemPitchPx <= 0f) return null
        val index = (y / itemPitchPx).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }

    Column(
        modifier = modifier
            .width(24.dp)
            .padding(vertical = 4.dp)
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    letterAt(offset.y)?.let { onLetterSelected(it, true) }
                }
            }
            .pointerInput(letters) {
                var lastLetter: Char? = null
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        letterAt(offset.y)?.let { letter ->
                            lastLetter = letter
                            onPressedLetterChange(letter)
                            onLetterSelected(letter, false)
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        val letter = letterAt(change.position.y)
                        if (letter != null && letter != lastLetter) {
                            lastLetter = letter
                            onPressedLetterChange(letter)
                            onLetterSelected(letter, false)
                        }
                    },
                    onDragEnd = { onPressedLetterChange(null) },
                    onDragCancel = { onPressedLetterChange(null) },
                )
            },
        verticalArrangement = Arrangement.spacedBy(LetterItemGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val active = letter == activeLetter
            Box(
                modifier = Modifier.height(LetterItemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
