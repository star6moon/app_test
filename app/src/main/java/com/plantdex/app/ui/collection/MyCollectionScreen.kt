package com.plantdex.app.ui.collection

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.components.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCollectionScreen(
    user: UserProfile,
    onEntryClick: (entryId: String) -> Unit,
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
                title = { Text("${user.displayName}님의 도감") },
                actions = {
                    IconButton(onClick = { container.authRepository.signOut() }) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "로그아웃")
                    }
                },
            )
        },
    ) { padding ->
        EntryCollectionContent(
            state = state,
            emptyTitle = "아직 수집한 식물이 없어요",
            emptyBody = "촬영 탭에서 주변 식물을 찍어 첫 기록을 남겨보세요.",
            onEntryClick = { onEntryClick(it.id) },
            modifier = Modifier.padding(padding),
        )
    }
}
