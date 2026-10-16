package com.plantdex.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 화면 밝기 모드: 휴대폰 설정(다크 모드)을 따르거나, 항상 밝게 / 항상 어둡게. */
enum class ThemeMode { System, Light, Dark }

/** 화면 모드 선택을 기기에 저장합니다 (계정과 상관없이 이 휴대폰에만). */
class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(read())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun read(): ThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: DEFAULT
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"

        /** 처음에는 자연 도감 디자인의 기본인 밝은 화면 */
        val DEFAULT = ThemeMode.Light
    }
}
