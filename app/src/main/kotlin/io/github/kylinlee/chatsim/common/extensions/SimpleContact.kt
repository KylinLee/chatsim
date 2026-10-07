package io.github.kylinlee.chatsim.common.extensions

import android.text.TextUtils
import io.github.kylinlee.chatsim.domain.model.SimpleContact

fun ArrayList<SimpleContact>.getThreadTitle(): String = TextUtils.join(", ", map { it.name }.toTypedArray()).orEmpty()

fun ArrayList<SimpleContact>.getAddresses() = flatMap { it.phoneNumbers }.map { it.normalizedNumber }
