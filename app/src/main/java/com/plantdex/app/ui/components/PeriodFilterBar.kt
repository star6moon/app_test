package com.plantdex.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plantdex.app.R
import com.plantdex.app.data.filter.PeriodFilter

/** 촬영 시기 필터 칩: 전체 / 이번 주 / 이번 달 / 올해 / 직접 선택 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodFilterBar(
    selected: PeriodFilter,
    onSelect: (PeriodFilter) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PeriodFilter.presets.forEach { preset ->
            FilterChip(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                label = { Text(preset.label) },
                shape = CircleShape,
                colors = softFilterChipColors(),
                border = softFilterChipBorder(selected == preset),
            )
        }
        FilterChip(
            selected = selected is PeriodFilter.Custom,
            onClick = { picking = true },
            label = { Text(if (selected is PeriodFilter.Custom) selected.label else stringResource(R.string.common_period_custom)) },
            leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            shape = CircleShape,
            colors = softFilterChipColors(),
            border = softFilterChipBorder(selected is PeriodFilter.Custom),
        )
    }

    if (picking) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        if (start != null) {
                            val end = pickerState.selectedEndDateMillis ?: start
                            onSelect(PeriodFilter.Custom(PeriodFilter.utcMillisToDate(start), PeriodFilter.utcMillisToDate(end)))
                        }
                        picking = false
                    },
                ) { Text(stringResource(R.string.common_period_apply)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_period_cancel)) } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text(stringResource(R.string.common_period_picker_title), Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 부드러운 필터 칩 색: 고르면 연초록(primaryContainer), 아니면 밝은 종이색. */
@Composable
fun softFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/** 부드러운 필터 칩 테두리: 고르지 않은 칩만 옅은 선을 둘러요. */
@Composable
fun softFilterChipBorder(selected: Boolean): BorderStroke = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
)
