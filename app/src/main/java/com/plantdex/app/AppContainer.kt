package com.plantdex.app

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.plantdex.app.data.network.NetworkMonitor
import com.google.firebase.storage.storage
import com.plantdex.app.data.catalog.CatalogRepository
import com.plantdex.app.data.location.LocationProvider
import com.plantdex.app.data.names.GbifWikidataNameLocalizer
import com.plantdex.app.data.names.PlantNameBook
import com.plantdex.app.data.names.PlantNameLocalizer
import com.plantdex.app.data.names.PlantNames
import com.plantdex.app.data.plantnet.LocalizingPlantIdentifier
import com.plantdex.app.data.plantnet.PlantIdentifier
import com.plantdex.app.data.plantnet.CloudPlantIdentifier
import com.plantdex.app.data.repository.AuthRepository
import com.plantdex.app.data.info.GbifFactsClient
import com.plantdex.app.data.info.WikipediaClient
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.data.repository.DraftRepository
import com.plantdex.app.data.repository.ModerationRepository
import com.plantdex.app.data.repository.ReactionRepository
import com.plantdex.app.data.repository.SocialRepository
import com.plantdex.app.data.settings.ThemeSettings
import com.plantdex.app.data.taxonomy.GbifTaxonomyClient
import com.plantdex.app.data.taxonomy.TaxonomyRepository
import com.plantdex.app.push.DeepLink
import com.plantdex.app.util.ShareCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 간단한 수동 DI 컨테이너. 앱 규모가 커지면 Hilt 로 옮기면 됩니다. */
class AppContainer(context: Context) {

    /** 앱 전체 Context (화면 밖에서 기기 저장소를 쓸 때) */
    val appContext: Context = context.applicationContext

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 서버 함수 (Firestore 와 같은 서울 지역) */
    val functions: FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION)

    /**
     * Firestore 는 기본으로 기기 캐시를 쓰지만, 크기를 넉넉히(100MB) 잡아 두어 산이나 시골처럼 신호가 약한 곳에서도
     * 내 도감·둘러보기가 마지막으로 본 상태로 열리게 합니다. 좋아요·댓글 같은 쓰기는 연결되면 자동으로 보내집니다.
     */
    private val firestore: FirebaseFirestore = Firebase.firestore.apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(FIRESTORE_CACHE_BYTES) })
        }
    }

    /** 인터넷 연결 여부 (오프라인 띠) */
    val networkMonitor = NetworkMonitor(appContext)

    val authRepository = AuthRepository(Firebase.auth, firestore, functions)

    val collectionRepository = CollectionRepository(firestore, Firebase.storage, functions)

    /** 앱이 살아 있는 동안 유지되는 작업 범위 (공유 상태 구독용) */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 좋아요·책갈피 */
    val reactionRepository = ReactionRepository(firestore, authRepository, appScope)

    /** 댓글·팔로우·프로필·알림 */
    val socialRepository = SocialRepository(firestore, Firebase.storage, authRepository, appScope)

    /** 신고·차단 */
    val moderationRepository = ModerationRepository(firestore, authRepository, appScope)

    /** 학명 → 사용자 언어 이름 (GBIF, Wikidata). */
    val plantNameLocalizer: PlantNameLocalizer = GbifWikidataNameLocalizer(httpClient)

    /** 서버(Pl@ntNet)로 식별하고, 대표 이름을 기기 언어로 맞춥니다. */
    val plantIdentifier: PlantIdentifier = LocalizingPlantIdentifier(
        delegate = CloudPlantIdentifier(
            functions = functions,
            language = { Locale.getDefault().toLanguageTag().substringBefore('-') },
        ),
        localizer = plantNameLocalizer,
        locale = { Locale.getDefault() },
    )

    val locationProvider = LocationProvider(context.applicationContext)

    /** 앱에 내장된 주제별 도감 목록 */
    val catalogRepository = CatalogRepository(context.applicationContext)

    /** 알림을 눌러 열었을 때 이동할 곳 (화면이 처리하면 null 로 비움) */
    val pendingDeepLink = MutableStateFlow<DeepLink?>(null)

    /** 식물 정보 페이지의 위키백과 설명 */
    val wikipediaClient = WikipediaClient(httpClient)

    /** 기록 공유용 도감 카드 이미지 */
    val shareCard = ShareCard(appContext, httpClient)

    /** 식물 정보 페이지의 '알고 있나요?' (GBIF 관찰 수·달·다른 언어 이름) */
    val gbifFactsClient = GbifFactsClient(httpClient)

    /** 식물 분류 체계 (계·문·강·목·과·속, GBIF) */
    val taxonomyRepository = TaxonomyRepository(GbifTaxonomyClient(httpClient), plantNameLocalizer)

    /** 직접 누른 임시저장 (기기에만 보관) */
    val draftRepository = DraftRepository(context.applicationContext.filesDir)

    /** 식물 이름 목록(한국어·영어)과 이름 고르기 */
    val plantNameBook = PlantNameBook(context.applicationContext)
    val plantNames = PlantNames(plantNameBook) { runCatching { catalogRepository.catalog }.getOrNull() }

    init {
        appScope.launch(Dispatchers.IO) { plantNameBook.preload() }
    }

    /** 화면 모드 (휴대폰 설정 따르기 / 밝게 / 어둡게) */
    val themeSettings = ThemeSettings(context.applicationContext)

    companion object {
        const val FUNCTIONS_REGION = "asia-northeast3"
        const val FIRESTORE_CACHE_BYTES = 100L * 1024 * 1024
    }
}
