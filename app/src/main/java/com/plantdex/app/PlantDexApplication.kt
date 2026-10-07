package com.plantdex.app

import android.app.Application

class PlantDexApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 도감 파일을 미리 읽어 두어 첫 화면에서 메인 스레드가 멈추지 않게 합니다. (lazy 는 스레드 안전)
        Thread { runCatching { container.catalogRepository.catalog } }.start()
    }
}
