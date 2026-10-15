package com.plantdex.app.ui.profile

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plantdex.app.R
import com.plantdex.app.data.repository.PublicProfile
import com.plantdex.app.data.repository.SocialRepository
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.UserAvatar
import com.plantdex.app.ui.components.appContainer
import com.plantdex.app.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 프로필 머리말: 사진, 이름, 소개, 기록·종·팔로워·팔로잉 수, 팔로우(또는 편집) 버튼.
 * 숫자는 서버가 계산해 저장한 값입니다.
 */
@Composable
fun ProfileHeader(
    profile: PublicProfile,
    isMe: Boolean,
    isFollowing: Boolean?,
    onFollowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    PaperCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(profile.displayName, profile.photoUrl, size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                    if (profile.bio.isNotBlank()) {
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(stringResource(R.string.profile_stat_records), profile.records, Modifier.weight(1f))
                Stat(stringResource(R.string.profile_stat_species), profile.species, Modifier.weight(1f))
                Stat(stringResource(R.string.profile_stat_followers), profile.followerCount, Modifier.weight(1f))
                Stat(stringResource(R.string.profile_stat_following), profile.followingCount, Modifier.weight(1f))
            }
            when {
                isMe -> OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_edit)) }
                isFollowing == true -> OutlinedButton(onClick = { onFollowChange(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_following_checked))
                }
                isFollowing == false -> Button(onClick = { onFollowChange(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_follow))
                }
                else -> Unit
            }
        }
    }
    if (editing) ProfileEditDialog(profile, onDismiss = { editing = false })
}

/** 숫자 칸: 카드 안에서 구분되도록 한 단계 진한 베이지 바탕 + 초록 숫자. */
@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 소개글과 프로필 사진 바꾸기 (닉네임은 설정 화면에서) */
@Composable
private fun ProfileEditDialog(profile: PublicProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val social = appContainer().socialRepository
    val scope = rememberCoroutineScope()
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UserAvatar(
                    profile.displayName,
                    avatarUri?.toString() ?: profile.photoUrl,
                    Modifier.clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    size = 80.dp,
                )
                Text(
                    stringResource(R.string.profile_tap_photo_to_change),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(SocialRepository.MAX_BIO_LENGTH) },
                    label = { Text(stringResource(R.string.profile_bio_label)) },
                    supportingText = { Text("${bio.length}/${SocialRepository.MAX_BIO_LENGTH}") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        val file = avatarUri?.let { prepareAvatar(context, it) }
                        social.updateProfile(bio, file)
                        file?.delete()
                        onDismiss()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.profile_save_failed, e.message), Toast.LENGTH_SHORT).show()
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) stringResource(R.string.profile_saving) else stringResource(R.string.profile_save)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.profile_cancel)) } },
    )
}

/** 고른 사진을 512px JPEG 로 줄입니다 (저장소 규칙: 2MB 이하). */
private suspend fun prepareAvatar(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val raw = File(context.cacheDir, "avatar_raw_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } }
        ?: error(context.getString(R.string.profile_photo_open_failed))
    try {
        ImageUtils.prepareForUpload(raw, File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg"), maxSize = 512)
    } finally {
        raw.delete()
    }
}
