package com.plantdex.app.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.repository.Draft
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.SectionHeader
import com.plantdex.app.util.Formatters
import java.io.File

/** 임시저장 목록. 하나를 고르면 이어서 등록합니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsSheet(
    drafts: List<Draft>,
    photoOf: (Draft) -> File?,
    onOpen: (Draft) -> Unit,
    onDelete: (Draft) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleting by remember { mutableStateOf<Draft?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            SectionHeader(
                stringResource(R.string.capture_drafts_title, drafts.size),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.capture_drafts_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 4.dp),
            )
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(drafts, key = { it.id }) { draft ->
                    PaperCard(Modifier.fillMaxWidth(), onClick = { onOpen(draft) }) {
                        Row(
                            Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = photoOf(draft),
                                contentDescription = stringResource(R.string.capture_draft_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    draft.selected?.displayName ?: stringResource(R.string.capture_draft_not_identified),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    listOfNotNull(
                                        Formatters.dateTime(draft.capturedAt),
                                        draft.location?.placeName,
                                        stringResource(R.string.capture_draft_photo_count, draft.photoFiles.size),
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleting = draft }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.capture_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { draft ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.capture_delete_draft_title)) },
            text = { Text(stringResource(R.string.capture_delete_draft_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(draft)
                }) { Text(stringResource(R.string.capture_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.capture_cancel)) } },
        )
    }
}
