package com.plantdex.app.ui.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plantdex.app.R
import com.plantdex.app.data.repository.Draft
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.capture_drafts_title, drafts.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.capture_drafts_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
                items(drafts, key = { it.id }) { draft ->
                    ListItem(
                        leadingContent = {
                            AsyncImage(
                                model = photoOf(draft),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                            )
                        },
                        headlineContent = { Text(draft.selected?.displayName ?: stringResource(R.string.capture_draft_not_identified)) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    Formatters.dateTime(draft.capturedAt),
                                    draft.location?.placeName,
                                    stringResource(R.string.capture_draft_photo_count, draft.photoFiles.size),
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { deleting = draft }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.capture_delete))
                            }
                        },
                        modifier = Modifier.clickable { onOpen(draft) },
                    )
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
