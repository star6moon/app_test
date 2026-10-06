package com.plantdex.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.plantdex.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isSignUp: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading && email.isNotBlank() && password.length >= 6 &&
            (!isSignUp || displayName.isNotBlank())
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() = _uiState.update { it.copy(isSignUp = !it.isSignUp, errorMessage = null) }
    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (state.isSignUp) {
                    authRepository.signUp(state.displayName, state.email, state.password)
                } else {
                    authRepository.signIn(state.email, state.password)
                }
                // 성공하면 AuthRepository.currentUser 가 바뀌면서 메인 화면으로 전환됩니다.
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.toMessage()) }
            }
        }
    }

    private fun Exception.toMessage(): String = when (this) {
        is FirebaseAuthWeakPasswordException -> "비밀번호는 6자 이상이어야 합니다."
        is FirebaseAuthUserCollisionException -> "이미 가입된 이메일입니다."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException,
        -> "이메일 또는 비밀번호가 올바르지 않습니다."
        else -> message ?: "알 수 없는 오류가 발생했습니다."
    }
}
