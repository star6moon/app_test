package com.plantdex.app

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.plantdex.app.data.location.LocationProvider
import com.plantdex.app.data.names.GbifWikidataNameLocalizer
import com.plantdex.app.data.names.PlantNameLocalizer
import com.plantdex.app.data.plantnet.LocalizingPlantIdentifier
import com.plantdex.app.data.plantnet.PlantIdentifier
import com.plantdex.app.data.plantnet.PlantNetIdentifier
import com.plantdex.app.data.repository.AuthRepository
import com.plantdex.app.data.repository.CollectionRepository
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 간단한 수동 DI 컨테이너. 앱 규모가 커지면 Hilt 로 옮기면 됩니다. */
class AppContainer(context: Context) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val authRepository = AuthRepository(Firebase.auth, Firebase.firestore)

    val collectionRepository = CollectionRepository(Firebase.firestore, Firebase.storage)

    /** 학명 → 사용자 언어 이름 (GBIF, Wikidata). */
    val plantNameLocalizer: PlantNameLocalizer = GbifWikidataNameLocalizer(httpClient)

    /** Pl@ntNet 으로 식별하고, 대표 이름을 기기 언어로 맞춥니다. */
    val plantIdentifier: PlantIdentifier = LocalizingPlantIdentifier(
        delegate = PlantNetIdentifier(
            apiKey = BuildConfig.PLANTNET_API_KEY,
            client = httpClient,
            language = { Locale.getDefault().toLanguageTag().substringBefore('-') },
        ),
        localizer = plantNameLocalizer,
        locale = { Locale.getDefault() },
    )

    val locationProvider = LocationProvider(context.applicationContext)
}
