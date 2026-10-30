package com.plantdex.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantdex.app.data.catalog.Catalog
import com.plantdex.app.data.catalog.CatalogRepository
import com.plantdex.app.data.catalog.PlantCatalog
import com.plantdex.app.data.catalog.SpeciesKey
import com.plantdex.app.data.location.LocationProvider
import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CapturedPhoto
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.plantnet.PlantIdentificationException
import com.plantdex.app.data.plantnet.PlantIdentifier
import com.plantdex.app.data.repository.AuthRepository
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.data.repository.Draft
import com.plantdex.app.data.repository.DraftRepository
import com.plantdex.app.data.repository.EntrySaveException
import com.plantdex.app.data.repository.NearbyDuplicateException
import com.plantdex.app.data.rules.NearbyRecord
import com.plantdex.app.data.rules.NearbyRule
import com.plantdex.app.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

sealed interface CaptureUiState {
    /** 카메라 미리보기. [photoCount] 장을 이미 찍었으면 다른 부위를 추가로 찍는 중입니다. */
    data class Camera(val photoCount: Int = 0) : CaptureUiState

    /** 찍은 사진 확인: 자르기, 다른 부위 추가 촬영(최대 [MAX_PHOTOS]장), 식별 시작 */
    data class Review(val photos: List<File>, val isBusy: Boolean = false) : CaptureUiState {
        val canAddMore: Boolean get() = photos.size < MAX_PHOTOS
    }

    /** 사진 처리 + AI 식별 중 */
    data class Identifying(val photoFile: File) : CaptureUiState

    /** 식별 결과에서 후보를 고르고 도감에 등록 */
    data class Results(
        val photo: CapturedPhoto,
        val candidates: List<PlantCandidate>,
        /** [candidates] 와 같은 순서의 도감 정보 */
        val badges: List<CandidateBadge> = emptyList(),
        val selectedIndex: Int = 0,
        val memo: String = "",
        val isPublic: Boolean = true,
        val isSaving: Boolean = false,
        val saveError: String? = null,
        /** [candidates] 와 같은 순서: 같은 종을 이미 등록한 반경 안의 내 기록 (없으면 null) */
        val nearby: List<NearbyRecord?> = emptyList(),
        /** 위치를 다시 가져오는 중 */
        val isLocating: Boolean = false,
    ) : CaptureUiState {
        val selected: PlantCandidate? get() = candidates.getOrNull(selectedIndex)
        val selectedNearby: NearbyRecord? get() = nearby.getOrNull(selectedIndex)
        val hasLocation: Boolean get() = photo.location != null

        /** 위치가 있고, 고른 종을 반경 안에 이미 등록하지 않았을 때만 등록할 수 있습니다. */
        val canSave: Boolean get() = !isSaving && selected != null && hasLocation && selectedNearby == null
    }

    /** 촬영 또는 식별 실패. [photo] 가 있으면 같은 사진으로 다시 식별할 수 있습니다. */
    data class Failed(val photo: CapturedPhoto?, val message: String) : CaptureUiState

    /** 도감 등록 완료. 새로운 종을 발견했다면 [message] 로 축하 문구를 전달합니다. */
    data class Saved(val entryId: String, val message: String?) : CaptureUiState
}

/** 한 번에 식별에 보낼 수 있는 사진 수 (Pl@ntNet 한도는 5장이지만 업로드 크기를 고려해 3장) */
const val MAX_PHOTOS = 3

/** 후보 식물이 들어 있는 주제별 도감과, 내가 아직 발견하지 못한 종인지 여부 */
data class CandidateBadge(val catalogs: List<Catalog>, val isNew: Boolean)

