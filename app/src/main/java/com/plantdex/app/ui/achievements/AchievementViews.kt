package com.plantdex.app.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.data.achievements.Badge
import com.plantdex.app.data.achievements.Mission
import com.plantdex.app.ui.components.LoadingBox
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
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이번 주 미션", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${missions.count { it.done }}/${missions.size} 완료 · 월요일에 바뀌어요",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            missions.forEach { mission ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mission.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            mission.title,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (mission.done) TextDecoration.LineThrough else null,
                        )
                        LinearProgressIndicator(
                            progress = { mission.shownProgress.toFloat() / mission.goal },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (mission.done) "✅" else "${mission.shownProgress}/${mission.goal}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 내 도감 화면의 '배지' 요약 카드 */
@Composable
fun BadgesSummaryCard(badges: List<Badge>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("배지 ${badges.count { it.earned }} / ${badges.size}", style = MaterialTheme.typography.titleMedium)
                Text(
                    badges.filter { it.earned }.joinToString(" ") { it.emoji }.ifEmpty { "첫 기록을 남기고 배지를 모아 보세요" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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
                title = { Text("배지") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { padding ->
        if (achievements == null) {
            LoadingBox(Modifier.padding(padding))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(achievements.badges.sortedByDescending { it.earned }, key = { it.id }) { badge -> BadgeCell(badge) }
            }
        }
    }
}

@Composable
private fun BadgeCell(badge: Badge) {
    Column(
        Modifier.fillMaxWidth().alpha(if (badge.earned) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (badge.earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) { Text(badge.emoji, style = MaterialTheme.typography.headlineMedium) }
        Text(badge.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        Text(
            if (badge.earned) badge.description else "${badge.description} (${badge.progress.coerceAtMost(badge.goal)}/${badge.goal})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
