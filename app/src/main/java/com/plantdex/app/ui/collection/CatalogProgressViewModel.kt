package com.plantdex.app.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantdex.app.data.catalog.CatalogRepository
import com.plantdex.app.data.catalog.CollectionProgress
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.ui.components.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 내 기록을 주제별 도감과 맞춰 수집 현황을 계산합니다. */
class CatalogProgressViewModel(
    uid: String,
    collectionRepository: CollectionRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    val state: StateFlow<LoadState<CollectionProgress>> = collectionRepository.observeMyEntries(uid)
        .map<List<CollectionEntry>, LoadState<CollectionProgress>> { LoadState.Success(catalogRepository.catalog.progress(it)) }
        .flowOn(Dispatchers.Default)
        .catch { emit(LoadState.Error(it.toMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
}
