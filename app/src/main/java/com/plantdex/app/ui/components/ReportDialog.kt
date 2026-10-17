package com.plantdex.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.plantdex.app.R
import com.plantdex.app.data.repository.ReportReason
import com.plantdex.app.data.repository.ReportTarget

/** 신고 사유를 고르는 창. [target] 에 맞는 사유만 보여주고, 고른 사유와 추가 설명을 돌려줍니다. */
@Composable
fun ReportDialog(
    title: String,
    target: ReportTarget,
    onDismiss: () -> Unit,
    onReport: (reason: ReportReason, detail: String) -> Unit,
) {
    val reasons = remember(target) { ReportReason.forTarget(target) }
    var selected by remember { mutableStateOf<ReportReason?>(null) }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                reasons.forEach { reason ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == reason, role = Role.RadioButton) { selected = reason },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == reason, onClick = null)
                        Text(stringResource(reason.labelRes))
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(200) },
                    label = { Text(stringResource(R.string.common_report_detail_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Text(
                    stringResource(policyNotice(selected)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = {
                val reason = selected ?: return@TextButton
                onReport(reason, detail.trim())
            }) { Text(stringResource(R.string.common_report_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_report_cancel)) } },
    )
}

/** 고른 사유가 어떻게 처리되는지 한 줄 안내 */
private fun policyNotice(reason: ReportReason?): Int = when (reason) {
    ReportReason.WrongInfo -> R.string.report_policy_wrong_info
    ReportReason.InappropriatePhoto, ReportReason.Abuse, ReportReason.Spam -> R.string.report_policy_violation
    else -> R.string.common_report_auto_hide_notice
}
