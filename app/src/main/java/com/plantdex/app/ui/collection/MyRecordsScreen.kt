package com.plantdex.app.ui.collection

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.R
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.components.appContainer

/** 내 기록 전체 (도감 밖 식물 포함). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRecordsScreen(
    user: UserProfile,
    onEntryClick: (entryId: String) -> Unit,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val viewModel: EntryListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EntryListViewModel(container.collectionRepository.observeMyEntries(user.uid)) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collection_all_records)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.collection_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            EntryCollectionContent(
                state = state,
                emptyTitle = stringResource(R.string.collection_records_empty_title),
                emptyBody = stringResource(R.string.collection_records_empty_body),
                emptyEmoji = "🌱",
                onEntryClick = { onEntryClick(it.id) },
            )
        }
    }
}
