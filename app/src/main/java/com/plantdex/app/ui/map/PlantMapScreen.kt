package com.plantdex.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.Dp
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.plantdex.app.ui.components.PlantBadge
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.plantdex.app.BuildConfig
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.util.Formatters
import kotlinx.coroutines.launch

/** 위치 정보가 있는 기록이 하나도 없을 때 처음 보여줄 위치 (서울시청). */
private val DEFAULT_POSITION = LatLng(37.5665, 126.9780)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantMapScreen(
    user: UserProfile,
    onEntryClick: (entryId: String) -> Unit,
) {
    val container = appContainer()
    val viewModel: PlantMapViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlantMapViewModel(
                    uid = user.uid,
                    collectionRepository = container.collectionRepository,
                    localizer = container.plantNameLocalizer,
                    catalogRepository = container.catalogRepository,
                )
            }
        },
    )
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("식물 지도") },
                actions = {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(end = 8.dp)) {
                        MapScope.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = scope == option,
                                onClick = { viewModel.setScope(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, MapScope.entries.size),
                                icon = {},
                            ) { Text(option.label, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            !BuildConfig.HAS_MAPS_API_KEY -> MessageBox(
                "지도 API 키가 설정되지 않았어요",
                modifier,
                body = "local.properties 에 MAPS_API_KEY 를 추가한 뒤 다시 빌드해 주세요. (README 참고)",
            )
            else -> when (val s = state) {
                LoadState.Loading -> LoadingBox(modifier)
                is LoadState.Error -> MessageBox("불러오지 못했어요", modifier, body = s.message)
                is LoadState.Success -> PlantMap(
                    data = s.data,
                    onEntryClick = onEntryClick,
                    modifier = modifier,
                )
            }
        }
    }
}

/** 화면에서 이 거리(dp) 안에 있는 마커는 종류와 상관없이 하나로 묶습니다. 확대하면 다시 나뉩니다. */
private const val CLUSTER_DISTANCE_DP = 80

/** 지도 아래에 띄우는 선택 정보: 마커 하나 또는 묶음 하나 */
private sealed interface MapSelection {
    data class Single(val item: PlantMapItem) : MapSelection
    data class Group(val summary: ClusterSummary<PlantMapItem>) : MapSelection
}

