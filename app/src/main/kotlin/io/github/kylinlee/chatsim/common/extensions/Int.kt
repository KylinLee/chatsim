package io.github.kylinlee.chatsim.common.extensions

import android.graphics.Color
import io.github.kylinlee.chatsim.common.DARK_GREY

// ===== Migrated from Simple-Commons =====

fun Int.getContrastColor(): Int {
    val y = (299 * Color.red(this) + 587 * Color.green(this) + 114 * Color.blue(this)) / 1000
    return if (y >= 149 && this != Color.BLACK) DARK_GREY else Color.WHITE
}
