package com.plantdex.app.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.rules.NearbyRecord
import com.plantdex.app.data.rules.NearbyRule
import com.plantdex.app.ui.components.EnglishName
import com.plantdex.app.ui.components.plantName
import com.plantdex.app.ui.components.InfoPill
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.PlantBadge
import com.plantdex.app.ui.components.ScientificName
import com.plantdex.app.ui.components.SectionHeader
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
    onSaveDraft: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 표본처럼 종이 카드 안에 들어간 찍은 사진과 날짜·장소
        item {
            PaperCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                AsyncImage(
                    model = state.photo.file,
                    contentDescription = stringResource(R.string.capture_taken_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp, end = 8.dp)
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.photo.extraFiles.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.photo.extraFiles.forEach { file ->
                                AsyncImage(
                                    model = file,
                                    contentDescription = stringResource(R.string.capture_extra_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                            }
                            Text(
                                stringResource(R.string.capture_extra_photos_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                    MetaRow(Icons.Outlined.Schedule, Formatters.dateTime(state.photo.capturedAt))
                    val location = state.photo.location
                    MetaRow(
                        Icons.Outlined.Place,
                        when {
                            location == null -> stringResource(R.string.capture_no_location)
                            location.placeName != null -> location.placeName
                            else -> Formatters.coordinates(location.latitude, location.longitude)
                        },
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeader(stringResource(R.string.capture_results_title))
                Text(
                    stringResource(R.string.capture_results_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        val topScore = state.candidates.firstOrNull()?.score ?: 0.0
        if (topScore < LOW_CONFIDENCE) {
            item {
                RuleNotice(
                    message = stringResource(R.string.capture_low_confidence_message, Formatters.percent(topScore)),
                    action = stringResource(R.string.capture_retake),
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
                hero = index == 0,
            )
        }
        // 메모와 공개 설정
        item {
            PaperCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.memo,
                        onValueChange = onMemoChange,
                        label = { Text(stringResource(R.string.capture_memo_label)) },
                        supportingText = { Text(stringResource(R.string.capture_memo_counter, state.memo.length, MAX_MEMO)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.capture_public_title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.capture_public_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = state.isPublic, onCheckedChange = onPublicChange)
                    }
                }
            }
        }
        if (!state.hasLocation) {
            item {
                RuleNotice(
                    message = stringResource(R.string.capture_no_location_message, NearbyRule.MIN_DISTANCE_METERS.toInt()),
                    action = if (state.isLocating) {
                        stringResource(R.string.capture_locating)
                    } else {
                        stringResource(R.string.capture_retry_location)
                    },
                    actionEnabled = !state.isLocating,
                    onAction = onRetryLocation,
                )
            }
        }
        state.selectedNearby?.let { nearby ->
            item {
                RuleNotice(
                    message = stringResource(
                        R.string.capture_nearby_message,
                        state.selected?.displayName.orEmpty(),
                        formatDistance(nearby.distanceMeters),
                        Formatters.date(nearby.entry.capturedAt),
                        NearbyRule.MIN_DISTANCE_METERS.toInt(),
                    ),
                    action = stringResource(R.string.capture_open_existing_entry),
                    actionEnabled = true,
                    onAction = { onOpenEntry(nearby.entry.id) },
                )
            }
        }
        state.saveError?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRetake,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.capture_retake)) }
                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.capture_save_to_collection))
                    }
                }
            }
            TextButton(
                onClick = onSaveDraft,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.capture_save_draft_register_later)) }
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

/**
 * 식별 후보 카드. 가장 유력한 첫 후보([hero])는 크게, 나머지는 한 줄짜리 작은 카드로 보여줍니다.
 * 고른 후보는 초록 테두리로 표시합니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    candidate: PlantCandidate,
    badge: CandidateBadge?,
    nearby: NearbyRecord?,
    selected: Boolean,
    onClick: () -> Unit,
    hero: Boolean = false,
) {
    val shape = if (hero) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    PaperCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = shape,
    ) {
        Column(
            Modifier.padding(if (hero) 16.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (hero) 10.dp else 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(if (hero) 72.dp else 48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    PlantBadge(rememberPlantArt(candidate.scientificName, candidate.family), size = if (hero) 64.dp else 44.dp)
                }
                Spacer(Modifier.width(12.dp))
                val plant = plantName(candidate)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            plant.korean,
                            style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (badge?.isNew == true) {
                            Spacer(Modifier.width(6.dp))
                            InfoPill(
                                "NEW",
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            )
                        }
                    }
                    EnglishName(
                        plant.english,
                        style = if (hero) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium,
                    )
                    ScientificName(
                        candidate.scientificName,
                        style = if (hero) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    )
                }
                RadioButton(selected = selected, onClick = null)
            }
            val family = candidate.family
            val hasCatalogs = badge != null && badge.catalogs.isNotEmpty()
            if (family != null || hasCatalogs) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    family?.let { InfoPill(stringResource(R.string.capture_family_label, it)) }
                    if (badge != null && badge.catalogs.isNotEmpty()) {
                        Text(
                            badge.catalogs.joinToString(" · ") { "${it.emoji} ${it.title}" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
            if (nearby != null) {
                Text(
                    stringResource(R.string.capture_nearby_badge, formatDistance(nearby.distanceMeters)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // 신뢰도: 둥근 막대 + 백분율
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { candidate.score.toFloat() },
                    modifier = Modifier
                        .weight(1f)
                        .height(if (hero) 8.dp else 6.dp)
                        .clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Spacer(Modifier.width(10.dp))
                if (hero) {
                    InfoPill(
                        Formatters.percent(candidate.score),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text(
                        Formatters.percent(candidate.score),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String = "${meters.toInt().coerceAtLeast(1)}m"

/** 가장 높은 후보의 신뢰도가 이보다 낮으면 다시 찍기를 권합니다. */
private const val LOW_CONFIDENCE = 0.2

/** 안내(등록할 수 없는 이유, 낮은 신뢰도 등)와 해결 버튼. 오류는 붉은 상자, 도움말은 부드러운 크라프트지 상자. */
@Composable
private fun RuleNotice(
    message: String,
    action: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    isError: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(start = 14.dp, top = 14.dp, end = 6.dp, bottom = 4.dp)) {
            Row(Modifier.padding(end = 8.dp)) {
                Icon(
                    if (isError) Icons.Outlined.Info else Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onAction, enabled = actionEnabled, modifier = Modifier.align(Alignment.End)) {
                Text(action)
            }
        }
    }
}
