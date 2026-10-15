package com.plantdex.app.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.util.ImageUtils
import java.io.File

/**
 * 사진에서 식물 부분만 남기도록 자릅니다.
 * 멀리서 찍었거나 배경이 복잡한 사진은 식물만 크게 남기면 AI 가 훨씬 잘 알아봅니다.
 * 상자 안쪽을 끌면 이동, 네 모서리를 끌면 크기 조절.
 */
@Composable
fun CropScreen(file: File, onCancel: () -> Unit, onConfirm: (CropRect) -> Unit) {
    BackHandler(onBack = onCancel)
    val imageSize = remember(file) { ImageUtils.imageSize(file) }
    var rect by remember(file) { mutableStateOf(CropRect.Initial) }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Text(
            stringResource(R.string.capture_crop_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val (imgW, imgH) = imageSize
            if (imgW <= 0 || imgH <= 0) {
                Text(stringResource(R.string.capture_crop_load_failed), color = Color.White)
            } else {
            // ContentScale.Fit 으로 그렸을 때 사진이 차지하는 영역
            val scale = minOf(boxW / imgW, boxH / imgH)
            val shownW = imgW * scale
            val shownH = imgH * scale
            val shownWdp = with(density) { shownW.toDp() }
            val shownHdp = with(density) { shownH.toDp() }
            val handleTolerance = with(density) { 28.dp.toPx() }
            val currentRect by rememberUpdatedState(rect)

            Box(Modifier.size(shownWdp, shownHdp)) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(shownW, shownH) {
                            var handle: CropRect.Handle? = null
                            detectDragGestures(
                                onDragStart = { pos ->
                                    handle = currentRect.handleAt(
                                        pos.x / shownW,
                                        pos.y / shownH,
                                        handleTolerance / shownW,
                                        handleTolerance / shownH,
                                    )
                                },
                                onDragEnd = { handle = null },
                                onDragCancel = { handle = null },
                                onDrag = { change, drag ->
                                    val h = handle ?: return@detectDragGestures
                                    change.consume()
                                    rect = currentRect.resized(h, drag.x / shownW, drag.y / shownH)
                                },
                            )
                        },
                ) {
                    val l = rect.left * size.width
                    val t = rect.top * size.height
                    val r = rect.right * size.width
                    val b = rect.bottom * size.height
                    // 상자 바깥은 어둡게
                    clipRect(l, t, r, b, clipOp = ClipOp.Difference) {
                        drawRect(Color.Black.copy(alpha = 0.55f))
                    }
                    // 3등분 안내선
                    for (i in 1..2) {
                        val x = l + (r - l) * i / 3f
                        val y = t + (b - t) * i / 3f
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(x, t), Offset(x, b), strokeWidth = 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(l, y), Offset(r, y), strokeWidth = 1.dp.toPx())
                    }
                    drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                    val handle = 14.dp.toPx()
                    for ((cx, cy) in listOf(l to t, r to t, l to b, r to b)) {
                        drawRoundRect(
                            Color.White,
                            topLeft = Offset(cx - handle / 2, cy - handle / 2),
                            size = Size(handle, handle),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            ) { Text(stringResource(R.string.capture_cancel), color = Color.White) }
            TextButton(onClick = { rect = CropRect.Full }, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.capture_crop_full), color = Color.White)
            }
            Button(onClick = { onConfirm(rect) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.capture_crop)) }
        }
    }
}
