package com.plantdex.app.ui.collection

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.data.repository.ReactionRepository
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 책갈피한 기록 목록 (최근에 책갈피한 순). 삭제되었거나 비공개로 바뀐 기록은 보이지 않습니다. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModel(
    reactionRepository: ReactionRepository,
    collectionRepository: CollectionRepository,
) : ViewModel() {

    val state: StateFlow<LoadState<List<CollectionEntry>>> = reactionRepository.myBookmarkIds
        .flatMapLatest<List<String>?, LoadState<List<CollectionEntry>>> { ids ->
            when {
                ids == null -> flowOf(LoadState.Loading)
                ids.isEmpty() -> flowOf(LoadState.Success(emptyList()))
                else -> combine(
                    ids.map { id ->
                        collectionRepository.observeEntry(id).catch { emit(null) }
                    },
                ) { entries -> LoadState.Success(entries.filterNotNull()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onEntryClick: (entryId: String) -> Unit,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val viewModel: BookmarksViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BookmarksViewModel(container.reactionRepository, container.collectionRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("책갈피") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        EntryCollectionContent(
            state = state,
            emptyTitle = "책갈피한 기록이 없어요",
            emptyBody = "피드나 기록 상세에서 🔖 버튼을 눌러 마음에 드는 기록을 모아 보세요.",
            onEntryClick = { onEntryClick(it.id) },
            modifier = Modifier.padding(padding),
            showSummary = false,
        )
    }
}
