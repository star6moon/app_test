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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.R
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
    val noNameLabel = stringResource(R.string.settings_blocked_no_name)
    val unblockFailedMessage = stringResource(R.string.settings_blocked_unblock_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_blocked_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_blocked_back)) }
                },
            )
        },
    ) { padding ->
        if (blocked.isEmpty()) {
            MessageBox(stringResource(R.string.settings_blocked_empty_title), Modifier.padding(padding), body = stringResource(R.string.settings_blocked_empty_body))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(blocked, key = { it.uid }) { user ->
                    ListItem(
                        headlineContent = { Text(user.displayName.ifBlank { noNameLabel }) },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { moderation.unblock(user.uid) }
                                        .onFailure { Toast.makeText(context, unblockFailedMessage, Toast.LENGTH_SHORT).show() }
                                }
                            }) { Text(stringResource(R.string.settings_blocked_unblock)) }
                        },
                    )
                }
            }
        }
    }
}
