package com.plantdex.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

/** 차단한 사용자 */
data class BlockedUser(val uid: String, val displayName: String)

enum class ReportTarget(val value: String) { Entry("entry"), Comment("comment"), User("user") }

/**
 * 신고와 차단.
 * - 신고: `reports` 에 남기고, 같은 대상에 3명 이상 신고하면 서버가 자동으로 숨깁니다.
 * - 차단: `users/{uid}/blocked/{target}`. 차단한 사용자의 기록·댓글은 내 화면에서 보이지 않고 알림도 오지 않습니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModerationRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
) {
    private fun blocked(uid: String) =
        firestore.collection(AuthRepository.USERS).document(uid).collection("blocked")

    /** 내가 차단한 사용자 (최근 순) */
    val blockedUsers: StateFlow<List<BlockedUser>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else blocked(user.uid).orderBy("createdAt", Query.Direction.DESCENDING).observe()
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 차단한 사용자 uid (기록·댓글 거르기용) */
    val blockedIds: StateFlow<Set<String>> = blockedUsers
        .map { users -> users.map { it.uid }.toSet() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    suspend fun block(targetUid: String, displayName: String) {
        val uid = requireUid()
        require(targetUid != uid) { "자기 자신은 차단할 수 없어요." }
        blocked(uid).document(targetUid)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp(), "displayName" to displayName.take(30)))
            .await()
    }

    suspend fun unblock(targetUid: String) {
        blocked(requireUid()).document(targetUid).delete().await()
    }

    suspend fun report(
        target: ReportTarget,
        targetId: String,
        reason: String,
        entryId: String? = null,
        targetOwnerId: String? = null,
    ) {
        firestore.collection("reports").add(
            mapOf(
                "reporterId" to requireUid(),
                "targetType" to target.value,
                "targetId" to targetId,
                "entryId" to entryId,
                "targetOwnerId" to targetOwnerId,
                "reason" to reason.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun requireUid() = authRepository.currentUser.value?.uid ?: error("로그인이 필요합니다.")

    private fun Query.observe(): Flow<List<BlockedUser>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map { BlockedUser(it.id, it.getString("displayName").orEmpty()) }.orEmpty())
        }
        awaitClose { registration.remove() }
    }.catch { emit(emptyList()) }

    companion object {
        val REPORT_REASONS = listOf(
            "부적절하거나 불쾌한 사진",
            "스팸·광고",
            "식물과 관계없는 내용",
            "개인정보 노출",
            "괴롭힘·혐오 표현",
        )
    }
}
