import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// 저장소에 커밋하지 않는 값들은 local.properties 에서 읽습니다.
// (Pl@ntNet API 키는 앱에 넣지 않고 서버 함수의 비밀값으로 둡니다. README 참고)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localOrEnv(name: String): String? = localProperties.getProperty(name) ?: System.getenv(name)
// Google Maps SDK for Android 키도 local.properties 에서 읽습니다.
//   MAPS_API_KEY=xxxxxxxx
val mapsApiKey: String = localOrEnv("MAPS_API_KEY") ?: ""

// 배포용 서명 키 (없으면 서명되지 않은 release 빌드를 만듭니다)
//   RELEASE_STORE_FILE=/home/me/plantdex-release.jks
//   RELEASE_STORE_PASSWORD=...  RELEASE_KEY_ALIAS=plantdex  RELEASE_KEY_PASSWORD=...
val releaseStoreFile: String? = localOrEnv("RELEASE_STORE_FILE")

// 버전 번호 = 지금까지의 커밋 수. 커밋할 때마다 1씩 올라가서 폰에 덮어 설치할 수 있고,
// 내 PC 와 자동 배포가 같은 번호를 씁니다. (git 이 없으면 1)
val buildNumber: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "com.plantdex.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.plantdex.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"

        buildConfigField("Boolean", "HAS_MAPS_API_KEY", (mapsApiKey.isNotBlank()).toString())
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
    }

    signingConfigs {
        // 개발용 공용 서명 키 (저장소에 포함). PC 와 자동 빌드(CI)가 같은 키로 서명해야
        // Firebase 에 등록한 지문과 맞아 Google 로그인이 됩니다. 배포(release)에는 절대 쓰지 마세요.
        create("dev") {
            storeFile = file("keystore/plantdex-dev.jks")
            storePassword = "plantdex-dev"
            keyAlias = "plantdex-dev"
            keyPassword = "plantdex-dev"
        }
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localOrEnv("RELEASE_STORE_PASSWORD")
                keyAlias = localOrEnv("RELEASE_KEY_ALIAS")
                keyPassword = localOrEnv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            // 코드 축소·난독화(R8)와 안 쓰는 리소스 제거로 앱 크기를 줄입니다.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.play.services.location)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.coil.network.okhttp)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
