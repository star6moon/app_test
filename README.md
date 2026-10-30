# PlantDex 🌿

식물을 촬영하면 AI 가 어떤 식물인지 알려주고, 나만의 도감에 모아 다른 사용자와 공유하는 SNS 앱입니다.

## 기능

| 분류 | 기능 |
|------|------|
| 촬영·식별 | CameraX 촬영 → 서버 함수를 거쳐 [Pl@ntNet](https://my.plantnet.org/) 으로 식별 (후보 최대 5개 + 신뢰도). 꽃·잎·줄기 등 **최대 3장**을 함께 보내 정확도 향상, **자르기**로 식물만 남기기. 이름은 기기 언어로 표시 |
| 기록 | 촬영 시각·위치·지명 자동 수집, 메모, 공개 여부. 저장은 서버 함수가 위치·중복 규칙을 확인한 뒤 처리. 목록용 썸네일 자동 생성 |
| 임시저장 | 식별·등록에 실패했을 때 "임시저장"을 누르면 기기에 보관 → 촬영 화면의 *임시저장* 에서 이어서 등록 (자동 재시도는 하지 않음) |
| 주제별 도감 | 도시·바닷가·산·물가·봄·여름·가을·겨울 8개 도감(103종), 도감별 `발견 / 전체`, NEW 표시 |
| 식물 정보 | 도감 칸이나 기록에서 열기: 그림, 관찰 시기, 찾는 법, 위키백과 설명·링크, 내 기록 |
| 지도 | 내 도감 / 모두의 기록을 식물 그림 마커로 표시, 가까운 마커 묶기("능소화 등 4종", 좋아요 순). 모두의 기록은 **보이는 영역만** 불러옴(지오해시) |
| 기간 필터 | 지도: 오늘·최근 1개월·최근 3개월 + 기타(직접 선택, 1년 전 1개월 — 추후 유료 기능 후보). 내 도감은 전체(기본)도 고를 수 있고, 모두의 기록은 최근 1개월이 기본. 내 기록 목록은 항상 전체 기간 |
| 소셜 | 피드(전체/팔로잉), 좋아요·책갈피·**댓글**, **팔로우**, 프로필(사진·소개·통계), **친구 랭킹** |
| 동기 부여 | **배지** 20여 개, **주간 미션** (매주 월요일 바뀜, 계절 식물 포함) |
| 알림 | 좋아요·댓글·팔로우 → 앱 안 알림 목록 + 휴대폰 푸시(FCM). 알림을 누르면 해당 기록으로 이동. 설정에서 종류별로 끄거나 푸시만 끌 수 있음 (`private/prefs`) |
| 계정 | 이메일 / **Google 로그인**, 비밀번호 재설정 메일, 닉네임 변경, **회원 탈퇴**(내 데이터 모두 삭제) |
| 안전 | 사유별 **신고**(아래 '신고와 제재'), **차단**(서로의 기록·댓글·알림 숨김) |
| 운영 | API 키는 서버에만, App Check, 사용자별 하루 식별 한도, Crashlytics, R8 축소 릴리스 빌드, GitHub Actions CI |

## 기술 스택

- Kotlin, Jetpack Compose (Material 3), Navigation Compose (type-safe routes)
- CameraX, Google Play services Location, Google Maps Compose (마커 클러스터링)
- Firebase Auth / Firestore / Storage / **Cloud Functions** / Cloud Messaging / Crashlytics / App Check
- Credential Manager (Google 로그인), OkHttp + kotlinx.serialization, Coil 3
- minSdk 26 / targetSdk 35
- 서버: Cloud Functions (TypeScript, Node 22, 서울 리전 `asia-northeast3`)

## 프로젝트 구조

```
app/src/main/java/com/plantdex/app/
├── PlantDexApplication.kt, AppContainer.kt   # 앱 진입점, 수동 DI
├── MainActivity.kt                           # 로그인 여부에 따라 Auth / 메인 화면 전환, 알림 딥링크
├── data/
│   ├── model/        # PlantCandidate, CollectionEntry, CaptureLocation, ...
│   ├── plantnet/     # PlantIdentifier + 서버 함수(CloudPlantIdentifier) 구현
│   ├── names/        # 학명 → 사용자 언어 이름 (GBIF, Wikidata)
│   ├── taxonomy/     # 분류 체계 (계·문·강·목·과·속, GBIF)
│   ├── catalog/      # 주제별 도감 목록과 기록 매칭
│   ├── achievements/ # 배지, 주간 미션 (순수 Kotlin)
│   ├── geo/          # 지오해시 (지도 영역 검색)
│   ├── filter/       # 기간 필터
│   ├── info/         # 위키백과 요약
│   ├── location/     # 현재 위치 + 지명 조회
│   └── repository/   # Auth, Collection, Reaction, Social, Moderation, Draft
├── push/             # FCM 알림 수신, 딥링크
├── ui/               # auth, capture, collection, feed, entry, map, species,
│                     # profile, social, achievements, settings, navigation
└── util/             # 이미지 리사이즈·회전·자르기·썸네일, 포맷터
functions/            # Cloud Functions (식별 프록시, 기록 등록, 정리, 소셜, 탈퇴)
firebase/             # Firestore·Storage 보안 규칙, Firestore 색인
.github/workflows/    # CI: 단위 테스트, 디버그·릴리스 APK, 서버 함수 테스트
```

## 시작하기

> 처음 설치라면 1 → 7 순서대로 하세요. 이미 앱을 쓰고 있었다면 **2, 3, 4** 가 새로 필요합니다.

### 1. Firebase 프로젝트 준비

1. [Firebase 콘솔](https://console.firebase.google.com/)에서 프로젝트를 만들고 Android 앱을 추가합니다. 패키지 이름: `com.plantdex.app`
2. 콘솔에서 다음을 켭니다.
   - **Authentication** → 로그인 방법 → **이메일/비밀번호**, **Google**
   - **Firestore Database**, **Storage**
   - **Crashlytics** (왼쪽 메뉴 *실행* → Crashlytics → 사용 설정)
3. 요금제를 **Blaze(종량제)** 로 바꿉니다. Cloud Functions 와 비밀 키 저장(Secret Manager)에 필요합니다.
   개인·지인 규모에서는 무료 사용량 안에서 대부분 0원이지만, [예산 알림](https://console.cloud.google.com/billing)을 꼭 설정해 두세요.
4. **SHA-1 지문 등록** (Google 로그인, App Check 에 필요)
   ```bash
   ./gradlew signingReport      # Variant: debug 의 SHA1 값을 복사
   ```
   Firebase 콘솔 → 프로젝트 설정 → 내 앱 → **디지털 지문 추가**에 붙여넣습니다. 릴리스 키를 만들었다면(6번) 그 SHA-1 도 추가합니다.
5. SHA-1 을 추가한 **다음에** `google-services.json` 을 내려받아 `app/` 폴더에 넣습니다 (Google 로그인 정보가 들어 있어야 하므로 순서가 중요합니다. 저장소에는 커밋하지 않습니다).

#### 서명 키

- **개발용(debug)**: 저장소의 `app/keystore/plantdex-dev.jks` 로 서명합니다. 누가 어디서 빌드해도(PC, GitHub Actions) 지문이 같아서 Google 로그인이 됩니다. 이 지문을 Firebase 에 등록하세요.
  - SHA-1: `37:8E:5F:6E:72:40:16:4D:60:24:40:A1:A0:3A:E8:4E:91:7E:E0:E7`
  - SHA-256: `09:40:32:05:3D:B5:B9:38:15:CD:75:A6:6F:1D:89:ED:3B:9C:11:D7:0B:31:43:D5:AA:88:F6:B8:95:E0:97:FE`
- **배포용(release)**: 6번에서 만드는 개인 키를 씁니다. 개발용 키는 배포에 쓰면 안 됩니다.

### 2. 서버 함수와 규칙 배포

```bash
npm install -g firebase-tools
firebase login
firebase use --add                          # 만든 프로젝트 선택
cd functions && npm ci && cd ..
firebase functions:secrets:set PLANTNET_API_KEY   # my.plantnet.org 에서 받은 키 붙여넣기
firebase deploy --only firestore,storage,functions
```

- Pl@ntNet 키는 [my.plantnet.org](https://my.plantnet.org/) 에서 무료로 받습니다 (하루 500회). 이제 키는 **서버에만** 있고 앱에는 들어가지 않습니다. 예전에 `local.properties` 에 넣은 `PLANTNET_API_KEY` 는 지워도 됩니다.
- 첫 배포 때 `IDENTIFY_DAILY_LIMIT`(사용자 1명의 하루 식별 횟수, 기본 50), `APP_CHECK_REQUIRED`(기본 false) 값을 물어보면 엔터로 기본값을 쓰면 됩니다. 나중에 바꾸려면 `functions/.env` 파일에 `APP_CHECK_REQUIRED=true` 처럼 적고 다시 배포합니다.
- 색인 생성에는 몇 분이 걸립니다. 그동안 "Firestore 색인이 필요합니다" 메시지가 보일 수 있습니다.
- 예전 버전에서 만든 기록은 앱이 로그인 후 한 번 자동으로 새 구조(지도 영역 검색, 통계)로 바꿉니다.

#### 서버 함수 호출 권한 (식별할 때 "서버가 요청을 거부했어요" 가 나오면)

첫 배포가 중간에 실패하면 함수의 **공개 호출 허용** 설정이 빠질 수 있습니다. 함수 코드가 Firebase 로그인을 직접 확인하므로, 이 설정을 켜도 로그인하지 않은 요청은 거부됩니다.

1. `firebase deploy --only functions` 를 다시 실행해 `✔ Deploy complete!` 가 나오는지 확인합니다. 대부분 이것으로 해결됩니다.
2. 그래도 같으면 [Cloud Run 콘솔](https://console.cloud.google.com/run)에서 프로젝트를 고르고 `identifyplant` 를 누릅니다.
3. **보안(Security)** 탭 → **인증** 에서 **공개 액세스 허용(Allow public access / 인증되지 않은 호출 허용)** 을 고르고 저장합니다.
4. `createentry`, `deleteaccount`, `migratemydata` 도 같은 문제가 있으면 똑같이 설정합니다 (`on...` 으로 시작하는 함수는 필요 없음).

### 3. App Check (우리 앱만 서버를 쓰도록)

1. Firebase 콘솔 → **App Check** → 앱 → **Play Integrity** 등록.
2. 디버그 빌드는 Play Integrity 대신 *디버그 토큰*을 씁니다. 앱을 한 번 실행한 뒤 Android Studio **Logcat** 에서 `DebugAppCheckProvider` 로 검색하면
   `Enter this debug secret into the allow list ...: xxxxxxxx-...` 가 보입니다. 이 값을 App Check → 앱 메뉴(⋮) → **디버그 토큰 관리**에 추가합니다.
3. 모든 기기(지인 폰 포함)에서 잘 되는 것을 확인한 뒤 `APP_CHECK_REQUIRED=true` 로 다시 배포하면, 우리 앱이 아닌 곳에서 오는 식별 요청을 거부합니다.

### 4. 푸시 알림

새 프로젝트는 Cloud Messaging(FCM V1)이 기본으로 켜져 있어 따로 할 일이 없습니다. Android 13 이상에서는 앱을 처음 열 때 알림 권한을 묻습니다.

### 5. Google 지도 API 키

1. [Google Cloud 콘솔](https://console.cloud.google.com/)에서 Firebase 와 **같은 프로젝트**를 선택합니다.
2. **API 및 서비스 → 라이브러리**에서 **Maps SDK for Android** 를 **사용 설정**합니다.
3. **API 및 서비스 → 사용자 인증 정보 → 사용자 인증 정보 만들기 → API 키**로 키를 만듭니다.
4. (권장) 키의 **API 제한사항**을 *Maps SDK for Android* 로 제한합니다.
5. `local.properties` 에 추가합니다.
   ```properties
   MAPS_API_KEY=발급받은_키
   ```

키가 없어도 앱은 빌드되며, 지도 탭에 안내 메시지가 표시됩니다.

### 6. 릴리스(배포용) 서명 키

지인에게 나눠 주거나 Play 스토어에 올릴 APK/AAB 는 릴리스 키로 서명합니다. **이 키 파일과 비밀번호를 잃어버리면 앱을 업데이트할 수 없으니** 안전한 곳에 백업하세요.

```bash
keytool -genkey -v -keystore ~/plantdex-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias plantdex
```

`local.properties` 에 추가 (저장소에 커밋되지 않는 파일입니다):

```properties
RELEASE_STORE_FILE=/home/내이름/plantdex-release.jks
RELEASE_STORE_PASSWORD=키저장소_비밀번호
RELEASE_KEY_ALIAS=plantdex
RELEASE_KEY_PASSWORD=키_비밀번호
```

```bash
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease      # Play 스토어용 .aab
```

릴리스 빌드는 R8 로 코드를 줄이고 난독화합니다. 릴리스 키의 SHA-1 도 1-4 처럼 Firebase 에 추가하세요.

### 7. 빌드 및 실행

Android Studio 로 프로젝트를 열고 실행하거나:

```bash
./gradlew installDebug     # 연결된 기기/에뮬레이터에 설치
./gradlew testDebugUnitTest
cd functions && npm test   # 서버 함수 테스트
```

에뮬레이터에서는 카메라 대신 가상 장면이 찍히므로 실제 식물 식별은 실기기에서 테스트하는 것이 좋습니다.

### 자동 배포 (GitHub Actions, 선택)

`.github/workflows/deploy.yml` 이 push 될 때 바뀐 부분만 처리합니다.

| 바뀐 곳 | 하는 일 |
|------|------|
| `functions/`, `firebase/`, 도감 파일 | 규칙·색인·서버 함수 배포 |
| `app/` | 내 PC 와 같은 서명으로 디버그 APK 를 만들어 Firebase App Distribution 으로 폰에 보냄 |

Actions 탭 → Deploy → **Run workflow** 로 직접 실행할 수도 있고, `logs` 를 고르면 최근 24시간 서버 로그를 출력합니다.
앱 버전 번호는 커밋 수로 자동으로 정해집니다 (예: 커밋 55개 → `0.1.55`, versionCode 55). 커밋할 때마다 올라가므로 폰에 그대로 덮어 설치됩니다.
`identify-test` 를 고르면 위키백과의 식물 사진으로 Pl@ntNet 과 서버 함수 식별을 시험합니다 (사진을 직접 찍지 않아도 됨).
필요한 비밀과 변수는 파일 맨 위 주석에 있습니다. 비밀이 없으면 건너뛰기만 하고 실패로 표시하지 않습니다.

서비스 계정 역할: **편집자**, **Cloud Run 관리자**, **Secret Manager 관리자**, **서비스 계정 사용자**.

### CI (GitHub Actions)

어느 브랜치든 push 하거나 PR 을 열면 단위 테스트, 디버그·릴리스 APK 빌드, 서버 함수 테스트가 자동으로 돌아갑니다.
GitHub 저장소 → **Actions** → 실행 기록 → 아래 *Artifacts* 에서 `app-debug-apk` 를 내려받을 수 있습니다.
(CI 는 가짜 `google-services.json` 으로 빌드하므로 이 APK 는 로그인 등 서버 기능이 동작하지 않습니다. 실제 사용할 APK 는 직접 빌드하세요.)

## 식물 이름 (한국어 · 영어)

화면에는 **한국어 이름**을 크게, 그 바로 아래에 **영어 이름**을 작게, 그 아래 학명을 기울여 보여줍니다.
한국어 이름은 ① 식별·기록에 들어 있던 한국어 이름 → ② 앱 도감 → ③ 앱에 들어 있는 이름 목록 → ④ 학명의 한글 발음 순서로 고릅니다
(④는 `LatinHangul`: Helianthus annuus → 헬리안투스 안누우스. 이 경우 GBIF·Wikidata 에서 한 번 더 찾아봅니다).

이름 목록 `app/src/main/assets/plant_names.json` 은 Actions → Deploy → Run workflow → **`names`** 로 새로 만듭니다 (약 1시간, `tools/plant_names/build.py`).
Pl@ntNet 이 아는 모든 종(약 8.6만)의 학명·영어 이름에 한국어 이름을 붙여 같은 브랜치에 커밋합니다. 한국어 이름은 **국립수목원 국가표준식물목록**(공공데이터포털 '국가표준식물목록 조회 서비스', GitHub 비밀 `KPNI_SERVICE_KEY`) → Wikidata → GBIF(한국에서 관찰된 식물) 순서로 고릅니다.
봇 커밋은 자동 배포를 일으키지 않으므로, 만든 뒤 Deploy → `app` 을 한 번 실행하세요.

## 분류 체계 그래프

기록 상세와 식물 정보 화면에는 **식물계 → 문 → 강 → 목 → 과 → 속 → 종**을 계단처럼 이어 그린 그래프가 있습니다.
기록에 저장된 과·속을 먼저 그리고, [GBIF](https://www.gbif.org/) 분류 체계에서 나머지 단계를 받아 채웁니다 (옛 학명이어도 현재 분류로).
단계 이름은 앱에 넣어 둔 한국어 이름(`data/taxonomy/TaxonKoreanNames.kt`) → GBIF·Wikidata → 학명 발음 + 목·과·속 순서로 고릅니다.

## 신고와 제재

신고 사유마다 처리가 다릅니다. 같은 사람이 여러 번 신고해도 한 명으로 세고, 주인 본인의 신고는 세지 않습니다.
기준 숫자는 `functions/src/moderationPolicy.ts` 의 `POLICY` 에서 바꿀 수 있습니다.

| 사유 | 대상 | 처리 |
|------|------|------|
| 식물 정보가 일치하지 않음 | 기록 | 2명 → 비공개, 주인이 **식물 정보를 고칠 때까지** 다시 공개 불가 |
| 부적절한 사진 · 욕설/비하 · 상업적 광고 및 판매 | 기록·댓글·사용자 | 3명 → 기록 숨김 / 댓글 삭제 + 주인에게 **경고 1회** |
| 실제 식물을 촬영한 사진이 아님 · 기타 | 기록·댓글 | 3명 → 숨김 (경고 없음) |

경고는 90일 안에 3회 모이면 **활동 정지**(첫 번째 3일, 두 번째 7일, 그 뒤 30일): 기록 저장·댓글·좋아요·팔로우가 막히고 앱 위에 안내가 보입니다.
처리될 때마다 당사자에게 알림이 갑니다. 잘못된 신고로 처리된 경우에는 Firebase 콘솔에서 해당 문서의 `moderation` 이나 `private/moderation` 을 지우면 됩니다.

## 데이터 모델

**`users/{uid}`** — `displayName`, `bio`, `photoUrl`, `createdAt` + 서버가 쓰는 `stats { records, species, catalogSpecies }`, `followerCount`, `followingCount`

| 하위 컬렉션 | 내용 |
|------|------|
| `likes/{entryId}`, `bookmarks/{entryId}` | 좋아요·책갈피 (`entryId`, `createdAt`) |
| `following/{uid}` / `followers/{uid}` | 팔로우 (앱은 following 만 쓰고 followers 는 서버가 맞춤) |
| `blocked/{uid}` | 차단한 사용자 |
| `notifications/{id}` | 알림 (서버가 만들고 본인은 읽음 표시·삭제) |
| `private/fcm`, `private/prefs`, `private/quota` | 푸시 토큰 / 알림 설정(종류별 on/off, 푸시 on/off) / 하루 식별 횟수 (서버 전용) |

**`entries/{entryId}/comments/{id}`** — `authorId`, `authorName`, `text`, `createdAt`

**`reports/{id}`** — 신고 (`reasonCode` 사유 코드 + `reason` 설명, 관리자만 Firebase 콘솔에서 확인)

**`users/{uid}/private/moderation`** — 활동 정지 (`suspendedUntil`, `suspensionCount`, `activeStrikes`) · **`users/{uid}/strikes/{대상}`** — 받은 경고 (모두 서버 전용)

**`entries/{entryId}`**

| 필드 | 타입 | 설명 |
|------|------|------|
| `ownerId`, `ownerName` | string | 기록한 사용자 |
| `scientificName`, `commonName`, `family`, `genus` | string | AI 식별 결과 중 사용자가 고른 식물 |
| `commonNameLanguage` | string? | `commonName` 의 언어 (예: `ko`) |
| `gbifId` | string? | GBIF 분류군 ID (다른 언어 이름을 찾을 때 사용) |
| `score` | number | 식별 신뢰도 (0~1) |
| `photoUrl`, `photoPath`, `thumbUrl` | string | Storage 사진 (`users/{uid}/entries/{entryId}.jpg`, 썸네일 `{entryId}_thumb.jpg`) |
| `capturedAt` | timestamp | 촬영 시각 |
| `location` | geopoint? | 촬영 좌표 (위치 권한이 없으면 null) |
| `locationAccuracy` | number? | 위치 정확도(m) |
| `placeName` | string? | 역지오코딩 지명 |
| `memo` | string | 메모 |
| `isPublic` | bool | 다른 사용자에게 공개 여부 |
| `geohash` | string | 지도 영역 검색용 지오해시 (서버가 계산) |
| `likeCount` | number | 좋아요 수. 좋아요 문서와 같은 요청에서만 ±1 (보안 규칙으로 강제) |
| `commentCount` | number | 댓글 수 (서버가 계산) |
| `moderation` | string? | 신고 누적으로 숨겨지면 `hidden`, 정보 정정이 필요하면 `needs_correction` |
| `createdAt` | timestamp | 등록 시각 (서버 시각) |

보안 규칙(`firebase/firestore.rules`): 기록은 **서버 함수(`createEntry`)만 만들 수 있고**, 공개 기록은 로그인한 모든 사용자가, 비공개 기록은 본인만 읽습니다. 등록 후에는 공개 여부와 메모만 수정할 수 있고, 신고로 숨겨진 기록은 다시 공개할 수 없고, 정보 정정이 필요한 기록은 `correctEntry` 로 고친 뒤에 공개할 수 있습니다. 활동 정지 중에는 댓글·좋아요·팔로우를 쓸 수 없습니다.
기록을 지우면 서버가 사진·썸네일·댓글·좋아요·책갈피를 함께 정리하고 통계를 다시 계산합니다.

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
- 앱에서 미리 확인하고, **서버 함수(`createEntry`)가 한 번 더 확인**하므로 수정된 앱으로도 우회할 수 없습니다.

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

- **식물 이름 언어**: 기기 언어(예: 한국어)로 표시합니다.
  1. Pl@ntNet 에 기기 언어로 요청 (지원하지 않는 언어면 영어로 다시 요청)
  2. 그 언어 이름이 없으면 [GBIF](https://www.gbif.org/) 일반명 → [Wikidata](https://www.wikidata.org/) 라벨 순서로 찾기
  3. 그래도 없으면 영어 이름, 그것도 없으면 학명
- **비용**: 식별은 Pl@ntNet 무료 한도(하루 500회)를 모든 사용자가 나눠 씁니다. 사용자가 늘면 `IDENTIFY_DAILY_LIMIT` 을 줄이거나 유료 플랜을 검토하세요.
- **화면 문자열**: 화면 문구는 `res/values/strings*.xml` 에 있습니다. 서버·ViewModel 의 안내 문구와 순수 Kotlin 로직(배지·미션 이름 등)은 아직 코드 안에 있습니다.
- **추후 유료 기능 후보**: 지역·식물 종류·도감별 필터, 더 많은 임시저장, 높은 하루 식별 한도.
- 다음 단계 후보: 갤러리 사진 불러오기(EXIF 날짜·위치), 다국어(영어) 번역, 관리자 신고 처리 화면, Hilt 도입.
