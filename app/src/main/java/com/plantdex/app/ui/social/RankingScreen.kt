package com.plantdex.app.ui.social

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.R
import com.plantdex.app.data.repository.PublicProfile
import com.plantdex.app.ui.components.EmojiBadge
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.UserAvatar
import com.plantdex.app.ui.components.appContainer
import kotlinx.coroutines.CancellationException

/** 랭킹 기준 */
private enum class RankBy(@StringRes val labelRes: Int, val value: (PublicProfile) -> Int, @StringRes val unitRes: Int) {
    Species(R.string.social_rank_by_species, { it.species }, R.string.social_unit_species),
    Catalog(R.string.social_rank_by_catalog, { it.catalogSpecies }, R.string.social_unit_species),
    Records(R.string.social_rank_by_records, { it.records }, R.string.social_unit_count),
}

/** 나와 내가 팔로우하는 사람들의 순위 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(uid: String, onUserClick: (uid: String, name: String) -> Unit, onBack: () -> Unit) {
    val social = appContainer().socialRepository
    val followingIds by social.followingIds.collectAsStateWithLifecycle()
    var profiles by remember { mutableStateOf<List<PublicProfile>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var rankBy by remember { mutableStateOf(RankBy.Species) }

    LaunchedEffect(followingIds) {
        val ids = followingIds ?: return@LaunchedEffect
        try {
            profiles = social.fetchProfiles(ids + uid)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.social_ranking_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.social_back)) }
                },
            )
        },
    ) { padding ->
        val list = profiles
        when {
            error != null && list == null -> MessageBox(stringResource(R.string.social_load_failed), Modifier.padding(padding), body = error, emoji = "⚠️")
            list == null -> LoadingBox(Modifier.padding(padding))
            else -> {
                val sorted = list.sortedWith(compareByDescending<PublicProfile> { rankBy.value(it) }.thenByDescending { it.records })
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SingleChoiceSegmentedButtonRow(Modifier.padding(vertical = 8.dp)) {
                            RankBy.entries.forEachIndexed { i, option ->
                                SegmentedButton(
                                    selected = rankBy == option,
                                    onClick = { rankBy = option },
                                    shape = SegmentedButtonDefaults.itemShape(i, RankBy.entries.size),
                                    icon = {},
                                ) { Text(stringResource(option.labelRes)) }
                            }
                        }
                    }
                    if (list.size <= 1) {
                        item {
                            // 아직 비교할 친구가 없을 때의 안내
                            PaperCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    EmojiBadge("🏆", size = 44.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.social_ranking_empty_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    itemsIndexed(sorted, key = { _, p -> p.uid }) { index, p ->
                        val isMe = p.uid == uid
                        val isTop3 = index < 3
                        PaperCard(Modifier.fillMaxWidth(), onClick = { if (!isMe) onUserClick(p.uid, p.displayName) }) {
                            ListItem(
                                leadingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RankMark(index)
                                        Spacer(Modifier.width(12.dp))
                                        UserAvatar(p.displayName, p.photoUrl)
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        if (isMe) stringResource(R.string.social_ranking_me, p.displayName) else p.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.social_ranking_summary, p.records, p.species, p.catalogSpecies),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        stringResource(R.string.social_ranking_value, rankBy.value(p), stringResource(rankBy.unitRes)),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isMe) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 순위 표시: 1~3위는 연초록 원 안의 메달, 그 아래는 베이지 원 안의 숫자. */
@Composable
private fun RankMark(index: Int) {
    val medal = when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> null }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (medal != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            medal ?: "${index + 1}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
