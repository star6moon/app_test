package com.plantdex.app.data.repository

import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
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
import java.io.File

/** 댓글 */
data class Comment(
    val id: String,
    val entryId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
)

/** 다른 사람에게 보이는 프로필과 통계 (통계·팔로워 수는 서버가 계산) */
data class PublicProfile(
    val uid: String,
    val displayName: String,
    val bio: String,
    val photoUrl: String?,
    val records: Int,
    val species: Int,
    val catalogSpecies: Int,
    val followerCount: Int,
    val followingCount: Int,
)

/** 앱 안의 알림 (좋아요, 댓글, 팔로우) */
data class AppNotification(
    val id: String,
    val type: String,
    val actorUid: String,
    val actorName: String,
    val entryId: String?,
    val body: String,
    val read: Boolean,
    val createdAt: Long,
)

/**
 * 댓글, 팔로우, 프로필, 알림.
 * 상대의 팔로워 목록·숫자, 댓글 수, 알림 생성은 서버(functions/src/social.ts)가 맞춥니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
) {
    private val users get() = firestore.collection(AuthRepository.USERS)
    private fun comments(entryId: String) =
        firestore.collection(CollectionRepository.ENTRIES).document(entryId).collection("comments")

    private fun requireUid() = authRepository.currentUser.value?.uid ?: error("로그인이 필요합니다.")

    // ── 댓글 ───────────────────────────────────────────

    fun observeComments(entryId: String): Flow<List<Comment>> =
        comments(entryId).orderBy("createdAt", Query.Direction.ASCENDING).limit(200)
            .snapshots { doc ->
                Comment(
                    id = doc.id,
                    entryId = entryId,
                    authorId = doc.getString("authorId").orEmpty(),
                    authorName = doc.getString("authorName").orEmpty().ifBlank { "식물 탐험가" },
                    text = doc.getString("text").orEmpty(),
                    createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                )
            }

    suspend fun addComment(entryId: String, text: String) {
        val user = authRepository.currentUser.value ?: error("로그인이 필요합니다.")
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "댓글을 입력해 주세요." }
        require(trimmed.length <= MAX_COMMENT_LENGTH) { "댓글은 ${MAX_COMMENT_LENGTH}자까지 쓸 수 있어요." }
        comments(entryId).add(
            mapOf(
                "authorId" to user.uid,
                "authorName" to user.displayName,
                "text" to trimmed,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun deleteComment(comment: Comment) {
        comments(comment.entryId).document(comment.id).delete().await()
    }

    // ── 프로필 ─────────────────────────────────────────

    fun observeProfile(uid: String): Flow<PublicProfile?> = callbackFlow {
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { registration.remove() }
    }

    /** 여러 사람의 프로필을 한 번에 (랭킹용). 30명씩 나눠 읽습니다. */
    suspend fun fetchProfiles(uids: Collection<String>): List<PublicProfile> = coroutineScope {
        uids.distinct().chunked(30).map { chunk ->
            async { users.whereIn(FieldPath.documentId(), chunk).get().await().documents.mapNotNull { it.toProfile() } }
        }.awaitAll().flatten()
    }

    /** 소개글과 프로필 사진을 바꿉니다. [avatar] 는 이미 작게 줄인 JPEG 입니다. */
    suspend fun updateProfile(bio: String, avatar: File?) {
        val uid = requireUid()
        val updates = mutableMapOf<String, Any?>("bio" to bio.trim().take(MAX_BIO_LENGTH))
        if (avatar != null) {
            val ref = storage.reference.child("users/$uid/profile/avatar.jpg")
            ref.putFile(Uri.fromFile(avatar), storageMetadata { contentType = "image/jpeg" }).await()
            updates["photoUrl"] = ref.downloadUrl.await().toString()
        }
        users.document(uid).update(updates).await()
    }

    private fun DocumentSnapshot.toProfile(): PublicProfile {
        @Suppress("UNCHECKED_CAST")
        val stats = get("stats") as? Map<String, Any?>
        fun stat(key: String) = (stats?.get(key) as? Number)?.toInt() ?: 0
        return PublicProfile(
            uid = id,
            displayName = getString("displayName").orEmpty().ifBlank { "식물 탐험가" },
            bio = getString("bio").orEmpty(),
            photoUrl = getString("photoUrl"),
            records = stat("records"),
            species = stat("species"),
            catalogSpecies = stat("catalogSpecies"),
            followerCount = (getLong("followerCount") ?: 0L).toInt().coerceAtLeast(0),
            followingCount = (getLong("followingCount") ?: 0L).toInt().coerceAtLeast(0),
        )
    }

    // ── 팔로우 ─────────────────────────────────────────

    /** 내가 팔로우하는 사람 uid. 불러오기 전에는 null */
    val followingIds: StateFlow<Set<String>?> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptySet())
            } else {
                users.document(user.uid).collection("following")
                    .snapshots { it.id }
                    .map { it.toSet() }
                    .catch { emit(emptySet()) }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun setFollowing(target: String, follow: Boolean) {
        val uid = requireUid()
        require(target != uid) { "자기 자신은 팔로우할 수 없어요." }
        val doc = users.document(uid).collection("following").document(target)
        if (follow) {
            doc.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
        } else {
            doc.delete().await()
        }
    }

    // ── 알림 ───────────────────────────────────────────

    val notifications: StateFlow<List<AppNotification>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                users.document(user.uid).collection("notifications")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .snapshots { doc ->
                        AppNotification(
                            id = doc.id,
                            type = doc.getString("type").orEmpty(),
                            actorUid = doc.getString("actorUid").orEmpty(),
                            actorName = doc.getString("actorName").orEmpty(),
                            entryId = doc.getString("entryId")?.takeIf { it.isNotBlank() },
                            body = doc.getString("body").orEmpty(),
                            read = doc.getBoolean("read") == true,
                            createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        )
                    }
                    .catch { emit(emptyList()) }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun markAllRead() {
        val uid = requireUid()
        val unread = notifications.value.filter { !it.read }
        if (unread.isEmpty()) return
        val col = users.document(uid).collection("notifications")
        firestore.batch().apply { unread.forEach { update(col.document(it.id), "read", true) } }.commit().await()
    }

    suspend fun deleteNotification(id: String) {
        users.document(requireUid()).collection("notifications").document(id).delete().await()
    }

    /** 푸시 알림 토큰을 등록합니다 (기기마다 하나, 최대 10대). */
    suspend fun registerPushToken(token: String) {
        val uid = authRepository.currentUser.value?.uid ?: return
        val ref = users.document(uid).collection("private").document("fcm")
        firestore.runTransaction { tx ->
            @Suppress("UNCHECKED_CAST")
            val current = (tx.get(ref).get("tokens") as? List<String>).orEmpty()
            if (token !in current) {
                tx.set(ref, mapOf("tokens" to (current + token).takeLast(MAX_PUSH_TOKENS)))
            }
            null
        }.await()
    }

    private fun <T> Query.snapshots(mapper: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map(mapper).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    companion object {
        const val MAX_COMMENT_LENGTH = 500
        const val MAX_BIO_LENGTH = 150
        private const val MAX_PUSH_TOKENS = 10
    }
}
