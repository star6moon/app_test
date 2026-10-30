package com.plantdex.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 알약 모양 선택 버튼 (내 도감 | 모두의 기록, 전체 | 팔로잉, 기록 수 | 종 수 …).
 * 기본 SegmentedButton 은 크기를 고정해 재서 글자가 잘리던 탓에, 글자 크기(휴대폰 글꼴 크기 포함)에 맞춰 늘어나게 직접 그립니다.
 */
@Composable
fun <T> PillToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.selectableGroup(),
        shape = CircleShape,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) colors.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 32.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
