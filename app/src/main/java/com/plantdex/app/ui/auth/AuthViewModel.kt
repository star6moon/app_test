package com.plantdex.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.plantdex.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
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
    /** 비밀번호 재설정 메일을 보냈다는 안내 등 */
    val infoMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading && email.isNotBlank() && password.length >= 6 &&
            (!isSignUp || displayName.isNotBlank() && displayName.trim().length <= MAX_NAME_LENGTH)
}

private const val MAX_NAME_LENGTH = AuthRepository.MAX_NAME_LENGTH

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() = _uiState.update { it.copy(isSignUp = !it.isSignUp, errorMessage = null) }
    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value.take(MAX_NAME_LENGTH)) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                if (state.isSignUp) {
                    authRepository.signUp(state.displayName, state.email, state.password)
                } else {
                    authRepository.signIn(state.email, state.password)
                }
                // 성공하면 AuthRepository.currentUser 가 바뀌면서 메인 화면으로 전환됩니다.
                // ViewModel 이 Activity 범위라 로그아웃 후 입력값이 남지 않도록 초기화합니다.
                _uiState.value = AuthUiState()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.toMessage()) }
            }
        }
    }

    /** 구글 계정 선택 창에서 받은 토큰으로 로그인 */
    fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.signInWithGoogle(idToken)
                _uiState.value = AuthUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 이메일 로그인용 문구("비밀번호가 올바르지 않습니다")가 나오지 않도록 원인을 그대로 보여줍니다.
                val reason = "${e.javaClass.simpleName}: ${e.message.orEmpty()}".take(300)
                _uiState.update { it.copy(isLoading = false, errorMessage = "구글 로그인을 완료하지 못했어요.\n($reason)") }
            }
        }
    }

    fun showError(message: String) = _uiState.update { it.copy(errorMessage = message, infoMessage = null) }

    /** 입력한 이메일로 비밀번호 재설정 메일을 보냅니다. */
    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "비밀번호를 재설정할 이메일을 먼저 입력해 주세요.", infoMessage = null) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update { it.copy(isLoading = false, infoMessage = "$email 로 비밀번호 재설정 메일을 보냈어요. 메일함을 확인해 주세요.") }
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
