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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.plantdex.app.util.Formatters

@Composable
fun IdentifyResultContent(
    state: CaptureUiState.Results,
    onSelect: (Int) -> Unit,
    onMemoChange: (String) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetake: () -> Unit,
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
        itemsIndexed(state.candidates) { index, candidate ->
            CandidateCard(
                candidate = candidate,
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
                    enabled = !state.isSaving && state.selected != null,
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
private fun CandidateCard(candidate: PlantCandidate, selected: Boolean, onClick: () -> Unit) {
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
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(candidate.displayName, style = MaterialTheme.typography.titleSmall)
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
