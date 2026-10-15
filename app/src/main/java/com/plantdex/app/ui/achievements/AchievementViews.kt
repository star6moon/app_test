package com.plantdex.app.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.R
import com.plantdex.app.data.achievements.Badge
import com.plantdex.app.data.achievements.Mission
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.SectionHeader
import com.plantdex.app.ui.components.appContainer

@Composable
fun rememberAchievements(uid: String): Achievements? {
    val container = appContainer()
    val viewModel: AchievementsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AchievementsViewModel(uid, container.collectionRepository, container.catalogRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    return state
}

/** 내 도감 화면의 '이번 주 미션' 카드 */
@Composable
fun MissionsCard(missions: List<Mission>, modifier: Modifier = Modifier) {
    PaperCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                stringResource(R.string.badges_weekly_missions),
                action = {
                    Text(
                        stringResource(R.string.badges_missions_progress, missions.count { it.done }, missions.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            missions.forEach { mission ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (mission.done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(mission.emoji, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            mission.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mission.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (mission.done) TextDecoration.LineThrough else null,
                        )
                        SoftProgressBar(mission.shownProgress.toFloat() / mission.goal.coerceAtLeast(1))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (mission.done) "✅" else "${mission.shownProgress}/${mission.goal}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 내 도감 화면의 '배지' 요약 카드 */
@Composable
fun BadgesSummaryCard(badges: List<Badge>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val emptyHint = stringResource(R.string.badges_summary_empty)
    PaperCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.MilitaryTech,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.badges_summary_count, badges.count { it.earned }, badges.size), style = MaterialTheme.typography.titleMedium)
                val earned = badges.filter { it.earned }.joinToString(" ") { it.emoji }
                Text(
                    earned.ifEmpty { emptyHint },
                    style = if (earned.isEmpty()) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                    color = if (earned.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 모든 배지 (얻은 것은 진하게, 아직인 것은 흐리게 + 진행률) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(uid: String, onBack: () -> Unit) {
    val achievements = rememberAchievements(uid)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.badges_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.badges_back)) }
                },
            )
        },
    ) { padding ->
        if (achievements == null) {
            LoadingBox(Modifier.padding(padding))
        } else if (achievements.badges.isEmpty()) {
            MessageBox(stringResource(R.string.badges_summary_empty), Modifier.padding(padding), emoji = "🏅")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(achievements.badges.sortedByDescending { it.earned }, key = { it.id }) { badge -> BadgeCell(badge) }
            }
        }
    }
}

/** 배지 한 칸: 얻은 배지는 연초록 원에 선명하게, 아직인 배지는 베이지 원에 흐리게 + 진행 막대. */
@Composable
private fun BadgeCell(badge: Badge) {
    PaperCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (badge.earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge.emoji,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.alpha(if (badge.earned) 1f else 0.35f),
                )
            }
            Text(
                badge.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (badge.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                if (badge.earned) badge.description else "${badge.description} (${badge.progress.coerceAtMost(badge.goal)}/${badge.goal})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
            )
            SoftProgressBar(
                badge.progress.coerceAtMost(badge.goal).toFloat() / badge.goal.coerceAtLeast(1),
                Modifier.padding(horizontal = 8.dp),
                color = if (badge.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 얇고 둥근 진행 막대 (베이지 바탕). */
@Composable
private fun SoftProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
