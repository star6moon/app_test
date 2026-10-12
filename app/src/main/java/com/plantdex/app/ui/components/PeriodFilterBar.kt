package com.plantdex.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
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
            )
        }
        FilterChip(
            selected = selected is PeriodFilter.Custom,
            onClick = { picking = true },
            label = { Text(if (selected is PeriodFilter.Custom) selected.label else "직접 선택") },
            leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
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
                ) { Text("적용") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("기간 선택", Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
