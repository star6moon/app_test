package com.plantdex.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.data.repository.AuthRepository
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
                Toast.makeText(context, e.message ?: "처리하지 못했어요.", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("계정")
            SettingRow(Icons.Outlined.Edit, "닉네임 변경", user?.displayName) { editingName = true }
            if (auth.hasPasswordLogin) {
                SettingRow(Icons.Outlined.Key, "비밀번호 재설정 메일 받기", auth.email) {
                    val email = auth.email ?: return@SettingRow
                    run("$email 로 재설정 메일을 보냈어요.") { auth.sendPasswordReset(email) }
                }
            }
            SettingRow(Icons.AutoMirrored.Outlined.Logout, "로그아웃", null) { auth.signOut() }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("커뮤니티")
            SettingRow(Icons.Outlined.Block, "차단한 사용자", "${blocked.size}명") { onBlockedUsersClick() }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("위험 구역")
            SettingRow(
                Icons.Outlined.DeleteForever,
                "회원 탈퇴",
                "내 기록·사진·댓글·좋아요가 모두 삭제돼요",
                danger = true,
            ) { confirmDelete = true }
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(user?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("닉네임 변경") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(AuthRepository.MAX_NAME_LENGTH) },
                    singleLine = true,
                    supportingText = { Text("${name.length}/${AuthRepository.MAX_NAME_LENGTH} · 내 기록과 댓글의 이름도 바뀌어요") },
                )
            },
            confirmButton = {
                TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                    editingName = false
                    run("닉네임을 바꿨어요.") { auth.updateDisplayName(name) }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("취소") } },
        )
    }

    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("정말 탈퇴할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("내 기록, 사진, 댓글, 좋아요, 팔로우가 모두 삭제되고 되돌릴 수 없어요.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text("계속하려면 '탈퇴'를 입력하세요") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && typed.trim() == "탈퇴", onClick = {
                    run("탈퇴가 완료됐어요. 그동안 고마웠어요.") { auth.deleteAccount() }
                }) { Text(if (busy) "삭제 중…" else "탈퇴", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        colors = ListItemDefaults.colors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
