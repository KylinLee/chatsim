package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.common.TAB_MESSAGES
import io.github.kylinlee.chatsim.common.tabsList
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _showTabs = MutableStateFlow(settings.showTabs)
    val showTabs: StateFlow<Int> = _showTabs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val conversations: StateFlow<List<ConversationThread>> = conversationRepository.conversations

    val unreadConversationsCount: StateFlow<Int> = conversations
        .map { conversations -> conversations.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val visibleTabs: StateFlow<List<Int>> = showTabs
        .map { mask -> tabsList.filter { mask and it != 0 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tabsList.filter { _showTabs.value and it != 0 })

    init {
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.RefreshConversations, is AppEvent.RefreshMessages, is AppEvent.ConversationsChanged -> {
                        conversationRepository.refresh()
                    }

                    else -> Unit
                }
            }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            _isRefreshing.value = true
            conversationRepository.refresh()
            _isRefreshing.value = false
        }
    }

    fun setShowTabs(mask: Int) {
        settings.showTabs = mask
        _showTabs.value = mask
    }

    fun saveLastUsedPage(index: Int) {
        settings.lastUsedViewPagerPage = index
    }

    fun isMessagesTabVisible(): Boolean = _showTabs.value and TAB_MESSAGES != 0
}
