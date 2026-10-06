package com.plantdex.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.repository.AuthRepository
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.ui.collection.toMessage
import com.plantdex.app.ui.components.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EntryDetailViewModel(
    entryId: String,
    private val collectionRepository: CollectionRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val currentUid = authRepository.currentUser.value?.uid

    val state: StateFlow<LoadState<CollectionEntry?>> = collectionRepository.observeEntry(entryId)
        .map<CollectionEntry?, LoadState<CollectionEntry?>> { LoadState.Success(it) }
        .catch { emit(LoadState.Error(it.toMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun isOwner(entry: CollectionEntry) = entry.ownerId == currentUid

    fun setPublic(entry: CollectionEntry, isPublic: Boolean) {
        viewModelScope.launch {
            runCatching { collectionRepository.setPublic(entry.id, isPublic) }
                .onFailure { _message.value = "공개 설정을 바꾸지 못했습니다." }
        }
    }

    fun delete(entry: CollectionEntry) {
        viewModelScope.launch {
            runCatching { collectionRepository.deleteEntry(entry) }
                .onSuccess { _deleted.value = true }
                .onFailure { _message.value = "삭제하지 못했습니다." }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
