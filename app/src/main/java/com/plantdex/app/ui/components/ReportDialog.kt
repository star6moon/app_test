package com.plantdex.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.plantdex.app.data.repository.ModerationRepository

/** 신고 사유를 고르는 창. 확인하면 "사유 (추가 설명)" 문자열을 돌려줍니다. */
@Composable
fun ReportDialog(title: String, onDismiss: () -> Unit, onReport: (reason: String) -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ModerationRepository.REPORT_REASONS.forEach { reason ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == reason, role = Role.RadioButton) { selected = reason },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == reason, onClick = null)
                        Text(reason)
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(200) },
                    label = { Text("추가 설명 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Text("신고가 여러 건 쌓이면 자동으로 숨겨져요.")
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = {
                val reason = selected ?: return@TextButton
                onReport(if (detail.isBlank()) reason else "$reason (${detail.trim()})")
            }) { Text("신고") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
