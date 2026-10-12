package com.plantdex.app.ui.map

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.plantdex.app.R
import com.plantdex.app.data.art.PlantArt
import com.plantdex.app.data.art.PlantArts
import com.plantdex.app.data.catalog.CatalogRepository
import com.plantdex.app.data.catalog.SpeciesKey
import com.plantdex.app.data.filter.PeriodFilter
import com.plantdex.app.data.geo.GeoBounds
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.names.PlantNameLocalizer
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.ui.collection.toMessage
import com.plantdex.app.ui.components.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** 지도를 멈춘 뒤 이만큼 기다렸다가 불러옵니다 (연속으로 움직일 때 읽기 횟수 절약). */
private const val VIEWPORT_DEBOUNCE_MS = 500L

/** 지도에 표시할 범위. */
enum class MapScope(@StringRes val labelRes: Int) {
    Mine(R.string.map_scope_mine),
    Everyone(R.string.map_scope_everyone),
}

/**
 * 지도 마커 하나. [name] 은 보는 사람의 언어로 바꾼 식물 이름,
 * [speciesKey] 는 묶음 안에서 같은 종을 셀 때 쓰는 키입니다.
 */
data class PlantMapItem(
    val entry: CollectionEntry,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val speciesKey: String,
    /** 종의 외형 그림 */
    val art: PlantArt,
) : ClusterItem {
    val latLng: LatLng get() = LatLng(latitude, longitude)

    override fun getPosition() = latLng
    override fun getTitle() = name
    override fun getSnippet() = entry.scientificName
    override fun getZIndex(): Float? = null
}

data class PlantMapData(
    /** 이 데이터를 만든 범위. 범위를 바꾸면 새 데이터가 올 때까지 이전 지도를 그대로 보여줍니다. */
    val scope: MapScope,
    val items: List<PlantMapItem>,
    /** 위치 정보가 없어 지도에 표시하지 못한 기록 수 */
    val withoutLocation: Int,
    /** true 면 지도 영역에 따라 불러온 데이터 (모두의 기록). 카메라를 자동으로 맞추지 않습니다. */
    val viewportDriven: Boolean = false,
    /** 기록이 많아 이 영역의 일부만 불러왔는지 */
    val truncated: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PlantMapViewModel(
    private val uid: String,
    private val collectionRepository: CollectionRepository,
    private val localizer: PlantNameLocalizer,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _scope = MutableStateFlow(MapScope.Mine)
    val scope: StateFlow<MapScope> = _scope.asStateFlow()

    private val _period = MutableStateFlow<PeriodFilter>(PeriodFilter.All)
    val period: StateFlow<PeriodFilter> = _period.asStateFlow()

    /** 지금 지도에 보이는 영역. 지도가 멈출 때마다 화면이 알려 줍니다. */
    private val viewport = MutableStateFlow<GeoBounds?>(null)

    /** 모두의 기록을 불러오는 중 (지도는 그대로 두고 작은 표시만) */
    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    /** 영역 불러오기 실패 안내 (지도는 그대로 두고, 움직이면 다시 시도) */
    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError: StateFlow<String?> = _fetchError.asStateFlow()

    private data class Source(val entries: List<CollectionEntry>, val viewportDriven: Boolean, val truncated: Boolean)

    private fun source(scope: MapScope): Flow<Source> = when (scope) {
        MapScope.Mine -> collectionRepository.observeMyEntries(uid).map { Source(it, viewportDriven = false, truncated = false) }
        // 모두의 기록은 화면에 보이는 영역만 불러옵니다 (지도를 움직이면 다시 불러옴).
        MapScope.Everyone -> viewport
            .filterNotNull()
            .debounce(VIEWPORT_DEBOUNCE_MS)
            .distinctUntilChanged()
            .mapLatest { bounds ->
                _isFetching.value = true
                try {
                    val (entries, truncated) = collectionRepository.fetchPublicInBounds(bounds)
                    _fetchError.value = null
                    Source(entries, viewportDriven = true, truncated = truncated)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _fetchError.value = e.toMessage()
                    Source(emptyList(), viewportDriven = true, truncated = false)
                } finally {
                    _isFetching.value = false
                }
            }
            .onStart { if (viewport.value == null) emit(Source(emptyList(), viewportDriven = true, truncated = false)) }
    }

    val state: StateFlow<LoadState<PlantMapData>> = _scope
        .flatMapLatest { scope ->
            combine(source(scope), _period) { src, period -> src to period }
                .transformLatest<Pair<Source, PeriodFilter>, LoadState<PlantMapData>> { (src, period) ->
                    val today = LocalDate.now()
                    val zone = ZoneId.systemDefault()
                    emitMapData(
                        scope,
                        src.entries.filter { period.matches(it.capturedAt, today, zone) },
                        src.viewportDriven,
                        src.truncated,
                    )
                }
                .catch { emit(LoadState.Error(it.toMessage())) }
        }
        // 도감 파일 읽기와 아이콘 계산을 메인 스레드 밖에서 합니다.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    /** 저장된 이름으로 먼저 보여주고, 보는 사람 언어의 이름을 찾으면 다시 보여줍니다. */
    private suspend fun FlowCollector<LoadState<PlantMapData>>.emitMapData(
        scope: MapScope,
        entries: List<CollectionEntry>,
        viewportDriven: Boolean,
        truncated: Boolean,
    ) {
        val located = entries.filter { it.location != null }
        val withoutLocation = entries.size - located.size
        fun data(items: List<PlantMapItem>) = PlantMapData(scope, items, withoutLocation, viewportDriven, truncated)
        emit(LoadState.Success(data(located.map { it.toItem(it.displayName) })))

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
        emit(LoadState.Success(data(localized)))
    }

    fun setScope(scope: MapScope) {
        _scope.value = scope
    }

    fun setPeriod(period: PeriodFilter) {
        _period.value = period
    }

    fun onViewportChanged(bounds: GeoBounds) {
        viewport.value = bounds
    }

    private fun CollectionEntry.toItem(name: String): PlantMapItem {
        val location = requireNotNull(location)
        val catalog = runCatching { catalogRepository.catalog }.getOrNull()
        return PlantMapItem(
            entry = this,
            name = name,
            latitude = location.latitude,
            longitude = location.longitude,
            speciesKey = SpeciesKey.of(scientificName, catalog),
            art = PlantArts.forPlant(scientificName, family, catalog),
        )
    }
}
