package com.plantdex.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plantdex.app.R
import com.plantdex.app.data.names.PlantDisplayName
import com.plantdex.app.data.taxonomy.TaxonNode
import com.plantdex.app.data.taxonomy.TaxonRank

/**
 * 분류 체계 그래프: 식물계에서 이 식물(종)까지 한 단계씩 계단처럼 내려가는 나무 그림.
 * 기록에 저장된 과·속을 먼저 그리고, GBIF 에서 계·문·강·목을 받아 오면 채웁니다.
 */
@Composable
fun TaxonomySection(
    scientificName: String,
    species: PlantDisplayName,
    modifier: Modifier = Modifier,
    gbifId: String? = null,
    family: String? = null,
    genus: String? = null,
) {
    val repository = appContainer().taxonomyRepository
    val state by produceState(TaxonomyState(emptyList(), loading = true), scientificName, gbifId) {
        repository.lineage(scientificName, gbifId, family, genus).collect { value = TaxonomyState(it, loading = true) }
        value = value.copy(loading = false)
    }
    val leaf = TaxonNode(TaxonRank.Species, scientificName, species.korean, isPronunciation = species.isPronunciation)
    val nodes = state.nodes + leaf

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.taxonomy_title)) {
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        PaperCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    stringResource(R.string.taxonomy_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                nodes.forEachIndexed { index, node ->
                    TaxonRow(node, depth = index, count = nodes.size)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.taxonomy_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class TaxonomyState(val nodes: List<TaxonNode>, val loading: Boolean)

private val STEP = 14.dp // 한 단계 내려갈 때마다 들여쓰는 너비
private val GUTTER = 22.dp // 점이 들어가는 칸
private val DOT_Y = 15.dp // 점의 세로 위치 (첫 줄 가운데)

@Composable
private fun TaxonRow(node: TaxonNode, depth: Int, count: Int) {
    val isLeaf = depth == count - 1
    val colors = MaterialTheme.colorScheme
    val dotColor = lerp(colors.primaryContainer, colors.primary, if (count > 1) depth / (count - 1f) else 1f)
    val lineColor = colors.outlineVariant
    val dotRadius = if (isLeaf) 7.dp else 5.dp

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .drawBehind {
                val stroke = 2.dp.toPx()
                val x = (STEP * depth).toPx() + GUTTER.toPx() / 2
                val y = DOT_Y.toPx()
                val r = dotRadius.toPx()
                if (depth > 0) {
                    // 윗 단계에서 꺾여 내려오는 선
                    val parentX = x - STEP.toPx()
                    drawLine(lineColor, Offset(parentX, 0f), Offset(parentX, y), stroke, StrokeCap.Round)
                    drawLine(lineColor, Offset(parentX, y), Offset(x - r, y), stroke, StrokeCap.Round)
                }
                if (!isLeaf) drawLine(lineColor, Offset(x, y + r), Offset(x, size.height), stroke, StrokeCap.Round)
                drawCircle(dotColor, r, Offset(x, y))
                if (isLeaf) drawCircle(colors.primary, r + 3.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
            }
            .padding(start = STEP * depth + GUTTER + 6.dp, bottom = if (isLeaf) 0.dp else 10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankChip(node.rank, highlighted = isLeaf)
                Spacer(Modifier.width(8.dp))
                val title = node.korean ?: node.scientificName
                Text(
                    title,
                    style = if (isLeaf) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = if (isLeaf) FontWeight.SemiBold else null,
                    color = when {
                        isLeaf -> colors.primary
                        node.isPronunciation -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (node.korean != null) ScientificName(node.scientificName, style = MaterialTheme.typography.labelMedium)
            node.hint?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RankChip(rank: TaxonRank, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (highlighted) colors.primary else colors.secondaryContainer,
        contentColor = if (highlighted) colors.onPrimary else colors.onSecondaryContainer,
    ) {
        Text(
            stringResource(rank.label),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private val TaxonRank.label: Int
    get() = when (this) {
        TaxonRank.Kingdom -> R.string.taxonomy_rank_kingdom
        TaxonRank.Phylum -> R.string.taxonomy_rank_phylum
        TaxonRank.Class -> R.string.taxonomy_rank_class
        TaxonRank.Order -> R.string.taxonomy_rank_order
        TaxonRank.Family -> R.string.taxonomy_rank_family
        TaxonRank.Genus -> R.string.taxonomy_rank_genus
        TaxonRank.Species -> R.string.taxonomy_rank_species
    }

