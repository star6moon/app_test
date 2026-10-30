package com.plantdex.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.plantdex.app.data.filter.PeriodFilter
import com.plantdex.app.ui.components.PeriodFilterBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PeriodFilterBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsPresetsAndPicksFromTheOtherMenu() {
        var picked: PeriodFilter? = null
        compose.setThemedContent {
            PeriodFilterBar(selected = PeriodFilter.LastMonth, onSelect = { picked = it })
        }
        compose.onNodeWithText("오늘").assertIsDisplayed()
        compose.onNodeWithText("최근 1개월").assertIsDisplayed()
        compose.onNodeWithText("최근 3개월").assertIsDisplayed()

        compose.onNodeWithText("기타").performClick()
        compose.onNodeWithText("직접 선택").assertIsDisplayed()
        compose.onNodeWithText("1년 전 (1개월)").performClick()
        assertEquals(PeriodFilter.YearAgoMonth, picked)
    }

    @Test
    fun selectedExtraShowsItsLabelOnTheChip() {
        compose.setThemedContent {
            PeriodFilterBar(selected = PeriodFilter.YearAgoMonth, onSelect = {})
        }
        compose.onNodeWithText("1년 전 (1개월)").assertIsDisplayed()
    }
}
