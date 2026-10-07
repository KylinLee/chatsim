package io.github.kylinlee.chatsim.data.events

import io.github.kylinlee.chatsim.domain.model.AppEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance

object AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun tryEmit(event: AppEvent) {
        _events.tryEmit(event)
    }

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    inline fun <reified T : AppEvent> eventsOfType(): Flow<T> = events.filterIsInstance<T>()
}
