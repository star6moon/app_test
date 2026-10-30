package com.plantdex.app.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantdex.app.data.achievements.Badge
import com.plantdex.app.data.achievements.Badges
import com.plantdex.app.data.achievements.Mission
import com.plantdex.app.data.achievements.WeeklyMissions
import com.plantdex.app.data.catalog.CatalogRepository
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.data.repository.SocialRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    socialRepository: SocialRepository,
) : ViewModel() {
    // 팔로우하는 사람들의 최근 기록 (친구 따라 찾기 미션용). 못 불러오면 빈 목록.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val friendsEntries: Flow<List<CollectionEntry>> = socialRepository.followingIds
        .map { it.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { ids -> if (ids.isEmpty()) flowOf(emptyList()) else collectionRepository.observeFeedOf(ids, limit = 50L) }
        .catch { emit(emptyList()) }

    val state: StateFlow<Achievements?> = combine(collectionRepository.observeMyEntries(uid), friendsEntries) { entries, friends ->
            val catalog = catalogRepository.catalog
            Achievements(
                badges = Badges.compute(entries, catalog),
                missions = WeeklyMissions.forWeek(LocalDate.now(), catalog, entries, friendsEntries = friends),
            )
        }
        .flowOn(Dispatchers.Default)
        .catch { emit(Achievements(emptyList(), emptyList())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
