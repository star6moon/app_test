package com.plantdex.app.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.plantdex.app.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    private val _currentUser = MutableStateFlow(auth.currentUser?.toProfile())

    /** 로그인한 사용자. 로그아웃 상태면 null. */
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser?.toProfile() }
    }

    /** 이메일·비밀번호로 가입했는지 (비밀번호 재설정 메뉴를 보여줄지) */
    val hasPasswordLogin: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    val email: String? get() = auth.currentUser?.email

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(displayName: String, email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("회원가입에 실패했습니다.")
        val name = displayName.trim()
        user.updateProfile(userProfileChangeRequest { this.displayName = name }).await()
        createProfile(user.uid, name)
        // updateProfile 은 AuthStateListener 를 다시 호출하지 않으므로 직접 갱신합니다.
        _currentUser.value = UserProfile(user.uid, name)
    }

    /** 구글 계정 ID 토큰으로 로그인합니다. 처음이면 프로필 문서를 만듭니다. */
    suspend fun signInWithGoogle(idToken: String) {
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            ?: error("구글 로그인에 실패했습니다.")
        val profile = firestore.collection(USERS).document(user.uid).get().await()
        if (!profile.exists()) {
            createProfile(user.uid, user.toProfile().displayName.take(MAX_NAME_LENGTH))
        }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** 닉네임 변경. 내 기록·댓글에 적힌 이름은 서버가 함께 바꿉니다. */
    suspend fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: error("로그인이 필요합니다.")
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        require(trimmed.isNotEmpty()) { "닉네임을 입력해 주세요." }
        firestore.collection(USERS).document(user.uid).update("displayName", trimmed).await()
        user.updateProfile(userProfileChangeRequest { displayName = trimmed }).await()
        _currentUser.value = UserProfile(user.uid, trimmed)
    }

    /** 회원 탈퇴: 서버가 내 기록·사진·댓글·관계를 모두 지우고 계정을 삭제합니다. */
    suspend fun deleteAccount() {
        functions.getHttpsCallable("deleteAccount").call().await()
        auth.signOut()
    }

    fun signOut() = auth.signOut()

    private suspend fun createProfile(uid: String, name: String) {
        firestore.collection(USERS).document(uid).set(
            mapOf(
                "displayName" to name,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun FirebaseUser.toProfile() = UserProfile(
        uid = uid,
        displayName = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')
            ?: "식물 탐험가",
    )

    companion object {
        const val USERS = "users"

        /** firestore.rules 의 users/{uid}.displayName 길이 제한과 같아야 합니다. */
        const val MAX_NAME_LENGTH = 30
    }
}
