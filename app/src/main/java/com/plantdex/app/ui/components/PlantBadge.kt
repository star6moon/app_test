package com.plantdex.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plantdex.app.data.catalog.PlantIcon

/** 식물 종 아이콘: 종마다 다른 색의 동그라미 위에 이모지. */
@Composable
fun PlantBadge(icon: PlantIcon, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val background = Color.hsl(icon.hue, 0.65f, if (isSystemInDarkTheme()) 0.32f else 0.86f)
    // 글꼴 크기 설정과 상관없이 동그라미 안에 맞도록 dp 기준으로 크기를 정합니다.
    val emojiSize = with(LocalDensity.current) { (size * 0.56f).toSp() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon.emoji, fontSize = emojiSize, lineHeight = emojiSize)
    }
}
