package com.plantdex.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.plantdex.app.data.achievements.Badge
import com.plantdex.app.ui.achievements.BadgeEarnedDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BadgeEarnedDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsBadgeAndDismisses() {
        var dismissed = false
        compose.mainClock.autoAdvance = false // 무한 애니메이션이 있어 시계를 직접 넘깁니다
        compose.setThemedContent {
            BadgeEarnedDialog(Badge("streak_7", "🔥", "일주일 연속", "7일 연속으로 기록", 7, 7), onDismiss = { dismissed = true })
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("새 배지를 얻었어요!").assertIsDisplayed()
        compose.onNodeWithText("일주일 연속").assertIsDisplayed()
        compose.onNodeWithText("7일 연속으로 기록").assertIsDisplayed()
        compose.onNodeWithText("멋져요").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(dismissed)
    }
}
