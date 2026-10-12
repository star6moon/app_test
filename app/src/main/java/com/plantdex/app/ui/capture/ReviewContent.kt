package com.plantdex.app.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

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
        Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large)) {
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
                Text(
                    "대표 사진",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            state.photos.forEachIndexed { i, file ->
                AsyncImage(
                    model = file,
                    contentDescription = "사진 ${i + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.small)
                        .border(
                            width = if (i == index) 3.dp else 0.dp,
                            color = if (i == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = MaterialTheme.shapes.small,
                        )
                        .clickable { selected = i },
                )
            }
            if (state.canAddMore && state.photos.isNotEmpty()) {
                OutlinedButton(
                    onClick = onAddMore,
                    enabled = !state.isBusy,
                    modifier = Modifier.height(64.dp),
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Text(" 다른 부위", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            "꽃·잎·줄기처럼 다른 부위를 함께 찍으면 더 정확해요 (최대 ${MAX_PHOTOS}장). " +
                "멀리서 찍었다면 '자르기' 로 식물만 남겨 주세요. 도감에는 첫 번째 사진이 올라가요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { cropping = index }, enabled = !state.isBusy && state.photos.isNotEmpty()) {
                Icon(Icons.Outlined.Crop, contentDescription = null)
                Text(" 자르기")
            }
            TextButton(onClick = { onRemove(index) }, enabled = !state.isBusy && state.photos.isNotEmpty()) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(" 이 사진 빼기")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReset, enabled = !state.isBusy, modifier = Modifier.weight(1f).height(48.dp)) {
                Text("처음부터")
            }
            Button(
                onClick = onIdentify,
                enabled = !state.isBusy && state.photos.isNotEmpty(),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("AI 식별하기 (${state.photos.size}장)") }
        }
    }
}
