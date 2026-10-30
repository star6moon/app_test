package com.plantdex.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.plantdex.app.ui.components.PremiumLockedBox
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PremiumLockedBoxTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun namesTheLockedFeature() {
        compose.setThemedContent { PremiumLockedBox("검색") }
        compose.onNodeWithText("검색 은(는) 준비 중인 유료 기능이에요").assertIsDisplayed()
    }
}
