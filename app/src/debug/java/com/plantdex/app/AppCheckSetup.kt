package com.plantdex.app

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * 개발용(debug) 빌드: 디버그 토큰으로 App Check 를 통과합니다.
 * 처음 실행하면 Logcat 에 "Enter this debug secret into the allow list" 와 함께 토큰이 찍히며,
 * Firebase 콘솔 → App Check → 앱 → 디버그 토큰 관리에 등록하면 됩니다.
 */
object AppCheckSetup {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
