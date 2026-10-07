package io.github.kylinlee.chatsim.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.repository.AttachmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import ezvcard.VCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VCardViewerViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
) : ViewModel() {
    private val _vCards = MutableStateFlow<List<VCard>>(emptyList())
    val vCards: StateFlow<List<VCard>> = _vCards.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _vCards.value = attachmentRepository.parseVCard(uri)
            _isLoading.value = false
        }
    }
}
