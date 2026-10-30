package com.plantdex.app.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plantdex.app.R
import com.plantdex.app.data.catalog.CatalogSpecies
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.ui.components.ScientificName
import com.plantdex.app.ui.components.appContainer

/**
 * '식물 정보가 일치하지 않음' 신고로 비공개가 된 기록의 식물을 고르는 창.
 * 도감 종에서 찾아 고르거나, 이름과 학명을 직접 적을 수 있습니다.
 */
@Composable
fun CorrectEntryDialog(
    entry: CollectionEntry,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (scientificName: String, commonName: String?) -> Unit,
) {
    val catalog = runCatching { appContainer().catalogRepository.catalog }.getOrNull()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<CatalogSpecies?>(null) }
    var manual by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualScientific by remember { mutableStateOf("") }

    val results = remember(query, catalog) {
        val q = query.trim()
        if (q.isEmpty() || catalog == null) {
            emptyList()
        } else {
            catalog.species.filter { it.name.contains(q) || it.scientificName.contains(q, ignoreCase = true) }.take(30)
        }
    }
    val canSubmit = !saving && if (manual) manualScientific.isNotBlank() else picked != null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.entry_correct_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.entry_correct_current, entry.displayName, entry.scientificName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (manual) {
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it.take(100) },
                        label = { Text(stringResource(R.string.entry_correct_manual_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = manualScientific,
                        onValueChange = { manualScientific = it.take(200) },
                        label = { Text(stringResource(R.string.entry_correct_manual_scientific)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { manual = false }) { Text(stringResource(R.string.entry_correct_search_instead)) }
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it.take(50)
                            picked = null
                        },
                        label = { Text(stringResource(R.string.entry_correct_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    picked?.let {
                        Text(
                            stringResource(R.string.entry_correct_picked, it.name),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (results.isNotEmpty() && picked == null) {
                        LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(results, key = { it.id }) { species ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) { picked = species }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(species.name, style = MaterialTheme.typography.bodyLarge)
                                    ScientificName(species.scientificName)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    } else if (query.isNotBlank() && results.isEmpty()) {
                        Text(
                            stringResource(R.string.entry_correct_no_result),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { manual = true }) { Text(stringResource(R.string.entry_correct_manual)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = {
                if (manual) {
                    onSubmit(manualScientific.trim(), manualName.trim().ifBlank { null })
                } else {
                    picked?.let { onSubmit(it.scientificName, it.name) }
                }
            }) { Text(stringResource(if (saving) R.string.entry_correct_saving else R.string.entry_correct_save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.entry_detail_cancel)) }
        },
    )
}
