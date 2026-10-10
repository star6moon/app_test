package com.plantdex.app.ui.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.rules.NearbyRecord
import com.plantdex.app.data.rules.NearbyRule
import com.plantdex.app.ui.components.PlantBadge
import com.plantdex.app.ui.components.rememberPlantArt
import com.plantdex.app.util.Formatters

@Composable
fun IdentifyResultContent(
    state: CaptureUiState.Results,
    onSelect: (Int) -> Unit,
    onMemoChange: (String) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onRetryLocation: () -> Unit,
    onOpenEntry: (entryId: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AsyncImage(
                model = state.photo.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.large),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetaRow(Icons.Outlined.Schedule, Formatters.dateTime(state.photo.capturedAt))
                val location = state.photo.location
                MetaRow(
                    Icons.Outlined.Place,
                    when {
                        location == null -> "위치 정보 없음"
                        location.placeName != null -> location.placeName
                        else -> Formatters.coordinates(location.latitude, location.longitude)
                    },
                )
            }
        }
        item {
            Column {
                Text("AI 식별 결과", style = MaterialTheme.typography.titleMedium)
                Text(
                    "가장 비슷한 식물을 골라 주세요 · 식별: Pl@ntNet · 이름: GBIF, Wikidata",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val topScore = state.candidates.firstOrNull()?.score ?: 0.0
        if (topScore < LOW_CONFIDENCE) {
            item {
                RuleNotice(
                    message = "AI가 확신하지 못하고 있어요 (가장 높은 후보 ${Formatters.percent(topScore)}). " +
                        "꽃 한 송이나 잎 몇 장이 화면을 가득 채우도록, 흔들리지 않게 가까이서 찍으면 훨씬 정확해져요.",
                    action = "다시 찍기",
                    actionEnabled = !state.isSaving,
                    onAction = onRetake,
                    isError = false,
                )
            }
        }
        itemsIndexed(state.candidates) { index, candidate ->
            CandidateCard(
                candidate = candidate,
                badge = state.badges.getOrNull(index),
                nearby = state.nearby.getOrNull(index),
                selected = index == state.selectedIndex,
                onClick = { onSelect(index) },
            )
        }
        item {
            OutlinedTextField(
                value = state.memo,
                onValueChange = onMemoChange,
                label = { Text("메모 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("다른 사용자에게 공개", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "공개하면 피드와 내 프로필 도감에 표시돼요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.isPublic, onCheckedChange = onPublicChange)
            }
        }
        if (!state.hasLocation) {
            item {
                RuleNotice(
                    message = "위치 정보가 없어 등록할 수 없어요. 같은 식물은 이미 등록한 곳에서 " +
                        "${NearbyRule.MIN_DISTANCE_METERS.toInt()}m 이상 떨어진 곳에서만 등록할 수 있어서 위치가 필요해요.",
                    action = if (state.isLocating) "위치 찾는 중…" else "위치 다시 가져오기",
                    actionEnabled = !state.isLocating,
                    onAction = onRetryLocation,
                )
            }
        }
        state.selectedNearby?.let { nearby ->
            item {
                RuleNotice(
                    message = "${state.selected?.displayName.orEmpty()}은(는) 여기서 ${formatDistance(nearby.distanceMeters)} 떨어진 곳에 " +
                        "이미 등록했어요 (${Formatters.date(nearby.entry.capturedAt)}). 같은 식물은 기존 기록에서 " +
                        "${NearbyRule.MIN_DISTANCE_METERS.toInt()}m 이상 떨어진 곳에서만 등록할 수 있어요.",
                    action = "기존 기록 보기",
                    actionEnabled = true,
                    onAction = { onOpenEntry(nearby.entry.id) },
                )
            }
        }
        state.saveError?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRetake,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("다시 찍기") }
                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("도감에 등록")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CandidateCard(
    candidate: PlantCandidate,
    badge: CandidateBadge?,
    nearby: NearbyRecord?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(4.dp))
            PlantBadge(rememberPlantArt(candidate.scientificName, candidate.family), size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(candidate.displayName, style = MaterialTheme.typography.titleSmall)
                    if (badge?.isNew == true) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ) {
                            Text(
                                "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (candidate.commonNames.isNotEmpty()) {
                    Text(
                        candidate.scientificName,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                    )
                }
                candidate.family?.let {
                    Text(
                        "과: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (badge != null && badge.catalogs.isNotEmpty()) {
                    Text(
                        badge.catalogs.joinToString(" · ") { "${it.emoji} ${it.title}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (nearby != null) {
                    Text(
                        "📍 ${formatDistance(nearby.distanceMeters)} 거리에 이미 등록함",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { candidate.score.toFloat() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(Formatters.percent(candidate.score), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String = "${meters.toInt().coerceAtLeast(1)}m"

/** 가장 높은 후보의 신뢰도가 이보다 낮으면 다시 찍기를 권합니다. */
private const val LOW_CONFIDENCE = 0.2

/** 안내(등록할 수 없는 이유, 낮은 신뢰도 등)와 해결 버튼 */
@Composable
private fun RuleNotice(
    message: String,
    action: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    isError: Boolean = true,
) {
    Card(
        colors = if (isError) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, enabled = actionEnabled, modifier = Modifier.align(Alignment.End)) {
                Text(action)
            }
        }
    }
}
