package com.plantdex.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFlorist
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
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
                PlantMapViewModel(user.uid, container.collectionRepository, container.plantNameLocalizer)
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
                    scope = scope,
                    onEntryClick = onEntryClick,
                    modifier = modifier,
                )
            }
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun PlantMap(
    data: PlantMapData,
    scope: MapScope,
    onEntryClick: (entryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 11f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<PlantMapItem?>(null) }
    // 카드가 사라지는 애니메이션 중에도 내용을 보여주기 위해 마지막 선택을 따로 기억합니다.
    var cardItem by remember { mutableStateOf<PlantMapItem?>(null) }
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    // 범위(내 도감/모두)를 바꿀 때마다 기록이 모두 보이도록 카메라를 맞춥니다.
    val hasItems = data.items.isNotEmpty()
    LaunchedEffect(mapLoaded, scope, hasItems) {
        if (!mapLoaded || !hasItems) return@LaunchedEffect
        selected = null
        val positions = data.items.map { it.latLng }
        val update = if (positions.distinct().size == 1) {
            CameraUpdateFactory.newLatLngZoom(positions.first(), 15f)
        } else {
            val bounds = LatLngBounds.builder().apply { positions.forEach { include(it) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 120)
        }
        cameraPositionState.animate(update, 600)
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
            onMapLoaded = { mapLoaded = true },
            onMapClick = { selected = null },
            contentPadding = PaddingValues(bottom = if (selected != null) 140.dp else 0.dp),
        ) {
            Clustering(
                items = data.items,
                onClusterClick = { cluster ->
                    // 묶음을 누르면 그 안의 기록들이 보이도록 확대합니다.
                    val bounds = LatLngBounds.builder().apply { cluster.items.forEach { include(it.latLng) } }.build()
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 160), 400)
                    }
                    true
                },
                onClusterItemClick = { item ->
                    selected = item
                    cardItem = item
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(item.latLng), 300)
                    }
                    true // 기본 말풍선(info window) 대신 아래 카드를 보여줍니다.
                },
                clusterContent = { cluster -> ClusterBadge(cluster.size) },
                clusterItemContent = { item -> PlantMarker(item.name) },
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
            visible = selected != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            cardItem?.let { item ->
                SelectedEntryCard(item = item, onClick = { onEntryClick(item.entry.id) })
            }
        }
    }
}

/** 개별 기록 마커: 꽃 아이콘 + 식물 이름 */
@Composable
private fun PlantMarker(name: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LocalFlorist, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
}

/** 가까운 기록 여러 개를 묶은 마커 */
@Composable
private fun ClusterBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (count > 99) "99+" else count.toString(), style = MaterialTheme.typography.labelLarge)
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
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
