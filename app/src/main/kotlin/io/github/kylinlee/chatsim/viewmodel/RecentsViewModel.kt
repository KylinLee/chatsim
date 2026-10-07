package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.repository.CallLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentsViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recentCalls = MutableStateFlow<List<CallRecord>>(emptyList())
    val recentCalls: StateFlow<List<CallRecord>> = _recentCalls.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.RefreshCallLog, is AppEvent.RefreshContacts -> refresh()
                    else -> Unit
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val calls = callLogRepository.loadRecentCalls()
            _recentCalls.value = if (_query.value.isBlank()) calls else callLogRepository.search(_query.value)
            _isLoading.value = false
        }
    }

    fun search(query: String) {
        _query.value = query
        viewModelScope.launch {
            _recentCalls.value = if (query.isBlank()) {
                callLogRepository.getCachedCalls()
            } else {
                callLogRepository.search(query)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            callLogRepository.removeAllCalls()
            _recentCalls.value = emptyList()
        }
    }

    fun deleteCalls(ids: List<Int>) {
        if (ids.isEmpty()) return

        viewModelScope.launch {
            callLogRepository.removeCalls(ids)
            _recentCalls.value = _recentCalls.value.filterNot { it.id in ids || it.neighbourIDs.any { id -> id in ids } }
        }
    }

    fun getCallById(id: Int): CallRecord? = _recentCalls.value.firstOrNull { it.id == id }
}
