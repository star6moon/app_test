package com.plantdex.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.push.DeepLink
import com.plantdex.app.ui.auth.AuthScreen
import com.plantdex.app.ui.navigation.PlantDexNavHost
import com.plantdex.app.ui.theme.PlantDexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PlantDexApplication).container
        val authRepository = container.authRepository
        if (savedInstanceState == null) DeepLink.from(intent)?.let { container.pendingDeepLink.value = it }
        setContent {
            PlantDexTheme {
                val user by authRepository.currentUser.collectAsStateWithLifecycle()
                val current = user
                if (current == null) {
                    AuthScreen()
                } else {
                    // 계정이 바뀌면 화면 상태(ViewModel 포함)를 새로 만듭니다.
                    key(current.uid) { PlantDexNavHost(current) }
                }
            }
        }
    }

    /** 앱이 열려 있을 때 알림을 누른 경우 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.from(intent)?.let { (application as PlantDexApplication).container.pendingDeepLink.value = it }
    }
}
