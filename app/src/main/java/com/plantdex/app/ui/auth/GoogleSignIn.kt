package com.plantdex.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInUnavailableException(message: String) : Exception(message)

/**
 * 구글 계정 선택 창을 띄워 ID 토큰을 받습니다. [context] 는 Activity 여야 합니다.
 *
 * Firebase 콘솔에서 Google 로그인을 켜고 앱의 SHA-1 지문을 등록한 뒤 google-services.json 을
 * 다시 받아야 `default_web_client_id` 가 생깁니다. (없으면 안내 메시지를 보여줍니다)
 */
suspend fun requestGoogleIdToken(context: Context): String {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) {
        throw GoogleSignInUnavailableException(
            "구글 로그인이 아직 설정되지 않았어요. Firebase 콘솔에서 Google 로그인을 켠 뒤 google-services.json 을 다시 받아 주세요.",
        )
    }
    val clientId = context.getString(resId)
    val manager = CredentialManager.create(context)
    // 1) 권장 방식: 아래에서 올라오는 계정 선택창 (기기에 로그인된 구글 계정 중에서 고르기)
    val accountSheet = GetGoogleIdOption.Builder()
        .setServerClientId(clientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()
    val credential = try {
        manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(accountSheet).build()).credential
    } catch (e: NoCredentialException) {
        // 2) 기기에 구글 계정이 없거나 선택창을 쓸 수 없으면 '구글로 로그인' 화면으로 다시 시도
        val signInButton = GetSignInWithGoogleOption.Builder(clientId).build()
        manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(signInButton).build()).credential
    }
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    error("구글 계정 정보를 받지 못했어요.")
}
