package com.plantdex.app

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** 배포용(release) 빌드: Play Integrity 로 "진짜 우리 앱"에서 온 요청임을 증명합니다. */
object AppCheckSetup {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
