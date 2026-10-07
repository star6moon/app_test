package com.plantdex.app.ui.entry

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.components.LoadState
import com.plantdex.app.ui.components.LoadingBox
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.PlantBadge
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.ui.components.localizedName
import com.plantdex.app.ui.components.rememberPlantArt
import com.plantdex.app.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onUserClick: (userId: String, userName: String) -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val viewModel: EntryDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EntryDetailViewModel(entryId, container.collectionRepository, container.authRepository)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val entry = (state as? LoadState.Success)?.data
    val name = localizedName(entry)
    val isOwner = entry != null && viewModel.isOwner(entry)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = { shareEntry(context, entry, name) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "공유")
                        }
                        if (isOwner) {
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "삭제")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            LoadState.Loading -> LoadingBox(modifier)
            is LoadState.Error -> MessageBox("불러오지 못했어요", modifier, body = s.message)
            is LoadState.Success -> {
                val data = s.data
                if (data == null) {
                    MessageBox("삭제되었거나 존재하지 않는 기록이에요", modifier)
                } else {
                    EntryDetailContent(
                        entry = data,
                        name = name,
                        isOwner = isOwner,
                        onPublicChange = { viewModel.setPublic(data, it) },
                        onOpenMap = { openMap(context, data, name) },
                        onUserClick = { onUserClick(data.ownerId, data.ownerName) },
                        modifier = modifier,
                    )
                }
            }
        }
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("기록을 삭제할까요?") },
            text = { Text("사진과 기록이 모두 삭제되며 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(entry)
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun EntryDetailContent(
    entry: CollectionEntry,
    name: String,
    isOwner: Boolean,
    onPublicChange: (Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        AsyncImage(
            model = entry.photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlantBadge(rememberPlantArt(entry.scientificName, entry.family), size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.headlineSmall)
                    Text(entry.scientificName, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                    val taxonomy = listOfNotNull(entry.family?.let { "과: $it" }, entry.genus?.let { "속: $it" })
                    if (taxonomy.isNotEmpty()) {
                        Text(
                            taxonomy.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            InfoRow(Icons.Outlined.Schedule, "촬영 일시", Formatters.dateTime(entry.capturedAt))
            val location = entry.location
            if (location != null) {
                InfoRow(
                    Icons.Outlined.Place,
                    "촬영 위치",
                    listOfNotNull(
                        location.placeName,
                        Formatters.coordinates(location.latitude, location.longitude),
                    ).joinToString("\n"),
                    onClick = onOpenMap,
                )
            } else {
                InfoRow(Icons.Outlined.Place, "촬영 위치", "위치 정보 없음")
            }
            InfoRow(Icons.Outlined.Verified, "AI 식별 신뢰도", "${Formatters.percent(entry.score)} (Pl@ntNet)")
            InfoRow(Icons.Outlined.Person, "기록한 사람", entry.ownerName, onClick = onUserClick)
            if (entry.memo.isNotBlank()) {
                HorizontalDivider()
                Text(entry.memo, style = MaterialTheme.typography.bodyLarge)
            }
            if (isOwner) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("다른 사용자에게 공개", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = entry.isPublic, onCheckedChange = onPublicChange)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun shareEntry(context: Context, entry: CollectionEntry, name: String) {
    val text = buildString {
        appendLine("🌿 $name (${entry.scientificName})")
        append(Formatters.dateTime(entry.capturedAt))
        entry.location?.placeName?.let { append(" · $it") }
        appendLine()
        if (entry.memo.isNotBlank()) appendLine(entry.memo)
        appendLine(entry.photoUrl)
        append("#PlantDex")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "도감 기록 공유"))
}

private fun openMap(context: Context, entry: CollectionEntry, name: String) {
    val location = entry.location ?: return
    val coords = "${location.latitude},${location.longitude}"
    val uri = Uri.parse("geo:$coords?q=$coords(${Uri.encode(name)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // 지도 앱이 없는 기기
    }
}
