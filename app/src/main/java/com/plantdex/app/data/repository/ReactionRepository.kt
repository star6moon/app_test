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

/**
 * 좋아요와 책갈피.
 *
 * - 좋아요: `users/{uid}/likes/{entryId}` 문서 + 기록의 `likeCount` 를 한 번에(batch) 바꿉니다.
 *   보안 규칙이 두 값이 함께 ±1 씩만 바뀌는지 확인하므로 한 사람이 여러 번 누를 수 없습니다.
 * - 책갈피: `users/{uid}/bookmarks/{entryId}`. 본인만 읽고 쓸 수 있습니다.
 *
 * 화면은 Firestore 의 로컬 반영(latency compensation) 덕분에 누르는 즉시 바뀌고,
 * 서버가 거부하면 자동으로 되돌아갑니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
) {
    private fun likes(uid: String) = firestore.collection(AuthRepository.USERS).document(uid).collection(LIKES)
    private fun bookmarks(uid: String) = firestore.collection(AuthRepository.USERS).document(uid).collection(BOOKMARKS)

    /** 내가 좋아요한 기록 id */
    val myLikedIds: StateFlow<Set<String>> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptySet())
            } else {
                likes(user.uid).observeIds().map { it.toSet() }.catch { emit(emptySet()) }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 내가 책갈피한 기록 id (최근에 추가한 순). 불러오기 전에는 null */
    val myBookmarkIds: StateFlow<List<String>?> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                bookmarks(user.uid).orderBy("createdAt", Query.Direction.DESCENDING)
                    .observeIds()
                    .catch { emit(emptyList()) }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun setLiked(entryId: String, liked: Boolean) {
        val uid = authRepository.currentUser.value?.uid ?: error("로그인이 필요합니다.")
        val likeDoc = likes(uid).document(entryId)
        val entryDoc = firestore.collection(CollectionRepository.ENTRIES).document(entryId)
        firestore.batch().apply {
            if (liked) {
                set(likeDoc, mapOf("createdAt" to FieldValue.serverTimestamp()))
                update(entryDoc, "likeCount", FieldValue.increment(1))
            } else {
                delete(likeDoc)
                update(entryDoc, "likeCount", FieldValue.increment(-1))
            }
        }.commit().await()
    }

    suspend fun setBookmarked(entryId: String, bookmarked: Boolean) {
        val uid = authRepository.currentUser.value?.uid ?: error("로그인이 필요합니다.")
        val doc = bookmarks(uid).document(entryId)
        if (bookmarked) {
            doc.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
        } else {
            doc.delete().await()
        }
    }

    private fun Query.observeIds(): Flow<List<String>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map { it.id }.orEmpty())
        }
        awaitClose { registration.remove() }
    }

    companion object {
        const val LIKES = "likes"
        const val BOOKMARKS = "bookmarks"
    }
}
