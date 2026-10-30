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
import com.plantdex.app.data.achievements.LevelProgress
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
    val queue = remember { mutableStateListOf<Celebration>() }
    val levelHeadline = stringResource(R.string.levels_up_title)
    val badgeHeadline = stringResource(R.string.badges_earned_title)
    LaunchedEffect(achievements?.badges, achievements?.speciesCount) {
        val current = achievements ?: return@LaunchedEffect
        ledger.levelUp(uid, current.level.level)?.let { level ->
            queue += Celebration(level.emoji, levelHeadline, "Lv.${level.number} ${level.title}", levelSubtitle(current.level))
        }
        queue += ledger.newlyEarned(uid, current.badges).map { Celebration(it.emoji, badgeHeadline, it.title, it.description) }
    }
    queue.firstOrNull()?.let { c ->
        CelebrationDialog(c, onDismiss = { queue.removeAt(0) })
    }
}

private fun levelSubtitle(progress: LevelProgress): String =
    progress.next?.let { "다음 레벨 ${it.title}까지 ${progress.remaining}종 남았어요" } ?: "가장 높은 레벨이에요!"

/** 축하 창에 보여줄 내용 (배지 또는 레벨 업) */
data class Celebration(val emoji: String, val headline: String, val title: String, val description: String)

/** 새 배지 축하 창 */
@Composable
fun BadgeEarnedDialog(badge: Badge, onDismiss: () -> Unit) {
    CelebrationDialog(Celebration(badge.emoji, stringResource(R.string.badges_earned_title), badge.title, badge.description), onDismiss)
}

/** 축하 창: 이모지가 통통 튀며 커지고, 뒤의 연초록 원이 은은하게 숨 쉽니다. */
@Composable
fun CelebrationDialog(celebration: Celebration, onDismiss: () -> Unit) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(celebration) {
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
            Text(celebration.headline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                        celebration.emoji,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                            .rotate(wiggle),
                    )
                }
                Text(celebration.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    celebration.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        },
    )
}