class CaptureViewModel(
    private val plantIdentifier: PlantIdentifier,
    private val locationProvider: LocationProvider,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val catalogRepository: CatalogRepository,
    private val draftRepository: DraftRepository,
    private val workDir: File,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Camera())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /** 토스트로 한 번 보여줄 안내 */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    val drafts: StateFlow<List<Draft>> = draftRepository.drafts

    /** 지금 찍고 있는 사진들 (대표 사진이 먼저) */
    private var photos: List<File> = emptyList()

    /** 임시저장에서 이어서 작업 중이면 그 id. 등록에 성공하면 임시저장을 지웁니다. */
    private var openedDraftId: String? = null

    init {
        viewModelScope.launch { draftRepository.refresh() }
    }

    private var shutterAt: Long = 0L
    private var pendingLocation: Deferred<CaptureLocation?>? = null
    private var identifyJob: Job? = null
    private var locationJob: Job? = null

    /** 식별 시점에 내가 이미 발견한 도감 종 id. 불러오지 못했으면 null (모름) */
    private var collectedSpeciesIds: Set<String>? = null

    /** 식별 시점에 불러온 내 기록. 불러오지 못했으면 null (모름) */
    private var myEntries: List<CollectionEntry>? = null
    private var catalog: PlantCatalog? = null

    /** 셔터를 누른 순간: 첫 사진이면 촬영 시각을 기록하고 위치 조회를 바로 시작합니다. */
    fun onShutter() {
        if (photos.isNotEmpty()) return
        shutterAt = System.currentTimeMillis()
        pendingLocation = viewModelScope.async { locationProvider.currentLocation() }
    }

    fun onPhotoSaved(rawFile: File) {
        _uiState.value = CaptureUiState.Review(photos, isBusy = true)
        identifyJob = viewModelScope.launch {
            try {
                val prepared = ImageUtils.prepareForUpload(rawFile, newWorkFile())
                rawFile.delete()
                photos = photos + prepared
                _uiState.value = CaptureUiState.Review(photos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rawFile.delete()
                if (photos.isEmpty()) {
                    _uiState.value = CaptureUiState.Failed(null, "사진을 처리하지 못했습니다: ${e.message}")
                } else {
                    _uiState.value = CaptureUiState.Review(photos)
                    _notices.tryEmit("사진을 처리하지 못했어요. 다시 찍어 주세요.")
                }
            }
        }
    }

    fun onCaptureError(message: String?) {
        if (photos.isEmpty()) {
            pendingLocation?.cancel()
            _uiState.value = CaptureUiState.Failed(null, "사진을 촬영하지 못했습니다. ${message.orEmpty()}")
        } else {
            _uiState.value = CaptureUiState.Review(photos)
            _notices.tryEmit("사진을 촬영하지 못했어요. ${message.orEmpty()}")
        }
    }

    /** 다른 부위(잎, 꽃, 줄기…)를 추가로 찍습니다. */
    fun addMorePhoto() {
        if (photos.isEmpty() || photos.size >= MAX_PHOTOS) return
        _uiState.value = CaptureUiState.Camera(photoCount = photos.size)
    }

    /** 추가 촬영을 그만두고 확인 화면으로 돌아갑니다. */
    fun backToReview() {
        if (photos.isNotEmpty()) _uiState.value = CaptureUiState.Review(photos)
    }

    fun removePhoto(index: Int) {
        val target = photos.getOrNull(index) ?: return
        if (photos.size == 1) {
            reset()
            return
        }
        target.delete()
        photos = photos.filterIndexed { i, _ -> i != index }
        _uiState.value = CaptureUiState.Review(photos)
    }

    /** [index] 사진에서 식물 부분만 잘라냅니다. 좌표는 0..1 비율. */
    fun cropPhoto(index: Int, rect: CropRect) {
        val source = photos.getOrNull(index) ?: return
        if (rect.isFull) return
        _uiState.value = CaptureUiState.Review(photos, isBusy = true)
        viewModelScope.launch {
            try {
                val cropped = ImageUtils.crop(source, newWorkFile(), rect.left, rect.top, rect.right, rect.bottom)
                source.delete()
                photos = photos.mapIndexed { i, f -> if (i == index) cropped else f }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notices.tryEmit("사진을 자르지 못했어요: ${e.message}")
            }
            _uiState.value = CaptureUiState.Review(photos)
        }
    }

    /** 찍은 사진 모두로 AI 식별을 시작합니다. */
    fun identifyPhotos() {
        val files = photos
        if (files.isEmpty()) return
        _uiState.value = CaptureUiState.Identifying(files.first())
        identifyJob = viewModelScope.launch {
            // AI 식별과 위치 조회(역지오코딩 포함)를 동시에 진행합니다.
            val identification = async { identify(files) }
            val photo = CapturedPhoto(
                file = files.first(),
                capturedAt = shutterAt,
                location = pendingLocation?.await(),
                extraFiles = files.drop(1),
            )
            _uiState.value = identification.await().toUiState(photo)
        }
    }

    fun retryIdentify() {
        val photo = (_uiState.value as? CaptureUiState.Failed)?.photo ?: return
        _uiState.value = CaptureUiState.Identifying(photo.file)
        identifyJob = viewModelScope.launch {
            _uiState.value = identify(photo.allFiles).toUiState(photo)
        }
    }

    private fun newWorkFile() = File(workDir, "plant_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")

    private sealed interface Identification {
        data class Success(val candidates: List<PlantCandidate>, val badges: List<CandidateBadge>) : Identification
        data class Error(val message: String) : Identification
    }

    private suspend fun identify(files: List<File>): Identification = try {
        val candidates = plantIdentifier.identify(files)
        Identification.Success(candidates, catalogBadges(candidates))
    } catch (e: PlantIdentificationException) {
        Identification.Error(e.message ?: "식물 식별에 실패했습니다.")
    }

    /** 도감 정보는 부가 기능이라 실패해도 식별 결과는 그대로 보여줍니다. */
    private suspend fun catalogBadges(candidates: List<PlantCandidate>): List<CandidateBadge> = try {
        // 이전 사진에서 불러온 값이 남아 잘못 판단하지 않도록 먼저 비웁니다.
        myEntries = null
        this.catalog = null
        collectedSpeciesIds = null
        val catalog = withContext(Dispatchers.Default) { catalogRepository.catalog }
        this.catalog = catalog
        val entries = loadMyEntries()
        myEntries = entries
        val collected = entries?.mapNotNull { catalog.match(it.scientificName)?.id }?.toSet()
        collectedSpeciesIds = collected
        candidates.map { candidate ->
            val species = catalog.match(candidate.scientificName)
            if (species == null) {
                CandidateBadge(emptyList(), isNew = false)
            } else {
                // 내 기록을 불러오지 못했으면 NEW 여부를 단정하지 않습니다.
                CandidateBadge(catalog.catalogsOf(species.id), isNew = collected != null && species.id !in collected)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun loadMyEntries(): List<CollectionEntry>? {
        val uid = authRepository.currentUser.value?.uid ?: return null
        return try {
            withTimeoutOrNull(3_000) { collectionRepository.observeMyEntries(uid).first() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** 후보마다 같은 종을 반경 안에 이미 등록했는지 확인합니다. 위치나 내 기록을 모르면 null. */
    private fun nearbyFor(candidates: List<PlantCandidate>, location: CaptureLocation?): List<NearbyRecord?> {
        val entries = myEntries
        if (location == null || entries == null) return candidates.map { null }
        val catalog = this.catalog ?: runCatching { catalogRepository.catalog }.getOrNull()
        return candidates.map { candidate ->
            NearbyRule.findNearby(
                speciesKey = SpeciesKey.of(candidate.scientificName, catalog),
                location = location,
                myEntries = entries,
                speciesKeyOf = { SpeciesKey.of(it.scientificName, catalog) },
            )
        }
    }

    /** 처음 발견한 종이면 "🎉 새로 발견! 서양민들레 — 🏙️ 도시의 꽃 13/40 · 🌸 봄꽃 9/34" */
    private fun discoveryMessage(plant: PlantCandidate): String? {
        val catalog = runCatching { catalogRepository.catalog }.getOrNull() ?: return null
        val species = catalog.match(plant.scientificName) ?: return null
        val known = collectedSpeciesIds ?: return null
        if (species.id in known) return null
        val collected = known + species.id
        collectedSpeciesIds = collected
        val progress = catalog.catalogsOf(species.id).joinToString(" · ") { c ->
            "${c.emoji} ${c.title} ${c.speciesIds.count { it in collected }}/${c.speciesIds.size}"
        }
        return "🎉 새로 발견! ${species.name} — $progress"
    }

    private fun Identification.toUiState(photo: CapturedPhoto): CaptureUiState = when (this) {
        is Identification.Error -> CaptureUiState.Failed(photo, message)
        is Identification.Success ->
            if (candidates.isEmpty()) {
                CaptureUiState.Failed(
                    photo,
                    "어떤 식물인지 찾지 못했어요.\n잎이나 꽃이 화면 가운데에 크게 나오도록 다시 찍어 주세요.",
                )
            } else {
                CaptureUiState.Results(photo, candidates, badges, nearby = nearbyFor(candidates, photo.location))
            }
    }

    fun selectCandidate(index: Int) = updateResults { it.copy(selectedIndex = index) }
    fun onMemoChange(memo: String) = updateResults { it.copy(memo = memo.take(MAX_MEMO)) }
    fun onPublicChange(isPublic: Boolean) = updateResults { it.copy(isPublic = isPublic) }

    /** 촬영 때 위치를 얻지 못했으면 지금 위치로 다시 시도합니다. */
    fun retryLocation() {
        val state = _uiState.value as? CaptureUiState.Results ?: return
        if (state.isLocating) return
        updateResults { it.copy(isLocating = true, saveError = null) }
        locationJob = viewModelScope.launch {
            val location = locationProvider.currentLocation()
            updateResults {
                // 그사이 다시 찍었다면 다른 사진이므로 적용하지 않습니다.
                if (it.photo.file != state.photo.file) {
                    it
                } else if (location == null) {
                    it.copy(
                        isLocating = false,
                        saveError = "위치를 가져오지 못했어요. 위치 권한과 GPS 를 확인해 주세요.",
                    )
                } else {
                    it.copy(
                        photo = it.photo.copy(location = location),
                        nearby = nearbyFor(it.candidates, location),
                        isLocating = false,
                    )
                }
            }
        }
    }

    fun save() {
        val state = _uiState.value as? CaptureUiState.Results ?: return
        val plant = state.selected ?: return
        val owner = authRepository.currentUser.value ?: return
        if (state.isSaving) return
        val location = state.photo.location
        if (location == null) {
            updateResults { it.copy(saveError = "위치 정보가 있어야 등록할 수 있어요.") }
            return
        }

        updateResults { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                // 식별 후에 다른 기기에서 등록했을 수도 있으니 저장 직전에 서버의 최신 기록으로 다시 확인합니다.
                val latest = try {
                    withTimeoutOrNull(8_000) { collectionRepository.fetchMyEntriesFromServer(owner.uid) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (latest == null) {
                    updateResults {
                        it.copy(isSaving = false, saveError = "기존 기록을 확인하지 못했어요. 네트워크 연결을 확인하고 다시 시도해 주세요.")
                    }
                    return@launch
                }
                myEntries = latest
                (this@CaptureViewModel.catalog ?: runCatching { catalogRepository.catalog }.getOrNull())?.let { catalog ->
                    collectedSpeciesIds = latest.mapNotNull { catalog.match(it.scientificName)?.id }.toSet()
                }
                val nearby = nearbyFor(state.candidates, location)
                if (nearby.getOrNull(state.selectedIndex) != null) {
                    updateResults { it.copy(isSaving = false, nearby = nearby) }
                    return@launch
                }
                val id = collectionRepository.addEntry(owner, state.photo, plant, state.memo, state.isPublic)
                state.photo.allFiles.forEach { it.delete() }
                photos = emptyList()
                openedDraftId?.let { draftId ->
                    openedDraftId = null
                    runCatching { draftRepository.delete(draftId) }
                }
                _uiState.value = CaptureUiState.Saved(id, discoveryMessage(plant))
            } catch (e: CancellationException) {
                throw e
            } catch (e: NearbyDuplicateException) {
                updateResults { it.copy(isSaving = false, saveError = e.message) }
            } catch (e: EntrySaveException) {
                updateResults { it.copy(isSaving = false, saveError = e.message) }
            } catch (e: Exception) {
                updateResults { it.copy(isSaving = false, saveError = "저장하지 못했습니다: ${e.message}") }
            }
        }
    }

    /** 카메라 화면으로 돌아갑니다. */
    fun reset() {
        identifyJob?.cancel()
        identifyJob = null
        locationJob?.cancel()
        locationJob = null
        when (val state = _uiState.value) {
            is CaptureUiState.Results -> state.photo.allFiles.forEach { it.delete() }
            is CaptureUiState.Failed -> state.photo?.allFiles?.forEach { it.delete() }
            else -> Unit
        }
        photos.forEach { it.delete() }
        photos = emptyList()
        openedDraftId = null
        pendingLocation?.cancel()
        pendingLocation = null
        _uiState.value = CaptureUiState.Camera()
    }

    /**
     * 지금 화면의 사진과 입력값을 기기에 임시저장하고 카메라로 돌아갑니다.
     * 식별 결과가 있으면 함께 저장하고, 식별 전이면 나중에 열 때 다시 식별합니다.
     */
    fun saveDraft() {
        val (photo, candidates, selected, memo, isPublic) = when (val state = _uiState.value) {
            is CaptureUiState.Results -> {
                if (state.isSaving) return
                DraftInput(state.photo, state.candidates, state.selectedIndex, state.memo, state.isPublic)
            }
            is CaptureUiState.Failed -> DraftInput(state.photo ?: return, emptyList(), 0, "", true)
            else -> return
        }
        viewModelScope.launch {
            try {
                draftRepository.save(photo, candidates, selected, memo, isPublic, replaceId = openedDraftId)
                openedDraftId = null
                _notices.tryEmit("임시저장했어요. 촬영 화면 위의 '임시저장' 에서 이어서 등록할 수 있어요.")
                reset()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notices.tryEmit(e.message ?: "임시저장하지 못했어요.")
            }
        }
    }

    private data class DraftInput(
        val photo: CapturedPhoto,
        val candidates: List<PlantCandidate>,
        val selectedIndex: Int,
        val memo: String,
        val isPublic: Boolean,
    )

    /** 임시저장을 열어 이어서 등록합니다. 식별 결과가 없으면 다시 식별합니다. */
    fun openDraft(draft: Draft) {
        reset()
        identifyJob = viewModelScope.launch {
            // 등록에 성공하면 작업 파일을 지우므로, 임시저장 원본은 두고 복사본으로 작업합니다.
            val copies = try {
                withContext(Dispatchers.IO) {
                    draftRepository.photoFiles(draft).map { it.copyTo(newWorkFile(), overwrite = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notices.tryEmit("임시저장 사진을 열지 못했어요.")
                return@launch
            }
            openedDraftId = draft.id
            shutterAt = draft.capturedAt
            val photo = CapturedPhoto(copies.first(), draft.capturedAt, draft.location, copies.drop(1))
            _uiState.value = CaptureUiState.Identifying(photo.file)
            _uiState.value = if (draft.candidates.isEmpty()) {
                identify(photo.allFiles).toUiState(photo)
            } else {
                CaptureUiState.Results(
                    photo = photo,
                    candidates = draft.candidates,
                    badges = catalogBadges(draft.candidates),
                    selectedIndex = draft.selectedIndex.coerceIn(0, draft.candidates.lastIndex),
                    memo = draft.memo,
                    isPublic = draft.isPublic,
                    nearby = nearbyFor(draft.candidates, draft.location),
                )
            }
        }
    }

    fun deleteDraft(draft: Draft) {
        viewModelScope.launch { draftRepository.delete(draft.id) }
    }

    private fun updateResults(transform: (CaptureUiState.Results) -> CaptureUiState.Results) {
        _uiState.update { if (it is CaptureUiState.Results) transform(it) else it }
    }
}

/** 메모 최대 글자 수 (서버 규칙과 같음) */
const val MAX_MEMO = 500
