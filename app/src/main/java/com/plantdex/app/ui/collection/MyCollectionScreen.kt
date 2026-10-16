package com.plantdex.app.ui.collection

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantdex.app.R
import com.plantdex.app.data.catalog.CatalogProgress
import com.plantdex.app.data.catalog.CollectionProgress
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.ui.achievements.BadgesSummaryCard
import com.plantdex.app.ui.achievements.MissionsCard
import com.plantdex.app.ui.achievements.rememberAchievements
import com.plantdex.app.ui.profile.ProfileHeader
import kotlinx.coroutines.flow.catch
import com.plantdex.app.ui.components.InfoPill
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.SectionHeader
import com.plantdex.app.ui.components.appContainer

/** 내 도감 홈: 야외 관찰 노트의 표지처럼 전체 수집률, 주제별 도감, 메뉴를 차례로 보여줍니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCollectionScreen(
    user: UserProfile,
    onCatalogClick: (catalogId: String) -> Unit,
    onAllRecordsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBadgesClick: () -> Unit,
    onRankingClick: () -> Unit,
) {
    val container = appContainer()
    val viewModel: CatalogProgressViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CatalogProgressViewModel(user.uid, container.collectionRepository, container.catalogRepository)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarkIds by container.reactionRepository.myBookmarkIds.collectAsStateWithLifecycle()
    val profileFlow = remember(user.uid) { container.socialRepository.observeProfile(user.uid).catch { emit(null) } }
    val profile by profileFlow.collectAsStateWithLifecycle(initialValue = null)
    val achievements = rememberAchievements(user.uid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collection_owner_title, user.displayName)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.collection_settings))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            LoadState.Loading -> LoadingBox(modifier)
            is LoadState.Error -> MessageBox(
                stringResource(R.string.collection_load_failed),
                modifier,
                body = s.message,
                emoji = "⚠️",
            )
            is LoadState.Success -> LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 표지: 프로필, 전체 수집률
                profile?.let { p ->
                    item { ProfileHeader(profile = p, isMe = true, isFollowing = null, onFollowChange = {}) }
                }
                item { OverallCard(s.data) }
                achievements?.missions?.takeIf { it.isNotEmpty() }?.let { missions ->
                    item { MissionsCard(missions, Modifier.padding(top = 4.dp)) }
                }
                // 본문: 주제별 도감
                items(s.data.catalogs, key = { it.catalog.id }) { progress ->
                    CatalogCard(progress, onClick = { onCatalogClick(progress.catalog.id) })
                }
                // 목차: 기록·책갈피·배지·랭킹·설정
                item {
                    MenuRow(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = stringResource(R.string.collection_all_records_title),
                        subtitle = stringResource(R.string.collection_all_records_subtitle, s.data.recordCount),
                        onClick = onAllRecordsClick,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item {
                    val count = bookmarkIds?.size
                    MenuRow(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = stringResource(R.string.collection_bookmarks_title),
                        subtitle = if (count == null) {
                            stringResource(R.string.collection_loading)
                        } else {
                            stringResource(R.string.collection_bookmarks_count, count)
                        },
                        onClick = onBookmarksClick,
                    )
                }
                achievements?.let { a ->
                    item { BadgesSummaryCard(a.badges, onClick = onBadgesClick) }
                }
                item {
                    MenuRow(
                        icon = Icons.Outlined.EmojiEvents,
                        title = stringResource(R.string.collection_ranking_title),
                        subtitle = stringResource(R.string.collection_ranking_subtitle),
                        onClick = onRankingClick,
                    )
                }
                item {
                    MenuRow(
                        icon = Icons.Outlined.Settings,
                        title = stringResource(R.string.collection_settings),
                        subtitle = null,
                        onClick = onSettingsClick,
                    )
                }
            }
        }
    }
}

/** 메뉴 한 줄: 연초록 원 안의 아이콘 + 제목 + 설명 + 화살표. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 전체 도감 진행 카드 (기록·종 수는 바로 위 프로필에 있으므로 수집률 중심으로). */
@Composable
private fun OverallCard(progress: CollectionProgress) {
    PaperCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(R.string.collection_overall_title))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${progress.collectedSpecies}",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.collection_overall_total_species, progress.totalSpecies),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            ProgressBar(progress.collectedSpecies, progress.totalSpecies)
            Text(
                if (progress.outsideCatalogSpecies > 0) {
                    stringResource(
                        R.string.collection_overall_summary_with_outside,
                        progress.recordCount,
                        progress.discoveredSpecies,
                        progress.outsideCatalogSpecies,
                    )
                } else {
                    stringResource(R.string.collection_overall_summary, progress.recordCount, progress.discoveredSpecies)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 주제별 도감 한 권 (표지 그림 + 제목 + 진행 막대). */
@Composable
private fun CatalogCard(progress: CatalogProgress, onClick: () -> Unit) {
    PaperCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(progress.catalog.emoji, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        progress.catalog.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress.isComplete) CompleteBadge()
                    Text(
                        "${progress.collected} / ${progress.total}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    progress.catalog.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressBar(progress.collected, progress.total, Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 둥근 진행 막대: 숲색 막대, 베이지 바탕. */
@Composable
internal fun ProgressBar(collected: Int, total: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else collected.toFloat() / total },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** 도감을 다 채웠을 때 붙는 테라코타 꼬리표. */
@Composable
internal fun CompleteBadge() {
    InfoPill(
        stringResource(R.string.collection_complete_badge),
        modifier = Modifier.padding(end = 8.dp),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
