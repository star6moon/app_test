package com.plantdex.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.names.PlantNameLocalizer
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.ui.collection.toMessage
import com.plantdex.app.ui.components.LoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import java.util.Locale

/** 지도에 표시할 범위. */
enum class MapScope(val label: String) {
    Mine("내 도감"),
    Everyone("모두의 기록"),
}

/** 지도 마커 하나. [name] 은 보는 사람의 언어로 바꾼 식물 이름입니다. */
data class PlantMapItem(
    val entry: CollectionEntry,
    val name: String,
    val latitude: Double,
    val longitude: Double,
) : ClusterItem {
    val latLng: LatLng get() = LatLng(latitude, longitude)

    override fun getPosition() = latLng
    override fun getTitle() = name
    override fun getSnippet() = entry.scientificName
    override fun getZIndex(): Float? = null
}

data class PlantMapData(
    val items: List<PlantMapItem>,
    /** 위치 정보가 없어 지도에 표시하지 못한 기록 수 */
    val withoutLocation: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlantMapViewModel(
    private val uid: String,
    private val collectionRepository: CollectionRepository,
    private val localizer: PlantNameLocalizer,
) : ViewModel() {

    private val _scope = MutableStateFlow(MapScope.Mine)
    val scope: StateFlow<MapScope> = _scope.asStateFlow()

    val state: StateFlow<LoadState<PlantMapData>> = _scope
        .flatMapLatest { scope ->
            val entries = when (scope) {
                MapScope.Mine -> collectionRepository.observeMyEntries(uid)
                MapScope.Everyone -> collectionRepository.observeFeed()
            }
            entries
                .transformLatest<List<CollectionEntry>, LoadState<PlantMapData>> { emitMapData(it) }
                .onStart { emit(LoadState.Loading) }
                .catch { emit(LoadState.Error(it.toMessage())) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    /** 저장된 이름으로 먼저 보여주고, 보는 사람 언어의 이름을 찾으면 다시 보여줍니다. */
    private suspend fun FlowCollector<LoadState<PlantMapData>>.emitMapData(entries: List<CollectionEntry>) {
        val located = entries.filter { it.location != null }
        val withoutLocation = entries.size - located.size
        emit(LoadState.Success(PlantMapData(located.map { it.toItem(it.displayName) }, withoutLocation)))

        val locale = Locale.getDefault()
        val language = locale.toLanguageTag().substringBefore('-').lowercase()
        if (located.none { it.commonNameLanguage != language }) return

        val localized = coroutineScope {
            located.map { entry ->
                async {
                    val name = if (entry.commonNameLanguage == language) {
                        entry.displayName
                    } else {
                        localizer.localName(entry.scientificName, entry.gbifId, locale) ?: entry.displayName
                    }
                    entry.toItem(name)
                }
            }.awaitAll()
        }
        emit(LoadState.Success(PlantMapData(localized, withoutLocation)))
    }

    fun setScope(scope: MapScope) {
        _scope.value = scope
    }

    private fun CollectionEntry.toItem(name: String): PlantMapItem {
        val location = requireNotNull(location)
        return PlantMapItem(this, name, location.latitude, location.longitude)
    }
}
