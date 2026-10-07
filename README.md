# PlantDex 🌿

식물을 촬영하면 AI 가 어떤 식물인지 알려주고, 나만의 도감에 모아 다른 사용자와 공유하는 SNS 앱입니다.

## 현재 구현된 기능 (MVP)

| # | 기능 | 구현 |
|---|------|------|
| 1 | 식물 촬영 + AI 식별 | CameraX 로 촬영 → [Pl@ntNet API](https://my.plantnet.org/) 로 후보 최대 5개와 신뢰도 표시. 이름은 기기 언어로 표시 |
| 2 | 촬영 날짜·시간·위치 수집 | 셔터를 누른 시각, Fused Location 좌표·정확도, 역지오코딩 지명 |
| 3 | 도감 기록·수집 | 후보 선택 + 메모 + 공개 여부 → Firebase Storage(사진) / Firestore(기록) 저장, 수집한 종 수 집계 |
| 4 | 다른 사용자와 열람·공유 | 공개 기록 피드, 다른 사용자의 도감 보기, 기록 상세에서 공유 시트로 내보내기 |
| 6 | 주제별 도감 | 도시·바닷가·산·물가·봄·여름·가을·겨울 8개 도감(102종). 도감별 `발견 / 전체` 수와 전체 수집률, 미발견 종 힌트, 촬영 결과의 NEW 표시 |
| 7 | 좋아요·책갈피 | 피드·기록 상세에서 ♥ 좋아요(개수 표시)와 🔖 책갈피. 내 도감 › 책갈피에서 모아 보기 |
| 5 | 식물 지도 | 내 도감 / 모두의 공개 기록을 Google 지도에 식물 그림 + 이름 마커로 표시. 화면에서 80dp 안의 마커는 종류와 상관없이 "능소화 등 4종" 처럼 묶고, 묶음을 누르면 종별 목록과 사진을 보여줌 |

그 밖에: 이메일 회원가입/로그인, 기록 삭제, 공개/비공개 전환, 지도 앱으로 위치 열기.

## 기술 스택

- Kotlin, Jetpack Compose (Material 3), Navigation Compose (type-safe routes)
- CameraX, Google Play services Location, Google Maps Compose (마커 클러스터링)
- Firebase Auth / Cloud Firestore / Cloud Storage
- OkHttp + kotlinx.serialization (Pl@ntNet 연동), Coil 3 (이미지 로딩)
- minSdk 26 / targetSdk 35

## 프로젝트 구조

```
app/src/main/java/com/plantdex/app/
├── PlantDexApplication.kt, AppContainer.kt   # 앱 진입점, 수동 DI
├── MainActivity.kt                           # 로그인 여부에 따라 Auth / 메인 화면 전환
├── data/
│   ├── model/        # PlantCandidate, CollectionEntry, CaptureLocation, ...
│   ├── plantnet/     # PlantIdentifier 인터페이스 + Pl@ntNet 구현 (다른 AI 로 교체 가능)
│   ├── names/        # 학명 → 사용자 언어 이름 (GBIF, Wikidata)
│   ├── catalog/      # 주제별 도감 목록과 기록 매칭
│   ├── location/     # 현재 위치 + 지명 조회
│   └── repository/   # AuthRepository, CollectionRepository (Firebase)
├── ui/
│   ├── auth/         # 로그인·회원가입
│   ├── capture/      # 카메라 → 식별 중 → 결과 선택 → 도감 등록
│   ├── collection/   # 내 도감, 다른 사용자 도감
│   ├── feed/         # 공개 기록 피드
│   ├── entry/        # 기록 상세 (공유, 공개 전환, 삭제)
│   └── navigation/   # 하단 탭 + 내비게이션
└── util/             # 이미지 리사이즈·회전 보정, 포맷터
firebase/                # Firestore·Storage 보안 규칙, Firestore 색인
```

## 시작하기

### 1. Firebase 프로젝트 준비

1. [Firebase 콘솔](https://console.firebase.google.com/)에서 프로젝트를 만들고 Android 앱을 추가합니다. 패키지 이름: `com.plantdex.app`
2. 내려받은 `google-services.json` 을 `app/` 폴더에 넣습니다. (저장소에는 커밋하지 않습니다)
3. 콘솔에서 다음을 켭니다.
   - **Authentication** → 로그인 방법 → 이메일/비밀번호
   - **Firestore Database** 생성
   - **Storage** 생성
4. 보안 규칙과 색인을 배포합니다.
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add          # 방금 만든 프로젝트 선택
   firebase deploy --only firestore,storage
   ```
   색인이 없으면 피드/도감 화면에 "Firestore 색인이 필요합니다" 메시지가 표시됩니다. 색인 생성에는 몇 분이 걸릴 수 있습니다.

### 2. Pl@ntNet API 키

1. [my.plantnet.org](https://my.plantnet.org/) 에 가입하고 API 키를 발급받습니다. (무료 플랜: 하루 500회 식별)
2. 프로젝트 루트의 `local.properties` 에 추가합니다.
   ```properties
   PLANTNET_API_KEY=발급받은_키
   ```

### 3. Google 지도 API 키

1. [Google Cloud 콘솔](https://console.cloud.google.com/)에서 Firebase 와 **같은 프로젝트**를 선택합니다.
2. **API 및 서비스 → 라이브러리**에서 **Maps SDK for Android** 를 검색해 **사용 설정**합니다.
3. **API 및 서비스 → 사용자 인증 정보 → 사용자 인증 정보 만들기 → API 키**로 키를 만듭니다.
4. (권장) 키의 **API 제한사항**을 *Maps SDK for Android* 로 제한합니다.
5. `local.properties` 에 추가합니다.
   ```properties
   MAPS_API_KEY=발급받은_키
   ```

키가 없어도 앱은 빌드되며, 지도 탭에 안내 메시지가 표시됩니다.

### 4. 빌드 및 실행

Android Studio 로 프로젝트를 열고 실행하거나:

```bash
./gradlew installDebug     # 연결된 기기/에뮬레이터에 설치
./gradlew testDebugUnitTest
```

에뮬레이터에서는 카메라 대신 가상 장면이 찍히므로 실제 식물 식별은 실기기에서 테스트하는 것이 좋습니다.

## 데이터 모델

**`users/{uid}`** — `displayName`, `createdAt`

**`users/{uid}/likes/{entryId}`** — 내가 좋아요한 기록 (`createdAt`)

**`users/{uid}/bookmarks/{entryId}`** — 내가 책갈피한 기록 (`createdAt`, 본인만 읽기)

**`entries/{entryId}`**

| 필드 | 타입 | 설명 |
|------|------|------|
| `ownerId`, `ownerName` | string | 기록한 사용자 |
| `scientificName`, `commonName`, `family`, `genus` | string | AI 식별 결과 중 사용자가 고른 식물 |
| `commonNameLanguage` | string? | `commonName` 의 언어 (예: `ko`) |
| `gbifId` | string? | GBIF 분류군 ID (다른 언어 이름을 찾을 때 사용) |
| `score` | number | 식별 신뢰도 (0~1) |
| `photoUrl`, `photoPath` | string | Storage 사진 (`users/{uid}/entries/{entryId}.jpg`) |
| `capturedAt` | timestamp | 촬영 시각 |
| `location` | geopoint? | 촬영 좌표 (위치 권한이 없으면 null) |
| `locationAccuracy` | number? | 위치 정확도(m) |
| `placeName` | string? | 역지오코딩 지명 |
| `memo` | string | 메모 |
| `isPublic` | bool | 다른 사용자에게 공개 여부 |
| `likeCount` | number | 좋아요 수. 좋아요 문서와 같은 요청에서만 ±1 (보안 규칙으로 강제) |
| `createdAt` | timestamp | 등록 시각 (서버 시각) |

보안 규칙(`firebase/firestore.rules`): 공개 기록은 로그인한 모든 사용자가, 비공개 기록은 본인만 읽을 수 있고, 등록 후에는 공개 여부와 메모만 수정할 수 있습니다.

## 주제별 도감

도감 목록은 `app/src/main/assets/catalogs.json` 에 들어 있습니다. 같은 식물이 여러 도감에 들어갈 수 있습니다 (예: 동백나무 → 바닷가·봄·겨울).

- **species**: `id`, `name`(표시 이름), `art`(외형 그림 사양 — 아래 참고), `scientificName`, `synonyms`(AI 가 다른 학명으로 답해도 인정할 이름), `acceptGenus`(같은 속이면 모두 인정 — 원예종용), `hint`(미발견 힌트)
- **catalogs**: `id`, `emoji`, `title`, `description`, `speciesIds`

내 기록의 학명을 "속 + 종소명"으로 정규화해 매칭합니다 (저자명·변종·교잡 기호 무시). 정확히 일치하는 종이 먼저, 없으면 `acceptGenus` 종으로 인정합니다.
도감을 고친 뒤에는 `./gradlew testDebugUnitTest` 로 검증하세요 — 없는 id 참조, 두 종이 같은 학명을 쓰는 경우 등을 잡아냅니다.

## 같은 식물 중복 등록 제한

같은 계정은 **같은 종을 이미 등록한 곳에서 반경 10m 안에서는 다시 등록할 수 없습니다** (`data/rules/NearbyRule.kt` 의 `MIN_DISTANCE_METERS`).

- 같은 종 판단은 지도·도감과 같은 기준입니다 (도감 종 id, 없으면 학명의 속+종소명). AI 가 이명으로 답해도 같은 종으로 봅니다.
- 위치 정보가 없는 사진은 등록할 수 없습니다. 식별 결과 화면에서 "위치 다시 가져오기"로 현재 위치를 다시 받을 수 있습니다.
- 식별 결과 화면에서 미리 알려주고, 저장 직전에 최신 기록으로 한 번 더 확인합니다.
- 앱(클라이언트)에서 확인하는 규칙이라 수정된 앱으로는 우회할 수 있습니다. 서버에서 강제하려면 Cloud Functions 로 등록을 처리해야 합니다.

## 식물 그림

지도 마커, 도감, 식별 결과, 기록 상세에 쓰는 식물 그림은 이미지 파일이 아니라 **앱이 코드로 그립니다** (`data/art/`).
도감 종은 `catalogs.json` 의 `art` 로 외형을 정하고, 도감 밖 식물은 과(family)에 맞는 형태와 학명으로 정한 색으로 자동 생성합니다.

| 필드 | 값 |
|------|----|
| `form` | `flower` 꽃잎형, `daisy` 국화형, `cluster` 작은 꽃 무리, `funnel` 나팔꽃형, `cup` 튤립·목련형, `bell` 종 모양, `iris` 붓꽃형, `spike` 꽃대·이삭, `plume` 억새·갈대, `conifer` 침엽수, `tree` 활엽수, `leaf` 잎, `rosette` 땅에 퍼진 잎, `berries` 열매, `sprout` 새싹·마디 줄기 |
| `color` / `accent` | 주색(꽃잎·잎·열매) / 보조색(꽃 중심·무늬) `#RRGGBB` |
| `petals`, `petal` | 꽃잎 수, 꽃잎 모양 `round` `pointed` `notched` `wavy` `oblong` `narrow` |
| `variant` | 형태별 세부 모양 (예: `stamen` 수술, `face` 팬지 무늬, `corona` 수선화 부화관, cluster 의 `ball`/`cone`/`spray`, conifer 의 `pine`/`narrow`, leaf 의 `clover`/`fan`/`maple`/`hand`/`holly` 등) |
| `spots`, `layers` | 꽃잎 반점, 꽃잎 겹 수 |

`./gradlew testDebugUnitTest` 를 실행하면 모든 그림을 모은 `app/build/plant-art-gallery.html` 이 만들어져 브라우저로 확인할 수 있습니다.

## 알아둘 점 / 다음 단계

- **API 키 보호**: 현재 Pl@ntNet 키가 앱(BuildConfig)에 포함됩니다. 출시 전에는 Cloud Functions 같은 서버를 거쳐 호출하도록 옮기는 것을 권장합니다. `PlantIdentifier` 인터페이스만 새로 구현하면 됩니다.
- **식물 이름 언어**: 기기 언어(예: 한국어)로 표시합니다.
  1. Pl@ntNet 에 기기 언어로 요청 (지원하지 않는 언어면 영어로 다시 요청)
  2. 그 언어 이름이 없으면 [GBIF](https://www.gbif.org/) 일반명 → [Wikidata](https://www.wikidata.org/) 라벨 순서로 찾기
  3. 그래도 없으면 영어 이름, 그것도 없으면 학명

  다른 언어 사용자가 등록한 기록도 보는 사람의 언어로 바꿔 보여줍니다. 결과는 앱 실행 중 메모리에 캐시합니다.
- 다음 단계 후보: 좋아요·댓글·팔로우, 지도에서 기록 보기, 갤러리 사진 불러오기(EXIF 날짜·위치 사용), 종별 도감 페이지, Google 로그인, 오프라인 업로드 대기열, Hilt 도입.
