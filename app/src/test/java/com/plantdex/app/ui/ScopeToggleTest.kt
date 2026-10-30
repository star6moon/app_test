package com.plantdex.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.plantdex.app.ui.map.MapScope
import com.plantdex.app.ui.map.ScopeToggle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScopeToggleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bothLabelsAreShownAndTappable() {
        var picked: MapScope? = null
        compose.setThemedContent {
            ScopeToggle(selected = MapScope.Mine, onSelect = { picked = it })
        }
        compose.onNodeWithText("내 도감").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("모두의 기록").assertIsDisplayed().performClick()
        assertEquals(MapScope.Everyone, picked)
    }
}
