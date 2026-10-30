package com.plantdex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.plantdex.app.ui.theme.PlantDexTheme

/** 화면 테스트에서 앱 테마를 씌워 그립니다. */
fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) {
    setContent { PlantDexTheme(darkTheme = false, content = content) }
}
