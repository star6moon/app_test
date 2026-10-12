package com.plantdex.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.ui.components.MessageBox
import com.plantdex.app.ui.components.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val moderation = appContainer().moderationRepository
    val blocked by moderation.blockedUsers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("차단한 사용자") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { padding ->
        if (blocked.isEmpty()) {
            MessageBox("차단한 사용자가 없어요", Modifier.padding(padding), body = "차단하면 그 사용자의 기록·댓글이 보이지 않고 알림도 오지 않아요.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(blocked, key = { it.uid }) { user ->
                    ListItem(
                        headlineContent = { Text(user.displayName.ifBlank { "(이름 없음)" }) },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { moderation.unblock(user.uid) }
                                        .onFailure { Toast.makeText(context, "차단을 해제하지 못했어요.", Toast.LENGTH_SHORT).show() }
                                }
                            }) { Text("차단 해제") }
                        },
                    )
                }
            }
        }
    }
}
