package io.github.kylinlee.chatsim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

private val AvatarColors = listOf(
    Color(0xFFE57373),
    Color(0xFFBA68C8),
    Color(0xFF7986CB),
    Color(0xFF4FC3F7),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFA1887F),
)

@Composable
fun ConversationAvatar(
    title: String = "",
    photoUri: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val shapeModifier = modifier
        .size(size)
        .clip(CircleShape)

    if (photoUri.isNotEmpty()) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = shapeModifier,
        )
    } else {
        Box(
            modifier = shapeModifier.background(avatarColor(title)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun avatarColor(name: String): Color {
    if (name.isEmpty()) return AvatarColors.first()
    return AvatarColors[name.hashCode().absoluteValue % AvatarColors.size]
}
