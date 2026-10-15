package com.plantdex.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.R
import com.plantdex.app.data.repository.AuthRepository
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.SectionHeader
import com.plantdex.app.ui.components.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 설정: 닉네임 변경, 비밀번호 재설정, 차단 관리, 로그아웃, 회원 탈퇴 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBlockedUsersClick: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val auth = container.authRepository
    val user by auth.currentUser.collectAsStateWithLifecycle()
    val blocked by container.moderationRepository.blockedUsers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun run(success: String?, action: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                action()
                success?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: context.getString(R.string.settings_action_failed), Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_section_account)) {
                SettingRow(Icons.Outlined.Edit, stringResource(R.string.settings_change_nickname), user?.displayName) { editingName = true }
                if (auth.hasPasswordLogin) {
                    GroupDivider()
                    SettingRow(Icons.Outlined.Key, stringResource(R.string.settings_password_reset), auth.email) {
                        val email = auth.email ?: return@SettingRow
                        run(context.getString(R.string.settings_password_reset_sent, email)) { auth.sendPasswordReset(email) }
                    }
                }
                GroupDivider()
                SettingRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.settings_sign_out), null, danger = true) { auth.signOut() }
            }

            SettingsGroup(stringResource(R.string.settings_section_community)) {
                SettingRow(Icons.Outlined.Block, stringResource(R.string.settings_blocked_users), stringResource(R.string.settings_blocked_count, blocked.size)) { onBlockedUsersClick() }
            }

            SettingsGroup(stringResource(R.string.settings_section_danger)) {
                SettingRow(
                    Icons.Outlined.DeleteForever,
                    stringResource(R.string.settings_delete_account),
                    stringResource(R.string.settings_delete_account_summary),
                    danger = true,
                ) { confirmDelete = true }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(user?.displayName.orEmpty()) }
        val nameChangedMessage = stringResource(R.string.settings_nickname_changed)
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.settings_change_nickname)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(AuthRepository.MAX_NAME_LENGTH) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.settings_nickname_supporting, name.length, AuthRepository.MAX_NAME_LENGTH)) },
                )
            },
            confirmButton = {
                TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                    editingName = false
                    run(nameChangedMessage) { auth.updateDisplayName(name) }
                }) { Text(stringResource(R.string.settings_save)) }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        val confirmWord = stringResource(R.string.settings_delete_confirm_word)
        val deleteDoneMessage = stringResource(R.string.settings_delete_done)
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_delete_message))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_delete_confirm_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && typed.trim() == confirmWord, onClick = {
                    run(deleteDoneMessage) { auth.deleteAccount() }
                }) { Text(stringResource(if (busy) R.string.settings_deleting else R.string.settings_delete_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/** 구역 제목 + 그 아래 종이 카드 한 장에 묶인 설정 줄들. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        PaperCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** 설정 한 줄: 둥근 아이콘 + 제목 + (선택) 설명. [danger] 이면 오류 색으로 표시합니다. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconBackground = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val iconTint = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
