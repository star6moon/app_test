package com.plantdex.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantdex.app.data.location.LocationProvider
import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CapturedPhoto
import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.plantnet.PlantIdentificationException
import com.plantdex.app.data.plantnet.PlantIdentifier
import com.plantdex.app.data.repository.AuthRepository
import com.plantdex.app.data.repository.CollectionRepository
import com.plantdex.app.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface CaptureUiState {
    /** 카메라 미리보기 */
    data object Camera : CaptureUiState

    /** 사진 처리 + AI 식별 중 */
    data class Identifying(val photoFile: File) : CaptureUiState

    /** 식별 결과에서 후보를 고르고 도감에 등록 */
    data class Results(
        val photo: CapturedPhoto,
        val candidates: List<PlantCandidate>,
        val selectedIndex: Int = 0,
        val memo: String = "",
        val isPublic: Boolean = true,
        val isSaving: Boolean = false,
        val saveError: String? = null,
    ) : CaptureUiState {
        val selected: PlantCandidate? get() = candidates.getOrNull(selectedIndex)
    }

    /** 촬영 또는 식별 실패. [photo] 가 있으면 같은 사진으로 다시 식별할 수 있습니다. */
    data class Failed(val photo: CapturedPhoto?, val message: String) : CaptureUiState

    /** 도감 등록 완료 */
    data class Saved(val entryId: String) : CaptureUiState
}

class CaptureViewModel(
    private val plantIdentifier: PlantIdentifier,
    private val locationProvider: LocationProvider,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository,
    private val workDir: File,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Camera)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var shutterAt: Long = 0L
    private var pendingLocation: Deferred<CaptureLocation?>? = null
    private var identifyJob: Job? = null

    /** 셔터를 누른 순간: 촬영 시각을 기록하고 위치 조회를 바로 시작합니다. */
    fun onShutter() {
        shutterAt = System.currentTimeMillis()
        pendingLocation = viewModelScope.async { locationProvider.currentLocation() }
    }

    fun onPhotoSaved(rawFile: File) {
        _uiState.value = CaptureUiState.Identifying(rawFile)
        identifyJob = viewModelScope.launch {
            val prepared = try {
                ImageUtils.prepareForUpload(rawFile, File(workDir, "plant_$shutterAt.jpg"))
                    .also { rawFile.delete() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CaptureUiState.Failed(null, "사진을 처리하지 못했습니다: ${e.message}")
                return@launch
            }
            // AI 식별과 위치 조회(역지오코딩 포함)를 동시에 진행합니다.
            val identification = async { identify(prepared) }
            val photo = CapturedPhoto(
                file = prepared,
                capturedAt = shutterAt,
                location = pendingLocation?.await(),
            )
            _uiState.value = identification.await().toUiState(photo)
        }
    }

    fun onCaptureError(message: String?) {
        pendingLocation?.cancel()
        _uiState.value = CaptureUiState.Failed(null, "사진을 촬영하지 못했습니다. ${message.orEmpty()}")
    }

    fun retryIdentify() {
        val photo = (_uiState.value as? CaptureUiState.Failed)?.photo ?: return
        _uiState.value = CaptureUiState.Identifying(photo.file)
        identifyJob = viewModelScope.launch {
            _uiState.value = identify(photo.file).toUiState(photo)
        }
    }

    private sealed interface Identification {
        data class Success(val candidates: List<PlantCandidate>) : Identification
        data class Error(val message: String) : Identification
    }

    private suspend fun identify(file: File): Identification = try {
        Identification.Success(plantIdentifier.identify(file))
    } catch (e: PlantIdentificationException) {
        Identification.Error(e.message ?: "식물 식별에 실패했습니다.")
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
                CaptureUiState.Results(photo, candidates)
            }
    }

    fun selectCandidate(index: Int) = updateResults { it.copy(selectedIndex = index) }
    fun onMemoChange(memo: String) = updateResults { it.copy(memo = memo) }
    fun onPublicChange(isPublic: Boolean) = updateResults { it.copy(isPublic = isPublic) }

    fun save() {
        val state = _uiState.value as? CaptureUiState.Results ?: return
        val plant = state.selected ?: return
        val owner = authRepository.currentUser.value ?: return
        if (state.isSaving) return

        updateResults { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val id = collectionRepository.addEntry(owner, state.photo, plant, state.memo, state.isPublic)
                state.photo.file.delete()
                _uiState.value = CaptureUiState.Saved(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateResults { it.copy(isSaving = false, saveError = "저장하지 못했습니다: ${e.message}") }
            }
        }
    }

    /** 카메라 화면으로 돌아갑니다. */
    fun reset() {
        identifyJob?.cancel()
        identifyJob = null
        when (val state = _uiState.value) {
            is CaptureUiState.Results -> state.photo.file.delete()
            is CaptureUiState.Failed -> state.photo?.file?.delete()
            else -> Unit
        }
        pendingLocation?.cancel()
        pendingLocation = null
        _uiState.value = CaptureUiState.Camera
    }

    private fun updateResults(transform: (CaptureUiState.Results) -> CaptureUiState.Results) {
        _uiState.update { if (it is CaptureUiState.Results) transform(it) else it }
    }
}
