package com.plantdex.app.data.repository

import androidx.annotation.StringRes
import com.plantdex.app.R
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
 * 신고 사유. [code] 는 서버(functions/src/moderationPolicy.ts)와 같고, 사유마다 처리가 다릅니다.
 * - 정보 불일치: 2명 → 주인이 정보를 고칠 때까지 비공개
 * - 부적절한 사진·욕설·광고: 3명 → 숨김(댓글은 삭제) + 주인에게 경고. 경고 3회면 활동 정지
 * - 실제 사진 아님·기타: 3명 → 숨김
 */
enum class ReportReason(val code: String, @StringRes val labelRes: Int, val targets: Set<ReportTarget>) {
    NotRealPhoto("not_real_photo", R.string.report_reason_not_real_photo, setOf(ReportTarget.Entry)),
    WrongInfo("wrong_info", R.string.report_reason_wrong_info, setOf(ReportTarget.Entry)),
    InappropriatePhoto("inappropriate_photo", R.string.report_reason_inappropriate_photo, setOf(ReportTarget.Entry, ReportTarget.User)),
    Abuse("abuse", R.string.report_reason_abuse, ReportTarget.entries.toSet()),
    Spam("spam", R.string.report_reason_spam, ReportTarget.entries.toSet()),
    Other("other", R.string.report_reason_other, ReportTarget.entries.toSet());

    companion object {
        fun forTarget(target: ReportTarget) = entries.filter { target in it.targets }
    }
}

/** 신고 누적 제재 상태 (서버만 씀): 정지가 끝나는 시각과 지금까지 받은 경고 수 */
data class Suspension(val untilMillis: Long?, val activeStrikes: Int) {
    val isSuspended: Boolean get() = untilMillis != null && untilMillis > System.currentTimeMillis()

    companion object {
        val NONE = Suspension(null, 0)
    }
}

/**
 * 신고와 차단.
 * - 신고: `reports` 에 사유 코드와 함께 남기면 서버가 사유별 기준에 따라 숨김·정보 정정 요청·경고를 처리합니다.
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

    /** 내 제재 상태. 정지가 끝나는 시각이 지나면 화면이 다시 그려질 때 자동으로 풀린 것으로 보입니다. */
    val suspension: StateFlow<Suspension> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(Suspension.NONE)
            } else {
                callbackFlow {
                    val registration = firestore.collection(AuthRepository.USERS).document(user.uid)
                        .collection("private").document("moderation")
                        .addSnapshotListener { snapshot, _ ->
                            trySend(
                                Suspension(
                                    untilMillis = snapshot?.getTimestamp("suspendedUntil")?.toDate()?.time,
                                    activeStrikes = snapshot?.getLong("activeStrikes")?.toInt() ?: 0,
                                ),
                            )
                        }
                    awaitClose { registration.remove() }
                }
            }
        }
        .catch { emit(Suspension.NONE) }
        .stateIn(scope, SharingStarted.Eagerly, Suspension.NONE)

    suspend fun report(
        target: ReportTarget,
        targetId: String,
        reason: ReportReason,
        detail: String = "",
        entryId: String? = null,
        targetOwnerId: String? = null,
    ) {
        val label = reasonLabels[reason] ?: reason.code
        firestore.collection("reports").add(
            mapOf(
                "reporterId" to requireUid(),
                "targetType" to target.value,
                "targetId" to targetId,
                "entryId" to entryId,
                "targetOwnerId" to targetOwnerId,
                "reasonCode" to reason.code,
                "reason" to (if (detail.isBlank()) label else "$label (${detail.trim()})").take(300),
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
        /** 관리자가 Firebase 콘솔에서 읽기 쉽도록 신고 문서에 함께 남기는 사유 이름 */
        private val reasonLabels = mapOf(
            ReportReason.NotRealPhoto to "실제 식물을 찍은 사진이 아님",
            ReportReason.WrongInfo to "식물 정보가 일치하지 않음",
            ReportReason.InappropriatePhoto to "부적절한 사진",
            ReportReason.Abuse to "욕설/비하",
            ReportReason.Spam to "상업적 광고 및 판매",
            ReportReason.Other to "기타",
        )
    }
}
