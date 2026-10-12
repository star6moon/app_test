package com.plantdex.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
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
    val option = GetSignInWithGoogleOption.Builder(context.getString(resId)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(context).getCredential(context, request).credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    error("구글 계정 정보를 받지 못했어요.")
}
