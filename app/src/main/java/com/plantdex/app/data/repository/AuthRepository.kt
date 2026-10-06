package com.plantdex.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.plantdex.app.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    private val _currentUser = MutableStateFlow(auth.currentUser?.toProfile())

    /** 로그인한 사용자. 로그아웃 상태면 null. */
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser?.toProfile() }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(displayName: String, email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("회원가입에 실패했습니다.")
        val name = displayName.trim()
        user.updateProfile(userProfileChangeRequest { this.displayName = name }).await()
        firestore.collection(USERS).document(user.uid).set(
            mapOf(
                "displayName" to name,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        // updateProfile 은 AuthStateListener 를 다시 호출하지 않으므로 직접 갱신합니다.
        _currentUser.value = UserProfile(user.uid, name)
    }

    fun signOut() = auth.signOut()

    private fun FirebaseUser.toProfile() = UserProfile(
        uid = uid,
        displayName = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')
            ?: "식물 탐험가",
    )

    companion object {
        const val USERS = "users"
    }
}
