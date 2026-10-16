package com.plantdex.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantdex.app.data.settings.ThemeMode
import com.plantdex.app.push.DeepLink
import com.plantdex.app.ui.auth.AuthScreen
import com.plantdex.app.ui.navigation.PlantDexNavHost
import com.plantdex.app.ui.theme.PlantDexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(dark = false)
        val container = (application as PlantDexApplication).container
        val authRepository = container.authRepository
        if (savedInstanceState == null) DeepLink.from(intent)?.let { container.pendingDeepLink.value = it }
        setContent {
            // 설정의 화면 모드 (휴대폰 설정 따르기 / 밝게 / 어둡게)
            val themeMode by container.themeSettings.mode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) { applySystemBars(dark) }
            PlantDexTheme(darkTheme = dark) {
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

    /** 상태 표시줄·내비게이션 바 아이콘 색을 앱 화면 밝기에 맞춥니다 (휴대폰 다크 모드와 달라도). */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** 앱이 열려 있을 때 알림을 누른 경우 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.from(intent)?.let { (application as PlantDexApplication).container.pendingDeepLink.value = it }
    }
}