private fun summarize(items: Collection<PlantMapItem>): ClusterSummary<PlantMapItem> =
    ClusterSummary.of(items, { it.speciesKey }, { it.name }, { it.entry.capturedAt })

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun PlantMap(
    data: PlantMapData,
    onEntryClick: (entryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 11f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<MapSelection?>(null) }
    // 패널이 사라지는 애니메이션 중에도 내용을 보여주기 위해 마지막 선택을 따로 기억합니다.
    var panelContent by remember { mutableStateOf<MapSelection?>(null) }
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun select(value: MapSelection, position: LatLng) {
        selection = value
        panelContent = value
        coroutineScope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLng(position), 300) }
    }

    fun zoomTo(items: List<PlantMapItem>) {
        val positions = items.map { it.latLng }.distinct()
        val update = if (positions.size == 1) {
            // 같은 자리에서 찍은 기록들은 확대해도 나뉘지 않으므로 적당히만 확대합니다.
            CameraUpdateFactory.newLatLngZoom(positions.first(), maxOf(cameraPositionState.position.zoom + 2f, 17f))
        } else {
            val bounds = LatLngBounds.builder().apply { positions.forEach { include(it) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 160)
        }
        selection = null
        coroutineScope.launch {
            // 패널 높이만큼의 지도 여백이 사라진 다음 프레임에 이동해야 화면이 좁을 때도 실패하지 않습니다.
            withFrameNanos { }
            animateSafely(cameraPositionState, update, 400)
        }
    }

    // 범위(내 도감/모두)를 바꿀 때마다 기록이 모두 보이도록 카메라를 맞춥니다.
    val hasItems = data.items.isNotEmpty()
    LaunchedEffect(mapLoaded, data.scope, hasItems) {
        if (!mapLoaded || !hasItems) return@LaunchedEffect
        selection = null
        val positions = data.items.map { it.latLng }
        val update = if (positions.distinct().size == 1) {
            CameraUpdateFactory.newLatLngZoom(positions.first(), 15f)
        } else {
            val bounds = LatLngBounds.builder().apply { positions.forEach { include(it) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 120)
        }
        animateSafely(cameraPositionState, update, 600)
    }

    val bottomPadding = when (selection) {
        is MapSelection.Single -> 140.dp
        is MapSelection.Group -> 340.dp
        null -> 0.dp
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
            onMapLoaded = { mapLoaded = true },
            onMapClick = { selection = null },
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            Clustering(
                items = data.items,
                onClusterClick = { cluster ->
                    select(MapSelection.Group(summarize(cluster.items)), cluster.position)
                    true // 기본 동작(확대) 대신 묶음 목록을 보여줍니다.
                },
                onClusterItemClick = { item ->
                    select(MapSelection.Single(item), item.latLng)
                    true // 기본 말풍선(info window) 대신 아래 카드를 보여줍니다.
                },
                onClusterItemInfoWindowClick = {},
                onClusterItemInfoWindowLongClick = {},
                clusterContent = { cluster ->
                    val summary = remember(cluster) { summarize(cluster.items) }
                    ClusterMarker(summary)
                },
                clusterItemContent = { item -> PlantMarker(item) },
                onClusterManager = { manager ->
                    // 기본값은 100dp 안에서 4개 이상일 때만 묶습니다. 2개부터 묶도록 바꿉니다.
                    val algorithm = manager.algorithm
                    if (algorithm.maxDistanceBetweenClusteredItems != CLUSTER_DISTANCE_DP) {
                        algorithm.maxDistanceBetweenClusteredItems = CLUSTER_DISTANCE_DP
                    }
                    (manager.renderer as? DefaultClusterRenderer<*>)?.minClusterSize = 2
                },
            )
        }

        if (data.withoutLocation > 0 || data.items.isEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                shape = RoundedCornerShape(50),
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                Text(
                    when {
                        data.items.isEmpty() -> "지도에 표시할 기록이 아직 없어요"
                        else -> "위치 정보가 없는 기록 ${data.withoutLocation}개는 표시되지 않아요"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = selection != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            when (val content = panelContent) {
                is MapSelection.Single -> SelectedEntryCard(
                    item = content.item,
                    onClick = { onEntryClick(content.item.entry.id) },
                )
                is MapSelection.Group -> ClusterPanel(
                    summary = content.summary,
                    onEntryClick = onEntryClick,
                    onZoom = { zoomTo(content.summary.groups.flatMap { it.items }) },
                    onClose = { selection = null },
                )
                null -> Unit
            }
        }
    }
}

/**
 * 영역 맞춤(newLatLngBounds)은 지도가 여백보다 작으면 IllegalStateException 을 던집니다.
 * 가로 화면 등에서 앱이 종료되지 않도록 그 경우는 이동을 건너뜁니다.
 */
private suspend fun animateSafely(state: CameraPositionState, update: CameraUpdate, durationMs: Int) {
    try {
        state.animate(update, durationMs)
    } catch (e: IllegalStateException) {
        // 이동 생략
    }
}

/** 개별 기록 마커: 종 아이콘 + 식물 이름 */
@Composable
private fun PlantMarker(item: PlantMapItem) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // 마커는 비트맵으로 그려져 그림자가 보이지 않으므로 테두리로 구분합니다.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlantBadge(item.icon, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

/** 겹쳐 놓은 종 아이콘 (최대 3개) */
@Composable
private fun StackedBadges(summary: ClusterSummary<PlantMapItem>, size: Dp) {
    val icons = summary.groups.take(3).map { it.items.first().icon }
    val step = size * 0.55f
    Box(Modifier.width(size + step * (icons.size - 1)).height(size)) {
        // 기록이 가장 많은 종이 맨 앞(왼쪽 위)에 오도록 뒤에서부터 그립니다.
        icons.withIndex().reversed().forEach { (index, icon) ->
            PlantBadge(icon, size = size, modifier = Modifier.offset(x = step * index))
        }
    }
}

/** 묶음 마커: 겹친 종 아이콘 + "능소화 등 4종" + 기록 수 */
@Composable
private fun ClusterMarker(summary: ClusterSummary<PlantMapItem>) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            Modifier.padding(start = 3.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StackedBadges(summary, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                summary.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    if (summary.recordCount > 99) "99+" else summary.recordCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectedEntryCard(item: PlantMapItem, onClick: () -> Unit) {
    val entry = item.entry
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.photoUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlantBadge(item.icon, size = 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    entry.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(Formatters.date(entry.capturedAt), entry.location?.placeName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${entry.ownerName} · 자세히 보기 ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 묶음을 눌렀을 때의 패널: 종별로 나눠 기록 수가 많은 순으로 보여주고,
 * 각 종의 사진을 옆으로 넘겨 볼 수 있습니다. 사진을 누르면 기록 상세로 갑니다.
 */
@Composable
private fun ClusterPanel(
    summary: ClusterSummary<PlantMapItem>,
    onEntryClick: (entryId: String) -> Unit,
    onZoom: () -> Unit,
    onClose: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StackedBadges(summary, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(summary.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "기록 ${summary.recordCount}개 · ${summary.groups.size}종",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
            }
            TextButton(onClick = onZoom, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Filled.ZoomIn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("이 지역 확대해서 보기")
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.heightIn(max = 240.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(summary.groups, key = { it.key }) { group ->
                    SpeciesGroupRow(group, onEntryClick)
                }
            }
        }
    }
}

@Composable
private fun SpeciesGroupRow(group: SpeciesGroup<PlantMapItem>, onEntryClick: (entryId: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlantBadge(group.items.first().icon, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                group.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${group.items.size}건",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(group.items, key = { it.entry.id }) { item ->
                Column(
                    Modifier.width(76.dp).clickable { onEntryClick(item.entry.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = item.entry.photoUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(76.dp).clip(MaterialTheme.shapes.small),
                    )
                    Text(
                        Formatters.date(item.entry.capturedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
