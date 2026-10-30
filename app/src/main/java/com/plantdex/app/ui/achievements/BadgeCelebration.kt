package com.plantdex.app.ui.achievements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantdex.app.data.achievements.Badge
import com.plantdex.app.data.achievements.BadgeLedger
import com.plantdex.app.R
import com.plantdex.app.ui.components.appContainer

/**
 * 새 배지를 얻는 순간 어느 화면에서든 축하 창을 띄웁니다. (여러 개면 하나씩)
 * 기록이 바뀔 때마다 배지를 다시 계산하고, 기기에 적어 둔 것과 비교해 새것만 보여줍니다.
 */
@Composable
fun BadgeCelebrationHost(uid: String) {
    val achievements = rememberAchievements(uid)
    val container = appContainer()
    val ledger = remember { BadgeLedger(container.appContext) }
    val queue = remember { mutableStateListOf<Badge>() }
    LaunchedEffect(achievements?.badges) {
        val badges = achievements?.badges ?: return@LaunchedEffect
        queue += ledger.newlyEarned(uid, badges)
    }
    queue.firstOrNull()?.let { badge ->
        BadgeEarnedDialog(badge, onDismiss = { queue.removeAt(0) })
    }
}

/** 축하 창: 배지 이모지가 통통 튀며 커지고, 뒤의 연초록 원이 은은하게 숨 쉽니다. */
@Composable
fun BadgeEarnedDialog(badge: Badge, onDismiss: () -> Unit) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(badge.id) {
        scale.snapTo(0f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val breathe by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "halo-scale",
    )
    val wiggle by rememberInfiniteTransition(label = "wiggle").animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "wiggle-deg",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.badges_earned_ok)) } },
        title = {
            Text(stringResource(R.string.badges_earned_title), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(120.dp)
                            .scale(breathe)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Text(
                        badge.emoji,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                            .rotate(wiggle),
                    )
                }
                Text(badge.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        },
    )
}
