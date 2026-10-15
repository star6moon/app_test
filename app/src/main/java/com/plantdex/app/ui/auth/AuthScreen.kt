package com.plantdex.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.plantdex.app.R
import com.plantdex.app.ui.components.EmojiBadge
import com.plantdex.app.ui.components.PaperCard
import com.plantdex.app.ui.components.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val container = appContainer()
    val viewModel: AuthViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthViewModel(container.authRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleFailedMessage = stringResource(R.string.auth_screen_google_failed)
    val googleFailedDetailMessage = stringResource(R.string.auth_screen_google_failed_detail)
    val googleSetupMessage = stringResource(R.string.auth_screen_google_setup_problem)

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            // 표지: 잎 그림 + 명조체 앱 이름 + 한 줄 소개
            EmojiBadge("🌿", size = 104.dp)
            Spacer(Modifier.height(4.dp))
            Text("PlantDex", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.auth_screen_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            // 입력 양식은 종이 카드 안에
            PaperCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.isSignUp) {
                        OutlinedTextField(
                            value = state.displayName,
                            onValueChange = viewModel::onDisplayNameChange,
                            label = { Text(stringResource(R.string.auth_screen_nickname)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text(stringResource(R.string.auth_screen_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(stringResource(R.string.auth_screen_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )

                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }

                    Button(
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(if (state.isSignUp) R.string.auth_screen_sign_up else R.string.auth_screen_sign_in))
                        }
                    }
                    state.infoMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            viewModel.signInWithGoogle(requestGoogleIdToken(context))
                        } catch (e: GetCredentialCancellationException) {
                            // 사용자가 창을 닫은 경우는 조용히 넘어갑니다. 하지만 Google 쪽 설정 문제
                            // (예: "[16] Account reauth failed") 도 같은 예외로 오므로 그때는 이유를 보여줍니다.
                            if (!e.isUserCancel()) {
                                viewModel.showError(googleSetupMessage.format(e.describe()))
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: GoogleSignInUnavailableException) {
                            viewModel.showError(e.message ?: googleFailedMessage)
                        } catch (e: Exception) {
                            viewModel.showError(googleFailedDetailMessage.format(e.describe()))
                        }
                    }
                },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.auth_screen_continue_with_google)) }
            TextButton(onClick = viewModel::toggleMode, enabled = !state.isLoading) {
                Text(stringResource(if (state.isSignUp) R.string.auth_screen_switch_to_sign_in else R.string.auth_screen_switch_to_sign_up))
            }
            if (!state.isSignUp) {
                TextButton(onClick = viewModel::sendPasswordReset, enabled = !state.isLoading) {
                    Text(stringResource(R.string.auth_screen_forgot_password))
                }
            }
        }
    }
}

/** 사용자가 직접 창을 닫았는지 (설정 문제로 취소된 경우와 구분) */
private fun GetCredentialCancellationException.isUserCancel(): Boolean {
    val text = message.orEmpty().lowercase()
    return text.isBlank() || "cancelled by the user" in text || "canceled by the user" in text || "user canceled" in text
}

/** 원인 확인용: 예외 종류와 메시지 */
private fun Exception.describe(): String = "${javaClass.simpleName}: ${message.orEmpty()}".take(300)
