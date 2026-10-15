package com.plantdex.app.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.ui.components.InfoPill
import com.plantdex.app.ui.components.PaperCard

/**
 * 찍은 사진 확인 화면.
 * - 자르기: 식물만 남겨 멀리서 찍은 사진도 잘 알아보게
 * - 다른 부위 추가: 꽃·잎·줄기를 함께 보내면 AI 가 더 정확해집니다 (최대 [MAX_PHOTOS]장)
 */
@Composable
fun ReviewContent(
    state: CaptureUiState.Review,
    onAddMore: () -> Unit,
    onRemove: (Int) -> Unit,
    onCrop: (Int, CropRect) -> Unit,
    onIdentify: () -> Unit,
    onReset: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    var cropping by remember { mutableStateOf<Int?>(null) }
    val index = selected.coerceIn(0, (state.photos.size - 1).coerceAtLeast(0))

    val cropIndex = cropping
    val cropFile = cropIndex?.let { state.photos.getOrNull(it) }
    if (cropIndex != null && cropFile != null) {
        CropScreen(
            file = cropFile,
            onCancel = { cropping = null },
            onConfirm = { rect ->
                cropping = null
                onCrop(cropIndex, rect)
            },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 고른 사진을 표본처럼 종이 카드 안에 크게
        PaperCard(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Box(
                Modifier
                    .padding(8.dp)
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                state.photos.getOrNull(index)?.let { file ->
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.isBusy) {
                    Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.4f)) {
                        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                    }
                }
                if (index == 0 && state.photos.isNotEmpty()) {
                    InfoPill(
                        stringResource(R.string.capture_main_photo),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // 사진 칸: 둥근 썸네일 + 다른 부위 추가 칸
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            state.photos.forEachIndexed { i, file ->
                AsyncImage(
                    model = file,
                    contentDescription = stringResource(R.string.capture_photo_description, i + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = if (i == index) 3.dp else 1.dp,
                            color = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable { selected = i },
                )
            }
            if (state.canAddMore && state.photos.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onAddMore,
                    enabled = !state.isBusy,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.height(68.dp),
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Text(stringResource(R.string.capture_add_other_part), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 도움말 상자
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(Modifier.padding(12.dp)) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.capture_review_hint, MAX_PHOTOS),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { cropping = index }, enabled = !state.isBusy && state.photos.isNotEmpty()) {
                Icon(Icons.Outlined.Crop, contentDescription = null)
                Text(stringResource(R.string.capture_crop_with_space))
            }
            TextButton(onClick = { onRemove(index) }, enabled = !state.isBusy && state.photos.isNotEmpty()) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(stringResource(R.string.capture_remove_photo))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReset, enabled = !state.isBusy, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.capture_start_over))
            }
            Button(
                onClick = onIdentify,
                enabled = !state.isBusy && state.photos.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(stringResource(R.string.capture_identify_photos, state.photos.size)) }
        }
    }
}
