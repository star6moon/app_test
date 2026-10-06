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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.components.CollectionSummary
import com.plantdex.app.ui.components.EntryGrid
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.appContainer

/** 다른 사용자의 공개 도감. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCollectionScreen(
    userId: String,
    userName: String,
    onEntryClick: (entryId: String) -> Unit,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val viewModel: EntryListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EntryListViewModel(container.collectionRepository.observePublicEntriesOf(userId)) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${userName}님의 도감") },
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
            emptyTitle = "공개된 기록이 없어요",
            onEntryClick = { onEntryClick(it.id) },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun EntryCollectionContent(
    state: LoadState<List<CollectionEntry>>,
    emptyTitle: String,
    onEntryClick: (CollectionEntry) -> Unit,
    modifier: Modifier = Modifier,
    emptyBody: String? = null,
) {
    when (state) {
        LoadState.Loading -> LoadingBox(modifier)
        is LoadState.Error -> MessageBox("불러오지 못했어요", modifier, body = state.message)
        is LoadState.Success ->
            if (state.data.isEmpty()) {
                MessageBox(emptyTitle, modifier, body = emptyBody)
            } else {
                EntryGrid(
                    entries = state.data,
                    onEntryClick = onEntryClick,
                    modifier = modifier,
                    header = { CollectionSummary(state.data) },
                )
            }
    }
}
