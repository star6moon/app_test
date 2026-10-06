package com.plantdex.app.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.components.LoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 도감 기록 목록(내 도감, 다른 사용자 도감, 피드)을 화면 상태로 변환합니다. */
class EntryListViewModel(source: Flow<List<CollectionEntry>>) : ViewModel() {

    val state: StateFlow<LoadState<List<CollectionEntry>>> = source
        .map<List<CollectionEntry>, LoadState<List<CollectionEntry>>> { LoadState.Success(it) }
        .catch { emit(LoadState.Error(it.toMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
}

internal fun Throwable.toMessage(): String = when {
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
        "Firestore 색인이 필요합니다. `firebase deploy --only firestore:indexes` 를 실행해 주세요."
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "볼 수 있는 권한이 없습니다."
    else -> message ?: "데이터를 불러오지 못했습니다."
}
