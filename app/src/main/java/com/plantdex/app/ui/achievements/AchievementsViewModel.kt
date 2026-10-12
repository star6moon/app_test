package com.plantdex.app.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantdex.app.data.achievements.Badge
import com.plantdex.app.data.achievements.Badges
import com.plantdex.app.data.achievements.Mission
import com.plantdex.app.data.achievements.WeeklyMissions
import com.plantdex.app.data.catalog.CatalogRepository
import com.plantdex.app.data.repository.CollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class Achievements(val badges: List<Badge>, val missions: List<Mission>) {
    val earnedCount: Int get() = badges.count { it.earned }
}

/** 내 기록으로 배지와 이번 주 미션 진행 상황을 계산합니다. */
class AchievementsViewModel(
    uid: String,
    collectionRepository: CollectionRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    val state: StateFlow<Achievements?> = collectionRepository.observeMyEntries(uid)
        .map { entries ->
            val catalog = catalogRepository.catalog
            Achievements(
                badges = Badges.compute(entries, catalog),
                missions = WeeklyMissions.forWeek(LocalDate.now(), catalog, entries),
            )
        }
        .flowOn(Dispatchers.Default)
        .catch { emit(Achievements(emptyList(), emptyList())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
