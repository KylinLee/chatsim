package io.github.kylinlee.chatsim.common.extensions

import android.view.View
import android.widget.RemoteViews

// ===== Migrated from Simple-Commons =====

fun RemoteViews.setText(id: Int, text: String) {
    setTextViewText(id, text)
}

fun RemoteViews.setVisibleIf(id: Int, beVisible: Boolean) {
    val visibility = if (beVisible) View.VISIBLE else View.GONE
    setViewVisibility(id, visibility)
}
