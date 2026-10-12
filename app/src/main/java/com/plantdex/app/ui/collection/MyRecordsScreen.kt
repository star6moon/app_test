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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.data.filter.PeriodFilter
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.PeriodFilterBar
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
    var period by remember { mutableStateOf<PeriodFilter>(PeriodFilter.All) }
    val filtered = remember(state, period) {
        when (val s = state) {
            is LoadState.Success -> LoadState.Success(s.data.filter { period.matches(it.capturedAt) })
            else -> s
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("모든 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PeriodFilterBar(selected = period, onSelect = { period = it }, modifier = Modifier.padding(vertical = 4.dp))
            EntryCollectionContent(
                state = filtered,
                emptyTitle = if (period == PeriodFilter.All) "아직 수집한 식물이 없어요" else "이 기간에 찍은 기록이 없어요",
                emptyBody = if (period == PeriodFilter.All) "촬영 탭에서 주변 식물을 찍어 첫 기록을 남겨보세요." else "다른 기간을 골라 보세요.",
                onEntryClick = { onEntryClick(it.id) },
            )
        }
    }
}
