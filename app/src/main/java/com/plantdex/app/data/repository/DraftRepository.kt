package com.plantdex.app.data.repository

import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CapturedPhoto
import com.plantdex.app.data.model.PlantCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 기기에 임시저장한 기록. 사진은 [photoFiles] 이름으로 임시저장 폴더 안에 있습니다. */
@Serializable
data class Draft(
    val id: String,
    val savedAt: Long,
    val capturedAt: Long,
    val location: CaptureLocation?,
    /** 대표 사진이 먼저 */
    val photoFiles: List<String>,
    /** 아직 식별하지 못했으면 비어 있음 */
    val candidates: List<PlantCandidate> = emptyList(),
    val selectedIndex: Int = 0,
    val memo: String = "",
    val isPublic: Boolean = true,
) {
    val selected: PlantCandidate? get() = candidates.getOrNull(selectedIndex)
}

/**
 * 직접 누른 "임시저장" 만 다룹니다 (자동 재시도 없음).
 * 네트워크가 없거나 서버 오류로 등록하지 못했을 때 사진과 입력값을 기기에 보관했다가 나중에 이어서 등록합니다.
 * 앱 데이터 폴더(filesDir)에 저장하므로 캐시 정리로 지워지지 않고, 앱을 지우면 함께 지워집니다.
 */
class DraftRepository(filesDir: File) {
    private val root = File(filesDir, "drafts")
    private val json = Json { ignoreUnknownKeys = true }
    private val _drafts = MutableStateFlow<List<Draft>>(emptyList())

    /** 최근 순 */
    val drafts: StateFlow<List<Draft>> = _drafts.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _drafts.value = root.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { dir ->
                runCatching { json.decodeFromString<Draft>(File(dir, DRAFT_JSON).readText()) }.getOrNull()
                    ?.takeIf { d -> d.photoFiles.isNotEmpty() && d.photoFiles.all { File(dir, it).exists() } }
            }
            .sortedByDescending { it.savedAt }
    }

    suspend fun save(
        photo: CapturedPhoto,
        candidates: List<PlantCandidate>,
        selectedIndex: Int,
        memo: String,
        isPublic: Boolean,
        replaceId: String? = null,
    ): Draft = withContext(Dispatchers.IO) {
        if (_drafts.value.size >= MAX_DRAFTS && replaceId == null) {
            error("임시저장은 최대 ${MAX_DRAFTS}개까지 할 수 있어요. 등록했거나 필요 없는 임시저장을 지워 주세요.")
        }
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        try {
            val names = photo.allFiles.mapIndexed { i, file ->
                "photo_$i.jpg".also { file.copyTo(File(dir, it), overwrite = true) }
            }
            val draft = Draft(
                id = id,
                savedAt = System.currentTimeMillis(),
                capturedAt = photo.capturedAt,
                location = photo.location,
                photoFiles = names,
                candidates = candidates,
                selectedIndex = selectedIndex,
                memo = memo,
                isPublic = isPublic,
            )
            File(dir, DRAFT_JSON).writeText(json.encodeToString(Draft.serializer(), draft))
            replaceId?.let { File(root, it).deleteRecursively() }
            refresh()
            draft
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    /** 임시저장 사진 파일 (대표 사진이 먼저) */
    fun photoFiles(draft: Draft): List<File> = draft.photoFiles.map { File(File(root, draft.id), it) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(root, id).deleteRecursively()
        refresh()
    }

    companion object {
        private const val DRAFT_JSON = "draft.json"
        const val MAX_DRAFTS = 30
    }
}
